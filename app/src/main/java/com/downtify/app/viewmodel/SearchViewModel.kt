package com.downtify.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.downtify.app.data.local.ArtistSearchResult
import com.downtify.app.data.local.DownloadProgress
import com.downtify.app.data.local.PlaylistSearchResult
import com.downtify.app.data.local.VideoStreamInfo
import com.downtify.app.data.repository.DowntifyRepository
import com.downtify.app.data.repository.SettingsRepository
import com.downtify.app.domain.model.AppSettings
import com.downtify.app.domain.model.Song
import com.downtify.app.domain.model.VideoQuality
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import javax.inject.Inject

@HiltViewModel
@OptIn(FlowPreview::class)
class SearchViewModel @Inject constructor(
    private val repository: DowntifyRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")
    private var settings = AppSettings()

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collectLatest {
                settings = it
            }
        }
        _query
            .debounce(500)
            .distinctUntilChanged()
            .filter { it.trim().length >= 2 }
            .onEach { query ->
                performSearch(query.trim())
            }
            .launchIn(viewModelScope)
    }

    fun onQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        _query.value = query
    }

    fun selectArtist(artist: ArtistSearchResult) {
        viewModelScope.launch {
            val artistSongs = _uiState.value.songs.filter { artist.name in it.artists }
            val downloaded = artistSongs
                .filter { repository.isSongDownloaded(it.songId) }
                .map { it.songId }
                .toSet()
            _uiState.value = _uiState.value.copy(
                selectedArtist = artist,
                selectedArtistSongs = artistSongs,
                downloadedSongs = _uiState.value.downloadedSongs + downloaded
            )
        }
    }

    fun selectPlaylist(playlist: PlaylistSearchResult) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDetailsLoading = true)
            try {
                val tracks = repository.getPlaylistTracks(playlist.playlistId)
                val downloaded = tracks
                    .filter { repository.isSongDownloaded(it.songId) }
                    .map { it.songId }
                    .toSet()
                _uiState.value = _uiState.value.copy(
                    selectedPlaylist = playlist,
                    selectedPlaylistTracks = tracks,
                    downloadedSongs = _uiState.value.downloadedSongs + downloaded,
                    isDetailsLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to load playlist",
                    isDetailsLoading = false
                )
            }
        }
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedArtist = null,
            selectedArtistSongs = emptyList(),
            selectedPlaylist = null,
            selectedPlaylistTracks = emptyList()
        )
    }

    fun loadMore() {
        val state = _uiState.value
        val nextPage = state.nextPage ?: return
        if (state.isLoadingMore) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            try {
                val more = repository.searchMore(state.query, nextPage)
                val existingSongIds = state.songs.map { it.songId }.toSet()
                val existingArtistIds = state.artists.map { it.channelId }.toSet()
                val existingPlaylistIds = state.playlists.map { it.playlistId }.toSet()
                val newSongs = more.songs.filter { it.songId !in existingSongIds }
                val newArtists = more.artists.filter { it.channelId !in existingArtistIds }
                val newPlaylists = more.playlists.filter { it.playlistId !in existingPlaylistIds }
                val downloaded = more.songs
                    .filter { repository.isSongDownloaded(it.songId) }
                    .map { it.songId }
                    .toSet()
                _uiState.value = _uiState.value.copy(
                    songs = state.songs + newSongs,
                    artists = state.artists + newArtists,
                    playlists = state.playlists + newPlaylists,
                    nextPage = more.nextPage,
                    downloadedSongs = state.downloadedSongs + downloaded,
                    isLoadingMore = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    error = e.message ?: "Failed to load more results"
                )
            }
        }
    }

    private fun performSearch(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val categorized = repository.searchAll(query)
                val downloaded = categorized.songs
                    .filter { repository.isSongDownloaded(it.songId) }
                    .map { it.songId }
                    .toSet()
                _uiState.value = _uiState.value.copy(
                    songs = categorized.songs,
                    artists = categorized.artists,
                    playlists = categorized.playlists,
                    nextPage = categorized.nextPage,
                    downloadedSongs = downloaded,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Search failed"
                )
            }
        }
    }

    fun downloadSong(song: Song) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloading = true,
                downloadingSongId = song.songId,
                downloadPercent = 0f,
                downloadMessage = "Starting...",
                downloadSpeed = 0,
                uploadSpeed = 0,
                error = null
            )
            try {
                repository.downloadSong(song, settings).collectLatest { progress ->
                    when (progress.stage) {
                        DownloadProgress.Stage.QUEUED -> {
                            _uiState.value = _uiState.value.copy(
                                downloadMessage = progress.message
                            )
                        }
                        DownloadProgress.Stage.DOWNLOADING -> {
                            _uiState.value = _uiState.value.copy(
                                downloadPercent = progress.percent,
                                downloadMessage = "Downloading...",
                                downloadSpeed = progress.downloadSpeed
                            )
                        }
                        DownloadProgress.Stage.CONVERTING -> {
                            _uiState.value = _uiState.value.copy(
                                downloadMessage = "Converting..."
                            )
                        }
                        DownloadProgress.Stage.DONE -> {
                            val downloaded = _uiState.value.downloadedSongs.toMutableSet()
                            downloaded.add(song.songId)
                            val allDownloaded = _uiState.value.songs
                                .filter { repository.isSongDownloaded(it.songId) }
                                .map { it.songId }
                                .toSet()
                            _uiState.value = _uiState.value.copy(
                                isDownloading = false,
                                downloadingSongId = null,
                                downloadPercent = 100f,
                                downloadMessage = null,
                                downloadSpeed = 0,
                                uploadSpeed = 0,
                                downloadedSongs = allDownloaded
                            )
                        }
                        DownloadProgress.Stage.ERROR -> {
                            _uiState.value = _uiState.value.copy(
                                isDownloading = false,
                                downloadingSongId = null,
                                downloadMessage = null,
                                downloadSpeed = 0,
                                uploadSpeed = 0,
                                error = progress.message
                            )
                        }
                        else -> {
                            _uiState.value = _uiState.value.copy(
                                downloadMessage = progress.message
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    downloadingSongId = null,
                    downloadMessage = null,
                    downloadSpeed = 0,
                    uploadSpeed = 0,
                    error = e.message ?: "Download failed"
                )
            }
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
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isVideoDownloading = true,
                videoDownloadingSongId = song.songId,
                error = null,
                videoQualitySong = null,
                videoQualities = null
            )
            try {
                repository.downloadVideo(song, settings, videoQuality = quality).collectLatest { progress ->
                    when (progress.stage) {
                        DownloadProgress.Stage.DONE -> {
                            val downloaded = _uiState.value.downloadedVideos.toMutableSet()
                            downloaded.add(song.songId)
                            _uiState.value = _uiState.value.copy(
                                isVideoDownloading = false,
                                videoDownloadingSongId = null,
                                downloadedVideos = downloaded
                            )
                        }
                        DownloadProgress.Stage.ERROR -> {
                            _uiState.value = _uiState.value.copy(
                                isVideoDownloading = false,
                                videoDownloadingSongId = null,
                                error = progress.message
                            )
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isVideoDownloading = false,
                    videoDownloadingSongId = null,
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
}

data class SearchUiState(
    val query: String = "",
    val songs: List<Song> = emptyList(),
    val artists: List<ArtistSearchResult> = emptyList(),
    val playlists: List<PlaylistSearchResult> = emptyList(),
    val nextPage: Page? = null,
    val downloadedSongs: Set<String> = emptySet(),
    val downloadedVideos: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadingSongId: String? = null,
    val downloadPercent: Float = 0f,
    val downloadMessage: String? = null,
    val downloadSpeed: Long = 0,
    val uploadSpeed: Long = 0,
    val isVideoDownloading: Boolean = false,
    val videoDownloadingSongId: String? = null,
    val videoQualitySong: Song? = null,
    val videoQualities: List<VideoStreamInfo>? = null,
    val isLoadingQualities: Boolean = false,
    val error: String? = null,
    val selectedArtist: ArtistSearchResult? = null,
    val selectedArtistSongs: List<Song> = emptyList(),
    val selectedPlaylist: PlaylistSearchResult? = null,
    val selectedPlaylistTracks: List<Song> = emptyList(),
    val isDetailsLoading: Boolean = false
)
