package com.downtify.app.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.downtify.app.domain.model.VideoQuality
import com.downtify.app.domain.model.Song
import com.downtify.app.ui.navigation.Screen
import com.downtify.app.viewmodel.HomeViewModel
import com.downtify.app.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSheet by remember { mutableStateOf(false) }
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val showWelcome by viewModel.showWelcome.collectAsState()

    if (showWelcome) {
        WelcomeDialog(onDismiss = viewModel::dismissWelcome)
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            HeroHeader(onNavigateToPlayer = { navController.navigate(Screen.Player.route) })

            Column(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                OutlinedTextField(
                    value = uiState.inputUrl,
                    onValueChange = viewModel::onUrlChanged,
                    label = { Text("Spotify / SoundCloud link") },
                    placeholder = { Text("Paste a Spotify or SoundCloud link") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Link, contentDescription = null)
                    },
                    trailingIcon = {
                        if (uiState.inputUrl.isNotEmpty()) {
                            Row {
                                IconButton(onClick = { viewModel.resolveUrl() }) {
                                    Icon(Icons.Default.Search, contentDescription = "Resolve")
                                }
                                IconButton(onClick = { viewModel.onUrlChanged("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { viewModel.resolveUrl() }),
                    shape = RoundedCornerShape(12.dp)
                )

                AnimatedVisibility(visible = uiState.error != null) {
                    Text(
                        text = uiState.error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (uiState.isLoading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    repeat(3) {
                        ShimmerTrackItem()
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (uiState.resolvedSongs.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${uiState.resolvedSongs.size} track(s)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            FilledTonalButton(
                                onClick = viewModel::downloadAll,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Download All")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            items(uiState.resolvedSongs, key = { it.songId }) { song ->
                                val progress = uiState.activeDownloads[song.songId]
                                val isDownloaded = uiState.downloadedSongs.contains(song.songId)
                                val videoProgress = uiState.activeVideoDownloads[song.songId]
                                val isVideoDownloaded = uiState.downloadedVideos.contains(song.songId)
                                SongItem(
                                    song = song,
                                    isDownloading = progress != null,
                                    isDownloaded = isDownloaded,
                                    downloadPercent = progress?.percent ?: 0f,
                                    downloadMessage = progress?.message,
                                    downloadSpeed = progress?.downloadSpeed ?: 0,
                                    uploadSpeed = progress?.uploadSpeed ?: 0,
                                    isVideoDownloading = videoProgress != null,
                                    isVideoDownloaded = isVideoDownloaded,
                                    onDownloadVideo = { viewModel.fetchVideoQualities(song) },
                                    onClick = {
                                        selectedSong = song
                                        showSheet = true
                                    }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                } else if (!uiState.isLoading) {
                    EmptyState()
                }
            }
        }
    }

    selectedSong?.let { song ->
        if (showSheet) {
            val progress = uiState.activeDownloads[song.songId]
            val isDownloaded = uiState.downloadedSongs.contains(song.songId)
            ModalBottomSheet(
                onDismissRequest = { showSheet = false },
                sheetState = sheetState,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                TrackBottomSheet(
                    song = song,
                    isDownloading = progress != null,
                    isDownloaded = isDownloaded,
                    downloadPercent = progress?.percent ?: 0f,
                    downloadMessage = progress?.message,
                    downloadSpeed = progress?.downloadSpeed ?: 0,
                    uploadSpeed = progress?.uploadSpeed ?: 0,
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
private fun HeroHeader(
    onNavigateToPlayer: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1DB954).copy(alpha = 0.15f),
                        Color.Transparent
                    )
                )
            )
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFF1DB954),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Downtify",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1DB954)
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onNavigateToPlayer) {
                    Icon(Icons.Default.Headset, contentDescription = "Player", tint = Color(0xFF1DB954))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Download music from Spotify & SoundCloud",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No tracks found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Try a different Spotify or SoundCloud link",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun TrackBottomSheet(
    song: Song,
    isDownloading: Boolean,
    isDownloaded: Boolean,
    downloadPercent: Float,
    downloadMessage: String?,
    downloadSpeed: Long,
    uploadSpeed: Long,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = song.coverUrl,
            contentDescription = "Cover Art",
            modifier = Modifier
                .size(220.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = song.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = song.artists.joinToString(", "),
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
            if (song.explicit) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "E",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = formatDuration(song.duration),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (song.year != null) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "·  ${song.year}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (song.albumName != null) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "·  ${song.albumName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isDownloading) {
            Column(modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { downloadPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFF1DB954),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = downloadMessage ?: "Downloading...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF1DB954)
                    )
                    Text(
                        text = "${downloadPercent.toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (downloadSpeed > 0 || uploadSpeed > 0) {
                    Text(
                        text = "${"↓"} ${formatSpeed(downloadSpeed)}  ${"↑"} ${formatSpeed(uploadSpeed)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    onPlay()
                    onDismiss()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Play")
            }
            if (isDownloaded) {
                FilledTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Downloaded")
                }
            } else if (isDownloading) {
                FilledTonalButton(
                    onClick = {},
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = false
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Downloading")
                }
            } else {
                FilledTonalButton(
                    onClick = {
                        onDownload()
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF1DB954),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download")
                }
            }
        }
    }
}

@Composable
private fun ShimmerTrackItem() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val shimmerGradient = remember(translateAnim, surfaceVariant) {
        Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                surfaceVariant.copy(alpha = 0.25f),
                Color.Transparent
            ),
            start = Offset(translateAnim - 300f, 0f),
            end = Offset(translateAnim, 0f)
        )
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .drawBehind { drawRect(brush = shimmerGradient) }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .drawBehind { drawRect(brush = shimmerGradient) }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .drawBehind { drawRect(brush = shimmerGradient) }
                )
            }
        }
    }
}

@Composable
fun SongItem(
    song: Song,
    isDownloading: Boolean = false,
    isDownloaded: Boolean = false,
    downloadPercent: Float = 0f,
    downloadMessage: String? = null,
    downloadSpeed: Long = 0,
    uploadSpeed: Long = 0,
    isVideoDownloading: Boolean = false,
    isVideoDownloaded: Boolean = false,
    onDownloadVideo: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (isDownloading && downloadPercent > 0f) {
                        drawRect(
                            color = Color(0xFF1DB954).copy(alpha = 0.12f),
                            size = Size(size.width * downloadPercent / 100f, size.height)
                        )
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = song.coverUrl,
                    contentDescription = "Cover Art",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artists.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (song.explicit) {
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "E",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        SourceBadge(source = song.source)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = formatDuration(song.duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                if (isDownloaded) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = Color(0xFF1DB954),
                        modifier = Modifier.size(24.dp)
                    )
                } else if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF1DB954)
                    )
                } else {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(modifier = Modifier.size(24.dp)) {
                    if (isVideoDownloaded) {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = "Video Downloaded",
                            tint = Color(0xFF1DB954),
                            modifier = Modifier.size(24.dp)
                        )
                    } else if (isVideoDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFFFF0000)
                        )
                    } else if (song.source != "soundcloud") {
                        IconButton(
                            onClick = onDownloadVideo,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Videocam,
                                contentDescription = "Download Video",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            if (isDownloading && downloadMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = downloadMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF1DB954),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (downloadSpeed > 0 || uploadSpeed > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${"↓"} ${formatSpeed(downloadSpeed)}  ${"↑"} ${formatSpeed(uploadSpeed)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
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

fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(minutes, secs)
}

fun formatSpeed(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0) return "0 B/s"
    val units = listOf("B/s", "KB/s", "MB/s", "GB/s")
    var speed = bytesPerSecond.toDouble()
    var unitIndex = 0
    while (speed >= 1024 && unitIndex < units.size - 1) {
        speed /= 1024
        unitIndex++
    }
    return "%.1f %s".format(speed, units[unitIndex])
}

@Composable
fun SourceBadge(source: String) {
    val (label, color) = when (source.lowercase()) {
        "spotify" -> "Spotify" to Color(0xFF1DB954)
        "soundcloud" -> "SoundCloud" to Color(0xFFFF5500)
        "youtube" -> "YouTube" to Color(0xFFFF0000)
        else -> source to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    }
    Surface(
        shape = RoundedCornerShape(3.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
    )
    }
}

@Composable
fun WelcomeDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Welcome to Downtify!", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Downtify lets you download music and videos from Spotify, SoundCloud, and YouTube.", style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider()
                Text("Built With", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("• Kotlin & Jetpack Compose (Material 3)", style = MaterialTheme.typography.bodySmall)
                Text("• MVVM + Hilt (dependency injection)", style = MaterialTheme.typography.bodySmall)
                Text("• Room database (local SQLite)", style = MaterialTheme.typography.bodySmall)
                Text("• DataStore Preferences", style = MaterialTheme.typography.bodySmall)
                Text("• NewPipe Extractor (YouTube metadata)", style = MaterialTheme.typography.bodySmall)
                Text("• FFmpeg (audio/video muxing)", style = MaterialTheme.typography.bodySmall)
                Text("• OkHttp (TLS downloads)", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("Download Formats", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Audio: MP3, FLAC, M4A, OGG, OPUS", style = MaterialTheme.typography.bodySmall)
                Text("Video: MP4, WEBM, MKV (up to 4K)", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("Features", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("• Resolve Spotify/SoundCloud links (playlists, albums, tracks)", style = MaterialTheme.typography.bodySmall)
                Text("• Search YouTube videos inline", style = MaterialTheme.typography.bodySmall)
                Text("• Choose audio quality (128–320 kbps)", style = MaterialTheme.typography.bodySmall)
                Text("• Download lyrics (LRC synced)", style = MaterialTheme.typography.bodySmall)
                Text("• Generate M3U playlists automatically", style = MaterialTheme.typography.bodySmall)
                Text("• Organize downloads by artist / album", style = MaterialTheme.typography.bodySmall)
                Text("• Video quality picker before download", style = MaterialTheme.typography.bodySmall)
                Text("• In-app player with progress tracking", style = MaterialTheme.typography.bodySmall)
                Text("• Parallel downloads (configurable)", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("Saves To", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Audio: {Music Folder}/Downtify/[artist]/[album]/", style = MaterialTheme.typography.bodySmall)
                Text("Videos: {Music Folder}/Downtify Videos/", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("To get started, paste a Spotify or SoundCloud link above, or use the Search tab to find YouTube content.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Get Started")
            }
        }
    )
}