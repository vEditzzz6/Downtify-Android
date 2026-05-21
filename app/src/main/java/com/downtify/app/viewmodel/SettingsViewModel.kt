package com.downtify.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.downtify.app.data.repository.SettingsRepository
import com.downtify.app.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            repository.settingsFlow.collectLatest { settings ->
                _uiState.value = _uiState.value.copy(
                    settings = settings,
                    isLoading = false
                )
            }
        }
    }

    fun updateFormat(format: AudioFormat) {
        viewModelScope.launch {
            repository.updateAudioFormat(format)
        }
    }

    fun updateBitrate(bitrate: Bitrate) {
        viewModelScope.launch {
            repository.updateBitrate(bitrate)
        }
    }

    fun toggleOrganizeByArtist() {
        viewModelScope.launch {
            val current = _uiState.value.settings.organizeByArtist
            repository.updateOrganizeByArtist(!current)
        }
    }

    fun toggleOrganizeByAlbum() {
        viewModelScope.launch {
            val current = _uiState.value.settings.organizeByAlbum
            repository.updateOrganizeByAlbum(!current)
        }
    }

    fun toggleGenerateM3U() {
        viewModelScope.launch {
            val current = _uiState.value.settings.generateM3u
            repository.updateGenerateM3U(!current)
        }
    }

    fun toggleDownloadLyrics() {
        viewModelScope.launch {
            val current = _uiState.value.settings.downloadLyrics
            repository.updateDownloadLyrics(!current)
        }
    }

    fun updateMaxParallelDownloads(count: Int) {
        viewModelScope.launch {
            repository.updateMaxParallelDownloads(count)
        }
    }

    fun updateLanguage(language: AppLanguage) {
        viewModelScope.launch {
            repository.updateLanguage(language)
        }
    }

    fun updateSoundCloudOAuthToken(token: String) {
        viewModelScope.launch {
            repository.updateSoundCloudOAuthToken(token)
        }
    }

    fun updateVideoFormat(format: VideoFormat) {
        viewModelScope.launch {
            repository.updateVideoFormat(format)
        }
    }

    fun updateVideoQuality(quality: VideoQuality) {
        viewModelScope.launch {
            repository.updateVideoQuality(quality)
        }
    }

    fun toggleOrganizeVideosByArtist() {
        viewModelScope.launch {
            val current = _uiState.value.settings.organizeVideosByArtist
            repository.updateOrganizeVideosByArtist(!current)
        }
    }

    fun toggleOrganizeVideosByAlbum() {
        viewModelScope.launch {
            val current = _uiState.value.settings.organizeVideosByAlbum
            repository.updateOrganizeVideosByAlbum(!current)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isLoading: Boolean = true,
    val error: String? = null
)
