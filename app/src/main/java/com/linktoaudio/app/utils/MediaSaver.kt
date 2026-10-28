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
import androidx.documentfile.provider.DocumentFile
import com.linktoaudio.app.data.AppSettings
import com.linktoaudio.app.data.AudioFormat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object MediaSaver {

    data class SaveResult(
        val uri: Uri?,
        val displayPath: String,
        val localFilePath: String,
        val size: Long,
        val usedFallback: Boolean = false
    )

    fun isTreeUriAccessible(context: Context, uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        return try {
            val uri = Uri.parse(uriString)
            val doc = DocumentFile.fromTreeUri(context, uri)
            doc != null && doc.exists() && doc.canWrite()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Saves a temporary audio file:
     * - If custom download folder tree URI is chosen and accessible: copies via DocumentFile into that folder
     *   (and creates subfolder for playlists).
     * - If custom folder is inaccessible or not set: falls back to default Music/Mohit Music via MediaStore.
     * Deletes the cache tempFile after copying.
     */
    fun saveAudioFile(
        context: Context,
        tempFile: File,
        desiredTitle: String,
        format: AudioFormat,
        subfolder: String? = null
    ): SaveResult {
        val appSettings = AppSettings.getInstance(context)
        val customTreeUri = appSettings.customDownloadTreeUri
        val hasCustomFolder = !customTreeUri.isNullOrBlank()

        var usedFallback = false

        if (hasCustomFolder) {
            if (isTreeUriAccessible(context, customTreeUri)) {
                try {
                    return saveToDocumentTree(
                        context = context,
                        treeUriString = customTreeUri!!,
                        tempFile = tempFile,
                        desiredTitle = desiredTitle,
                        format = format,
                        subfolder = subfolder
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    usedFallback = true
                }
            } else {
                usedFallback = true
            }
        }

        // Fall back to default folder: Music/Mohit Music
        val result = saveToDefaultMediaStore(
            context = context,
            tempFile = tempFile,
            desiredTitle = desiredTitle,
            format = format,
            subfolder = subfolder
        )
        return result.copy(usedFallback = usedFallback)
    }

    private fun saveToDocumentTree(
        context: Context,
        treeUriString: String,
        tempFile: File,
        desiredTitle: String,
        format: AudioFormat,
        subfolder: String?
    ): SaveResult {
        val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(treeUriString))
            ?: throw IllegalStateException("Cannot access root directory from tree URI")

        val sanitizedTitle = sanitizeFilename(desiredTitle)
        val filename = "$sanitizedTitle.${format.extension}"

        // Handle playlist subfolder
        val targetDir: DocumentFile = if (!subfolder.isNullOrBlank()) {
            val sanitizedFolder = sanitizeFilename(subfolder)
            val existing = rootDoc.findFile(sanitizedFolder)
            if (existing != null && existing.isDirectory) {
                existing
            } else {
                rootDoc.createDirectory(sanitizedFolder) ?: rootDoc
            }
        } else {
            rootDoc
        }

        // Create target audio file
        val targetFileDoc = targetDir.createFile(format.mimeType, filename)
            ?: throw IllegalStateException("Failed to create file $filename in chosen folder")

        val resolver = context.contentResolver
        resolver.openOutputStream(targetFileDoc.uri)?.use { output ->
            FileInputStream(tempFile).use { input ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Failed to open output stream for ${targetFileDoc.uri}")

        // Also check if sidecar JPG exists (for M4A artwork fallback)
        val sidecarJpg = File(tempFile.parentFile, "${tempFile.nameWithoutExtension}.jpg")
        if (sidecarJpg.exists()) {
            try {
                val sidecarDoc = targetDir.createFile("image/jpeg", "$sanitizedTitle.jpg")
                if (sidecarDoc != null) {
                    resolver.openOutputStream(sidecarDoc.uri)?.use { outJpg ->
                        FileInputStream(sidecarJpg).use { inJpg -> inJpg.copyTo(outJpg) }
                    }
                }
                sidecarJpg.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val size = tempFile.length()
        tempFile.delete()

        val folderDisplayName = targetDir.name ?: "Mohit Music"
        return SaveResult(
            uri = targetFileDoc.uri,
            displayPath = "$folderDisplayName/$filename",
            localFilePath = targetFileDoc.uri.toString(),
            size = size,
            usedFallback = false
        )
    }

    private fun saveToDefaultMediaStore(
        context: Context,
        tempFile: File,
        desiredTitle: String,
        format: AudioFormat,
        subfolder: String?
    ): SaveResult {
        val sanitizedTitle = sanitizeFilename(desiredTitle)
        val filename = "$sanitizedTitle.${format.extension}"
        val sanitizedSubfolder = subfolder?.let { sanitizeFilename(it) }?.takeIf { it.isNotBlank() }

        val relativePath = if (sanitizedSubfolder != null) {
            "${Environment.DIRECTORY_MUSIC}/Mohit Music/$sanitizedSubfolder"
        } else {
            "${Environment.DIRECTORY_MUSIC}/Mohit Music"
        }

        val displayPath = if (sanitizedSubfolder != null) {
            "Music/Mohit Music/$sanitizedSubfolder/$filename"
        } else {
            "Music/Mohit Music/$filename"
        }

        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, filename)
                put(MediaStore.Audio.Media.TITLE, desiredTitle)
                put(MediaStore.Audio.Media.MIME_TYPE, format.mimeType)
                put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }

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

                // Copy sidecar JPG if exists for M4A
                val sidecarJpg = File(tempFile.parentFile, "${tempFile.nameWithoutExtension}.jpg")
                if (sidecarJpg.exists()) {
                    try {
                        val imageValues = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, "$sanitizedTitle.jpg")
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                        }
                        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageValues)
                        if (imageUri != null) {
                            resolver.openOutputStream(imageUri)?.use { out ->
                                FileInputStream(sidecarJpg).use { inp -> inp.copyTo(out) }
                            }
                        }
                        sidecarJpg.delete()
                    } catch (ignored: Exception) {}
                }

                val size = tempFile.length()
                tempFile.delete()

                return SaveResult(
                    uri = uri,
                    displayPath = displayPath,
                    localFilePath = tempFile.absolutePath,
                    size = size,
                    usedFallback = false
                )
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            // Legacy storage for API 24 - 28
            @Suppress("DEPRECATION")
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val parentDir = if (sanitizedSubfolder != null) {
                File(File(musicDir, "Mohit Music"), sanitizedSubfolder)
            } else {
                File(musicDir, "Mohit Music")
            }
            if (!parentDir.exists()) {
                parentDir.mkdirs()
            }
            val destFile = File(parentDir, filename)

            FileInputStream(tempFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            val sidecarJpg = File(tempFile.parentFile, "${tempFile.nameWithoutExtension}.jpg")
            if (sidecarJpg.exists()) {
                try {
                    val destJpg = File(parentDir, "$sanitizedTitle.jpg")
                    FileInputStream(sidecarJpg).use { inJpg ->
                        FileOutputStream(destJpg).use { outJpg -> inJpg.copyTo(outJpg) }
                    }
                    sidecarJpg.delete()
                } catch (ignored: Exception) {}
            }

            var scannedUri: Uri? = null
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf(format.mimeType)
            ) { _, scanned ->
                scannedUri = scanned
            }

            val size = destFile.length()
            tempFile.delete()

            return SaveResult(
                uri = scannedUri ?: Uri.fromFile(destFile),
                displayPath = destFile.absolutePath,
                localFilePath = destFile.absolutePath,
                size = size,
                usedFallback = false
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

    fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(120)
            .ifEmpty { "audio_${System.currentTimeMillis()}" }
    }
}
