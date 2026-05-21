package com.downtify.app.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.downtify.app.ui.screens.home.SourceBadge
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.downtify.app.data.database.DownloadedTrackEntity
import com.downtify.app.data.database.toSong
import com.downtify.app.ui.navigation.Screen
import com.downtify.app.ui.screens.home.formatDuration
import com.downtify.app.viewmodel.AlbumItem
import com.downtify.app.viewmodel.ArtistItem
import com.downtify.app.viewmodel.LibraryViewModel
import com.downtify.app.viewmodel.PlaylistItem
import com.downtify.app.viewmodel.PlayerViewModel

private val tabTitles = listOf("Songs", "Albums", "Artists", "Playlists", "Videos")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    navController: NavController,
    viewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf<DownloadedTrackEntity?>(null) }
    var showSheet by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<DownloadedTrackEntity?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pagerState = rememberPagerState(pageCount = { tabTitles.size })
    val scope = rememberCoroutineScope()

    var selectedAlbum by remember { mutableStateOf<AlbumItem?>(null) }
    var selectedArtist by remember { mutableStateOf<ArtistItem?>(null) }
    var selectedPlaylist by remember { mutableStateOf<PlaylistItem?>(null) }

    val detailTracks: List<DownloadedTrackEntity>? = selectedAlbum?.tracks
        ?: selectedArtist?.tracks
        ?: selectedPlaylist?.tracks
    val detailTitle: String? = selectedAlbum?.name
        ?: selectedArtist?.name
        ?: selectedPlaylist?.name
    val detailQueue: List<DownloadedTrackEntity>? = detailTracks

    val activeQueue: List<DownloadedTrackEntity>
    val activeTrackIndex: Int
    if (detailQueue != null && selectedTrack != null) {
        activeQueue = detailQueue
        activeTrackIndex = detailQueue.indexOf(selectedTrack).coerceAtLeast(0)
    } else {
        activeQueue = uiState.tracks
        activeTrackIndex = uiState.tracks.indexOf(selectedTrack).coerceAtLeast(0)
    }

    if (selectedAlbum != null || selectedArtist != null || selectedPlaylist != null) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            DetailHeader(
                title = detailTitle ?: "",
                onBack = {
                    selectedAlbum = null
                    selectedArtist = null
                    selectedPlaylist = null
                }
            )
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                items(detailTracks ?: emptyList(), key = { it.id }) { track ->
                    TrackItem(track = track, onClick = {
                        selectedTrack = track
                        showSheet = true
                    })
                }
            }
        }
    } else {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        viewModel.searchTracks(it)
                    },
                    label = { Text("Search library...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = Color(0xFF1DB954)
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(title, fontWeight = FontWeight.Medium) },
                            selectedContentColor = Color(0xFF1DB954),
                            unselectedContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (page) {
                            0 -> SongsTab(
                                tracks = uiState.tracks,
                                onTrackClick = { track ->
                                    selectedTrack = track
                                    showSheet = true
                                }
                            )
                            1 -> AlbumsTab(
                                albums = uiState.albums,
                                onAlbumClick = { album -> selectedAlbum = album }
                            )
                            2 -> ArtistsTab(
                                artists = uiState.artists,
                                onArtistClick = { artist -> selectedArtist = artist }
                            )
                            3 -> PlaylistsTab(
                                playlists = uiState.playlists,
                                onPlaylistClick = { playlist -> selectedPlaylist = playlist }
                            )
                            4 -> VideosTab(
                                videos = uiState.videos,
                                onVideoClick = { }
                            )
                        }
                    }
                }
            }
        }
    }

    selectedTrack?.let { track ->
        if (showSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSheet = false },
                sheetState = sheetState,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                LibraryTrackSheet(
                    track = track,
                    onPlay = {
                        playerViewModel.setQueue(
                            activeQueue.map { it.toSong() },
                            activeTrackIndex
                        )
                        navController.navigate(Screen.Player.route)
                    },
                    onDelete = {
                        showDeleteDialog = track
                        showSheet = false
                    }
                )
            }
        }
    }

    showDeleteDialog?.let { track ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Track") },
            text = { Text("Delete \"${track.name}\" from your library?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTrack(track)
                    showDeleteDialog = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DetailHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SongsTab(
    tracks: List<DownloadedTrackEntity>,
    onTrackClick: (DownloadedTrackEntity) -> Unit
) {
    if (tracks.isEmpty()) {
        EmptyTabState("No songs yet", "Download songs from Home or Search")
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(tracks, key = { it.id }) { track ->
                TrackItem(track = track, onClick = { onTrackClick(track) })
            }
        }
    }
}

@Composable
private fun AlbumsTab(
    albums: List<AlbumItem>,
    onAlbumClick: (AlbumItem) -> Unit
) {
    if (albums.isEmpty()) {
        EmptyTabState("No albums yet", "Downloaded tracks are grouped by album")
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(albums, key = { it.name }) { album ->
                AlbumCard(album = album, onClick = { onAlbumClick(album) })
            }
        }
    }
}

@Composable
private fun ArtistsTab(
    artists: List<ArtistItem>,
    onArtistClick: (ArtistItem) -> Unit
) {
    if (artists.isEmpty()) {
        EmptyTabState("No artists yet", "Downloaded tracks are grouped by artist")
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(artists, key = { it.name }) { artist ->
                ArtistCard(artist = artist, onClick = { onArtistClick(artist) })
            }
        }
    }
}

@Composable
private fun PlaylistsTab(
    playlists: List<PlaylistItem>,
    onPlaylistClick: (PlaylistItem) -> Unit
) {
    if (playlists.isEmpty()) {
        EmptyTabState("No playlists yet", "Monitor a playlist to see it here")
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(playlists, key = { it.id }) { playlist ->
                PlaylistCard(playlist = playlist, onClick = { onPlaylistClick(playlist) })
            }
        }
    }
}

@Composable
private fun EmptyTabState(title: String, subtitle: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.MusicOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun AlbumCard(album: AlbumItem, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column {
            AsyncImage(
                model = album.coverUrl,
                contentDescription = album.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = album.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${album.tracks.size} track${if (album.tracks.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF1DB954)
                )
            }
        }
    }
}

@Composable
private fun ArtistCard(artist: ArtistItem, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color(0xFF1DB954).copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${artist.tracks.size} track${if (artist.tracks.size != 1) "s" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF1DB954)
            )
        }
    }
}

@Composable
@Suppress("DEPRECATION")
private fun PlaylistCard(playlist: PlaylistItem, onClick: () -> Unit) {
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
            Icon(
                Icons.Default.QueueMusic,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color(0xFF1DB954).copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.tracks.size} track${if (playlist.tracks.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun LibraryTrackSheet(
    track: DownloadedTrackEntity,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = track.coverUrl,
            contentDescription = "Cover Art",
            modifier = Modifier
                .size(220.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = track.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = track.artists,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDuration(track.duration),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (track.albumName != null) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "·  ${track.albumName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (track.fileSize > 0) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "·  ${formatFileSize(track.fileSize)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(
                onClick = onPlay,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFF1DB954),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Play")
            }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Delete")
            }
        }
    }
}

@Composable
private fun TrackItem(
    track: DownloadedTrackEntity,
    onClick: () -> Unit
) {
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
            AsyncImage(
                model = track.coverUrl,
                contentDescription = "Cover Art",
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artists,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SourceBadge(source = track.source)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatDuration(track.duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Downloaded",
                tint = Color(0xFF1DB954),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    return "%.1f %s".format(size, units[unitIndex])
}

@Composable
private fun VideosTab(
    videos: List<com.downtify.app.data.database.DownloadedVideoEntity>,
    onVideoClick: (com.downtify.app.data.database.DownloadedVideoEntity) -> Unit
) {
    if (videos.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No videos downloaded yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(videos, key = { it.id }) { video ->
                VideoItem(video = video, onClick = { onVideoClick(video) })
            }
        }
    }
}

@Composable
private fun VideoItem(
    video: com.downtify.app.data.database.DownloadedVideoEntity,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = video.coverUrl,
                contentDescription = "Cover Art",
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = video.artists,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SourceBadge(source = video.source)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${video.videoQuality} · ${video.videoFormat.uppercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatFileSize(video.fileSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            Icon(
                Icons.Default.Videocam,
                contentDescription = "Video",
                tint = Color(0xFFFF0000),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
