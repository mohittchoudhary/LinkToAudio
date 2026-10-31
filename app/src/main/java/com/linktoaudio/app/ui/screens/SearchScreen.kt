package com.linktoaudio.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.linktoaudio.app.R
import com.linktoaudio.app.player.model.Album
import com.linktoaudio.app.player.model.Artist
import com.linktoaudio.app.player.model.Playlist
import com.linktoaudio.app.player.model.Song
import com.linktoaudio.app.ui.components.ScreenStatePanel
import com.linktoaudio.app.ui.components.SongMenuBottomSheet
import com.linktoaudio.app.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    musicViewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val searchQuery by musicViewModel.searchQuery.collectAsState()
    val searchSongs by musicViewModel.searchResultsSongs.collectAsState()
    val searchAlbums by musicViewModel.searchResultsAlbums.collectAsState()
    val searchArtists by musicViewModel.searchResultsArtists.collectAsState()
    val searchPlaylists by musicViewModel.searchResultsPlaylists.collectAsState()
    val recentSearches by musicViewModel.recentSearches.collectAsState()
    val favorites by musicViewModel.favorites.collectAsState()
    val playlists by musicViewModel.playlists.collectAsState()
    val allSongs by musicViewModel.allSongs.collectAsState()
    val isRefreshing by musicViewModel.isRefreshing.collectAsState()
    val libraryError by musicViewModel.libraryError.collectAsState()

    var activeMenuSong by remember { mutableStateOf<Song?>(null) }
    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var selectedArtist by remember { mutableStateOf<Artist?>(null) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Search Header Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { musicViewModel.onSearchQueryChanged(it) },
            placeholder = { Text("Search songs, artists, albums, playlists...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { musicViewModel.onSearchQueryChanged("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search"
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (searchQuery.isNotBlank()) {
                    musicViewModel.addRecentSearch(searchQuery)
                }
            }),
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        if (isRefreshing && allSongs.isNotEmpty()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (searchQuery.isBlank()) {
            if (isRefreshing && allSongs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ScreenStatePanel(
                        title = "Loading your music",
                        message = "Scanning your library…",
                        isLoading = true
                    )
                }
            } else if (!libraryError.isNullOrBlank() && allSongs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ScreenStatePanel(
                        title = "Couldn't load your music",
                        message = libraryError ?: "Check audio access and try again.",
                        icon = Icons.Default.Warning,
                        actionLabel = "Retry",
                        onAction = { musicViewModel.refreshLibrary() }
                    )
                }
            } else if (recentSearches.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent Searches",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { musicViewModel.clearRecentSearches() }) {
                                Text("Clear all", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    items(recentSearches) { term ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { musicViewModel.onSearchQueryChanged(term) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = term,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = { musicViewModel.removeRecentSearch(term) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ManageSearch,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "Search offline music",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Find tracks by song name, artist, album, or playlist title.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else if (isRefreshing && allSongs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ScreenStatePanel(
                    title = "Loading your music",
                    message = "Scanning your library…",
                    isLoading = true
                )
            }
        } else if (!libraryError.isNullOrBlank() && allSongs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ScreenStatePanel(
                    title = "Couldn't load your music",
                    message = libraryError ?: "Check audio access and try again.",
                    icon = Icons.Default.Warning,
                    actionLabel = "Retry",
                    onAction = { musicViewModel.refreshLibrary() }
                )
            }
        } else if (allSongs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ScreenStatePanel(
                    title = "No music to search yet",
                    message = "Add tracks to your library or refresh the local music scan.",
                    icon = Icons.Default.Search,
                    actionLabel = "Refresh library",
                    onAction = { musicViewModel.refreshLibrary() }
                )
            }
        } else if (searchSongs.isEmpty() && searchAlbums.isEmpty() && searchArtists.isEmpty() && searchPlaylists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "No results found for \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Check the spelling or try searching for another term.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Songs section
                if (searchSongs.isNotEmpty()) {
                    item {
                        SearchSectionHeader("Songs (${searchSongs.size})")
                    }
                    items(searchSongs) { song ->
                        SongRowItem(
                            song = song,
                            onClick = {
                                musicViewModel.addRecentSearch(searchQuery)
                                musicViewModel.playerController.playSong(song, searchSongs)
                            },
                            onMenuClick = { activeMenuSong = song }
                        )
                    }
                }

                // Albums section
                if (searchAlbums.isNotEmpty()) {
                    item {
                        SearchSectionHeader("Albums (${searchAlbums.size})")
                    }
                    items(searchAlbums) { album ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    musicViewModel.addRecentSearch(searchQuery)
                                    selectedAlbum = album
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (!album.albumArtUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = album.albumArtUri,
                                    contentDescription = album.title,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.size(48.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_default_album_art),
                                        contentDescription = null,
                                        tint = Color.Unspecified,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Artists section
                if (searchArtists.isNotEmpty()) {
                    item {
                        SearchSectionHeader("Artists (${searchArtists.size})")
                    }
                    items(searchArtists) { artist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    musicViewModel.addRecentSearch(searchQuery)
                                    selectedArtist = artist
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = RoundedCornerShape(22.dp),
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
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${artist.songCount} songs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Playlists section
                if (searchPlaylists.isNotEmpty()) {
                    item {
                        SearchSectionHeader("Playlists (${searchPlaylists.size})")
                    }
                    items(searchPlaylists) { pl ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    musicViewModel.addRecentSearch(searchQuery)
                                    selectedPlaylist = pl
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.QueueMusic, contentDescription = null)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pl.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${pl.songIds.size} songs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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

    // Detail Dialogs
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
}

@Composable
private fun SearchSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}
