package com.downtify.app.data.repository

import com.downtify.app.data.database.DownloadedVideoDao
import com.downtify.app.data.database.DownloadedVideoEntity
import com.downtify.app.data.database.DownloadedTrackDao
import com.downtify.app.data.database.DownloadedTrackEntity
import com.downtify.app.data.database.MonitoredPlaylistDao
import com.downtify.app.data.database.MonitoredPlaylistEntity
import com.downtify.app.data.local.CategorizedSearch
import com.downtify.app.data.local.DownloadPipeline
import com.downtify.app.data.local.DownloadProgress
import com.downtify.app.data.local.M3UGenerator
import com.downtify.app.data.local.SoundCloudScraper
import com.downtify.app.data.local.SpotifyScraper
import com.downtify.app.data.local.YouTubeExtractor
import com.downtify.app.domain.model.AppSettings
import com.downtify.app.domain.model.Song
import com.downtify.app.domain.model.VideoFormat
import com.downtify.app.domain.model.VideoQuality
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DowntifyRepository @Inject constructor(
    private val spotifyScraper: SpotifyScraper,
    private val soundCloudScraper: SoundCloudScraper,
    private val youTubeExtractor: YouTubeExtractor,
    private val downloadPipeline: DownloadPipeline,
    private val m3uGenerator: M3UGenerator,
    private val trackDao: DownloadedTrackDao,
    private val playlistDao: MonitoredPlaylistDao,
    private val videoDao: DownloadedVideoDao,
    private val settingsRepository: SettingsRepository
) {
    private var downloadSemaphore = Semaphore(3)
    private var currentMaxParallel = 3

    private fun updateSemaphore(maxParallel: Int) {
        if (currentMaxParallel != maxParallel) {
            downloadSemaphore = Semaphore(maxParallel)
            currentMaxParallel = maxParallel
        }
    }

    // Search
    suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        youTubeExtractor.searchSongs(query)
    }

    suspend fun searchAll(query: String): CategorizedSearch = withContext(Dispatchers.IO) {
        youTubeExtractor.searchAll(query)
    }

    suspend fun searchMore(query: String, nextPage: Page): CategorizedSearch = withContext(Dispatchers.IO) {
        youTubeExtractor.searchMore(query, nextPage)
    }

    suspend fun getPlaylistTracks(playlistId: String): List<Song> = withContext(Dispatchers.IO) {
        youTubeExtractor.getPlaylistTracks(playlistId)
    }

    // URL Resolution
    suspend fun resolveSpotifyUrl(url: String): List<Song> = withContext(Dispatchers.IO) {
        val parsed = spotifyScraper.parseUrl(url)
            ?: throw IllegalArgumentException("Invalid Spotify URL")
        
        when (parsed.type) {
            SpotifyScraper.Type.TRACK -> listOf(spotifyScraper.resolveTrack(parsed.id))
            SpotifyScraper.Type.ALBUM -> spotifyScraper.resolveAlbum(parsed.id)
            SpotifyScraper.Type.PLAYLIST -> spotifyScraper.resolvePlaylist(parsed.id)
            else -> throw IllegalArgumentException("Unsupported Spotify type: ${parsed.type}")
        }
    }

    suspend fun resolveSoundCloudUrl(url: String): List<Song> = withContext(Dispatchers.IO) {
        val settings = settingsRepository.settingsFlow.first()
        val token = settings.soundCloudOAuthToken
        val result = soundCloudScraper.resolveTrack(url, token)
        listOf(result.song.copy(streamUrl = result.hlsStreamUrl))
    }

    // Downloads
    suspend fun isSongDownloaded(songId: String): Boolean {
        val existing = trackDao.getTrackBySongId(songId) ?: return false
        return File(existing.filePath).exists()
    }

    fun downloadSong(
        song: Song,
        settings: AppSettings
    ): Flow<DownloadProgress> = channelFlow {
        val existing = trackDao.getTrackBySongId(song.songId)
        if (existing != null && File(existing.filePath).exists()) {
            send(DownloadProgress(DownloadProgress.Stage.DONE, 100f, "Already downloaded"))
            return@channelFlow
        }

        send(DownloadProgress(DownloadProgress.Stage.QUEUED, 0f, "Waiting in queue..."))
        updateSemaphore(settings.maxParallelDownloads)
        downloadSemaphore.withPermit {
            downloadPipeline.download(
                song = song,
                format = settings.audioFormat,
                bitrate = "${settings.bitrate.value}k",
                downloadLyrics = settings.downloadLyrics,
                organizeByArtist = settings.organizeByArtist,
                organizeByAlbum = settings.organizeByAlbum
            ).collect { progress ->
                if (progress.stage == DownloadProgress.Stage.DONE) {
                    val filePath = progress.message // We passed the path in the message
                    val trackEntity = DownloadedTrackEntity(
                        songId = song.songId,
                        name = song.name,
                        artists = song.artists.joinToString(", "),
                        albumName = song.albumName,
                        coverUrl = song.coverUrl,
                        duration = song.duration,
                        filePath = filePath,
                        fileSize = File(filePath).length(),
                        source = song.source
                    )
                    saveTrack(trackEntity)
                    send(progress.copy(message = "Done"))
                } else {
                    send(progress)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    // Library
    fun getLocalTracks(): Flow<List<DownloadedTrackEntity>> = trackDao.getAllTracks()

    suspend fun saveTrack(track: DownloadedTrackEntity): Long = trackDao.insertTrack(track)

    suspend fun deleteTrack(track: DownloadedTrackEntity) = trackDao.deleteTrack(track)

    suspend fun deleteTrackById(id: Int) = trackDao.deleteTrackById(id)

    // Video
    fun getLocalVideos(): Flow<List<DownloadedVideoEntity>> = videoDao.getAllVideos()

    suspend fun saveVideo(video: DownloadedVideoEntity): Long = videoDao.insertVideo(video)

    suspend fun deleteVideo(video: DownloadedVideoEntity) = videoDao.deleteVideo(video)

    suspend fun deleteVideoById(id: Int) = videoDao.deleteVideoById(id)

    suspend fun isVideoDownloaded(songId: String): Boolean = 
        videoDao.getVideoBySongId(songId) != null

    suspend fun getAvailableVideoQualities(song: Song): List<com.downtify.app.data.local.VideoStreamInfo> =
        youTubeExtractor.getAvailableVideoQualities(song)

    fun downloadVideo(
        song: Song,
        settings: AppSettings,
        videoFormat: VideoFormat = settings.videoFormat,
        videoQuality: VideoQuality = settings.videoQuality
    ): Flow<DownloadProgress> = channelFlow {
        val existing = videoDao.getVideoBySongId(song.songId)
        if (existing != null && File(existing.filePath).exists()) {
            send(DownloadProgress(DownloadProgress.Stage.DONE, 100f, "Already downloaded"))
            return@channelFlow
        }

        send(DownloadProgress(DownloadProgress.Stage.QUEUED, 0f, "Waiting in queue..."))
        downloadSemaphore.withPermit {
            downloadPipeline.downloadVideo(
                song = song,
                format = videoFormat,
                quality = videoQuality,
                organizeByArtist = settings.organizeVideosByArtist,
                organizeByAlbum = settings.organizeVideosByAlbum
            ).collect { progress ->
                if (progress.stage == DownloadProgress.Stage.DONE) {
                    val filePath = progress.message
                    val videoEntity = DownloadedVideoEntity(
                        songId = song.songId,
                        name = song.name,
                        artists = song.artists.joinToString(", "),
                        albumName = song.albumName,
                        coverUrl = song.coverUrl,
                        duration = song.duration,
                        filePath = filePath,
                        fileSize = File(filePath).length(),
                        source = song.source,
                        videoFormat = videoFormat.extension,
                        videoQuality = videoQuality.label
                    )
                    saveVideo(videoEntity)
                    send(progress.copy(message = "Done"))
                } else {
                    send(progress)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    // Monitor
    fun getMonitoredPlaylists(): Flow<List<MonitoredPlaylistEntity>> = 
        playlistDao.getAllPlaylists()

    suspend fun addMonitoredPlaylist(
        spotifyId: String,
        name: String,
        url: String,
        intervalMinutes: Int
    ): MonitoredPlaylistEntity {
        val playlist = MonitoredPlaylistEntity(
            spotifyId = spotifyId,
            name = name,
            url = url,
            intervalMinutes = intervalMinutes
        )
        val id = playlistDao.insertPlaylist(playlist)
        return playlist.copy(id = id.toInt())
    }

    suspend fun updatePlaylistEnabled(id: Int, enabled: Boolean) = 
        playlistDao.updateEnabled(id, enabled)

    suspend fun updatePlaylistInterval(id: Int, interval: Int) = 
        playlistDao.updateInterval(id, interval)

    suspend fun deletePlaylist(id: Int) = playlistDao.deletePlaylistById(id)

    suspend fun updateLastCheck(id: Int, trackCount: Int) = 
        playlistDao.updateLastCheck(id, System.currentTimeMillis(), trackCount)

    // M3U
    suspend fun generateM3U(playlistName: String, entries: List<M3UGenerator.M3UEntry>): File? {
        return m3uGenerator.generateM3U(playlistName, entries)
    }
}
