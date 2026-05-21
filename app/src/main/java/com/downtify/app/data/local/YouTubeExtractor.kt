package com.downtify.app.data.local

import android.util.Log
import com.downtify.app.domain.model.Song
import com.downtify.app.domain.model.VideoQuality
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.Page
import javax.inject.Inject
import javax.inject.Singleton

private fun StreamInfoItem.toSong(): Song = Song(
    songId = url.substringAfter("v="),
    name = name,
    artists = listOf(uploaderName ?: "Unknown"),
    albumName = null,
    coverUrl = thumbnails.firstOrNull()?.url ?: "",
    duration = duration.toInt(),
    url = url,
    explicit = false,
    source = "youtube"
)

data class ArtistSearchResult(
    val name: String,
    val channelId: String,
    val thumbnailUrl: String?,
    val subscriberCount: Long = 0
)

data class PlaylistSearchResult(
    val name: String,
    val playlistId: String,
    val thumbnailUrl: String?,
    val trackCount: Int = 0,
    val uploaderName: String? = null
)

data class CategorizedSearch(
    val songs: List<Song> = emptyList(),
    val artists: List<ArtistSearchResult> = emptyList(),
    val playlists: List<PlaylistSearchResult> = emptyList(),
    val nextPage: Page? = null
)

data class VideoStreamInfo(
    val videoUrl: String,
    val audioUrl: String?,
    val resolution: String,
    val format: String
)

@Singleton
class YouTubeExtractor @Inject constructor() {

    companion object {
        private const val TAG = "YouTubeExtractor"
        private const val YT_MUSIC_BASE = "https://music.youtube.com"
        private var isInitialized = false
        
        @Synchronized
        fun initialize() {
            if (isInitialized) {
                Log.d(TAG, "Already initialized")
                return
            }
            try {
                val existingDownloader = NewPipe.getDownloader()
                if (existingDownloader == null) {
                    Log.d(TAG, "Initializing NewPipe with AndroidDownloader")
                    NewPipe.init(
                        AndroidDownloader(),
                        Localization.DEFAULT,
                        ContentCountry.DEFAULT
                    )
                } else {
                    Log.d(TAG, "Downloader already set: ${existingDownloader.javaClass.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking downloader, reinitializing", e)
                NewPipe.init(
                    AndroidDownloader(),
                    Localization.DEFAULT,
                    ContentCountry.DEFAULT
                )
            }
            isInitialized = true
            Log.d(TAG, "NewPipe initialized")
        }
    }

    init {
        initialize()
    }

    suspend fun searchAll(query: String): CategorizedSearch {
        Log.d(TAG, "Searching YouTube (all categories) for: $query")
        return try {
            val service = NewPipe.getService(ServiceList.YouTube.serviceId) as YoutubeService
            val searchInfo = SearchInfo.getInfo(
                service,
                service.searchQHFactory.fromQuery(query, emptyList(), "")
            )

            val allItems = searchInfo.relatedItems
            Log.d(TAG, "Total related items: ${allItems.size}")

            val songs = allItems.filterIsInstance<StreamInfoItem>()
                .map { it.toSong() }

            val artists = allItems.filterIsInstance<ChannelInfoItem>()
                .map { item ->
                    ArtistSearchResult(
                        name = item.name,
                        channelId = item.url.substringAfter("/channel/"),
                        thumbnailUrl = item.thumbnails.firstOrNull()?.url,
                        subscriberCount = item.subscriberCount
                    )
                }

            val playlists = allItems.filterIsInstance<PlaylistInfoItem>()
                .map { item ->
                    PlaylistSearchResult(
                        name = item.name,
                        playlistId = item.url.substringAfter("list="),
                        thumbnailUrl = item.thumbnails.firstOrNull()?.url,
                        trackCount = item.streamCount.toInt(),
                        uploaderName = item.uploaderName
                    )
                }

            val nextPage = try { searchInfo.nextPage } catch (_: Exception) { null }
            val result = CategorizedSearch(songs, artists, playlists, nextPage)
            Log.d(TAG, "searchAll: ${songs.size} songs, ${artists.size} artists, ${playlists.size} playlists, hasMore=${nextPage != null}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "searchAll failed for query: '$query'", e)
            throw e
        }
    }

    suspend fun searchMore(query: String, nextPage: Page): CategorizedSearch {
        Log.d(TAG, "Loading more results for: $query")
        return try {
            val service = NewPipe.getService(ServiceList.YouTube.serviceId) as YoutubeService
            val searchQuery = service.searchQHFactory.fromQuery(query, emptyList(), "")
            val page = SearchInfo.getMoreItems(service, searchQuery, nextPage)
            val items = page.items
            Log.d(TAG, "Got ${items.size} more items")

            val songs = items.filterIsInstance<StreamInfoItem>()
                .map { it.toSong() }

            val artists = items.filterIsInstance<ChannelInfoItem>()
                .map { item ->
                    ArtistSearchResult(
                        name = item.name,
                        channelId = item.url.substringAfter("/channel/"),
                        thumbnailUrl = item.thumbnails.firstOrNull()?.url,
                        subscriberCount = item.subscriberCount
                    )
                }

            val playlists = items.filterIsInstance<PlaylistInfoItem>()
                .map { item ->
                    PlaylistSearchResult(
                        name = item.name,
                        playlistId = item.url.substringAfter("list="),
                        thumbnailUrl = item.thumbnails.firstOrNull()?.url,
                        trackCount = item.streamCount.toInt(),
                        uploaderName = item.uploaderName
                    )
                }

            val newNextPage = try { page.nextPage } catch (_: Exception) { null }
            CategorizedSearch(songs, artists, playlists, newNextPage)
        } catch (e: Exception) {
            Log.e(TAG, "searchMore failed for query: '$query'", e)
            throw e
        }
    }

    suspend fun searchSongs(query: String, limit: Int = 20): List<Song> {
        Log.d(TAG, "Searching YouTube for: $query")
        return try {
            val service = NewPipe.getService(ServiceList.YouTube.serviceId) as YoutubeService
            val searchInfo = SearchInfo.getInfo(
                service,
                service.searchQHFactory.fromQuery(query, emptyList(), "")
            )

            val results = searchInfo.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .take(limit)
                .map { it.toSong() }
            Log.d(TAG, "Found ${results.size} results")
            results
        } catch (e: Exception) {
            Log.e(TAG, "searchSongs failed for query: '$query'", e)
            throw e
        }
    }

    suspend fun findBestMatch(song: Song): String? {
        val query = "${song.artists.joinToString(" ")} ${song.name}"
        Log.d(TAG, "Finding best match for: $query")
        val results = searchSongs(query, limit = 10)
        
        if (results.isEmpty()) {
            Log.d(TAG, "No results found")
            return null
        }
        
        // Pick the result with closest duration
        val best = results.minByOrNull { 
            kotlin.math.abs(it.duration - song.duration) 
        }
        
        Log.d(TAG, "Best match: ${best?.name} (${best?.songId})")
        return best?.songId
    }

    suspend fun findStreamUrl(song: Song): String {
        val query = "${song.artists.joinToString(" ")} ${song.name}"
        Log.d(TAG, "Searching for stream URL: $query")
        val results = searchSongs(query, limit = 5)
        if (results.isEmpty()) throw Exception("No matching videos found on YouTube")

        val candidates = results.filter { it.duration > 0 }
            .sortedBy { kotlin.math.abs(it.duration - song.duration) }

        val errors = mutableListOf<String>()
        for (candidate in candidates) {
            try {
                Log.d(TAG, "Trying candidate: ${candidate.name} (${candidate.songId})")
                return getAudioStreamUrl(candidate.songId)
            } catch (e: org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException) {
                Log.w(TAG, "Candidate age-restricted, trying next")
                errors.add("${candidate.name}: age-restricted")
            } catch (e: Exception) {
                Log.w(TAG, "Candidate failed: ${e.message}, trying next")
                errors.add("${candidate.name}: ${e.message}")
            }
        }

        throw Exception("No playable video found: ${errors.joinToString("; ")}")
    }

    suspend fun findVideoStreams(song: Song, quality: VideoQuality = VideoQuality.P_1080): VideoStreamInfo {
        val query = "${song.artists.joinToString(" ")} ${song.name}"
        Log.d(TAG, "Finding video streams for: $query")
        val results = searchSongs(query, limit = 5)
        if (results.isEmpty()) throw Exception("No matching videos found on YouTube")

        val candidates = results.filter { it.duration > 0 }
            .sortedBy { kotlin.math.abs(it.duration - song.duration) }

        val errors = mutableListOf<String>()
        for (candidate in candidates) {
            try {
                return getVideoStreamUrl(candidate.songId, quality)
            } catch (e: Exception) {
                Log.w(TAG, "Candidate video failed: ${e.message}")
                errors.add("${candidate.name}: ${e.message}")
            }
        }

        throw Exception("No playable video found: ${errors.joinToString("; ")}")
    }

    @Suppress("DEPRECATION")
    suspend fun getVideoStreamUrl(videoId: String, quality: VideoQuality): VideoStreamInfo {
        Log.d(TAG, "Getting video stream URL for: $videoId at ${quality.label}")
        val service = NewPipe.getService(ServiceList.YouTube.serviceId) as YoutubeService
        val extractor = service.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
        extractor.fetchPage()

        val allVideoStreams = extractor.videoStreams
        val audioStreams = extractor.audioStreams

        val targetHeight = when (quality) {
            VideoQuality.P_360 -> 360
            VideoQuality.P_480 -> 480
            VideoQuality.P_720 -> 720
            VideoQuality.P_1080 -> 1080
            VideoQuality.P_2160 -> 2160
        }

        // Prefer progressive streams (contain both video and audio)
        val progressive = allVideoStreams.filterNot { it.isVideoOnly }
        val videoOnly = allVideoStreams.filter { it.isVideoOnly }

        val bestVideo = findBestVideoStream(progressive + videoOnly, targetHeight)
            ?: throw Exception("No suitable video stream found")
        val videoUrl = appendUserAgent(bestVideo.content)

        if (!bestVideo.isVideoOnly) {
            Log.d(TAG, "Using progressive stream at ${bestVideo.resolution}")
            return VideoStreamInfo(videoUrl, null, bestVideo.resolution, "mp4")
        }

        // Need separate audio stream
        val bestAudio = audioStreams.maxByOrNull { it.averageBitrate }
        val audioUrl = bestAudio?.content?.let { appendUserAgent(it) }
        Log.d(TAG, "Using video-only stream at ${bestVideo.resolution} with separate audio")
        return VideoStreamInfo(videoUrl, audioUrl, bestVideo.resolution, "mp4")
    }

    @Suppress("DEPRECATION")
    private fun findBestVideoStream(
        streams: List<org.schabi.newpipe.extractor.stream.VideoStream>,
        targetHeight: Int
    ): org.schabi.newpipe.extractor.stream.VideoStream? {
        if (streams.isEmpty()) return null
        val exactOrLower = streams.filter { parseResolution(it.resolution) <= targetHeight }
        if (exactOrLower.isNotEmpty()) {
            return exactOrLower.maxBy { parseResolution(it.resolution) }
        }
        return streams.minByOrNull { parseResolution(it.resolution) }
    }

    suspend fun getAvailableVideoQualities(song: Song): List<VideoStreamInfo> {
        val query = "${song.artists.joinToString(" ")} ${song.name}"
        Log.d(TAG, "Getting available video qualities for: $query")
        val results = searchSongs(query, limit = 5)
        if (results.isEmpty()) throw Exception("No matching videos found on YouTube")

        val candidates = results.filter { it.duration > 0 }
            .sortedBy { kotlin.math.abs(it.duration - song.duration) }

        for (candidate in candidates) {
            try {
                val service = NewPipe.getService(ServiceList.YouTube.serviceId) as YoutubeService
                val extractor = service.getStreamExtractor("https://www.youtube.com/watch?v=${candidate.songId}")
                val streamInfo = StreamInfo.getInfo(extractor)

                val allStreams = streamInfo.videoStreams ?: emptyList()
                val audioStreams = streamInfo.audioStreams ?: emptyList()
                val bestAudio = audioStreams.maxByOrNull { it.averageBitrate }

                val seenResolutions = mutableSetOf<String>()
                val result = mutableListOf<VideoStreamInfo>()

                for (stream in allStreams) {
                    val height = parseResolution(stream.resolution)
                    val label = when {
                        height >= 2160 -> "4K"
                        height >= 1440 -> "1440p"
                        height >= 1080 -> "1080p"
                        height >= 720 -> "720p"
                        height >= 480 -> "480p"
                        height >= 360 -> "360p"
                        else -> "${height}p"
                    }

                    if (label in seenResolutions) continue
                    seenResolutions.add(label)

                    val url = appendUserAgent(stream.content)
                    val audioUrl = if (stream.isVideoOnly) {
                        bestAudio?.content?.let { appendUserAgent(it) }
                    } else null

                    result.add(VideoStreamInfo(url, audioUrl, stream.resolution, "mp4"))
                }

                if (result.isNotEmpty()) {
                    Log.d(TAG, "Available qualities: ${result.map { it.resolution }}")
                    return result
                }
            } catch (e: Exception) {
                Log.w(TAG, "Candidate failed for qualities: ${e.message}")
            }
        }

        throw Exception("No video qualities found")
    }

    private fun parseResolution(resolution: String): Int {
        val nums = Regex("(\\d+)").findAll(resolution).map { it.value.toIntOrNull() ?: 0 }.toList()
        return when {
            nums.isEmpty() -> 0
            resolution.contains("x", ignoreCase = true) && nums.size >= 2 -> nums[1]
            else -> nums[0]
        }
    }

    suspend fun getAudioStreamUrl(videoId: String): String {
        Log.d(TAG, "Getting audio stream URL for: $videoId")
        val service = NewPipe.getService(ServiceList.YouTube.serviceId) as YoutubeService
        val extractor = service.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
        extractor.fetchPage()
        
        val audioStreams = extractor.audioStreams
        Log.d(TAG, "Found ${audioStreams.size} audio streams")
        
        if (audioStreams.isNotEmpty()) {
            val bestStream = audioStreams
                .filter { it.format?.name?.lowercase() != "webm" || audioStreams.size == 1 }
                .maxByOrNull { it.averageBitrate } ?: audioStreams.maxByOrNull { it.averageBitrate }

            var url = bestStream?.content ?: throw Exception("No stream content URL found")
            url = appendUserAgent(url)
            Log.d(TAG, "Got stream URL: ${url.take(100)}...")
            return url
        }
        
        // Fallback: try progressive video streams (contain embedded audio)
        Log.d(TAG, "No audio streams, falling back to progressive video streams")
        val videoStreams = extractor.videoStreams
        @Suppress("DEPRECATION")
        val progressiveStreams = videoStreams.filterNot { it.isVideoOnly }
        Log.d(TAG, "Found ${progressiveStreams.size} progressive video streams")
        
        if (progressiveStreams.isNotEmpty()) {
            var url = progressiveStreams.first().content
            url = appendUserAgent(url)
            Log.d(TAG, "Got progressive stream URL: ${url.take(100)}...")
            return url
        }

        throw Exception("No audio streams found for $videoId")
    }

    suspend fun getPlaylistTracks(playlistId: String): List<Song> {
        Log.d(TAG, "Fetching playlist tracks for: $playlistId")
        return try {
            val service = NewPipe.getService(ServiceList.YouTube.serviceId) as YoutubeService
            val playlistInfo = PlaylistInfo.getInfo(
                service,
                "https://www.youtube.com/playlist?list=$playlistId"
            )
            val tracks = playlistInfo.relatedItems.map { it.toSong() }
            Log.d(TAG, "Found ${tracks.size} tracks in playlist")
            tracks
        } catch (e: Exception) {
            Log.e(TAG, "getPlaylistTracks failed for: $playlistId", e)
            throw e
        }
    }

    private fun appendUserAgent(url: String): String {
        return if (!url.contains("user_agent=")) {
            "$url&user_agent=Mozilla/5.0%20(Linux;%20Android%2010;%20K)%20AppleWebKit/537.36%20(KHTML,%20like%20Gecko)%20Chrome/119.0.0.0%20Mobile%20Safari/537.36"
        } else url
    }
}
