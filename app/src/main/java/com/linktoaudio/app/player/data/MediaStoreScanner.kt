package com.linktoaudio.app.player.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.linktoaudio.app.player.model.Album
import com.linktoaudio.app.player.model.Artist
import com.linktoaudio.app.player.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MediaStoreScanner {

    suspend fun scanAudio(
        context: Context,
        scanEntireMusicFolder: Boolean
    ): List<Song> = withContext(Dispatchers.IO) {
        val songList = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.SIZE
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 5000"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use {
                val idCol = it.getColumnIndex(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = it.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val durationCol = it.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val dataCol = it.getColumnIndex(MediaStore.Audio.Media.DATA)
                val dateAddedCol = it.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val albumIdCol = it.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val sizeCol = it.getColumnIndex(MediaStore.Audio.Media.SIZE)

                while (it.moveToNext()) {
                    val id = if (idCol >= 0) it.getLong(idCol) else 0L
                    val rawTitle = if (titleCol >= 0) it.getString(titleCol) else null
                    val rawArtist = if (artistCol >= 0) it.getString(artistCol) else null
                    val rawAlbum = if (albumCol >= 0) it.getString(albumCol) else null
                    val duration = if (durationCol >= 0) it.getLong(durationCol) else 0L
                    val filePath = (if (dataCol >= 0) it.getString(dataCol) else null).orEmpty()
                    val dateAdded = if (dateAddedCol >= 0) it.getLong(dateAddedCol) else 0L
                    val albumId = if (albumIdCol >= 0) it.getLong(albumIdCol) else 0L
                    val size = if (sizeCol >= 0) it.getLong(sizeCol) else 0L

                    // Filter based on setting
                    if (!scanEntireMusicFolder) {
                        val normalized = filePath.replace("\\", "/")
                        val isLinkToAudio = normalized.contains("Music/LinkToAudio", ignoreCase = true) ||
                                normalized.contains("/LinkToAudio/", ignoreCase = true) ||
                                normalized.contains("LinkToAudio", ignoreCase = true)
                        if (!isLinkToAudio) {
                            continue
                        }
                    }

                    val title = if (!rawTitle.isNullOrBlank()) rawTitle.trim() else {
                        if (filePath.isNotBlank()) File(filePath).nameWithoutExtension else "Unknown Title"
                    }

                    val artist = if (!rawArtist.isNullOrBlank() && rawArtist != "<unknown>") {
                        rawArtist.trim()
                    } else {
                        "Unknown Artist"
                    }

                    val album = if (!rawAlbum.isNullOrBlank() && rawAlbum != "<unknown>") {
                        rawAlbum.trim()
                    } else {
                        "Unknown Album"
                    }

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    val albumArtUri = if (albumId > 0) {
                        ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            albumId
                        ).toString()
                    } else null

                    val folder = if (filePath.isNotBlank()) {
                        File(filePath).parentFile?.name.orEmpty().ifEmpty { "Music" }
                    } else {
                        "Music"
                    }

                    songList.add(
                        Song(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            contentUri = contentUri,
                            albumArtUri = albumArtUri,
                            filePath = filePath,
                            dateAdded = dateAdded,
                            folder = folder,
                            sizeBytes = size
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        songList
    }

    fun groupIntoAlbums(songs: List<Song>): List<Album> {
        return songs.groupBy { it.album.ifEmpty { "Unknown Album" } }
            .map { (albumTitle, albumSongs) ->
                val first = albumSongs.firstOrNull()
                Album(
                    id = albumTitle.hashCode().toLong(),
                    title = albumTitle,
                    artist = first?.artist ?: "Unknown Artist",
                    albumArtUri = first?.albumArtUri,
                    songs = albumSongs
                )
            }
            .sortedBy { it.title.lowercase() }
    }

    fun groupIntoArtists(songs: List<Song>): List<Artist> {
        return songs.groupBy { it.artist.ifEmpty { "Unknown Artist" } }
            .map { (artistName, artistSongs) ->
                Artist(
                    name = artistName,
                    songs = artistSongs
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    fun groupIntoFolders(songs: List<Song>): Map<String, List<Song>> {
        return songs.groupBy { it.folder.ifEmpty { "Music" } }
            .toSortedMap(compareBy { it.lowercase() })
    }
}
