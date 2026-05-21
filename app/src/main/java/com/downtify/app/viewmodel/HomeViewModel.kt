package com.downtify.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.downtify.app.data.local.DownloadProgress
import com.downtify.app.data.local.VideoStreamInfo
import com.downtify.app.data.repository.DowntifyRepository
import com.downtify.app.data.repository.SettingsRepository
import com.downtify.app.domain.model.AppSettings
import com.downtify.app.domain.model.Song
import com.downtify.app.domain.model.VideoQuality
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: DowntifyRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "HomeViewModel"
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _showWelcome = MutableStateFlow(false)
    val showWelcome: StateFlow<Boolean> = _showWelcome.asStateFlow()

    private var settings = AppSettings()

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collectLatest { 
                settings = it
                if (!it.welcomeShown) {
                    _showWelcome.value = true
                }
            }
        }
    }

    fun onUrlChanged(url: String) {
        _uiState.value = _uiState.value.copy(inputUrl = url)
    }

    fun resolveUrl() {
        val url = _uiState.value.inputUrl.trim()
        if (url.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val songs = withContext(Dispatchers.IO) {
                    when {
                        url.contains("soundcloud.com") || url.contains("on.soundcloud.com") ->
                            repository.resolveSoundCloudUrl(url)
                        url.contains("spotify.com") || url.startsWith("spotify:") ->
                            repository.resolveSpotifyUrl(url)
                        else -> throw IllegalArgumentException("Unsupported URL. Use a Spotify or SoundCloud link.")
                    }
                }
                val downloaded = withContext(Dispatchers.IO) {
                    songs.filter { repository.isSongDownloaded(it.songId) }
                        .map { it.songId }
                        .toSet()
                }
                _uiState.value = _uiState.value.copy(
                    resolvedSongs = songs,
                    downloadedSongs = downloaded,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to resolve URL"
                )
            }
        }
    }

    fun downloadSong(song: Song) {
        Log.d(TAG, "downloadSong called for: ${song.name}")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(error = null)
            try {
                Log.d(TAG, "Starting download flow...")
                repository.downloadSong(song, settings).collect { progress ->
                    Log.d(TAG, "Progress for ${song.name}: ${progress.stage} - ${progress.message}")
                    val currentDownloads = _uiState.value.activeDownloads.toMutableMap()
                    
                    when (progress.stage) {
                        DownloadProgress.Stage.DONE -> {
                            currentDownloads.remove(song.songId)
                            val downloaded = _uiState.value.downloadedSongs.toMutableSet()
                            downloaded.add(song.songId)
                            _uiState.value = _uiState.value.copy(
                                activeDownloads = currentDownloads,
                                downloadedSongs = downloaded
                            )
                        }
                        DownloadProgress.Stage.ERROR -> {
                            currentDownloads.remove(song.songId)
                            _uiState.value = _uiState.value.copy(error = progress.message)
                        }
                        else -> {
                            currentDownloads[song.songId] = progress
                        }
                    }
                    _uiState.value = _uiState.value.copy(activeDownloads = currentDownloads)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for ${song.name}", e)
                val currentDownloads = _uiState.value.activeDownloads.toMutableMap()
                currentDownloads.remove(song.songId)
                _uiState.value = _uiState.value.copy(
                    activeDownloads = currentDownloads,
                    error = e.message ?: "Download failed"
                )
            }
        }
    }

    fun downloadAll() {
        Log.d(TAG, "downloadAll called for ${_uiState.value.resolvedSongs.size} songs")
        _uiState.value = _uiState.value.copy(error = null)
        
        for (song in _uiState.value.resolvedSongs) {
            downloadSong(song)
        }
    }

    fun downloadVideo(song: Song) {
        downloadVideoWithQuality(song, settings.videoQuality)
    }

    fun fetchVideoQualities(song: Song) {
        _uiState.value = _uiState.value.copy(videoQualities = emptyList(), isLoadingQualities = true, videoQualitySong = song)
        viewModelScope.launch {
            try {
                val qualities = withContext(Dispatchers.IO) {
                    repository.getAvailableVideoQualities(song)
                }
                _uiState.value = _uiState.value.copy(
                    videoQualities = qualities,
                    isLoadingQualities = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    videoQualities = null,
                    isLoadingQualities = false,
                    error = e.message ?: "Failed to load video qualities"
                )
            }
        }
    }

    fun downloadVideoWithQuality(song: Song, quality: VideoQuality) {
        Log.d(TAG, "downloadVideo called for: ${song.name} at ${quality.label}")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(error = null, videoQualitySong = null, videoQualities = null)
            try {
                repository.downloadVideo(song, settings, videoQuality = quality).collect { progress ->
                    val currentVideoDownloads = _uiState.value.activeVideoDownloads.toMutableMap()

                    when (progress.stage) {
                        DownloadProgress.Stage.DONE -> {
                            currentVideoDownloads.remove(song.songId)
                            val downloaded = _uiState.value.downloadedVideos.toMutableSet()
                            downloaded.add(song.songId)
                            _uiState.value = _uiState.value.copy(
                                activeVideoDownloads = currentVideoDownloads,
                                downloadedVideos = downloaded
                            )
                        }
                        DownloadProgress.Stage.ERROR -> {
                            currentVideoDownloads.remove(song.songId)
                            _uiState.value = _uiState.value.copy(error = progress.message)
                        }
                        else -> {
                            currentVideoDownloads[song.songId] = progress
                        }
                    }
                    _uiState.value = _uiState.value.copy(activeVideoDownloads = currentVideoDownloads)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Video download failed for ${song.name}", e)
                val currentVideoDownloads = _uiState.value.activeVideoDownloads.toMutableMap()
                currentVideoDownloads.remove(song.songId)
                _uiState.value = _uiState.value.copy(
                    activeVideoDownloads = currentVideoDownloads,
                    error = e.message ?: "Video download failed"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearVideoQualityState() {
        _uiState.value = _uiState.value.copy(videoQualitySong = null, videoQualities = null, isLoadingQualities = false)
    }

    fun clearResolvedSongs() {
        _uiState.value = _uiState.value.copy(
            resolvedSongs = emptyList(),
            inputUrl = ""
        )
    }

    fun dismissWelcome() {
        _showWelcome.value = false
        viewModelScope.launch {
            settingsRepository.markWelcomeShown()
        }
    }
}

data class HomeUiState(
    val inputUrl: String = "",
    val resolvedSongs: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val activeDownloads: Map<String, DownloadProgress> = emptyMap(),
    val downloadedSongs: Set<String> = emptySet(),
    val activeVideoDownloads: Map<String, DownloadProgress> = emptyMap(),
    val downloadedVideos: Set<String> = emptySet(),
    val videoQualitySong: Song? = null,
    val videoQualities: List<VideoStreamInfo>? = null,
    val isLoadingQualities: Boolean = false,
    val error: String? = null
) {
    val isDownloading: Boolean get() = activeDownloads.isNotEmpty()
}
