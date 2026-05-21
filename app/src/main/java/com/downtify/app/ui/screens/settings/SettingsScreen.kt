package com.downtify.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.downtify.app.domain.model.*
import com.downtify.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showFormatDialog by remember { mutableStateOf(false) }
    var showBitrateDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showParallelDialog by remember { mutableStateOf(false) }
    var showVideoFormatDialog by remember { mutableStateOf(false) }
    var showVideoQualityDialog by remember { mutableStateOf(false) }
    var showAboutSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Format
            SettingsCard(
                title = "Output Format",
                value = uiState.settings.audioFormat.name,
                icon = Icons.Default.AudioFile,
                onClick = { showFormatDialog = true }
            )
            
            // Bitrate
            if (uiState.settings.audioFormat != AudioFormat.FLAC) {
                SettingsCard(
                    title = "Bitrate",
                    value = "${uiState.settings.bitrate.value} kbps",
                    icon = Icons.Default.Speed,
                    onClick = { showBitrateDialog = true }
                )
            }
            
            // Organize by Artist
            SettingsSwitchCard(
                title = "Organize by Artist",
                checked = uiState.settings.organizeByArtist,
                icon = Icons.Default.Folder,
                onCheckedChange = { viewModel.toggleOrganizeByArtist() }
            )

            // Organize by Album
            SettingsSwitchCard(
                title = "Organize by Album",
                checked = uiState.settings.organizeByAlbum,
                icon = Icons.Default.Album,
                onCheckedChange = { viewModel.toggleOrganizeByAlbum() }
            )
            
            // Generate M3U
            SettingsSwitchCard(
                title = "Generate M3U Files",
                checked = uiState.settings.generateM3u,
                icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                onCheckedChange = { viewModel.toggleGenerateM3U() }
            )
            
            // Download Lyrics
            SettingsSwitchCard(
                title = "Download Lyrics",
                checked = uiState.settings.downloadLyrics,
                icon = Icons.Default.LibraryMusic,
                onCheckedChange = { viewModel.toggleDownloadLyrics() }
            )
            
            // Max Parallel Downloads
            SettingsCard(
                title = "Max Parallel Downloads",
                value = uiState.settings.maxParallelDownloads.toString(),
                icon = Icons.Default.Sync,
                onClick = { showParallelDialog = true }
            )
            
            // Language
            SettingsCard(
                title = "Language",
                value = uiState.settings.language.displayName,
                icon = Icons.Default.Language,
                onClick = { showLanguageDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Video Downloads",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Video Format
            SettingsCard(
                title = "Video Format",
                value = uiState.settings.videoFormat.name,
                icon = Icons.Default.Videocam,
                onClick = { showVideoFormatDialog = true }
            )

            // Video Quality
            SettingsCard(
                title = "Video Quality",
                value = uiState.settings.videoQuality.label,
                icon = Icons.Default.Speed,
                onClick = { showVideoQualityDialog = true }
            )

            // Organize Videos by Artist
            SettingsSwitchCard(
                title = "Organize by Artist",
                checked = uiState.settings.organizeVideosByArtist,
                icon = Icons.Default.Folder,
                onCheckedChange = { viewModel.toggleOrganizeVideosByArtist() }
            )

            // Organize Videos by Album
            SettingsSwitchCard(
                title = "Organize by Album",
                checked = uiState.settings.organizeVideosByAlbum,
                icon = Icons.Default.Album,
                onCheckedChange = { viewModel.toggleOrganizeVideosByAlbum() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "SoundCloud",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            var showToken by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Key, contentDescription = null)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("OAuth Token", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = uiState.settings.soundCloudOAuthToken,
                            onValueChange = viewModel::updateSoundCloudOAuthToken,
                            placeholder = { Text("Paste your SoundCloud OAuth token") },
                            visualTransformation = if (showToken) {
                                androidx.compose.ui.text.input.VisualTransformation.None
                            } else {
                                androidx.compose.ui.text.input.PasswordVisualTransformation()
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { showToken = !showToken }) {
                                    Icon(
                                        if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showToken) "Hide token" else "Show token"
                                    )
                                }
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // About
            Card(
                onClick = { showAboutSheet = true },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Downtify",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Version 2.7.0",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Self-hosted music downloader",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    
    // Format Dialog
    if (showFormatDialog) {
        AlertDialog(
            onDismissRequest = { showFormatDialog = false },
            title = { Text("Output Format") },
            text = {
                Column {
                    AudioFormat.entries.forEach { format ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.settings.audioFormat == format,
                                onClick = {
                                    viewModel.updateFormat(format)
                                    showFormatDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(format.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFormatDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Bitrate Dialog
    if (showBitrateDialog) {
        AlertDialog(
            onDismissRequest = { showBitrateDialog = false },
            title = { Text("Bitrate") },
            text = {
                Column {
                    Bitrate.entries.forEach { bitrate ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.settings.bitrate == bitrate,
                                onClick = {
                                    viewModel.updateBitrate(bitrate)
                                    showBitrateDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${bitrate.value} kbps")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBitrateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Language Dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Language") },
            text = {
                Column {
                    AppLanguage.entries.forEach { language ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.settings.language == language,
                                onClick = {
                                    viewModel.updateLanguage(language)
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(language.displayName)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Video Format Dialog
    if (showVideoFormatDialog) {
        AlertDialog(
            onDismissRequest = { showVideoFormatDialog = false },
            title = { Text("Video Format") },
            text = {
                Column {
                    VideoFormat.entries.forEach { format ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.settings.videoFormat == format,
                                onClick = {
                                    viewModel.updateVideoFormat(format)
                                    showVideoFormatDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(format.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVideoFormatDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Video Quality Dialog
    if (showVideoQualityDialog) {
        AlertDialog(
            onDismissRequest = { showVideoQualityDialog = false },
            title = { Text("Video Quality") },
            text = {
                Column {
                    VideoQuality.entries.forEach { quality ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.settings.videoQuality == quality,
                                onClick = {
                                    viewModel.updateVideoQuality(quality)
                                    showVideoQualityDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(quality.label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVideoQualityDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Parallel Downloads Dialog
    if (showParallelDialog) {
        val options = listOf(1, 2, 3, 4, 8, 20)
        var customValue by remember { mutableStateOf(uiState.settings.maxParallelDownloads.toString()) }
        var isCustomSelected by remember { mutableStateOf(!options.contains(uiState.settings.maxParallelDownloads)) }

        AlertDialog(
            onDismissRequest = { showParallelDialog = false },
            title = { Text("Max Parallel Downloads") },
            text = {
                Column {
                    options.forEach { count ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !isCustomSelected && uiState.settings.maxParallelDownloads == count,
                                onClick = {
                                    viewModel.updateMaxParallelDownloads(count)
                                    showParallelDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(count.toString())
                        }
                    }
                    
                    // Custom Option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isCustomSelected,
                            onClick = { isCustomSelected = true }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = customValue,
                                onValueChange = { 
                                    customValue = it.filter { char -> char.isDigit() }
                                    isCustomSelected = true
                                },
                                label = { Text("Custom") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (isCustomSelected) {
                        val value = customValue.toIntOrNull() ?: 3
                        viewModel.updateMaxParallelDownloads(value.coerceIn(1, 100))
                    }
                    showParallelDialog = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showParallelDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // About Bottom Sheet
    if (showAboutSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAboutSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            AboutSheet()
        }
    }
}

@Composable
fun SettingsCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null)
                Spacer(modifier = Modifier.width(16.dp))
                Text(title)
            }
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsSwitchCard(
    title: String,
    checked: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null)
                Spacer(modifier = Modifier.width(16.dp))
                Text(title)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun AboutSheet() {
    val githubUrl = "https://github.com"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "Downtify",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        val annotatedString = buildAnnotatedString {
            append("Visit ")
            pushLink(LinkAnnotation.Url(url = githubUrl))
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)) {
                append("GitHub")
            }
            pop()
            append(" for more")
        }
        Text(
            text = annotatedString,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Downtify is a self-hosted music downloader that lets you download music from Spotify, SoundCloud, and YouTube. Search for tracks, download them in your preferred format, and organize your music library — all from one app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
