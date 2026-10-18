package com.linktoaudio.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.linktoaudio.app.data.AudioFormat
import com.linktoaudio.app.data.AudioQuality
import com.linktoaudio.app.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val autoConvertOnShare by viewModel.autoConvertOnShare.collectAsState()
    val skipAlreadyDownloaded by viewModel.skipAlreadyDownloaded.collectAsState()
    val selectedFormat by viewModel.selectedFormat.collectAsState()
    val selectedAudioQuality by viewModel.selectedAudioQuality.collectAsState()
    val isUpdatingEngine by viewModel.isUpdatingEngine.collectAsState()
    val engineUpdateMessage by viewModel.engineUpdateMessage.collectAsState()
    val engineVersion by viewModel.engineVersion.collectAsState()

    val spotifyClientId by viewModel.spotifyClientId.collectAsState()
    val spotifyClientSecret by viewModel.spotifyClientSecret.collectAsState()

    var clientIdInput by remember(spotifyClientId) { mutableStateOf(spotifyClientId) }
    var clientSecretInput by remember(spotifyClientSecret) { mutableStateOf(spotifyClientSecret) }
    var showSecret by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Conversion Preferences Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Conversion Preferences",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Auto-convert on share
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
                    ) {
                        Text(
                            text = "Auto-convert on share",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "When sharing any link to the app, start converting automatically without extra taps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = autoConvertOnShare,
                        onCheckedChange = { viewModel.setAutoConvertOnShare(it) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Skip already downloaded tracks toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
                    ) {
                        Text(
                            text = "Skip already downloaded",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Skip tracks that already exist in your conversion history when downloading playlists.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = skipAlreadyDownloaded,
                        onCheckedChange = { viewModel.setSkipAlreadyDownloaded(it) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Audio Quality Choice
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Audio Quality",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AudioQuality.values().forEach { quality ->
                            FilterChip(
                                selected = selectedAudioQuality == quality,
                                onClick = { viewModel.onAudioQualitySelected(quality) },
                                label = { Text(quality.displayName, style = MaterialTheme.typography.labelMedium) },
                                leadingIcon = if (selectedAudioQuality == quality) {
                                    {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Default audio format
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Default Audio Format",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AudioFormat.values().forEach { format ->
                            FilterChip(
                                selected = selectedFormat == format,
                                onClick = { viewModel.onFormatSelected(format) },
                                label = { Text(format.displayName, style = MaterialTheme.typography.labelMedium) },
                                leadingIcon = if (selectedFormat == format) {
                                    {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }
            }
        }

        // Spotify API Credentials (Optional Fallback)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Spotify API (Optional Fallback)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Public Spotify embed pages are parsed automatically without any login. If public parsing fails, you can optionally provide free credentials from developer.spotify.com as a fallback.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = clientIdInput,
                    onValueChange = { clientIdInput = it },
                    label = { Text("Spotify Client ID", style = MaterialTheme.typography.bodyMedium) },
                    placeholder = { Text("e.g. 32-character hex ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = clientSecretInput,
                    onValueChange = { clientSecretInput = it },
                    label = { Text("Spotify Client Secret", style = MaterialTheme.typography.bodyMedium) },
                    placeholder = { Text("e.g. 32-character hex secret") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showSecret = !showSecret }) {
                            Icon(
                                if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showSecret) "Hide secret" else "Show secret"
                            )
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                )

                Button(
                    onClick = {
                        viewModel.setSpotifyCredentials(clientIdInput.trim(), clientSecretInput.trim())
                    },
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save Spotify Settings", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Engine Maintenance Section
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "yt-dlp Engine",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Streaming platforms frequently update their internal video delivery protocols. If conversions stall or fail, update the on-device yt-dlp binary here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Engine version:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = engineVersion ?: "Ready",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (!engineUpdateMessage.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = engineUpdateMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Button(
                    onClick = { viewModel.updateEngine() },
                    enabled = !isUpdatingEngine,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isUpdatingEngine) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Updating Engine...", style = MaterialTheme.typography.labelLarge)
                    } else {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Update Engine", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // About & Save Location Info
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "About LinkToAudio",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "• Version: 1.1.0\n" +
                            "• Output folder: Music/LinkToAudio (and Music/LinkToAudio/<Playlist>/)\n" +
                            "• Universal support: YouTube, Spotify, SoundCloud, Instagram, TikTok, Facebook, Twitter/X, Bandcamp, Vimeo, and more.\n" +
                            "• Architecture: 100% on-device processing via yt-dlp & FFmpeg. No backend server required.\n" +
                            "• Disclaimer: Only download media you own or have explicit permission to use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
