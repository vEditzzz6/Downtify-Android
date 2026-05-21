package com.downtify.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.downtify.app.data.database.DownloadedTrackEntity
import com.downtify.app.data.database.DownloadedVideoEntity
import com.downtify.app.data.database.MonitoredPlaylistEntity
import com.downtify.app.data.repository.DowntifyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumItem(
    val name: String,
    val artist: String,
    val coverUrl: String?,
    val tracks: List<DownloadedTrackEntity>
)

data class ArtistItem(
    val name: String,
    val tracks: List<DownloadedTrackEntity>
)

data class PlaylistItem(
    val id: Int,
    val name: String,
    val tracks: List<DownloadedTrackEntity>
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: DowntifyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
            combine(
                repository.getLocalTracks(),
                repository.getMonitoredPlaylists(),
                repository.getLocalVideos(),
                _searchQuery
            ) { tracks, monitoredPlaylists, videos, query ->
                if (query.isBlank()) {
                    _uiState.value.copy(
                        tracks = tracks,
                        albums = groupAlbums(tracks),
                        artists = groupArtists(tracks),
                        playlists = groupPlaylists(tracks, monitoredPlaylists),
                        videos = videos
                    )
                } else {
                    val filtered = tracks.filter { it.matchesQuery(query) }
                    _uiState.value.copy(
                        tracks = filtered,
                        albums = groupAlbums(filtered),
                        artists = groupArtists(filtered),
                        playlists = emptyList(),
                        videos = videos
                    )
                }
            }.collect { state ->
                _uiState.value = state.copy(isLoading = false)
            }
        }
    }

    fun refresh() {
        // Flow is observed in init, no manual refresh needed
    }

    fun searchTracks(query: String) {
        _searchQuery.value = query
    }

    fun deleteTrack(track: DownloadedTrackEntity) {
        viewModelScope.launch {
            repository.deleteTrack(track)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun DownloadedTrackEntity.matchesQuery(query: String): Boolean {
        val q = query.lowercase()
        return name.lowercase().contains(q) ||
            artists.lowercase().contains(q) ||
            (albumName?.lowercase()?.contains(q) == true)
    }

    private fun groupAlbums(tracks: List<DownloadedTrackEntity>): List<AlbumItem> {
        return tracks.groupBy { it.albumName ?: "Unknown Album" }
            .map { (name, albumTracks) ->
                val first = albumTracks.first()
                AlbumItem(
                    name = name,
                    artist = first.artists,
                    coverUrl = first.coverUrl,
                    tracks = albumTracks.sortedBy { it.name }
                )
            }
            .sortedBy { it.name }
    }

    private fun groupArtists(tracks: List<DownloadedTrackEntity>): List<ArtistItem> {
        val artistMap = mutableMapOf<String, MutableList<DownloadedTrackEntity>>()
        tracks.forEach { track ->
            track.artists.split(", ").filter { it.isNotBlank() }.forEach { artist ->
                artistMap.getOrPut(artist) { mutableListOf() }.add(track)
            }
        }
        return artistMap.map { (name, artistTracks) ->
            ArtistItem(name = name, tracks = artistTracks)
        }.sortedBy { it.name }
    }

    private fun groupPlaylists(
        tracks: List<DownloadedTrackEntity>,
        monitored: List<MonitoredPlaylistEntity>
    ): List<PlaylistItem> {
        return monitored.map { playlist ->
            PlaylistItem(
                id = playlist.id,
                name = playlist.name,
                tracks = tracks.filter { it.playlistId == playlist.id }
            )
        }.filter { it.tracks.isNotEmpty() }
    }
}

data class LibraryUiState(
    val tracks: List<DownloadedTrackEntity> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val videos: List<DownloadedVideoEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
