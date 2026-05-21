package com.downtify.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.downtify.app.domain.model.Song
import com.downtify.app.ui.screens.home.formatDuration
import com.downtify.app.viewmodel.PlayerUiState
import com.downtify.app.viewmodel.PlayerViewModel
import com.downtify.app.viewmodel.RepeatMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    navController: NavController,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showPlaybackSpeedDialog by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

    val song = uiState.currentSong
    val dominantColor = song?.coverUrl?.let { Color(0xFF1A1A2E) } ?: Color(0xFF1A1A2E)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        dominantColor,
                        MaterialTheme.colorScheme.background
                    ),
                    startY = 0f,
                    endY = 2000f
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showQueue) {
                QueueList(
                    queue = uiState.queue,
                    currentSong = song,
                    onPlayTrack = { index -> viewModel.playTrackAtIndex(index) },
                    onBackClick = { showQueue = false }
                )
            } else if (showLyrics) {
                LyricsView(
                    lyrics = uiState.lyrics,
                    currentIndex = uiState.currentLyricIndex,
                    hasLyrics = uiState.lyrics.isNotEmpty(),
                    onBackClick = { showLyrics = false }
                )
            } else {
                NowPlayingCard(
                    song = song,
                    isPlaying = uiState.isPlaying,
                    currentPosition = uiState.currentPosition,
                    totalDuration = uiState.totalDuration,
                    volume = uiState.volume,
                    isMuted = uiState.isMuted,
                    isShuffle = uiState.isShuffle,
                    repeatMode = uiState.repeatMode,
                    sleepTimerMinutes = uiState.sleepTimerMinutes,
                    sleepTimerRemainingSeconds = uiState.sleepTimerRemainingSeconds,
                    playbackSpeed = uiState.playbackSpeed,
                    hasLyrics = uiState.lyrics.isNotEmpty(),
                    lyrics = uiState.lyrics,
                    currentLyricIndex = uiState.currentLyricIndex,
                    onPlayPause = { viewModel.togglePlayPause() },
                    onNext = { viewModel.next() },
                    onPrevious = { viewModel.previous() },
                    onToggleShuffle = { viewModel.toggleShuffle() },
                    onCycleRepeat = { viewModel.cycleRepeatMode() },
                    onSeek = { viewModel.seekTo(it) },
                    onUpdateVolume = { viewModel.updateVolume(it) },
                    onToggleMute = { viewModel.toggleMute() },
                    onSleepTimerClick = { showSleepTimerDialog = true },
                    onSpeedClick = { showPlaybackSpeedDialog = true },
                    onLyricsClick = { showLyrics = true },
                    onQueueClick = { showQueue = true },
                    onBackClick = { navController.popBackStack() }
                )
            }
        }

        if (showSleepTimerDialog) {
            SleepTimerDialog(
                uiState = uiState,
                viewModel = viewModel,
                onDismiss = { showSleepTimerDialog = false }
            )
        }

        if (showPlaybackSpeedDialog) {
            PlaybackSpeedDialog(
                uiState = uiState,
                viewModel = viewModel,
                onDismiss = { showPlaybackSpeedDialog = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlayingCard(
    song: Song?,
    isPlaying: Boolean,
    currentPosition: Long,
    totalDuration: Long,
    volume: Float,
    isMuted: Boolean,
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    sleepTimerMinutes: Int?,
    sleepTimerRemainingSeconds: Long,
    playbackSpeed: Float,
    hasLyrics: Boolean,
    lyrics: List<PlayerViewModel.LyricsLine>,
    currentLyricIndex: Int,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSeek: (Long) -> Unit,
    onUpdateVolume: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onQueueClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragging by interactionSource.collectIsDraggedAsState()

    // The single source of truth for the slider's visual position
    var sliderDisplayValue by remember { mutableFloatStateOf(currentPosition.toFloat()) }

    // 1. Sync with the Player's position when it changes significantly or when we stop dragging
    LaunchedEffect(currentPosition, isDragging) {
        if (!isDragging) {
            sliderDisplayValue = currentPosition.toFloat()
        }
    }

    // 2. Liquid Smooth Ticker: Locally increment the position every frame while playing
    // This removes the "ticking" look caused by the 200ms polling interval.
    if (isPlaying && !isDragging) {
        LaunchedEffect(playbackSpeed) {
            var lastFrameTime = System.nanoTime()
            while (true) {
                withFrameNanos { frameTime ->
                    val elapsedMillis = (frameTime - lastFrameTime) / 1_000_000f
                    sliderDisplayValue += elapsedMillis * playbackSpeed
                    lastFrameTime = frameTime
                }
            }
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState, enabled = !isDragging)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.weight(1f))

            if (sleepTimerMinutes != null) {
                Text(
                    text = "${sleepTimerRemainingSeconds / 60}:${(sleepTimerRemainingSeconds % 60).toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF1DB954)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            IconButton(onClick = onSleepTimerClick) {
                Icon(Icons.Default.Timer, contentDescription = "Sleep Timer", tint = Color(0xFF1DB954))
            }
            TextButton(onClick = onSpeedClick) {
                Text("${playbackSpeed}x", color = Color(0xFF1DB954))
            }
            IconButton(onClick = onLyricsClick) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Lyrics",
                    tint = if (hasLyrics) Color(0xFF1DB954) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            IconButton(onClick = onQueueClick) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue")
            }
        }

        if (song == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No track selected",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }

        Spacer(modifier = Modifier.height(24.dp))

        AsyncImage(
            model = song.coverUrl,
            contentDescription = "Cover Art",
            modifier = Modifier
                .size(300.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = song.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = song.artists.joinToString(", "),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration((sliderDisplayValue.toLong() / 1000).toInt()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatDuration((totalDuration / 1000).toInt()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = sliderDisplayValue.coerceIn(0f, if (totalDuration > 0) totalDuration.toFloat() else 1f),
            onValueChange = { 
                sliderDisplayValue = it
            },
            onValueChangeFinished = {
                onSeek(sliderDisplayValue.toLong())
            },
            valueRange = 0f..if (totalDuration > 0) totalDuration.toFloat() else 1f,
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF1DB954),
                activeTrackColor = Color(0xFF1DB954),
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (isShuffle) Color(0xFF1DB954) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            IconButton(onClick = onPrevious) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(32.dp))
            }
            FilledTonalButton(
                onClick = onPlayPause,
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFF1DB954)
                )
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp)
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = onCycleRepeat) {
                Icon(
                    imageVector = when (repeatMode) {
                        RepeatMode.ONE -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    },
                    contentDescription = "Repeat",
                    tint = if (repeatMode != RepeatMode.OFF) Color(0xFF1DB954) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleMute) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Volume",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Slider(
                value = volume,
                onValueChange = onUpdateVolume,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF1DB954),
                    activeTrackColor = Color(0xFF1DB954),
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }

        if (hasLyrics && lyrics.isNotEmpty() && currentLyricIndex in lyrics.indices) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(
                text = lyrics[currentLyricIndex].text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1DB954),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun QueueList(
    queue: List<Song>,
    currentSong: Song?,
    onPlayTrack: (Int) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Queue",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (queue.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Queue is empty",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(queue, key = { i, _ -> i }) { index, song ->
                    QueueItem(
                        song = song,
                        isCurrent = song == currentSong,
                        onClick = { onPlayTrack(index) }
                    )
                }
            }
        }
    }
}

@Composable
fun LyricsView(
    lyrics: List<PlayerViewModel.LyricsLine>,
    currentIndex: Int,
    hasLyrics: Boolean,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (currentIndex > 0 && lyrics.isNotEmpty()) {
            listState.animateScrollToItem((currentIndex - 1).coerceAtLeast(0))
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Lyrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (!hasLyrics) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No lyrics available for this track",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                itemsIndexed(lyrics, key = { i, _ -> i }) { index, line ->
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal,
                        fontSize = if (index == currentIndex) MaterialTheme.typography.titleLarge.fontSize else MaterialTheme.typography.titleMedium.fontSize,
                        color = if (index == currentIndex) Color(0xFF1DB954) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 24.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueItem(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
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
            AsyncImage(
                model = song.coverUrl,
                contentDescription = "Cover Art",
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
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
            }

            if (isCurrent) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Now Playing",
                    tint = Color(0xFF1DB954),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = formatDuration(song.duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun SleepTimerDialog(
    uiState: PlayerUiState,
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val options = listOf(5, 15, 30, 45, 60, 90)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep Timer") },
        text = {
            Column {
                if (uiState.sleepTimerMinutes != null) {
                    TextButton(
                        onClick = {
                            viewModel.cancelSleepTimer()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cancel Timer")
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                options.forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.setSleepTimer(minutes)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("$minutes minutes")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun PlaybackSpeedDialog(
    uiState: PlayerUiState,
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback Speed") },
        text = {
            Column {
                speeds.forEach { speed ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = uiState.playbackSpeed == speed,
                            onClick = {
                                viewModel.setPlaybackSpeed(speed)
                                onDismiss()
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF1DB954)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${speed}x")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
