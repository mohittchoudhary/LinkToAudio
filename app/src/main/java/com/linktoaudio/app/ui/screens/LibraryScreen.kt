package com.linktoaudio.app.ui.screens

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.linktoaudio.app.R
import com.linktoaudio.app.player.data.SongArtwork
import com.linktoaudio.app.player.model.*
import com.linktoaudio.app.ui.components.CreatePlaylistDialog
import com.linktoaudio.app.ui.components.ScreenStatePanel
import com.linktoaudio.app.ui.components.SongMenuBottomSheet
import com.linktoaudio.app.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    musicViewModel: MusicViewModel,
    onNavigateToHome: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTab by musicViewModel.currentTab.collectAsState()
    val isRefreshing by musicViewModel.isRefreshing.collectAsState()
    val libraryError by musicViewModel.libraryError.collectAsState()
    val hasPermission by musicViewModel.hasStoragePermission.collectAsState()
    val allSongs by musicViewModel.allSongs.collectAsState()
    val sortOption by musicViewModel.sortOption.collectAsState()
    val sortDirection by musicViewModel.sortDirection.collectAsState()
    val playlists by musicViewModel.playlists.collectAsState()
    val favorites by musicViewModel.favorites.collectAsState()
    val recentlyDownloaded by musicViewModel.recentlyDownloadedSongs.collectAsState()

    var activeMenuSong by remember { mutableStateOf<Song?>(null) }
    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var selectedArtist by remember { mutableStateOf<Artist?>(null) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var selectedFolder by remember { mutableStateOf<Pair<String, List<Song>>?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Permission launcher
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Library Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Your Library",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Sort Menu Button
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort Options"
                        )
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SongSortOption.values().forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    val isSelected = sortOption == option
                                    val dirIndicator = if (isSelected) {
                                        if (sortDirection == SortDirection.ASCENDING) " ↑" else " ↓"
                                    } else ""
                                    Text(
                                        text = "${option.displayName}$dirIndicator",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    musicViewModel.setSortOption(option)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }

                // Rescan Library Button
                IconButton(onClick = { musicViewModel.refreshLibrary() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Rescan Library"
                    )
                }
            }
        }

        // Permission Missing Warning
        if (!hasPermission) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Storage Permission Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Grant audio access to scan and play your music files from storage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { permissionLauncher.launch(permissionToRequest) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Grant Permission")
                        }
                        OutlinedButton(
                            onClick = {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }
                        ) {
                            Text("App Settings")
                        }
                    }
                }
            }
        }

        if (isRefreshing && allSongs.isNotEmpty()) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (hasPermission && isRefreshing && allSongs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                ScreenStatePanel(
                    title = "Loading your music",
                    message = "Scanning your library…",
                    isLoading = true
                )
            }
        } else if (hasPermission && !libraryError.isNullOrBlank() && allSongs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                ScreenStatePanel(
                    title = "Couldn't load your music",
                    message = libraryError ?: "Check audio access and try again.",
                    icon = Icons.Default.Warning,
                    actionLabel = "Retry",
                    onAction = { musicViewModel.refreshLibrary() }
                )
            }
        } else if (hasPermission && !isRefreshing && allSongs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_default_album_art),
                        contentDescription = null,
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Text(
                        text = "Your Library is empty",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Convert any link to audio on the Home tab to build your offline music collection.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onNavigateToHome,
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download Music")
                    }
                }
            }
        } else if (hasPermission) {

        if (!libraryError.isNullOrBlank()) {
            ScreenStatePanel(
                title = "Library refresh failed",
                message = libraryError ?: "Some music may be missing.",
                icon = Icons.Default.Warning,
                actionLabel = "Retry",
                onAction = { musicViewModel.refreshLibrary() },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Recently Downloaded Horizontal Row (Feature 12)
        if (recentlyDownloaded.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                Text(
                    text = "Recently Downloaded",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentlyDownloaded) { song ->
                        RecentlyDownloadedItem(
                            song = song,
                            onClick = {
                                musicViewModel.playerController.playSong(song, recentlyDownloaded)
                            }
                        )
                    }
                }
            }
        }

        // Filter chips keep the library categories compact and easy to scan.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(LibraryTab.values().toList(), key = { it.name }) { tab ->
                FilterChip(
                    selected = currentTab == tab,
                    onClick = { musicViewModel.setTab(tab) },
                    label = {
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // Tab Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (currentTab) {
                LibraryTab.SONGS -> {
                    val songs by musicViewModel.displayedSongs.collectAsState()
                    SongsListTab(
                        songs = songs,
                        onSongClick = { song ->
                            musicViewModel.playerController.playSong(song, songs)
                        },
                        onSongMenuClick = { activeMenuSong = it }
                    )
                }

                LibraryTab.ALBUMS -> {
                    val albums by musicViewModel.albums.collectAsState()
                    AlbumsListTab(
                        albums = albums,
                        onAlbumClick = { selectedAlbum = it }
                    )
                }

                LibraryTab.ARTISTS -> {
                    val artists by musicViewModel.artists.collectAsState()
                    ArtistsListTab(
                        artists = artists,
                        onArtistClick = { selectedArtist = it }
                    )
                }

                LibraryTab.PLAYLISTS -> {
                    PlaylistsTab(
                        playlists = playlists,
                        onCreatePlaylist = { showCreatePlaylistDialog = true },
                        onPlaylistClick = { selectedPlaylist = it },
                        onDeletePlaylist = { musicViewModel.deletePlaylist(it) }
                    )
                }

                LibraryTab.FOLDERS -> {
                    val folders by musicViewModel.folders.collectAsState()
                    FoldersListTab(
                        folders = folders,
                        onFolderClick = { folderName, songs ->
                            selectedFolder = Pair(folderName, songs)
                        }
                    )
                }

                LibraryTab.FAVORITES -> {
                    val favSongs by musicViewModel.favoriteSongs.collectAsState()
                    SongsListTab(
                        songs = favSongs,
                        emptyMessage = "No favorite songs yet. Tap the heart icon on any song to add it here.",
                        onSongClick = { song ->
                            musicViewModel.playerController.playSong(song, favSongs)
                        },
                        onSongMenuClick = { activeMenuSong = it }
                    )
                }

                LibraryTab.RECENTLY_PLAYED -> {
                    val recSongs by musicViewModel.recentlyPlayedSongs.collectAsState()
                    SongsListTab(
                        songs = recSongs,
                        emptyMessage = "No recently played tracks yet.",
                        onSongClick = { song ->
                            musicViewModel.playerController.playSong(song, recSongs)
                        },
                        onSongMenuClick = { activeMenuSong = it }
                    )
                }

                LibraryTab.MOST_PLAYED -> {
                    val mostSongs by musicViewModel.mostPlayedSongs.collectAsState()
                    SongsListTab(
                        songs = mostSongs,
                        emptyMessage = "No play statistics yet. Play your favorite songs to see them ranked here.",
                        onSongClick = { song ->
                            musicViewModel.playerController.playSong(song, mostSongs)
                        },
                        onSongMenuClick = { activeMenuSong = it }
                    )
                }
            }
        }
    }
}

    // Song 3-dot Menu Bottom Sheet
    activeMenuSong?.let { song ->
        val isFav = favorites.contains(song.id)
        SongMenuBottomSheet(
            song = song,
            isFavorite = isFav,
            playlists = playlists,
            onDismiss = { activeMenuSong = null },
            onPlayNext = { musicViewModel.playerController.playNext(song) },
            onAddToQueue = { musicViewModel.playerController.addToQueue(song) },
            onToggleFavorite = { musicViewModel.toggleFavorite(song) },
            onAddToPlaylist = { plId -> musicViewModel.addSongToPlaylist(plId, song.id) },
            onCreatePlaylistAndAdd = { name ->
                musicViewModel.createPlaylist(name) { pl ->
                    musicViewModel.addSongToPlaylist(pl.id, song.id)
                }
            },
            onShare = { musicViewModel.shareSong(song, context) },
            onDelete = { musicViewModel.deleteSong(song) }
        )
    }

    // Create Playlist Dialog
    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onCreate = { name ->
                musicViewModel.createPlaylist(name)
                showCreatePlaylistDialog = false
            }
        )
    }

    // Detail Dialogs for Album, Artist, Playlist, Folder
    selectedAlbum?.let { album ->
        SongsDetailDialog(
            title = album.title,
            subtitle = "${album.artist} • ${album.songCount} songs",
            songs = album.songs,
            onDismiss = { selectedAlbum = null },
            onPlaySong = { song ->
                musicViewModel.playerController.playSong(song, album.songs)
            },
            onSongMenuClick = { activeMenuSong = it }
        )
    }

    selectedArtist?.let { artist ->
        SongsDetailDialog(
            title = artist.name,
            subtitle = "${artist.songCount} songs",
            songs = artist.songs,
            onDismiss = { selectedArtist = null },
            onPlaySong = { song ->
                musicViewModel.playerController.playSong(song, artist.songs)
            },
            onSongMenuClick = { activeMenuSong = it }
        )
    }

    selectedPlaylist?.let { pl ->
        val plSongs = pl.songIds.mapNotNull { id -> allSongs.firstOrNull { it.id == id } }
        SongsDetailDialog(
            title = pl.name,
            subtitle = "${plSongs.size} songs",
            songs = plSongs,
            onDismiss = { selectedPlaylist = null },
            onPlaySong = { song ->
                musicViewModel.playerController.playSong(song, plSongs)
            },
            onSongMenuClick = { activeMenuSong = it }
        )
    }

    selectedFolder?.let { (folderName, songs) ->
        SongsDetailDialog(
            title = folderName,
            subtitle = "${songs.size} songs",
            songs = songs,
            onDismiss = { selectedFolder = null },
            onPlaySong = { song ->
                musicViewModel.playerController.playSong(song, songs)
            },
            onSongMenuClick = { activeMenuSong = it }
        )
    }
}

@Composable
fun RecentlyDownloadedItem(
    song: Song,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            SongArtwork(
                song = song,
                contentDescription = song.title,
                modifier = Modifier
                    .size(114.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = song.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SongsListTab(
    songs: List<Song>,
    emptyMessage: String = "No songs found in this folder or filter.",
    onSongClick: (Song) -> Unit,
    onSongMenuClick: (Song) -> Unit
) {
    if (songs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(songs) { song ->
                SongRowItem(
                    song = song,
                    onClick = { onSongClick(song) },
                    onMenuClick = { onSongMenuClick(song) }
                )
            }
        }
    }
}

@Composable
fun SongRowItem(
    song: Song,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SongArtwork(
            song = song,
            contentDescription = song.title,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${song.artist} • ${song.formattedDuration}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onMenuClick) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Song options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AlbumsListTab(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit
) {
    if (albums.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No albums found.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(albums) { album ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAlbumClick(album) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!album.albumArtUri.isNullOrBlank()) {
                        AsyncImage(
                            model = album.albumArtUri,
                            contentDescription = album.title,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                            error = painterResource(id = R.drawable.ic_default_album_art),
                            placeholder = painterResource(id = R.drawable.ic_default_album_art)
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.ic_default_album_art),
                            contentDescription = null,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = album.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${album.artist} • ${album.songCount} songs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistsListTab(
    artists: List<Artist>,
    onArtistClick: (Artist) -> Unit
) {
    if (artists.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No artists found.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(artists) { artist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onArtistClick(artist) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${artist.songCount} songs • ${artist.albumCount} albums",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistsTab(
    playlists: List<Playlist>,
    onCreatePlaylist: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onDeletePlaylist: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCreatePlaylist),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Create New Playlist",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        if (playlists.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No custom playlists created yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(playlists) { pl ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlaylistClick(pl) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                            Column {
                                Text(
                                    text = pl.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${pl.songIds.size} songs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(onClick = { onDeletePlaylist(pl.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Playlist",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FoldersListTab(
    folders: Map<String, List<Song>>,
    onFolderClick: (String, List<Song>) -> Unit
) {
    if (folders.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No audio folders found.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(folders.keys.toList()) { folderName ->
                val songs = folders[folderName] ?: emptyList()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFolderClick(folderName, songs) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folderName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${songs.size} songs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SongsDetailDialog(
    title: String,
    subtitle: String,
    songs: List<Song>,
    onDismiss: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onSongMenuClick: (Song) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            if (songs.isEmpty()) {
                Text(
                    text = "No songs found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 350.dp)) {
                    items(songs) { song ->
                        SongRowItem(
                            song = song,
                            onClick = { onPlaySong(song) },
                            onMenuClick = { onSongMenuClick(song) }
                        )
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
