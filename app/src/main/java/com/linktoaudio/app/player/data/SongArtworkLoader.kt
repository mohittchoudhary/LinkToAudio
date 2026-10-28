package com.linktoaudio.app.player.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.linktoaudio.app.R
import com.linktoaudio.app.player.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object SongArtworkLoader {

    // 15 MB in-memory LRU cache for embedded album art bytes
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceIn(4096, 20480) // 4MB - 20MB
    private val memoryCache = object : LruCache<String, ByteArray>(cacheSize) {
        override fun sizeOf(key: String, value: ByteArray): Int {
            return value.size / 1024
        }
    }

    private fun getCacheKey(song: Song): String {
        return if (song.contentUri.isNotBlank()) song.contentUri else "song_${song.id}"
    }

    /**
     * Synchronously returns cached bytes if already in memory cache or in filesDir covers.
     */
    fun getEmbeddedOrCachedBytes(context: Context, song: Song): ByteArray? {
        val key = getCacheKey(song)
        memoryCache.get(key)?.let { return it }

        // Check file cache in filesDir/covers
        val cachedFile = getCachedCoverFile(context, song)
        if (cachedFile.exists()) {
            try {
                val bytes = cachedFile.readBytes()
                memoryCache.put(key, bytes)
                return bytes
            } catch (ignored: Exception) {}
        }

        // Try quick extraction if contentUri is valid
        if (song.contentUri.isNotBlank()) {
            val retriever = MediaMetadataRetriever()
            try {
                if (song.contentUri.startsWith("content://")) {
                    retriever.setDataSource(context, Uri.parse(song.contentUri))
                } else if (song.filePath.isNotBlank()) {
                    retriever.setDataSource(song.filePath)
                }
                val pic = retriever.embeddedPicture
                if (pic != null) {
                    memoryCache.put(key, pic)
                    return pic
                }
            } catch (ignored: Exception) {
            } finally {
                try { retriever.release() } catch (ignored: Exception) {}
            }
        }

        return null
    }

    fun getArtworkUri(context: Context, song: Song): Uri? {
        val cachedFile = getCachedCoverFile(context, song)
        if (cachedFile.exists()) {
            return Uri.fromFile(cachedFile)
        }
        if (!song.albumArtUri.isNullOrBlank()) {
            return Uri.parse(song.albumArtUri)
        }
        return null
    }

    fun getCachedCoverFile(context: Context, song: Song): File {
        val coversDir = File(context.filesDir, "covers").apply { if (!exists()) mkdirs() }
        val hash = (song.contentUri.ifBlank { song.title }).hashCode()
        return File(coversDir, "$hash.jpg")
    }

    /**
     * Loads artwork per song with the priority:
     * 1. In-memory LruCache
     * 2. App filesDir/covers/ cache file
     * 3. MediaMetadataRetriever embedded picture (on Dispatchers.IO)
     * 4. Sidecar JPG in the same folder
     * 5. MediaStore album art URI (legacy fallback)
     * 6. Null (caller falls back to placeholder)
     */
    suspend fun loadArtworkModel(context: Context, song: Song): Any? = withContext(Dispatchers.IO) {
        val key = getCacheKey(song)

        // 1. In-memory cache
        memoryCache.get(key)?.let { return@withContext it }

        // 2. App filesDir/covers
        val cachedFile = getCachedCoverFile(context, song)
        if (cachedFile.exists()) {
            try {
                val bytes = cachedFile.readBytes()
                memoryCache.put(key, bytes)
                return@withContext cachedFile
            } catch (ignored: Exception) {}
        }

        // 3. Embedded picture via MediaMetadataRetriever
        if (song.contentUri.isNotBlank()) {
            val retriever = MediaMetadataRetriever()
            try {
                if (song.contentUri.startsWith("content://")) {
                    retriever.setDataSource(context, Uri.parse(song.contentUri))
                } else if (song.filePath.isNotBlank()) {
                    retriever.setDataSource(song.filePath)
                }
                val pic = retriever.embeddedPicture
                if (pic != null) {
                    memoryCache.put(key, pic)
                    // Also cache to filesDir for faster subsequent access
                    try {
                        FileOutputStream(cachedFile).use { it.write(pic) }
                    } catch (ignored: Exception) {}
                    return@withContext pic
                }
            } catch (ignored: Exception) {
            } finally {
                try { retriever.release() } catch (ignored: Exception) {}
            }
        }

        // 4. Sidecar JPG next to audio file
        if (song.filePath.isNotBlank()) {
            try {
                val audioFile = File(song.filePath)
                val parent = audioFile.parentFile
                if (parent != null && parent.exists()) {
                    val sidecar = File(parent, "${audioFile.nameWithoutExtension}.jpg")
                    if (sidecar.exists()) {
                        return@withContext sidecar
                    }
                }
            } catch (ignored: Exception) {}
        }

        // 5. MediaStore albumArtUri fallback
        if (!song.albumArtUri.isNullOrBlank()) {
            return@withContext song.albumArtUri
        }

        // 6. Null
        null
    }

    /**
     * Center-crops a bitmap to a 1:1 square.
     */
    fun cropToSquare(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width == height) return bitmap
        val size = minOf(width, height)
        val x = (width - size) / 2
        val y = (height - size) / 2
        return Bitmap.createBitmap(bitmap, x, y, size, size)
    }

    /**
     * Processes a downloaded thumbnail file: crops to square and stores in covers cache.
     */
    fun processAndCacheThumbnail(
        context: Context,
        thumbnailFile: File,
        songTitle: String,
        songContentUri: String?
    ): File? {
        if (!thumbnailFile.exists()) return null
        return try {
            val bitmap = BitmapFactory.decodeFile(thumbnailFile.absolutePath) ?: return thumbnailFile
            val square = cropToSquare(bitmap)
            FileOutputStream(thumbnailFile).use { out ->
                square.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            if (square != bitmap) {
                square.recycle()
            }
            bitmap.recycle()

            val key = songContentUri?.takeIf { it.isNotBlank() } ?: songTitle
            val coversDir = File(context.filesDir, "covers").apply { if (!exists()) mkdirs() }
            val cachedFile = File(coversDir, "${key.hashCode()}.jpg")
            thumbnailFile.copyTo(cachedFile, overwrite = true)

            thumbnailFile
        } catch (e: Exception) {
            e.printStackTrace()
            thumbnailFile
        }
    }
}

/**
 * Universal Compose component for per-song artwork with square crop, smooth coil loading,
 * memory caching, sidecar fallback, and placeholder.
 */
@Composable
fun SongArtwork(
    song: Song?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val artModel by produceState<Any?>(initialValue = null, key1 = song?.id, key2 = song?.contentUri) {
        value = if (song != null) {
            SongArtworkLoader.loadArtworkModel(context, song)
        } else null
    }

    if (artModel != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(artModel)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_default_album_art),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(0.6f),
                contentScale = ContentScale.Fit
            )
        }
    }
}
