package com.downtify.app.ui.screens.monitor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.downtify.app.data.database.MonitoredPlaylistEntity
import com.downtify.app.domain.model.CheckInterval
import com.downtify.app.viewmodel.MonitorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    viewModel: MonitorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Color(0xFF1DB954)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Playlist")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.playlists.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.VisibilityOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No monitored playlists",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { showAddDialog = true }) {
                            Text("Add Playlist")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(uiState.playlists) { playlist ->
                        PlaylistItem(
                            playlist = playlist,
                            onToggle = { viewModel.togglePlaylist(playlist.id, !playlist.enabled) },
                            onDelete = { viewModel.deletePlaylist(playlist.id) },
                            onCheckNow = { viewModel.checkNow(playlist.id) }
                        )
                    }
                }
            }
        }
    }
    
    if (showAddDialog) {
        AddPlaylistDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { url, interval ->
                viewModel.onUrlChanged(url)
                viewModel.onIntervalChanged(interval)
                viewModel.addPlaylist()
                showAddDialog = false
            }
        )
    }
}

@Composable
fun PlaylistItem(
    playlist: MonitoredPlaylistEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onCheckNow: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Every ${playlist.intervalMinutes} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Switch(
                    checked = playlist.enabled,
                    onCheckedChange = { onToggle() }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCheckNow) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Check Now")
                }
                
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun AddPlaylistDialog(
    onDismiss: () -> Unit,
    onAdd: (String, CheckInterval) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var selectedInterval by remember { mutableStateOf(CheckInterval.HOUR_1) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Playlist") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Spotify Playlist URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Check Interval")
                Spacer(modifier = Modifier.height(8.dp))
                
                CheckInterval.entries.forEach { interval ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedInterval == interval,
                            onClick = { selectedInterval = interval }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(interval.name.replace("_", " "))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(url, selectedInterval) },
                enabled = url.isNotBlank()
            ) {
                Text("Watch")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
