package com.downtify.app.viewmodel

import androidx.lifecycle.ViewModel
import com.downtify.app.domain.model.Song
import com.downtify.app.service.MusicControllerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val musicControllerManager: MusicControllerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var sleepTimerJob: Job? = null

    data class LyricsLine(val timestampMs: Long, val text: String)

    private fun parseLrc(content: String): List<LyricsLine> {
        val lines = mutableListOf<LyricsLine>()
        val regex = Regex("""\[(\d+):(\d+(?:[.]\d+)?)\](.*)""")
        content.lines().forEach { line ->
            val matches = regex.findAll(line)
            if (matches.any()) {
                var text: String
                for (match in matches) {
                    val minutes = match.groupValues[1].toIntOrNull() ?: 0
                    val seconds = match.groupValues[2].toFloatOrNull() ?: 0f
                    text = match.groupValues[3].trim()
                    if (text.isNotEmpty()) {
                        lines.add(LyricsLine(
                            timestampMs = minutes * 60_000L + (seconds * 1000L).toLong(),
                            text = text
                        ))
                    }
                }
            }
        }
        return lines.sortedBy { it.timestampMs }
    }

    private suspend fun loadLyrics(filePath: String?): List<LyricsLine> = withContext(Dispatchers.IO) {
        if (filePath == null) return@withContext emptyList()
        val lrcFile = File(filePath.substringBeforeLast(".") + ".lrc")
        if (lrcFile.exists()) {
            parseLrc(lrcFile.readText())
        } else emptyList()
    }

    private fun findCurrentLyricIndex(lyrics: List<LyricsLine>, positionMs: Long): Int {
        if (lyrics.isEmpty()) return -1
        var index = lyrics.indexOfLast { it.timestampMs <= positionMs }
        if (index < 0) index = 0
        return index
    }

    private var playerListener: Player.Listener? = null

    init {
        observePlaybackState()
        startPositionUpdate()
    }

    private fun startPositionUpdate() {
        viewModelScope.launch {
            while (true) {
                musicControllerManager.controller.value?.let { controller ->
                    val position = controller.currentPosition
                    val lyrics = _uiState.value.lyrics
                    _uiState.value = _uiState.value.copy(
                        currentPosition = position,
                        totalDuration = if (controller.duration > 0) controller.duration else 0L,
                        currentLyricIndex = findCurrentLyricIndex(lyrics, position)
                    )
                }
                delay(200)
            }
        }
    }

    private fun observePlaybackState() {
        viewModelScope.launch {
            musicControllerManager.currentSong.collect { song ->
                val loadedLyrics = if (song?.localFilePath != _uiState.value.currentSong?.localFilePath) {
                    loadLyrics(song?.localFilePath)
                } else _uiState.value.lyrics
                _uiState.value = _uiState.value.copy(
                    currentSong = song,
                    lyrics = loadedLyrics,
                    currentLyricIndex = if (loadedLyrics.isNotEmpty()) {
                        findCurrentLyricIndex(loadedLyrics, _uiState.value.currentPosition)
                    } else -1
                )
            }
        }
        viewModelScope.launch {
            musicControllerManager.isPlaying.collect { isPlaying ->
                _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
            }
        }
        viewModelScope.launch {
            musicControllerManager.controller.collect { controller ->
                playerListener?.let { listener ->
                    controller?.removeListener(listener)
                }
                
                controller?.let {
                    _uiState.value = _uiState.value.copy(
                        isShuffle = it.shuffleModeEnabled,
                        repeatMode = when (it.repeatMode) {
                            Player.REPEAT_MODE_OFF -> RepeatMode.OFF
                            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                            else -> RepeatMode.OFF
                        },
                        volume = it.volume,
                        playbackSpeed = it.playbackParameters.speed
                    )

                    val listener = object : Player.Listener {
                        override fun onVolumeChanged(volume: Float) {
                            _uiState.value = _uiState.value.copy(
                                volume = volume,
                                isMuted = volume == 0f
                            )
                        }

                        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                            _uiState.value = _uiState.value.copy(isShuffle = shuffleModeEnabled)
                        }

                        override fun onRepeatModeChanged(repeatMode: Int) {
                            _uiState.value = _uiState.value.copy(
                                repeatMode = when (repeatMode) {
                                    Player.REPEAT_MODE_OFF -> RepeatMode.OFF
                                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                                    else -> RepeatMode.OFF
                                }
                            )
                        }

                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            _uiState.value = _uiState.value.copy(
                                error = error.message ?: "Playback error occurred",
                                isLoading = false
                            )
                        }

                        override fun onPlaybackStateChanged(state: Int) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = state == Player.STATE_BUFFERING
                            )
                        }

                        override fun onPlaybackParametersChanged(params: androidx.media3.common.PlaybackParameters) {
                            _uiState.value = _uiState.value.copy(playbackSpeed = params.speed)
                        }
                    }
                    playerListener = listener
                    it.addListener(listener)
                }
            }
        }
    }

    fun setQueue(songs: List<Song>, startIndex: Int = 0) {
        musicControllerManager.playSongs(songs, startIndex)
        _uiState.value = _uiState.value.copy(queue = songs)
    }

    fun togglePlayPause() {
        musicControllerManager.togglePlayPause()
    }

    fun next() {
        musicControllerManager.next()
    }

    fun previous() {
        musicControllerManager.previous()
    }

    fun toggleShuffle() {
        musicControllerManager.controller.value?.let {
            it.shuffleModeEnabled = !it.shuffleModeEnabled
            _uiState.value = _uiState.value.copy(isShuffle = it.shuffleModeEnabled)
        }
    }

    fun cycleRepeatMode() {
        musicControllerManager.controller.value?.let {
            val nextMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
                else -> Player.REPEAT_MODE_OFF
            }
            it.repeatMode = nextMode
            _uiState.value = _uiState.value.copy(
                repeatMode = when (nextMode) {
                    Player.REPEAT_MODE_OFF -> RepeatMode.OFF
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    else -> RepeatMode.OFF
                }
            )
        }
    }

    fun seekTo(position: Long) {
        musicControllerManager.controller.value?.seekTo(position)
        _uiState.value = _uiState.value.copy(currentPosition = position)
    }

    fun updateVolume(volume: Float) {
        musicControllerManager.controller.value?.volume = volume
        _uiState.value = _uiState.value.copy(volume = volume, isMuted = volume == 0f)
    }

    fun toggleMute() {
        val currentVolume = _uiState.value.volume
        if (_uiState.value.isMuted) {
            val lastVolume = _uiState.value.lastVolume
            updateVolume(if (lastVolume > 0) lastVolume else 1.0f)
            _uiState.value = _uiState.value.copy(isMuted = false)
        } else {
            _uiState.value = _uiState.value.copy(lastVolume = currentVolume)
            updateVolume(0f)
            _uiState.value = _uiState.value.copy(isMuted = true)
        }
    }

    fun playTrackAtIndex(index: Int) {
        musicControllerManager.controller.value?.let { controller ->
            controller.seekTo(index, 0)
            controller.play()
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        val totalSeconds = minutes * 60L
        _uiState.value = _uiState.value.copy(
            sleepTimerMinutes = minutes,
            sleepTimerRemainingSeconds = totalSeconds
        )
        sleepTimerJob = viewModelScope.launch {
            var remaining = totalSeconds
            while (remaining > 0) {
                delay(1000)
                remaining--
                _uiState.value = _uiState.value.copy(sleepTimerRemainingSeconds = remaining)
            }
            musicControllerManager.controller.value?.pause()
            _uiState.value = _uiState.value.copy(
                sleepTimerMinutes = null,
                sleepTimerRemainingSeconds = 0L
            )
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _uiState.value = _uiState.value.copy(
            sleepTimerMinutes = null,
            sleepTimerRemainingSeconds = 0L
        )
    }

    fun setPlaybackSpeed(speed: Float) {
        musicControllerManager.controller.value?.let { controller ->
            controller.setPlaybackSpeed(speed)
            _uiState.value = _uiState.value.copy(playbackSpeed = speed)
        }
    }

    fun cyclePlaybackSpeed() {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        val currentSpeed = _uiState.value.playbackSpeed
        val currentIndex = speeds.indexOf(currentSpeed)
        val nextIndex = if (currentIndex < speeds.size - 1) currentIndex + 1 else 0
        setPlaybackSpeed(speeds[nextIndex])
    }
}

data class PlayerUiState(
    val currentSong: Song? = null,
    val queue: List<Song> = emptyList(),
    val isPlaying: Boolean = false,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val currentPosition: Long = 0L,
    val totalDuration: Long = 0L,
    val volume: Float = 1.0f,
    val lastVolume: Float = 1.0f,
    val isMuted: Boolean = false,
    val error: String? = null,
    val isLoading: Boolean = false,
    val sleepTimerMinutes: Int? = null,
    val sleepTimerRemainingSeconds: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val lyrics: List<PlayerViewModel.LyricsLine> = emptyList(),
    val currentLyricIndex: Int = -1
)

enum class RepeatMode {
    OFF, ALL, ONE
}
