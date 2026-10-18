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

enum class AudioQuality(val value: String, val displayName: String) {
    BEST("0", "Best"),
    KBPS_192("192K", "192 kbps"),
    KBPS_128("128K", "128 kbps");

    companion object {
        fun fromValue(value: String?): AudioQuality {
            return values().firstOrNull {
                it.value.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true)
            } ?: BEST
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

data class PlaylistTrack(
    val id: String,
    val title: String,
    val artist: String? = null,
    val durationSeconds: Long = 0L,
    val url: String,
    val isSelected: Boolean = true
) {
    fun formattedDuration(): String {
        if (durationSeconds <= 0) return "--:--"
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}

data class PlaylistInfo(
    val title: String,
    val sourceUrl: String,
    val tracks: List<PlaylistTrack>,
    val isSpotify: Boolean = false
)

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

    // Playlist queue states
    data class QueueRunning(
        val currentIndex: Int, // 1-based index (e.g. 3)
        val totalCount: Int,   // Total tracks (e.g. 25)
        val currentTitle: String,
        val progress: Float,    // Track download progress 0..100
        val etaSeconds: Long,
        val playlistTitle: String?,
        val completedCount: Int,
        val failedCount: Int
    ) : ConversionState()

    data class QueueCompleted(
        val completedCount: Int,
        val failedCount: Int,
        val failedTitles: List<String>,
        val playlistTitle: String?,
        val destinationFolder: String
    ) : ConversionState()
}
