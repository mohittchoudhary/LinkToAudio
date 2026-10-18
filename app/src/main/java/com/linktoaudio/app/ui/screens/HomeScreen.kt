package com.linktoaudio.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.linktoaudio.app.R
import com.linktoaudio.app.auth.data.UserProfile
import com.linktoaudio.app.player.model.Playlist
import com.linktoaudio.app.player.model.Song
import com.linktoaudio.app.ui.theme.SpotifyGreen
import com.linktoaudio.app.viewmodel.MusicViewModel
import java.util.Calendar

@Composable
fun HomeScreen(
    musicViewModel: MusicViewModel,
    userProfile: UserProfile? = null,
    onNavigateToConverter: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allSongs by musicViewModel.allSongs.collectAsState()
    val recentlyPlayed by musicViewModel.recentlyPlayedSongs.collectAsState()
    val recentlyDownloaded by musicViewModel.recentlyDownloadedSongs.collectAsState()
    val mostPlayed by musicViewModel.mostPlayedSongs.collectAsState()
    val favorites by musicViewModel.favoriteSongs.collectAsState()
    val playlists by musicViewModel.playlists.collectAsState()
    val isRefreshing by musicViewModel.isRefreshing.collectAsState()
    val hasPermission by musicViewModel.hasStoragePermission.collectAsState()

    // Permission check & launcher
    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        musicViewModel.onPermissionResult(isGranted)
    }

    LaunchedEffect(Unit) {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        musicViewModel.onPermissionResult(granted)
        if (!granted) {
            permissionLauncher.launch(permissionToRequest)
        }
    }

    // Greeting calculation
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 4..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    val fallbackDeviceName = remember {
        try {
            val name = android.provider.Settings.Global.getString(context.contentResolver, "device_name")
            if (!name.isNullOrBlank() && name != "Android" && name != "unknown") name else null
        } catch (e: Exception) {
            null
        }
    }

    val displayName = userProfile?.name?.takeIf { it.isNotBlank() } ?: fallbackDeviceName
    val greetingText = if (!displayName.isNullOrBlank()) "$greeting, $displayName" else greeting

    // Quick picks candidates: recently played, or fallback to all songs (recently added)
    val quickPickCandidates = remember(recentlyPlayed, allSongs) {
        if (recentlyPlayed.isNotEmpty()) recentlyPlayed else allSongs
    }
    val quickPicks = remember(quickPickCandidates) {
        quickPickCandidates.take(6)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 90.dp)
    ) {
        // Top Greeting Header with Profile Icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = greetingText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onNavigateToConverter,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (userProfile?.isAdmin == true) SpotifyGreen else MaterialTheme.colorScheme.surfaceVariant
                    )
            ) {
                if (userProfile != null && userProfile.name.isNotBlank()) {
                    Text(
                        text = userProfile.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (userProfile.isAdmin) Color.Black else MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile & Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Empty State or Content
        if (!hasPermission) {
            PermissionRequiredCard(
                onGrantClick = { permissionLauncher.launch(permissionToRequest) }
            )
        } else if (allSongs.isEmpty() && !isRefreshing) {
            EmptyLibraryHomeState(onNavigateToConverter = onNavigateToConverter)
        } else {
            // Quick Picks: 2-column grid of 6 rounded cards
            if (quickPicks.isNotEmpty()) {
                Text(
                    text = "Quick picks",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val rows = quickPicks.chunked(2)
                    rows.forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.forEach { song ->
                                Box(modifier = Modifier.weight(1f)) {
                                    QuickPickCard(
                                        song = song,
                                        onClick = {
                                            musicViewModel.playerController.playSong(song, quickPickCandidates)
                                        }
                                    )
                                }
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Shuffle all Button Card
            if (allSongs.isNotEmpty()) {
                ShuffleAllCard(
                    totalSongs = allSongs.size,
                    onClick = {
                        val shuffled = allSongs.shuffled()
                        if (shuffled.isNotEmpty()) {
                            musicViewModel.playerController.playSong(shuffled.first(), shuffled)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Horizontal Row: Recently played
            if (recentlyPlayed.isNotEmpty()) {
                HomeSongRow(
                    title = "Recently played",
                    songs = recentlyPlayed,
                    onSongClick = { song ->
                        musicViewModel.playerController.playSong(song, recentlyPlayed)
                    }
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Horizontal Row: Recently downloaded
            if (recentlyDownloaded.isNotEmpty()) {
                HomeSongRow(
                    title = "Recently downloaded",
                    songs = recentlyDownloaded,
                    onSongClick = { song ->
                        musicViewModel.playerController.playSong(song, recentlyDownloaded)
                    }
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Horizontal Row: Most played
            if (mostPlayed.isNotEmpty()) {
                HomeSongRow(
                    title = "Most played",
                    songs = mostPlayed,
                    onSongClick = { song ->
                        musicViewModel.playerController.playSong(song, mostPlayed)
                    }
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Horizontal Row: Favorites
            if (favorites.isNotEmpty()) {
                HomeSongRow(
                    title = "Favorites",
                    songs = favorites,
                    onSongClick = { song ->
                        musicViewModel.playerController.playSong(song, favorites)
                    }
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Horizontal Row: Your playlists
            if (playlists.isNotEmpty()) {
                HomePlaylistRow(
                    title = "Your playlists",
                    playlists = playlists,
                    allSongs = allSongs,
                    onPlaylistClick = { playlist, playlistSongs ->
                        if (playlistSongs.isNotEmpty()) {
                            musicViewModel.playerController.playSong(playlistSongs.first(), playlistSongs)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun QuickPickCard(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = song.title,
                fallback = painterResource(R.drawable.ic_default_album_art),
                error = painterResource(R.drawable.ic_default_album_art),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            )
        }
    }
}

@Composable
private fun ShuffleAllCard(
    totalSongs: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(SpotifyGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = "Shuffle all",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$totalSongs tracks available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeSongRow(
    title: String,
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(songs, key = { it.id }) { song ->
                SongSquareCard(
                    song = song,
                    onClick = { onSongClick(song) }
                )
            }
        }
    }
}

@Composable
private fun SongSquareCard(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = song.albumArtUri,
            contentDescription = song.title,
            fallback = painterResource(R.drawable.ic_default_album_art),
            error = painterResource(R.drawable.ic_default_album_art),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )

        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HomePlaylistRow(
    title: String,
    playlists: List<Playlist>,
    allSongs: List<Song>,
    onPlaylistClick: (Playlist, List<Song>) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(playlists, key = { it.id }) { playlist ->
                val playlistSongs = remember(playlist, allSongs) {
                    val idSet = playlist.songIds.toSet()
                    allSongs.filter { idSet.contains(it.id) }
                }
                val coverArt = playlistSongs.firstOrNull { !it.albumArtUri.isNullOrBlank() }?.albumArtUri

                Column(
                    modifier = Modifier
                        .width(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPlaylistClick(playlist, playlistSongs) }
                ) {
                    if (!coverArt.isNullOrBlank()) {
                        AsyncImage(
                            model = coverArt,
                            contentDescription = playlist.name,
                            fallback = painterResource(R.drawable.ic_default_album_art),
                            error = painterResource(R.drawable.ic_default_album_art),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(140.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Text(
                        text = "${playlist.songIds.size} songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryHomeState(
    onNavigateToConverter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(100.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(52.dp)
                )
            }
        }

        Text(
            text = "Your library is empty",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Convert any YouTube, Spotify, SoundCloud, or web audio link to start building your offline library.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onNavigateToConverter,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Link,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Open Link Converter",
                color = Color.Black,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PermissionRequiredCard(
    onGrantClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )

            Text(
                text = "Storage access needed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "LinkToAudio needs permission to scan and play audio files on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onGrantClick,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
            ) {
                Text(
                    text = "Grant Permission",
                    color = Color.Black,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
