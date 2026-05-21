package com.downtify.app.ui.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.downtify.app.data.local.ArtistSearchResult
import com.downtify.app.data.local.PlaylistSearchResult
import com.downtify.app.domain.model.VideoQuality
import com.downtify.app.domain.model.Song
import com.downtify.app.ui.screens.home.SongItem
import com.downtify.app.ui.screens.home.TrackBottomSheet
import com.downtify.app.viewmodel.PlayerViewModel
import com.downtify.app.viewmodel.SearchViewModel
import com.downtify.app.viewmodel.SearchUiState
import kotlinx.coroutines.launch

private val tabTitles = listOf("Songs", "Artists", "Playlists")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(pageCount = { tabTitles.size })
    val scope = rememberCoroutineScope()

    var showSheet by remember { mutableStateOf(false) }
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val selectedArtist = uiState.selectedArtist
    val selectedPlaylist = uiState.selectedPlaylist

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (selectedArtist != null || selectedPlaylist != null) {
                DetailView(
                    uiState = uiState,
                    onBack = { viewModel.clearSelection() },
                    onSongClick = { song ->
                        selectedSong = song
                        showSheet = true
                    },
                    onDownloadVideo = { song -> viewModel.fetchVideoQualities(song) }
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = { viewModel.onQueryChanged(it) },
                    label = { Text("Search YouTube...") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                if (uiState.error != null) {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.songs.isNotEmpty() || uiState.artists.isNotEmpty() || uiState.playlists.isNotEmpty()) {
                    TabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = Color(0xFF1DB954)
                    ) {
                        tabTitles.forEachIndexed { index, title ->
                            val count = when (index) {
                                0 -> uiState.songs.size
                                1 -> uiState.artists.size
                                2 -> uiState.playlists.size
                                else -> 0
                            }
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                text = {
                                    Text(
                                        "$title ($count)",
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                selectedContentColor = Color(0xFF1DB954),
                                unselectedContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (page) {
                            0 -> SongsTab(
                                songs = uiState.songs,
                                downloadedSongs = uiState.downloadedSongs,
                                downloadedVideos = uiState.downloadedVideos,
                                downloadingSongId = uiState.downloadingSongId,
                                isDownloading = uiState.isDownloading,
                                downloadPercent = uiState.downloadPercent,
                                downloadMessage = uiState.downloadMessage,
                                downloadSpeed = uiState.downloadSpeed,
                                uploadSpeed = uiState.uploadSpeed,
                                videoDownloadingSongId = uiState.videoDownloadingSongId,
                                isVideoDownloading = uiState.isVideoDownloading,
                                hasMore = uiState.nextPage != null,
                                isLoadingMore = uiState.isLoadingMore,
                                onLoadMore = { viewModel.loadMore() },
                                onSongClick = { song ->
                                    selectedSong = song
                                    showSheet = true
                                },
                                onDownloadVideo = { song -> viewModel.fetchVideoQualities(song) }
                            )
                            1 -> ArtistsTab(
                                artists = uiState.artists,
                                onArtistClick = { viewModel.selectArtist(it) }
                            )
                            2 -> PlaylistsTab(
                                playlists = uiState.playlists,
                                onPlaylistClick = { viewModel.selectPlaylist(it) }
                            )
                        }
                    }
                } else if (uiState.query.isNotEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No results found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    selectedSong?.let { song ->
        if (showSheet) {
            val isDownloading = uiState.isDownloading && song.songId == uiState.downloadingSongId
            val isDownloaded = song.songId in uiState.downloadedSongs
            ModalBottomSheet(
                onDismissRequest = { showSheet = false },
                sheetState = sheetState,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                TrackBottomSheet(
                    song = song,
                    isDownloading = isDownloading,
                    isDownloaded = isDownloaded,
                    downloadPercent = if (isDownloading) uiState.downloadPercent else 0f,
                    downloadMessage = if (isDownloading) uiState.downloadMessage else null,
                    downloadSpeed = if (isDownloading) uiState.downloadSpeed else 0,
                    uploadSpeed = if (isDownloading) uiState.uploadSpeed else 0,
                    onPlay = { playerViewModel.setQueue(listOf(song), 0) },
                    onDownload = { viewModel.downloadSong(song) },
                    onDismiss = { showSheet = false }
                )
            }
        }
    }

    uiState.videoQualitySong?.let { song ->
        AlertDialog(
            onDismissRequest = { viewModel.clearVideoQualityState() },
            title = { Text("Select Video Quality") },
            text = {
                if (uiState.isLoadingQualities) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.videoQualities.isNullOrEmpty()) {
                    Text("No qualities available")
                } else {
                    Column {
                        uiState.videoQualities!!.forEach { info ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = false,
                                    onClick = {
                                        val quality = resolutionToQuality(info.resolution)
                                        viewModel.downloadVideoWithQuality(song, quality)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = info.resolution,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = if (info.audioUrl != null) "Video only" else "Full video",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearVideoQualityState() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DetailView(
    uiState: SearchUiState,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onDownloadVideo: (Song) -> Unit = {}
) {
    val artist = uiState.selectedArtist
    val playlist = uiState.selectedPlaylist
    val tracks = if (artist != null) uiState.selectedArtistSongs else uiState.selectedPlaylistTracks
    val name = artist?.name ?: playlist?.name ?: ""
    val thumbnailUrl = artist?.thumbnailUrl ?: playlist?.thumbnailUrl
    val subtitle = artist?.let { formatSubscribers(it.subscriberCount) }
        ?: playlist?.let { "${it.trackCount} tracks · ${it.uploaderName ?: ""}" }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                if (thumbnailUrl != null) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = name,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(if (artist != null) 24.dp else 8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null && subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            HorizontalDivider()
        }

        if (uiState.isDetailsLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (tracks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (artist != null) "No songs found for this artist" else "No tracks found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(tracks, key = { it.songId }) { song ->
                SongItem(
                    song = song,
                    isDownloaded = song.songId in uiState.downloadedSongs,
                    isDownloading = uiState.isDownloading && song.songId == uiState.downloadingSongId,
                    downloadPercent = if (song.songId == uiState.downloadingSongId) uiState.downloadPercent else 0f,
                    downloadMessage = if (song.songId == uiState.downloadingSongId) uiState.downloadMessage else null,
                    downloadSpeed = if (song.songId == uiState.downloadingSongId) uiState.downloadSpeed else 0,
                    uploadSpeed = if (song.songId == uiState.downloadingSongId) uiState.uploadSpeed else 0,
                    isVideoDownloaded = song.songId in uiState.downloadedVideos,
                    isVideoDownloading = uiState.isVideoDownloading && song.songId == uiState.videoDownloadingSongId,
                    onDownloadVideo = { onDownloadVideo(song) },
                    onClick = { onSongClick(song) }
                )
            }
        }
    }
}

@Composable
private fun SongsTab(
    songs: List<Song>,
    downloadedSongs: Set<String>,
    downloadedVideos: Set<String> = emptySet(),
    downloadingSongId: String? = null,
    isDownloading: Boolean = false,
    downloadPercent: Float = 0f,
    downloadMessage: String? = null,
    downloadSpeed: Long = 0,
    uploadSpeed: Long = 0,
    videoDownloadingSongId: String? = null,
    isVideoDownloading: Boolean = false,
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onSongClick: (Song) -> Unit,
    onDownloadVideo: (Song) -> Unit = {}
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(songs, key = { it.songId }) { song ->
            SongItem(
                song = song,
                isDownloaded = song.songId in downloadedSongs,
                isDownloading = isDownloading && song.songId == downloadingSongId,
                downloadPercent = if (song.songId == downloadingSongId) downloadPercent else 0f,
                downloadMessage = if (song.songId == downloadingSongId) downloadMessage else null,
                downloadSpeed = if (song.songId == downloadingSongId) downloadSpeed else 0,
                uploadSpeed = if (song.songId == downloadingSongId) uploadSpeed else 0,
                isVideoDownloaded = song.songId in downloadedVideos,
                isVideoDownloading = isVideoDownloading && song.songId == videoDownloadingSongId,
                onDownloadVideo = { onDownloadVideo(song) },
                onClick = { onSongClick(song) }
            )
        }

        if (hasMore || isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingMore) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    } else {
                        OutlinedButton(onClick = onLoadMore) {
                            Text("Load More")
                        }
                    }
                }
            }
        }
    }
}

fun resolutionToQuality(resolution: String): VideoQuality {
    val nums = Regex("(\\d+)").findAll(resolution).map { it.value.toIntOrNull() ?: 0 }.toList()
    val height = when {
        nums.isEmpty() -> 360
        resolution.contains("x", ignoreCase = true) && nums.size >= 2 -> nums[1]
        else -> nums[0]
    }
    return when {
        height >= 2160 -> VideoQuality.P_2160
        height >= 1080 -> VideoQuality.P_1080
        height >= 720 -> VideoQuality.P_720
        height >= 480 -> VideoQuality.P_480
        else -> VideoQuality.P_360
    }
}

@Composable
private fun ArtistsTab(
    artists: List<ArtistSearchResult>,
    onArtistClick: (ArtistSearchResult) -> Unit
) {
    if (artists.isEmpty()) {
        EmptySearchCategory("No artists found")
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(artists, key = { it.channelId }) { artist ->
                ArtistCard(
                    artist = artist,
                    onClick = { onArtistClick(artist) }
                )
            }
        }
    }
}

@Composable
private fun PlaylistsTab(
    playlists: List<PlaylistSearchResult>,
    onPlaylistClick: (PlaylistSearchResult) -> Unit
) {
    if (playlists.isEmpty()) {
        EmptySearchCategory("No playlists found")
    } else {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(playlists, key = { it.playlistId }) { playlist ->
                PlaylistCard(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist) }
                )
            }
        }
    }
}

@Composable
private fun EmptySearchCategory(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ArtistCard(artist: ArtistSearchResult, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (artist.thumbnailUrl != null) {
                AsyncImage(
                    model = artist.thumbnailUrl,
                    contentDescription = artist.name,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(40.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color(0xFF1DB954).copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (artist.subscriberCount > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatSubscribers(artist.subscriberCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PlaylistCard(playlist: PlaylistSearchResult, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (playlist.thumbnailUrl != null) {
                AsyncImage(
                    model = playlist.thumbnailUrl,
                    contentDescription = playlist.name,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color(0xFF1DB954).copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (playlist.uploaderName != null) {
                    Text(
                        text = playlist.uploaderName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (playlist.trackCount > 0) {
                    Text(
                        text = "${playlist.trackCount} tracks",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF1DB954)
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

private fun formatSubscribers(count: Long): String {
    return when {
        count >= 1_000_000 -> "${count / 1_000_000}M subscribers"
        count >= 1_000 -> "${count / 1_000}K subscribers"
        else -> "$count subscribers"
    }
}
