package com.linktoaudio.app.player.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.linktoaudio.app.data.AppSettings
import com.linktoaudio.app.player.model.Album
import com.linktoaudio.app.player.model.Artist
import com.linktoaudio.app.player.model.Song
import com.linktoaudio.app.utils.MediaSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MediaStoreScanner {

    suspend fun scanAudio(
        context: Context,
        scanEntireMusicFolder: Boolean
    ): List<Song> = withContext(Dispatchers.IO) {
        val songList = mutableListOf<Song>()
        val appSettings = AppSettings.getInstance(context)
        val customTreeUri = appSettings.customDownloadTreeUri

        val projection = mutableListOf(
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection += MediaStore.MediaColumns.RELATIVE_PATH
            projection += MediaStore.MediaColumns.VOLUME_NAME
        }

        val projectionArray = projection.toTypedArray()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 5000"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projectionArray,
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
                val relativePathCol = it.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                val volumeNameCol = it.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME)

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
                    val relativePath = if (relativePathCol >= 0) it.getString(relativePathCol).orEmpty() else ""
                    val volumeName = if (volumeNameCol >= 0) it.getString(volumeNameCol) else null

                    // Filter based on setting
                    if (!scanEntireMusicFolder) {
                        val normalized = relativePath.ifBlank { filePath }.replace("\\", "/")
                        val isMohitMusic = normalized.contains("Music/Mohit Music", ignoreCase = true) ||
                                normalized.contains("/Mohit Music/", ignoreCase = true) ||
                                normalized.contains("Mohit Music", ignoreCase = true)
                        val isLegacyLinkToAudio = normalized.contains("Music/LinkToAudio", ignoreCase = true) ||
                                normalized.contains("/LinkToAudio/", ignoreCase = true) ||
                                normalized.contains("LinkToAudio", ignoreCase = true)
                        if (!isMohitMusic && !isLegacyLinkToAudio) {
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

                    val mediaCollection = if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !volumeName.isNullOrBlank()
                    ) {
                        MediaStore.Audio.Media.getContentUri(volumeName)
                    } else {
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                    val contentUri = ContentUris.withAppendedId(mediaCollection, id).toString()

                    val albumArtUri = if (albumId > 0) {
                        ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            albumId
                        ).toString()
                    } else null

                    val folder = if (relativePath.isNotBlank()) {
                        relativePath.trimEnd('/').substringAfterLast('/').ifEmpty { "Mohit Music" }
                    } else if (filePath.isNotBlank()) {
                        File(filePath).parentFile?.name.orEmpty().ifEmpty { "Mohit Music" }
                    } else {
                        "Mohit Music"
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
            throw e
        }

        // Also scan custom download folder (SAF DocumentTree) if selected and accessible
        if (MediaSaver.isTreeUriAccessible(context, customTreeUri)) {
            try {
                val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(customTreeUri.orEmpty()))
                if (rootDoc != null && rootDoc.exists() && rootDoc.canRead()) {
                    val existingUris = songList.map { it.contentUri }.toSet()
                    val existingKeys = songList.map { it.title.lowercase() to it.sizeBytes }.toSet()
                    scanDocumentTree(
                        context = context,
                        dir = rootDoc,
                        folderName = rootDoc.name ?: "Mohit Music",
                        existingUris = existingUris,
                        existingKeys = existingKeys,
                        outSongs = songList
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        songList
    }

    private fun scanDocumentTree(
        context: Context,
        dir: DocumentFile,
        folderName: String,
        existingUris: Set<String>,
        existingKeys: Set<Pair<String, Long>>,
        outSongs: MutableList<Song>
    ) {
        val files = dir.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                scanDocumentTree(
                    context = context,
                    dir = file,
                    folderName = file.name ?: folderName,
                    existingUris = existingUris,
                    existingKeys = existingKeys,
                    outSongs = outSongs
                )
            } else if (file.isFile) {
                val name = file.name.orEmpty()
                val isAudio = file.type?.startsWith("audio/") == true ||
                        name.endsWith(".mp3", ignoreCase = true) ||
                        name.endsWith(".m4a", ignoreCase = true) ||
                        name.endsWith(".wav", ignoreCase = true) ||
                        name.endsWith(".ogg", ignoreCase = true) ||
                        name.endsWith(".flac", ignoreCase = true) ||
                        name.endsWith(".opus", ignoreCase = true)

                if (isAudio) {
                    val uriStr = file.uri.toString()
                    val titleWithoutExt = name.substringBeforeLast('.')
                    val key = titleWithoutExt.lowercase() to file.length()
                    if (!existingUris.contains(uriStr) && !existingKeys.contains(key)) {
                        var title = titleWithoutExt
                        var artist = "Unknown Artist"
                        var album = "Unknown Album"
                        var durationMs = 0L

                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(context, file.uri)
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                                ?.takeIf { it.isNotBlank() }?.let { title = it }
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                                ?.takeIf { it.isNotBlank() }?.let { artist = it }
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                                ?.takeIf { it.isNotBlank() }?.let { album = it }
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                ?.toLongOrNull()?.let { durationMs = it }
                        } catch (e: Exception) {
                            // Fallback to filename
                        } finally {
                            try { retriever.release() } catch (ignored: Exception) {}
                        }

                        val id = file.uri.hashCode().toLong()
                        outSongs.add(
                            Song(
                                id = id,
                                title = title,
                                artist = artist,
                                album = album,
                                durationMs = durationMs,
                                contentUri = uriStr,
                                albumArtUri = null,
                                filePath = "",
                                dateAdded = file.lastModified() / 1000L,
                                folder = folderName,
                                sizeBytes = file.length()
                            )
                        )
                    }
                }
            }
        }
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
        return songs.groupBy { it.folder.ifEmpty { "Mohit Music" } }
            .toSortedMap(compareBy { it.lowercase() })
    }
}
