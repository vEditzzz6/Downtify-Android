package com.downtify.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.downtify.app.data.repository.DowntifyRepository
import com.downtify.app.domain.model.CheckInterval
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MonitorViewModel @Inject constructor(
    private val repository: DowntifyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getMonitoredPlaylists().collect { playlists ->
                _uiState.value = _uiState.value.copy(playlists = playlists, isLoading = false)
            }
        }
    }

    fun onUrlChanged(url: String) {
        _uiState.value = _uiState.value.copy(inputUrl = url)
    }

    fun onIntervalChanged(interval: CheckInterval) {
        _uiState.value = _uiState.value.copy(selectedInterval = interval)
    }

    fun addPlaylist() {
        val url = _uiState.value.inputUrl.trim()
        if (url.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val spotifyId = extractSpotifyId(url)
                repository.addMonitoredPlaylist(
                    spotifyId = spotifyId,
                    name = "Loading...",
                    url = url,
                    intervalMinutes = _uiState.value.selectedInterval.minutes
                )
                _uiState.value = _uiState.value.copy(
                    inputUrl = "",
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to add playlist"
                )
            }
        }
    }

    fun togglePlaylist(id: Int, enabled: Boolean) {
        viewModelScope.launch {
            repository.updatePlaylistEnabled(id, enabled)
        }
    }

    fun deletePlaylist(id: Int) {
        viewModelScope.launch {
            repository.deletePlaylist(id)
        }
    }

    fun checkNow(id: Int) {
        viewModelScope.launch {
            repository.updateLastCheck(id, 0)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun extractSpotifyId(url: String): String {
        val regex = Regex("spotify\\.com/playlist/([A-Za-z0-9]+)")
        val match = regex.find(url)
        return match?.groupValues?.get(1) ?: url
    }
}

data class MonitorUiState(
    val playlists: List<com.downtify.app.data.database.MonitoredPlaylistEntity> = emptyList(),
    val inputUrl: String = "",
    val selectedInterval: CheckInterval = CheckInterval.HOUR_1,
    val isLoading: Boolean = false,
    val error: String? = null
)
