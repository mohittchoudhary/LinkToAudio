package com.linktoaudio.app.player.model

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: String,
    val albumArtUri: String? = null,
    val filePath: String = "",
    val dateAdded: Long = 0L,
    val folder: String = "",
    val sizeBytes: Long = 0L
) {
    val formattedDuration: String
        get() = formatDuration(durationMs)

    companion object {
        fun formatDuration(durationMs: Long): String {
            if (durationMs <= 0) return "0:00"
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%d:%02d", minutes, seconds)
        }
    }
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val albumArtUri: String? = null,
    val songs: List<Song> = emptyList()
) {
    val songCount: Int get() = songs.size
    val totalDurationMs: Long get() = songs.sumOf { it.durationMs }
}

data class Artist(
    val name: String,
    val songs: List<Song> = emptyList()
) {
    val songCount: Int get() = songs.size
    val albumCount: Int get() = songs.map { it.album }.distinct().size
}

data class Playlist(
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val songIds: List<Long> = emptyList()
)

data class SongStats(
    val songId: Long,
    val playCount: Int = 0,
    val lastPlayedTime: Long = 0L,
    val isFavorite: Boolean = false
)

enum class PlayerRepeatMode {
    OFF, ALL, ONE
}

enum class LibraryTab(val title: String) {
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists"),
    FOLDERS("Folders"),
    FAVORITES("Favorites"),
    RECENTLY_PLAYED("Recently played"),
    MOST_PLAYED("Most played")
}

enum class SongSortOption(val displayName: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    DATE_ADDED("Date Added"),
    DURATION("Duration")
}

enum class SortDirection {
    ASCENDING, DESCENDING
}
