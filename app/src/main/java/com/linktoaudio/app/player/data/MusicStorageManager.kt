package com.linktoaudio.app.player.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.linktoaudio.app.player.model.Playlist
import com.linktoaudio.app.player.model.SongStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

data class MusicPlayerData(
    val playlists: List<Playlist> = emptyList(),
    val favorites: Set<Long> = emptySet(),
    val stats: Map<String, SongStats> = emptyMap(),
    val lastPlayedSongId: Long? = null,
    val lastPlayedPositionMs: Long = 0L,
    val recentSearches: List<String> = emptyList()
)

class MusicStorageManager private constructor(private val context: Context) {

    private val gson = Gson()
    private val storageFile: File = File(context.filesDir, "music_player_data.json")
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _playlistsFlow = MutableStateFlow<List<Playlist>>(emptyList())
    val playlistsFlow: StateFlow<List<Playlist>> = _playlistsFlow.asStateFlow()

    private val _favoritesFlow = MutableStateFlow<Set<Long>>(emptySet())
    val favoritesFlow: StateFlow<Set<Long>> = _favoritesFlow.asStateFlow()

    private val _songStatsFlow = MutableStateFlow<Map<Long, SongStats>>(emptyMap())
    val songStatsFlow: StateFlow<Map<Long, SongStats>> = _songStatsFlow.asStateFlow()

    private val _lastPlayedSongIdFlow = MutableStateFlow<Long?>(null)
    val lastPlayedSongIdFlow: StateFlow<Long?> = _lastPlayedSongIdFlow.asStateFlow()

    private val _lastPlayedPositionFlow = MutableStateFlow(0L)
    val lastPlayedPositionFlow: StateFlow<Long> = _lastPlayedPositionFlow.asStateFlow()

    private val _recentSearchesFlow = MutableStateFlow<List<String>>(emptyList())
    val recentSearchesFlow: StateFlow<List<String>> = _recentSearchesFlow.asStateFlow()

    init {
        scope.launch {
            loadFromDisk()
        }
    }

    private suspend fun loadFromDisk() {
        mutex.withLock {
            try {
                if (storageFile.exists()) {
                    val json = storageFile.readText()
                    val type = object : TypeToken<MusicPlayerData>() {}.type
                    val data: MusicPlayerData? = gson.fromJson(json, type)
                    if (data != null) {
                        _playlistsFlow.value = data.playlists
                        _favoritesFlow.value = data.favorites
                        val mappedStats = data.stats.mapNotNull { entry ->
                            entry.key.toLongOrNull()?.let { id -> id to entry.value }
                        }.toMap()
                        _songStatsFlow.value = mappedStats
                        _lastPlayedSongIdFlow.value = data.lastPlayedSongId
                        _lastPlayedPositionFlow.value = data.lastPlayedPositionMs
                        _recentSearchesFlow.value = data.recentSearches
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun saveToDiskLocked() {
        try {
            val statsForJson = _songStatsFlow.value.mapKeys { it.key.toString() }
            val data = MusicPlayerData(
                playlists = _playlistsFlow.value,
                favorites = _favoritesFlow.value,
                stats = statsForJson,
                lastPlayedSongId = _lastPlayedSongIdFlow.value,
                lastPlayedPositionMs = _lastPlayedPositionFlow.value,
                recentSearches = _recentSearchesFlow.value
            )
            val json = gson.toJson(data)
            storageFile.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun createPlaylist(name: String): Playlist {
        val trimmed = name.trim()
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = if (trimmed.isEmpty()) "Untitled Playlist" else trimmed,
            createdAt = System.currentTimeMillis(),
            songIds = emptyList()
        )
        mutex.withLock {
            val list = _playlistsFlow.value.toMutableList()
            list.add(0, playlist)
            _playlistsFlow.value = list
            saveToDiskLocked()
        }
        return playlist
    }

    suspend fun renamePlaylist(id: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        mutex.withLock {
            val list = _playlistsFlow.value.map { pl ->
                if (pl.id == id) pl.copy(name = trimmed) else pl
            }
            _playlistsFlow.value = list
            saveToDiskLocked()
        }
    }

    suspend fun deletePlaylist(id: String) {
        mutex.withLock {
            val list = _playlistsFlow.value.filterNot { it.id == id }
            _playlistsFlow.value = list
            saveToDiskLocked()
        }
    }

    suspend fun addSongToPlaylist(playlistId: String, songId: Long) {
        mutex.withLock {
            val list = _playlistsFlow.value.map { pl ->
                if (pl.id == playlistId) {
                    val updatedIds = pl.songIds.toMutableList()
                    if (!updatedIds.contains(songId)) {
                        updatedIds.add(songId)
                    }
                    pl.copy(songIds = updatedIds)
                } else {
                    pl
                }
            }
            _playlistsFlow.value = list
            saveToDiskLocked()
        }
    }

    suspend fun removeSongFromPlaylist(playlistId: String, songId: Long) {
        mutex.withLock {
            val list = _playlistsFlow.value.map { pl ->
                if (pl.id == playlistId) {
                    pl.copy(songIds = pl.songIds.filterNot { it == songId })
                } else {
                    pl
                }
            }
            _playlistsFlow.value = list
            saveToDiskLocked()
        }
    }

    suspend fun reorderPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) {
        mutex.withLock {
            val list = _playlistsFlow.value.map { pl ->
                if (pl.id == playlistId) {
                    val ids = pl.songIds.toMutableList()
                    if (fromIndex in ids.indices && toIndex in ids.indices && fromIndex != toIndex) {
                        val item = ids.removeAt(fromIndex)
                        ids.add(toIndex, item)
                        pl.copy(songIds = ids)
                    } else {
                        pl
                    }
                } else {
                    pl
                }
            }
            _playlistsFlow.value = list
            saveToDiskLocked()
        }
    }

    suspend fun toggleFavorite(songId: Long): Boolean {
        val isFav: Boolean
        mutex.withLock {
            val current = _favoritesFlow.value.toMutableSet()
            if (current.contains(songId)) {
                current.remove(songId)
                isFav = false
            } else {
                current.add(songId)
                isFav = true
            }
            _favoritesFlow.value = current

            val currentStats = _songStatsFlow.value.toMutableMap()
            val existing = currentStats[songId] ?: SongStats(songId = songId)
            currentStats[songId] = existing.copy(isFavorite = isFav)
            _songStatsFlow.value = currentStats

            saveToDiskLocked()
        }
        return isFav
    }

    fun isFavorite(songId: Long): Boolean {
        return _favoritesFlow.value.contains(songId)
    }

    suspend fun recordSongPlay(songId: Long) {
        mutex.withLock {
            val currentStats = _songStatsFlow.value.toMutableMap()
            val existing = currentStats[songId] ?: SongStats(
                songId = songId,
                isFavorite = _favoritesFlow.value.contains(songId)
            )
            currentStats[songId] = existing.copy(
                playCount = existing.playCount + 1,
                lastPlayedTime = System.currentTimeMillis()
            )
            _songStatsFlow.value = currentStats
            saveToDiskLocked()
        }
    }

    suspend fun saveLastPlayed(songId: Long, positionMs: Long) {
        mutex.withLock {
            _lastPlayedSongIdFlow.value = songId
            _lastPlayedPositionFlow.value = positionMs
            saveToDiskLocked()
        }
    }

    suspend fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        mutex.withLock {
            val current = _recentSearchesFlow.value.toMutableList()
            current.removeAll { it.equals(trimmed, ignoreCase = true) }
            current.add(0, trimmed)
            val limited = current.take(10)
            _recentSearchesFlow.value = limited
            saveToDiskLocked()
        }
    }

    suspend fun removeRecentSearch(query: String) {
        val trimmed = query.trim()
        mutex.withLock {
            val current = _recentSearchesFlow.value.toMutableList()
            current.removeAll { it.equals(trimmed, ignoreCase = true) }
            _recentSearchesFlow.value = current
            saveToDiskLocked()
        }
    }

    suspend fun clearRecentSearches() {
        mutex.withLock {
            _recentSearchesFlow.value = emptyList()
            saveToDiskLocked()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: MusicStorageManager? = null

        fun getInstance(context: Context): MusicStorageManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MusicStorageManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
