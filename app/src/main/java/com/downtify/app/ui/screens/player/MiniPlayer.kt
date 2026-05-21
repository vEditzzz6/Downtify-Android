package com.downtify.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.downtify.app.domain.model.Song
import com.downtify.app.ui.navigation.Screen
import com.downtify.app.viewmodel.PlayerViewModel

@Composable
fun MiniPlayer(
    navController: NavController,
    onHasSong: (Boolean) -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentSong = uiState.currentSong ?: run {
        onHasSong(false)
        return
    }
    val isPlaying = uiState.isPlaying

    onHasSong(true)

    var dragOffset by remember { mutableFloatStateOf(0f) }
    val threshold = 50f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffset < -threshold) {
                            navController.navigate(Screen.Player.route)
                        }
                        dragOffset = 0f
                    }
                ) { _, dragAmount ->
                    dragOffset = (dragOffset + dragAmount).coerceAtMost(0f)
                }
            },
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color(0xFF1DB954))
            )

            LinearProgressIndicator(
                progress = { if (uiState.totalDuration > 0) uiState.currentPosition.toFloat() / uiState.totalDuration else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = Color(0xFF1DB954),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate(Screen.Player.route) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = currentSong.coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                    error = null,
                    placeholder = null
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentSong.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentSong.artists.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color(0xFF1DB954),
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.next() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color(0xFF1DB954),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
