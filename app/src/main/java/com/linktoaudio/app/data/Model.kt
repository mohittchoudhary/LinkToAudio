package com.linktoaudio.app.data

enum class AudioFormat(val extension: String, val displayName: String, val mimeType: String) {
    MP3("mp3", "MP3", "audio/mpeg"),
    M4A("m4a", "M4A", "audio/mp4");

    companion object {
        fun fromExtension(ext: String?): AudioFormat {
            return when (ext?.lowercase()) {
                "m4a" -> M4A
                else -> MP3
            }
        }
    }
}

data class VideoMetadata(
    val title: String,
    val durationSeconds: Long,
    val thumbnail: String?,
    val uploader: String?,
    val sourceUrl: String
) {
    fun formattedDuration(): String {
        if (durationSeconds <= 0) return "--:--"
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}

data class ConversionItem(
    val id: String,
    val title: String,
    val sourceUrl: String,
    val format: String,
    val durationSeconds: Long,
    val timestamp: Long,
    val filePath: String,
    val contentUri: String?,
    val thumbnail: String?,
    val fileSize: Long
) {
    fun formattedDuration(): String {
        if (durationSeconds <= 0) return "--:--"
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    fun formattedSize(): String {
        if (fileSize <= 0) return ""
        val kb = fileSize / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format("%.1f MB", mb)
        } else {
            String.format("%.0f KB", kb)
        }
    }
}

sealed class ConversionState {
    object Idle : ConversionState()
    data class FetchingInfo(val url: String) : ConversionState()
    data class Downloading(
        val progress: Float,
        val etaSeconds: Long,
        val metadata: VideoMetadata?
    ) : ConversionState()
    data class Converting(val metadata: VideoMetadata?) : ConversionState()
    data class Saving(val metadata: VideoMetadata?) : ConversionState()
    data class Success(val item: ConversionItem) : ConversionState()
    data class Error(val message: String, val url: String? = null) : ConversionState()
}
