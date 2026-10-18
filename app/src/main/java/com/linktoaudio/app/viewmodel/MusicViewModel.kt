package com.linktoaudio.app.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.linktoaudio.app.data.AppSettings
import com.linktoaudio.app.data.ConversionState
import com.linktoaudio.app.data.ConversionStateHolder
import com.linktoaudio.app.player.PlayerController
import com.linktoaudio.app.player.data.MediaStoreScanner
import com.linktoaudio.app.player.data.MusicStorageManager
import com.linktoaudio.app.player.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val appSettings = AppSettings.getInstance(application)
    private val storageManager = MusicStorageManager.getInstance(application)
    val playerController = PlayerController.getInstance(application)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    val allSongs: StateFlow<List<Song>> = _allSongs.asStateFlow()

    private val _currentTab = MutableStateFlow(LibraryTab.SONGS)
    val currentTab: StateFlow<LibraryTab> = _currentTab.asStateFlow()

    private val _sortOption = MutableStateFlow(SongSortOption.TITLE)
    val sortOption: StateFlow<SongSortOption> = _sortOption.asStateFlow()

    private val _sortDirection = MutableStateFlow(SortDirection.ASCENDING)
    val sortDirection: StateFlow<SortDirection> = _sortDirection.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isEqualizerAvailable = MutableStateFlow(false)
    val isEqualizerAvailable: StateFlow<Boolean> = _isEqualizerAvailable.asStateFlow()

    private val _hasStoragePermission = MutableStateFlow(false)
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()

    val playlists = storageManager.playlistsFlow
    val favorites = storageManager.favoritesFlow
    val songStats = storageManager.songStatsFlow
    val recentSearches = storageManager.recentSearchesFlow

    // Sorted and filtered songs for Library tab
    val displayedSongs: StateFlow<List<Song>> = combine(
        _allSongs,
        _sortOption,
        _sortDirection
    ) { songs, option, direction ->
        val sorted = when (option) {
            SongSortOption.TITLE -> songs.sortedBy { it.title.lowercase() }
            SongSortOption.ARTIST -> songs.sortedBy { it.artist.lowercase() }
            SongSortOption.DATE_ADDED -> songs.sortedByDescending { it.dateAdded }
            SongSortOption.DURATION -> songs.sortedByDescending { it.durationMs }
        }
        if (direction == SortDirection.DESCENDING && option != SongSortOption.DATE_ADDED && option != SongSortOption.DURATION) {
            sorted.reversed()
        } else if (direction == SortDirection.ASCENDING && (option == SongSortOption.DATE_ADDED || option == SongSortOption.DURATION)) {
            sorted.reversed()
        } else {
            sorted
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<Album>> = _allSongs.map { songs ->
        MediaStoreScanner.groupIntoAlbums(songs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artists: StateFlow<List<Artist>> = _allSongs.map { songs ->
        MediaStoreScanner.groupIntoArtists(songs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<Map<String, List<Song>>> = _allSongs.map { songs ->
        MediaStoreScanner.groupIntoFolders(songs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val favoriteSongs: StateFlow<List<Song>> = combine(
        _allSongs,
        favorites
    ) { songs, favIds ->
        songs.filter { favIds.contains(it.id) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayedSongs: StateFlow<List<Song>> = combine(
        _allSongs,
        songStats
    ) { songs, stats ->
        songs.filter { (stats[it.id]?.lastPlayedTime ?: 0L) > 0L }
            .sortedByDescending { stats[it.id]?.lastPlayedTime ?: 0L }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayedSongs: StateFlow<List<Song>> = combine(
        _allSongs,
        songStats
    ) { songs, stats ->
        songs.filter { (stats[it.id]?.playCount ?: 0) > 0 }
            .sortedByDescending { stats[it.id]?.playCount ?: 0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyDownloadedSongs: StateFlow<List<Song>> = _allSongs.map { songs ->
        songs.sortedByDescending { it.dateAdded }.take(10)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live search results
    val searchResultsSongs: StateFlow<List<Song>> = combine(
        _allSongs,
        _searchQuery
    ) { songs, query ->
        if (query.isBlank()) emptyList()
        else songs.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.artist.contains(query, ignoreCase = true) ||
            it.album.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchResultsAlbums: StateFlow<List<Album>> = combine(
        albums,
        _searchQuery
    ) { albumList, query ->
        if (query.isBlank()) emptyList()
        else albumList.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.artist.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchResultsArtists: StateFlow<List<Artist>> = combine(
        artists,
        _searchQuery
    ) { artistList, query ->
        if (query.isBlank()) emptyList()
        else artistList.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchResultsPlaylists: StateFlow<List<Playlist>> = combine(
        playlists,
        _searchQuery
    ) { plList, query ->
        if (query.isBlank()) emptyList()
        else plList.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        checkEqualizerAvailability()
        observeConversionChanges()
    }

    private fun checkEqualizerAvailability() {
        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
        val packageManager = getApplication<Application>().packageManager
        val resolved = intent.resolveActivity(packageManager) != null
        _isEqualizerAvailable.value = resolved
    }

    private fun observeConversionChanges() {
        viewModelScope.launch {
            ConversionStateHolder.stateFlow.collect { state ->
                if (state is ConversionState.Success || state is ConversionState.QueueCompleted) {
                    refreshLibrary()
                }
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _hasStoragePermission.value = granted
        if (granted) {
            refreshLibrary()
        }
    }

    fun setTab(tab: LibraryTab) {
        _currentTab.value = tab
    }

    fun setSortOption(option: SongSortOption) {
        if (_sortOption.value == option) {
            _sortDirection.value = if (_sortDirection.value == SortDirection.ASCENDING) {
                SortDirection.DESCENDING
            } else {
                SortDirection.ASCENDING
            }
        } else {
            _sortOption.value = option
            _sortDirection.value = SortDirection.ASCENDING
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val songs = MediaStoreScanner.scanAudio(
                    context = getApplication(),
                    scanEntireMusicFolder = appSettings.scanEntireMusicFolder
                )
                _allSongs.value = songs
                playerController.resumeLastPlayed(songs)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            storageManager.toggleFavorite(song.id)
        }
    }

    fun isFavorite(songId: Long): Boolean {
        return storageManager.isFavorite(songId)
    }

    fun createPlaylist(name: String, onCreated: (Playlist) -> Unit = {}) {
        viewModelScope.launch {
            val pl = storageManager.createPlaylist(name)
            onCreated(pl)
        }
    }

    fun renamePlaylist(id: String, newName: String) {
        viewModelScope.launch {
            storageManager.renamePlaylist(id, newName)
        }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch {
            storageManager.deletePlaylist(id)
        }
    }

    fun addSongToPlaylist(playlistId: String, songId: Long) {
        viewModelScope.launch {
            storageManager.addSongToPlaylist(playlistId, songId)
        }
    }

    fun removeSongFromPlaylist(playlistId: String, songId: Long) {
        viewModelScope.launch {
            storageManager.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun reorderPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            storageManager.reorderPlaylist(playlistId, fromIndex, toIndex)
        }
    }

    fun shareSong(song: Song, context: Context) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                val uri = if (song.filePath.isNotBlank()) {
                    val file = File(song.filePath)
                    if (file.exists()) {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                    } else {
                        Uri.parse(song.contentUri)
                    }
                } else {
                    Uri.parse(song.contentUri)
                }
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, song.title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share ${song.title}"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteSong(song: Song, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = getApplication<Application>().contentResolver
                val uri = Uri.parse(song.contentUri)
                resolver.delete(uri, null, null)

                if (song.filePath.isNotBlank()) {
                    val file = File(song.filePath)
                    if (file.exists()) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            withContext(Dispatchers.Main) {
                refreshLibrary()
                onComplete()
            }
        }
    }

    fun openEqualizer(context: Context) {
        try {
            val sessionId = playerController.getAudioSessionId()
            val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addRecentSearch(query: String) {
        viewModelScope.launch {
            storageManager.addRecentSearch(query)
        }
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch {
            storageManager.removeRecentSearch(query)
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            storageManager.clearRecentSearches()
        }
    }
}
