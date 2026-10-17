package com.linktoaudio.app.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.linktoaudio.app.data.AudioFormat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object MediaSaver {

    data class SaveResult(
        val uri: Uri?,
        val displayPath: String,
        val localFilePath: String,
        val size: Long
    )

    /**
     * Saves a temporary audio file into Music/LinkToAudio via MediaStore (API 29+)
     * or standard filesystem with MediaScanner (API <= 28).
     */
    fun saveAudioFile(
        context: Context,
        tempFile: File,
        desiredTitle: String,
        format: AudioFormat
    ): SaveResult {
        val sanitizedTitle = sanitizeFilename(desiredTitle)
        val filename = "$sanitizedTitle.${format.extension}"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, filename)
                put(MediaStore.Audio.Media.TITLE, desiredTitle)
                put(MediaStore.Audio.Media.MIME_TYPE, format.mimeType)
                put(
                    MediaStore.Audio.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_MUSIC}/LinkToAudio"
                )
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IllegalStateException("Failed to create MediaStore entry for $filename")

            try {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    FileInputStream(tempFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                contentValues.clear()
                contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                return SaveResult(
                    uri = uri,
                    displayPath = "Music/LinkToAudio/$filename",
                    localFilePath = tempFile.absolutePath,
                    size = tempFile.length()
                )
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            // Legacy storage for API 24 - 28
            @Suppress("DEPRECATION")
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val appMusicDir = File(musicDir, "LinkToAudio").apply { if (!exists()) mkdirs() }
            val destFile = File(appMusicDir, filename)

            FileInputStream(tempFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            var scannedUri: Uri? = null
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf(format.mimeType)
            ) { _, uri ->
                scannedUri = uri
            }

            return SaveResult(
                uri = scannedUri ?: Uri.fromFile(destFile),
                displayPath = destFile.absolutePath,
                localFilePath = destFile.absolutePath,
                size = destFile.length()
            )
        }
    }

    fun createPlayIntent(context: Context, filePath: String, contentUriString: String?, format: AudioFormat): Intent {
        val uri: Uri = if (!contentUriString.isNullOrBlank()) {
            Uri.parse(contentUriString)
        } else {
            val file = File(filePath)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, format.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createShareIntent(context: Context, filePath: String, contentUriString: String?, format: AudioFormat, title: String): Intent {
        val uri: Uri = if (!contentUriString.isNullOrBlank()) {
            Uri.parse(contentUriString)
        } else {
            val file = File(filePath)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }

        return Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(120)
            .ifEmpty { "audio_${System.currentTimeMillis()}" }
    }
}
