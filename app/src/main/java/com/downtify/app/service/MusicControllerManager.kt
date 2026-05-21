package com.downtify.app.service

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.downtify.app.domain.model.Song
import com.downtify.app.domain.model.DownloadStatus
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicControllerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller = _controller.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentSong.value = mediaItem?.toSong()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }
    }

    init {
        initializeController()
    }

    private fun initializeController() {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            val controller = try { controllerFuture?.get() } catch (e: Exception) { null }
            _controller.value = controller
            controller?.addListener(listener)
            _currentSong.value = controller?.currentMediaItem?.toSong()
            _isPlaying.value = controller?.isPlaying == true
        }, MoreExecutors.directExecutor())
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        val controller = _controller.value ?: return
        
        val mediaItems = songs.map { it.toMediaItem() }

        controller.setMediaItems(mediaItems)
        controller.seekTo(startIndex, 0)
        controller.prepare()
        controller.play()
    }

    fun togglePlayPause() {
        val controller = _controller.value ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun next() {
        _controller.value?.seekToNext()
    }

    fun previous() {
        _controller.value?.seekToPrevious()
    }

    fun release() {
        _controller.value?.removeListener(listener)
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}

private fun Song.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(name)
        .setArtist(artists.joinToString(", "))
        .setAlbumTitle(albumName)
        .setArtworkUri(android.net.Uri.parse(coverUrl ?: ""))
        .build()

    return MediaItem.Builder()
        .setMediaId(songId)
        .setUri(localFilePath ?: url)
        .setMediaMetadata(metadata)
        .build()
}

private fun MediaItem.toSong(): Song {
    val metadata = mediaMetadata
    return Song(
        songId = mediaId,
        name = metadata.title?.toString() ?: "Unknown",
        artists = metadata.artist?.toString()?.split(", ") ?: listOf("Unknown"),
        albumName = metadata.albumTitle?.toString(),
        coverUrl = metadata.artworkUri?.toString(),
        url = localConfiguration?.uri?.toString() ?: "",
        localFilePath = localConfiguration?.uri?.toString(),
        downloadStatus = DownloadStatus.DONE
    )
}
