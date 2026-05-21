package com.downtify.app.data.local

import android.content.Context
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.downtify.app.domain.model.AudioFormat
import com.downtify.app.domain.model.Song
import com.downtify.app.domain.model.VideoFormat
import com.downtify.app.domain.model.VideoQuality
import com.downtify.app.util.StorageUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadProgress(
    val stage: Stage,
    val percent: Float,
    val message: String,
    val downloadSpeed: Long = 0, // bytes/sec
    val uploadSpeed: Long = 0    // bytes/sec
) {
    enum class Stage { QUEUED, SEARCHING, DOWNLOADING, CONVERTING, TAGGING, DONE, ERROR }
}

@Singleton
class DownloadPipeline @Inject constructor(
    private val youTubeExtractor: YouTubeExtractor,
    private val nativeDownloader: NativeDownloader,
    private val audioConverter: AudioConverter,
    private val audioTagger: AudioTagger,
    private val lyricsFetcher: LyricsFetcher,
    private val storageUtils: StorageUtils,
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "DownloadPipeline"
    }

    fun download(
        song: Song,
        format: AudioFormat = AudioFormat.MP3,
        bitrate: String = "320k",
        downloadLyrics: Boolean = true,
        organizeByArtist: Boolean = false,
        organizeByAlbum: Boolean = false
    ): Flow<DownloadProgress> = channelFlow {
        try {
            Log.d(TAG, "Starting download for: ${song.name} by ${song.artists.joinToString(", ")}")

            var convertedFile: File

            if (song.source == "soundcloud" && !song.streamUrl.isNullOrBlank()) {
                send(DownloadProgress(DownloadProgress.Stage.SEARCHING, 0f, "Downloading from SoundCloud..."))
                Log.d(TAG, "Downloading SoundCloud HLS: ${song.streamUrl}")
                convertedFile = audioConverter.downloadAndConvert(
                    url = song.streamUrl,
                    outputFormat = format,
                    bitrate = bitrate
                )
                Log.d(TAG, "SoundCloud download complete: ${convertedFile.absolutePath}")
                val properName = "${song.artists.firstOrNull() ?: "Unknown"} - ${song.name}.${format.extension}"
                    .replace(Regex("[/\\\\:*?\"<>|]"), "")
                val renamed = File(convertedFile.parent, properName)
                if (convertedFile.renameTo(renamed)) {
                    convertedFile = renamed
                }
                Log.d(TAG, "Renamed to: ${convertedFile.absolutePath}")
            } else {
                send(DownloadProgress(DownloadProgress.Stage.SEARCHING, 0f, "Finding audio on Spotify..."))
                
                Log.d(TAG, "Finding stream URL for: ${song.name}")
                val streamUrl = youTubeExtractor.findStreamUrl(song)
                Log.d(TAG, "Got stream URL")
                
                send(DownloadProgress(DownloadProgress.Stage.DOWNLOADING, 0f, "Downloading audio..."))
                
                Log.d(TAG, "Starting native download...")
                val downloadedFile = nativeDownloader.downloadAudio(
                    streamUrl = streamUrl,
                    song = song,
                    format = "cache",
                    progressCallback = { progress, downSpeed, upSpeed ->
                        send(DownloadProgress(
                            stage = DownloadProgress.Stage.DOWNLOADING,
                            percent = progress,
                            message = "Downloading: ${progress.toInt()}%",
                            downloadSpeed = downSpeed,
                            uploadSpeed = upSpeed
                        ))
                    }
                )
                Log.d(TAG, "Downloaded to: ${downloadedFile.absolutePath}")
                
                send(DownloadProgress(DownloadProgress.Stage.CONVERTING, 95f, "Converting to ${format.name}..."))
                
                Log.d(TAG, "Converting audio...")
                convertedFile = audioConverter.convert(
                    inputFile = downloadedFile,
                    outputFormat = format,
                    bitrate = bitrate
                )
                Log.d(TAG, "Converted to: ${convertedFile.absolutePath}")
            }
            
            send(DownloadProgress(DownloadProgress.Stage.TAGGING, 98f, "Embedding metadata..."))
            
            Log.d(TAG, "Tagging audio...")
            audioTagger.tag(convertedFile, song)
            Log.d(TAG, "Tagging complete")
            
            if (downloadLyrics) {
                Log.d(TAG, "Fetching lyrics...")
                val lyrics = lyricsFetcher.fetchLyrics(song)
                if (lyrics != null) {
                    lyricsFetcher.saveLyricsSidecar(convertedFile, lyrics)
                    Log.d(TAG, "Lyrics saved")
                }
            }
            
            // Copy final file to external storage
            val artistName = song.artists.firstOrNull() ?: "Unknown Artist"
            val albumName = song.albumName ?: "Unknown Album"

            val externalDir = when {
                organizeByArtist && organizeByAlbum -> {
                    storageUtils.getAlbumDirectory(artistName, albumName)
                }
                organizeByArtist -> {
                    storageUtils.getArtistDirectory(artistName)
                }
                organizeByAlbum -> {
                    storageUtils.getAlbumOnlyDirectory(albumName)
                }
                else -> {
                    storageUtils.getDownloadsDirectory()
                }
            }

            val externalFile = File(externalDir, convertedFile.name)
            convertedFile.copyTo(externalFile, overwrite = true)
            Log.d(TAG, "Copied to external storage: ${externalFile.absolutePath}")
            
            // Move lyrics if they exist
            val lrcFile = File(convertedFile.parent, convertedFile.nameWithoutExtension + ".lrc")
            if (lrcFile.exists()) {
                val externalLrc = File(externalDir, lrcFile.name)
                lrcFile.copyTo(externalLrc, overwrite = true)
                lrcFile.delete()
                Log.d(TAG, "Lyrics copied to: ${externalLrc.absolutePath}")
            }
            
            // Clean up cache files
            convertedFile.delete()
            
            Log.d(TAG, "Download complete!")
            send(DownloadProgress(DownloadProgress.Stage.DONE, 100f, externalFile.absolutePath))
            
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "Download failed", e)
            send(DownloadProgress(DownloadProgress.Stage.ERROR, 0f, "Error: ${e.message ?: e.javaClass.simpleName}"))
        }
    }.flowOn(Dispatchers.IO)

    fun downloadVideo(
        song: Song,
        format: VideoFormat = VideoFormat.MP4,
        quality: VideoQuality = VideoQuality.P_1080,
        organizeByArtist: Boolean = false,
        organizeByAlbum: Boolean = false
    ): Flow<DownloadProgress> = channelFlow {
        try {
            Log.d(TAG, "Starting video download for: ${song.name} by ${song.artists.joinToString(", ")}")

            send(DownloadProgress(DownloadProgress.Stage.SEARCHING, 0f, "Finding video on YouTube..."))

            val videoInfo = youTubeExtractor.findVideoStreams(song, quality)

            send(DownloadProgress(DownloadProgress.Stage.DOWNLOADING, 10f, "Downloading video stream..."))

            val cacheDir = File(context.cacheDir, "video_downloads").also { it.mkdirs() }
            val baseName = storageUtils.sanitizeFileName("${song.artists.firstOrNull() ?: "Unknown"} - ${song.name}")

            // Download video stream via OkHttp (handles TLS properly)
            val videoFile = File(cacheDir, "${baseName}_video.mp4")
            nativeDownloader.downloadFile(
                url = videoInfo.videoUrl,
                outputFile = videoFile,
                progressCallback = { progress, downSpeed, _ ->
                    send(DownloadProgress(
                        stage = DownloadProgress.Stage.DOWNLOADING,
                        percent = 10f + progress * 0.4f,
                        message = "Downloading video: ${progress.toInt()}%",
                        downloadSpeed = downSpeed
                    ))
                }
            )

            val audioFile: File?
            if (videoInfo.audioUrl != null) {
                send(DownloadProgress(DownloadProgress.Stage.DOWNLOADING, 50f, "Downloading audio stream..."))
                audioFile = File(cacheDir, "${baseName}_audio.m4a")
                nativeDownloader.downloadFile(
                    url = videoInfo.audioUrl,
                    outputFile = audioFile,
                    progressCallback = { progress, downSpeed, _ ->
                        send(DownloadProgress(
                            stage = DownloadProgress.Stage.DOWNLOADING,
                            percent = 50f + progress * 0.4f,
                            message = "Downloading audio: ${progress.toInt()}%",
                            downloadSpeed = downSpeed
                        ))
                    }
                )
            } else {
                audioFile = null
            }

            send(DownloadProgress(DownloadProgress.Stage.CONVERTING, 90f, "Muxing video and audio..."))

            // Mux local files with FFmpeg (no network I/O)
            val outputName = "${baseName}.${format.extension}"
            val tempFile = File(cacheDir, outputName)
            if (tempFile.exists()) tempFile.delete()

            val ffmpegArgs = mutableListOf("-y", "-i", videoFile.absolutePath)
            if (audioFile != null) {
                ffmpegArgs.addAll(listOf("-i", audioFile.absolutePath))
            }
            ffmpegArgs.addAll(listOf("-c:v", "copy"))
            if (audioFile != null) {
                ffmpegArgs.addAll(listOf("-c:a", "aac"))
            } else {
                ffmpegArgs.addAll(listOf("-c:a", "copy"))
            }
            ffmpegArgs.addAll(listOf("-shortest", tempFile.absolutePath))

            Log.d(TAG, "Running FFmpeg for muxing: ${ffmpegArgs.joinToString(" ")}")
            val session = FFmpegKit.executeWithArguments(ffmpegArgs.toTypedArray())
            val returnCode = session.returnCode

            if (!ReturnCode.isSuccess(returnCode)) {
                val logs = session.allLogsAsString
                throw Exception("Video muxing failed: $logs")
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                throw Exception("Muxed video file was not created")
            }

            // Clean up downloaded streams
            videoFile.delete()
            audioFile?.delete()

            Log.d(TAG, "Video muxed: ${tempFile.absolutePath}")
            send(DownloadProgress(DownloadProgress.Stage.CONVERTING, 95f, "Copying to storage..."))

            // Copy to external storage
            val artistName = song.artists.firstOrNull() ?: "Unknown Artist"
            val albumName = song.albumName ?: "Unknown Album"

            val externalDir = when {
                organizeByArtist && organizeByAlbum -> {
                    storageUtils.getVideoAlbumDirectory(artistName, albumName)
                }
                organizeByArtist -> {
                    storageUtils.getVideoArtistDirectory(artistName)
                }
                organizeByAlbum -> {
                    storageUtils.getVideoAlbumOnlyDirectory(albumName)
                }
                else -> {
                    storageUtils.getVideoDownloadsDirectory()
                }
            }

            val externalFile = File(externalDir, tempFile.name)
            tempFile.copyTo(externalFile, overwrite = true)
            Log.d(TAG, "Copied video to: ${externalFile.absolutePath}")

            tempFile.delete()

            Log.d(TAG, "Video download complete!")
            send(DownloadProgress(DownloadProgress.Stage.DONE, 100f, externalFile.absolutePath))

        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "Video download failed", e)
            send(DownloadProgress(DownloadProgress.Stage.ERROR, 0f, "Error: ${e.message ?: e.javaClass.simpleName}"))
        }
    }.flowOn(Dispatchers.IO)
}
