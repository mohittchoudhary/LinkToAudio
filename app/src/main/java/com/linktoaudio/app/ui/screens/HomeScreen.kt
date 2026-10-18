package com.linktoaudio.app.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.linktoaudio.app.data.AudioFormat
import com.linktoaudio.app.data.ConversionItem
import com.linktoaudio.app.data.ConversionState
import com.linktoaudio.app.data.PlaylistInfo
import com.linktoaudio.app.data.VideoMetadata
import com.linktoaudio.app.utils.MediaSaver
import com.linktoaudio.app.viewmodel.MainViewModel

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val urlInput by viewModel.urlInput.collectAsState()
    val selectedFormat by viewModel.selectedFormat.collectAsState()
    val conversionState by viewModel.conversionState.collectAsState()
    val playlistPreview by viewModel.playlistPreview.collectAsState()
    val pendingChooserUrl by viewModel.pendingChooserUrl.collectAsState()
    val isLoadingInfo by viewModel.isLoadingInfo.collectAsState()
    val loadingMessage by viewModel.loadingMessage.collectAsState()

    val isBusy = isLoadingInfo ||
            conversionState is ConversionState.FetchingInfo ||
            conversionState is ConversionState.Downloading ||
            conversionState is ConversionState.Converting ||
            conversionState is ConversionState.Saving ||
            conversionState is ConversionState.QueueRunning

    // Chooser dialog when a video inside a playlist is detected
    if (pendingChooserUrl != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissChooser() },
            icon = { Icon(Icons.Default.PlaylistPlay, contentDescription = null) },
            title = {
                Text(
                    text = "Playlist Detected",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "This link is a video inside a playlist: convert just this video or the whole playlist?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onChooserChoice(justThisVideo = false) }
                ) {
                    Text("Whole playlist")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { viewModel.onChooserChoice(justThisVideo = true) }
                ) {
                    Text("Single video only")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Legal & Copyright Disclaimer Banner
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Disclaimer",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Only download content you own or have permission to use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Link Input Field Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Paste any link",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                // Supported Sources Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sources = listOf("YouTube", "Spotify", "SoundCloud", "Instagram", "TikTok", "+ More")
                    sources.forEach { source ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = source,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { viewModel.onUrlChanged(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "Paste any link (YouTube, Spotify, SoundCloud...)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Link, contentDescription = "Link")
                    },
                    trailingIcon = {
                        if (urlInput.isNotEmpty() && !isBusy) {
                            IconButton(onClick = { viewModel.onUrlChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val clip = clipboardManager.getText()?.text
                            viewModel.onPaste(clip)
                        },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentPaste,
                            contentDescription = "Paste",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste", style = MaterialTheme.typography.labelLarge)
                    }

                    // Format Selection (MP3 / M4A)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AudioFormat.values().forEach { format ->
                            FilterChip(
                                selected = selectedFormat == format,
                                onClick = { if (!isBusy) viewModel.onFormatSelected(format) },
                                label = { Text(format.displayName, style = MaterialTheme.typography.labelMedium) },
                                enabled = !isBusy,
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

                Button(
                    onClick = { viewModel.startConversion() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = urlInput.isNotBlank() && !isBusy,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Audiotrack, contentDescription = "Convert")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Convert to Audio", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        // Loading Info Card (resolving redirects / inspecting playlist)
        if (isLoadingInfo) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                    Text(
                        text = loadingMessage ?: "Inspecting link...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Playlist Preview Screen / Card (when detected and not auto-converted)
        val preview = playlistPreview
        if (preview != null) {
            PlaylistPreviewCard(
                playlist = preview,
                onToggleTrack = { trackId -> viewModel.onTrackSelectionToggled(trackId) },
                onSelectAll = { selectAll -> viewModel.selectAllTracks(selectAll) },
                onConvert = { viewModel.convertSelectedPlaylist() },
                onCancel = { viewModel.clearPlaylistPreview() }
            )
        }

        // Active State Cards (Fetching, Downloading, Converting, Saving, QueueRunning, QueueCompleted, Done, Error)
        AnimatedVisibility(visible = conversionState !is ConversionState.Idle) {
            when (val state = conversionState) {
                is ConversionState.FetchingInfo -> {
                    ProgressCard(
                        title = "Fetching Media Details...",
                        subtitle = "Inspecting audio streams from source",
                        progress = null,
                        onCancel = { viewModel.cancelConversion() }
                    )
                }

                is ConversionState.Downloading -> {
                    ActiveConversionCard(
                        metadata = state.metadata,
                        stageTitle = "Downloading Audio (${state.progress.toInt()}%)",
                        etaSeconds = state.etaSeconds,
                        progress = state.progress / 100f,
                        onCancel = { viewModel.cancelConversion() }
                    )
                }

                is ConversionState.Converting -> {
                    ActiveConversionCard(
                        metadata = state.metadata,
                        stageTitle = "Converting with FFmpeg...",
                        etaSeconds = null,
                        progress = null,
                        onCancel = { viewModel.cancelConversion() }
                    )
                }

                is ConversionState.Saving -> {
                    ActiveConversionCard(
                        metadata = state.metadata,
                        stageTitle = "Saving to Music/LinkToAudio...",
                        etaSeconds = null,
                        progress = null,
                        onCancel = { viewModel.cancelConversion() }
                    )
                }

                is ConversionState.QueueRunning -> {
                    QueueRunningCard(
                        state = state,
                        onCancel = { viewModel.cancelConversion() }
                    )
                }

                is ConversionState.QueueCompleted -> {
                    QueueCompletedCard(
                        state = state,
                        onDismiss = { viewModel.resetConversion() }
                    )
                }

                is ConversionState.Success -> {
                    SuccessCard(
                        item = state.item,
                        format = selectedFormat,
                        onPlay = {
                            try {
                                val playIntent = MediaSaver.createPlayIntent(
                                    context,
                                    state.item.filePath,
                                    state.item.contentUri,
                                    selectedFormat
                                )
                                context.startActivity(playIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "No audio player app found to play file", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onShare = {
                            try {
                                val shareIntent = MediaSaver.createShareIntent(
                                    context,
                                    state.item.filePath,
                                    state.item.contentUri,
                                    selectedFormat,
                                    state.item.title
                                )
                                context.startActivity(Intent.createChooser(shareIntent, "Share Audio"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to share audio file", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDismiss = { viewModel.resetConversion() }
                    )
                }

                is ConversionState.Error -> {
                    ErrorCard(
                        message = state.message,
                        onDismiss = { viewModel.resetConversion() },
                        onRetry = { viewModel.startConversion() }
                    )
                }

                ConversionState.Idle -> {}
            }
        }
    }
}

@Composable
private fun PlaylistPreviewCard(
    playlist: PlaylistInfo,
    onToggleTrack: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onConvert: () -> Unit,
    onCancel: () -> Unit
) {
    val totalTracks = playlist.tracks.size
    val selectedCount = playlist.tracks.count { it.isSelected }
    val allSelected = totalTracks > 0 && selectedCount == totalTracks

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$totalTracks tracks ($selectedCount selected)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(onClick = { onSelectAll(!allSelected) }) {
                    Text(if (allSelected) "Select none" else "Select all", style = MaterialTheme.typography.labelLarge)
                }
            }

            // Spotify Note if applicable
            if (playlist.isSpotify) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Audio is matched from YouTube, so it may differ slightly from the Spotify version. Private playlists are not supported.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Scrollable tracks list
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                playlist.tracks.forEach { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggleTrack(track.id) }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = track.isSelected,
                            onCheckedChange = { onToggleTrack(track.id) }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subtitle = if (!track.artist.isNullOrBlank()) {
                                "${track.artist} • ${track.formattedDuration()}"
                            } else {
                                track.formattedDuration()
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                }

                Button(
                    onClick = onConvert,
                    modifier = Modifier.weight(1f),
                    enabled = selectedCount > 0,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Convert ($selectedCount)", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun QueueRunningCard(
    state: ConversionState.QueueRunning,
    onCancel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Track ${state.currentIndex} of ${state.totalCount}: ${state.currentTitle}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!state.playlistTitle.isNullOrBlank()) {
                Text(
                    text = "Playlist: ${state.playlistTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }

            // Track progress
            LinearProgressIndicator(
                progress = (state.progress / 100f).coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Overall: ${state.completedCount}/${state.totalCount} completed" +
                            if (state.failedCount > 0) " (${state.failedCount} failed)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                if (state.etaSeconds > 0) {
                    Text(
                        text = "ETA: ${state.etaSeconds}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.End),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Cancel Queue", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun QueueCompletedCard(
    state: ConversionState.QueueCompleted,
    onDismiss: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Playlist Download Finished",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Text(
                text = "${state.completedCount} done, ${state.failedCount} failed",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Text(
                text = "Saved to: ${state.destinationFolder}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )

            if (state.failedTitles.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Skipped / Failed tracks:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        state.failedTitles.forEach { failedTitle ->
                            Text(
                                text = "• $failedTitle",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Dismiss", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ActiveConversionCard(
    metadata: VideoMetadata?,
    stageTitle: String,
    etaSeconds: Long?,
    progress: Float?,
    onCancel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (metadata != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!metadata.thumbnail.isNullOrBlank()) {
                        AsyncImage(
                            model = metadata.thumbnail,
                            contentDescription = "Thumbnail",
                            modifier = Modifier
                                .size(70.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = metadata.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!metadata.uploader.isNullOrBlank()) {
                            Text(
                                text = metadata.uploader,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        if (metadata.durationSeconds > 0) {
                            Text(
                                text = "Duration: ${metadata.formattedDuration()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Text(
                text = stageTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            if (progress != null) {
                LinearProgressIndicator(
                    progress = progress.coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                if (etaSeconds != null && etaSeconds > 0) {
                    Text(
                        text = "ETA: ${etaSeconds}s",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.End),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ProgressCard(
    title: String,
    subtitle: String,
    progress: Float?,
    onCancel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)

            if (progress != null) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.End),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun SuccessCard(
    item: ConversionItem,
    format: AudioFormat,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(item.id) {
        Toast.makeText(context, "Saved to Music/LinkToAudio", Toast.LENGTH_LONG).show()
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Audio Saved!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!item.thumbnail.isNullOrBlank()) {
                    AsyncImage(
                        model = item.thumbnail,
                        contentDescription = "Thumbnail",
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        modifier = Modifier.size(60.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Audiotrack, contentDescription = null)
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.format} • ${item.formattedDuration()} • ${item.formattedSize()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "Saved in Music/LinkToAudio",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play", style = MaterialTheme.typography.labelLarge)
                }

                FilledTonalButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Conversion Failed",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Retry", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
