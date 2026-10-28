package com.linktoaudio.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.linktoaudio.app.MainActivity
import com.linktoaudio.app.R
import com.linktoaudio.app.data.AppSettings
import com.linktoaudio.app.data.AudioFormat
import com.linktoaudio.app.data.AudioQuality
import com.linktoaudio.app.data.ConversionHistoryManager
import com.linktoaudio.app.data.ConversionItem
import com.linktoaudio.app.data.ConversionState
import com.linktoaudio.app.data.ConversionStateHolder
import com.linktoaudio.app.data.PlaylistTrack
import com.linktoaudio.app.data.VideoMetadata
import com.linktoaudio.app.utils.ErrorParser
import com.linktoaudio.app.utils.MediaSaver
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

object ConversionQueueHolder {
    @Volatile
    var queueTracks: List<PlaylistTrack> = emptyList()

    @Volatile
    var playlistTitle: String? = null

    @Volatile
    var isSpotify: Boolean = false
}

class ConversionForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var conversionJob: Job? = null
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val url = intent.getStringExtra(EXTRA_URL)
                val formatStr = intent.getStringExtra(EXTRA_FORMAT)
                val qualityStr = intent.getStringExtra(EXTRA_QUALITY)
                val title = intent.getStringExtra(EXTRA_TITLE)
                val artist = intent.getStringExtra(EXTRA_ARTIST)
                val isSpotify = intent.getBooleanExtra(EXTRA_IS_SPOTIFY, false)
                val forceSingle = intent.getBooleanExtra(EXTRA_FORCE_SINGLE, true)
                val format = AudioFormat.fromExtension(formatStr)
                val quality = AudioQuality.fromValue(qualityStr)

                if (!url.isNullOrBlank()) {
                    startSingleConversion(
                        url = url,
                        format = format,
                        quality = quality,
                        desiredTitle = title,
                        artist = artist,
                        isSpotify = isSpotify,
                        forceSingleVideo = forceSingle
                    )
                }
            }

            ACTION_START_QUEUE -> {
                val formatStr = intent.getStringExtra(EXTRA_FORMAT)
                val qualityStr = intent.getStringExtra(EXTRA_QUALITY)
                val format = AudioFormat.fromExtension(formatStr)
                val quality = AudioQuality.fromValue(qualityStr)
                val tracks = ConversionQueueHolder.queueTracks
                val playlistTitle = ConversionQueueHolder.playlistTitle
                val isSpotify = ConversionQueueHolder.isSpotify

                if (tracks.isNotEmpty()) {
                    startQueueConversion(
                        tracks = tracks,
                        playlistTitle = playlistTitle,
                        format = format,
                        quality = quality,
                        isSpotify = isSpotify
                    )
                }
            }

            ACTION_CANCEL -> {
                cancelConversion()
            }
        }
        return START_NOT_STICKY
    }

    private fun startSingleConversion(
        url: String,
        format: AudioFormat,
        quality: AudioQuality,
        desiredTitle: String?,
        artist: String?,
        isSpotify: Boolean,
        forceSingleVideo: Boolean
    ) {
        conversionJob?.cancel()

        val initialNotification = buildProgressNotification(
            title = "Converting to ${format.displayName}",
            content = "Fetching audio details...",
            progress = 0,
            isIndeterminate = true
        )

        promoteToForeground(initialNotification)

        conversionJob = serviceScope.launch {
            val processId = UUID.randomUUID().toString()
            ConversionStateHolder.currentProcessId = processId
            ConversionStateHolder.isCancelled = false

            val tempDir = File(cacheDir, "conversions").apply {
                if (!exists()) mkdirs()
            }
            tempDir.listFiles()?.forEach { it.delete() }

            try {
                ConversionStateHolder.updateState(ConversionState.FetchingInfo(url))
                updateNotification(
                    title = "Converting to ${format.displayName}",
                    content = "Fetching info from source...",
                    progress = 0,
                    isIndeterminate = true
                )

                val videoInfo = if (!isSpotify) {
                    try {
                        YoutubeDL.getInstance().getInfo(url)
                    } catch (e: Exception) {
                        null
                    }
                } else null

                if (ConversionStateHolder.isCancelled) return@launch

                val fallbackTitle = "Audio_${System.currentTimeMillis()}"
                val rawTitle: String = desiredTitle?.takeIf { it.isNotBlank() }
                    ?: videoInfo?.title?.takeIf { it.isNotBlank() }
                    ?: fallbackTitle

                val resolvedTitle: String = if (isSpotify && !artist.isNullOrBlank() && !desiredTitle.isNullOrBlank()) {
                    "$artist - $rawTitle"
                } else {
                    rawTitle
                }
                val duration = videoInfo?.duration?.toLong() ?: 0L
                val thumbnail = videoInfo?.thumbnail
                val uploader = artist ?: videoInfo?.uploader

                val metadata = VideoMetadata(
                    title = resolvedTitle,
                    durationSeconds = duration,
                    thumbnail = thumbnail,
                    uploader = uploader,
                    sourceUrl = url
                )

                ConversionStateHolder.updateState(
                    ConversionState.Downloading(
                        progress = 0f,
                        etaSeconds = 0L,
                        metadata = metadata
                    )
                )

                executeDownload(
                    url = url,
                    format = format,
                    quality = quality,
                    tempDir = tempDir,
                    processId = processId,
                    forceSingleVideo = forceSingleVideo,
                    isSpotify = isSpotify
                ) { progress, eta ->
                    if (!ConversionStateHolder.isCancelled) {
                        if (progress >= 100f) {
                            ConversionStateHolder.updateState(ConversionState.Converting(metadata))
                            updateNotification(
                                title = "Extracting Audio",
                                content = "Processing with FFmpeg...",
                                progress = 0,
                                isIndeterminate = true
                            )
                        } else {
                            ConversionStateHolder.updateState(
                                ConversionState.Downloading(
                                    progress = progress,
                                    etaSeconds = eta,
                                    metadata = metadata
                                )
                            )
                            updateNotification(
                                title = "Downloading Audio (${progress.toInt()}%)",
                                content = "${metadata.title} - ETA: ${eta}s",
                                progress = progress.toInt(),
                                isIndeterminate = false
                            )
                        }
                    }
                }

                if (ConversionStateHolder.isCancelled) return@launch

                ConversionStateHolder.updateState(ConversionState.Saving(metadata))
                updateNotification(
                    title = "Saving Audio",
                    content = "Writing to audio library...",
                    progress = 0,
                    isIndeterminate = true
                )

                val generatedFile = tempDir.listFiles()?.firstOrNull {
                    it.extension.equals(format.extension, ignoreCase = true)
                } ?: throw IllegalStateException("Audio conversion finished but output file was not found.")

                val saveResult = MediaSaver.saveAudioFile(
                    context = applicationContext,
                    tempFile = generatedFile,
                    desiredTitle = metadata.title,
                    format = format,
                    subfolder = null
                )

                val historyItem = ConversionItem(
                    id = UUID.randomUUID().toString(),
                    title = metadata.title,
                    sourceUrl = url,
                    format = format.displayName,
                    durationSeconds = metadata.durationSeconds,
                    timestamp = System.currentTimeMillis(),
                    filePath = saveResult.localFilePath,
                    contentUri = saveResult.uri?.toString(),
                    thumbnail = metadata.thumbnail,
                    fileSize = saveResult.size
                )

                ConversionHistoryManager.getInstance(applicationContext).addItem(historyItem)
                ConversionStateHolder.updateState(ConversionState.Success(historyItem))
                showCompletionNotification(historyItem, format)

            } catch (e: Exception) {
                e.printStackTrace()
                if (!ConversionStateHolder.isCancelled) {
                    val friendlyError = ErrorParser.parse(e)
                    ConversionStateHolder.updateState(ConversionState.Error(friendlyError, url))
                    showErrorNotification(friendlyError)
                }
            } finally {
                cleanupTemp(tempDir)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun startQueueConversion(
        tracks: List<PlaylistTrack>,
        playlistTitle: String?,
        format: AudioFormat,
        quality: AudioQuality,
        isSpotify: Boolean
    ) {
        conversionJob?.cancel()

        val totalCount = tracks.size
        val initialNotification = buildProgressNotification(
            title = "Playlist: ${playlistTitle ?: "Downloading"}",
            content = "Starting queue (0 of $totalCount)...",
            progress = 0,
            isIndeterminate = true
        )

        promoteToForeground(initialNotification)

        conversionJob = serviceScope.launch {
            val appSettings = AppSettings.getInstance(applicationContext)
            val skipAlreadyDownloaded = appSettings.skipAlreadyDownloaded
            val historyManager = ConversionHistoryManager.getInstance(applicationContext)

            val tempDir = File(cacheDir, "conversions").apply {
                if (!exists()) mkdirs()
            }

            var completedCount = 0
            var failedCount = 0
            val failedTitles = mutableListOf<String>()

            ConversionStateHolder.isCancelled = false

            for ((index, track) in tracks.withIndex()) {
                if (ConversionStateHolder.isCancelled) break

                val trackNumber = index + 1
                val trackTitle = track.title.ifBlank { "Track $trackNumber" }
                val artist = track.artist

                // Skip track if URL is blank
                if (track.url.isBlank()) {
                    failedCount++
                    failedTitles.add(trackTitle)
                    continue
                }

                // Check if already downloaded
                if (skipAlreadyDownloaded && isTrackAlreadyDownloaded(historyManager, track)) {
                    completedCount++
                    continue
                }

                ConversionStateHolder.updateState(
                    ConversionState.QueueRunning(
                        currentIndex = trackNumber,
                        totalCount = totalCount,
                        currentTitle = trackTitle,
                        progress = 0f,
                        etaSeconds = 0L,
                        playlistTitle = playlistTitle,
                        completedCount = completedCount,
                        failedCount = failedCount
                    )
                )

                updateNotification(
                    title = "Track $trackNumber of $totalCount: $trackTitle",
                    content = "Overall: $completedCount/$totalCount done",
                    progress = ((completedCount.toFloat() / totalCount) * 100).toInt(),
                    isIndeterminate = true
                )

                val processId = UUID.randomUUID().toString()
                ConversionStateHolder.currentProcessId = processId

                // Clear tempDir before each track
                tempDir.listFiles()?.forEach { it.delete() }

                try {
                    val resolvedTitle: String = if (isSpotify && !artist.isNullOrBlank()) {
                        "$artist - $trackTitle"
                    } else {
                        trackTitle
                    }

                    executeDownload(
                        url = track.url,
                        format = format,
                        quality = quality,
                        tempDir = tempDir,
                        processId = processId,
                        forceSingleVideo = true,
                        isSpotify = isSpotify
                    ) { progress, eta ->
                        if (!ConversionStateHolder.isCancelled) {
                            ConversionStateHolder.updateState(
                                ConversionState.QueueRunning(
                                    currentIndex = trackNumber,
                                    totalCount = totalCount,
                                    currentTitle = trackTitle,
                                    progress = progress,
                                    etaSeconds = eta,
                                    playlistTitle = playlistTitle,
                                    completedCount = completedCount,
                                    failedCount = failedCount
                                )
                            )

                            val overallPct = (((completedCount.toFloat() + (progress / 100f)) / totalCount) * 100).toInt()
                            updateNotification(
                                title = "Track $trackNumber of $totalCount: $trackTitle",
                                content = "${progress.toInt()}% • Overall: $completedCount/$totalCount done",
                                progress = overallPct.coerceIn(0, 100),
                                isIndeterminate = false
                            )
                        }
                    }

                    if (ConversionStateHolder.isCancelled) break

                    val generatedFile = tempDir.listFiles()?.firstOrNull {
                        it.extension.equals(format.extension, ignoreCase = true)
                    } ?: throw IllegalStateException("Audio file not found for track: $trackTitle")

                    val saveResult = MediaSaver.saveAudioFile(
                        context = applicationContext,
                        tempFile = generatedFile,
                        desiredTitle = resolvedTitle,
                        format = format,
                        subfolder = playlistTitle
                    )

                    val historyItem = ConversionItem(
                        id = UUID.randomUUID().toString(),
                        title = resolvedTitle,
                        sourceUrl = track.url,
                        format = format.displayName,
                        durationSeconds = track.durationSeconds,
                        timestamp = System.currentTimeMillis(),
                        filePath = saveResult.localFilePath,
                        contentUri = saveResult.uri?.toString(),
                        thumbnail = null,
                        fileSize = saveResult.size
                    )

                    historyManager.addItem(historyItem)
                    completedCount++

                } catch (e: Exception) {
                    e.printStackTrace()
                    if (ConversionStateHolder.isCancelled) break
                    failedCount++
                    failedTitles.add(trackTitle)
                } finally {
                    tempDir.listFiles()?.forEach { it.delete() }
                }
            }

            if (ConversionStateHolder.isCancelled) {
                ConversionStateHolder.updateState(ConversionState.Error("Playlist conversion cancelled by user."))
            } else {
                val folderBase = appSettings.customDownloadFolderName?.takeIf { it.isNotBlank() } ?: "Music/Mohit Music"
                val destFolder = if (!playlistTitle.isNullOrBlank()) {
                    "$folderBase/$playlistTitle"
                } else {
                    folderBase
                }

                ConversionStateHolder.updateState(
                    ConversionState.QueueCompleted(
                        completedCount = completedCount,
                        failedCount = failedCount,
                        failedTitles = failedTitles,
                        playlistTitle = playlistTitle,
                        destinationFolder = destFolder
                    )
                )

                showQueueCompletionNotification(completedCount, failedCount, playlistTitle)
            }

            cleanupTemp(tempDir)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun isTrackAlreadyDownloaded(
        historyManager: ConversionHistoryManager,
        track: PlaylistTrack
    ): Boolean {
        val list = historyManager.historyFlow.value
        return list.any { item ->
            item.sourceUrl == track.url ||
                    item.title.equals(track.title, ignoreCase = true) ||
                    (track.artist != null && item.title.equals("${track.artist} - ${track.title}", ignoreCase = true))
        }
    }

    private fun executeDownload(
        url: String,
        format: AudioFormat,
        quality: AudioQuality,
        tempDir: File,
        processId: String,
        forceSingleVideo: Boolean,
        isSpotify: Boolean,
        callback: (Float, Long) -> Unit
    ) {
        fun buildRequest(embedMetadata: Boolean, embedThumbnail: Boolean): YoutubeDLRequest {
            return YoutubeDLRequest(url).apply {
                addOption("-x")
                addOption("--audio-format", format.extension)
                addOption("--audio-quality", quality.value)
                if (forceSingleVideo) {
                    addOption("--no-playlist")
                }
                addOption("-o", "${tempDir.absolutePath}/%(title)s.%(ext)s")
                if (embedMetadata) {
                    addOption("--embed-metadata")
                }
                if (embedThumbnail) {
                    addOption("--embed-thumbnail")
                }
            }
        }

        try {
            val request = buildRequest(embedMetadata = isSpotify, embedThumbnail = isSpotify)
            YoutubeDL.getInstance().execute(request, processId) { progress, eta, _ ->
                callback(progress, eta)
            }
        } catch (e: Exception) {
            if (isSpotify && !ConversionStateHolder.isCancelled) {
                // Retry without embed-thumbnail/metadata in case FFmpeg or environment fails embedding
                val retryRequest = buildRequest(embedMetadata = false, embedThumbnail = false)
                YoutubeDL.getInstance().execute(retryRequest, processId) { progress, eta, _ ->
                    callback(progress, eta)
                }
            } else {
                throw e
            }
        }
    }

    private fun cancelConversion() {
        ConversionStateHolder.cancel()
        conversionJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun promoteToForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildProgressNotification(
        title: String,
        content: String,
        progress: Int,
        isIndeterminate: Boolean
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, ConversionForegroundService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setProgress(100, progress, isIndeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .build()
    }

    private fun updateNotification(
        title: String,
        content: String,
        progress: Int,
        isIndeterminate: Boolean
    ) {
        val notification = buildProgressNotification(title, content, progress, isIndeterminate)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompletionNotification(item: ConversionItem, format: AudioFormat) {
        val playIntent = MediaSaver.createPlayIntent(
            this,
            item.filePath,
            item.contentUri,
            format
        )
        val playPendingIntent = PendingIntent.getActivity(
            this,
            2,
            playIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Audio saved")
            .setContentText("${item.title}.${format.extension} saved")
            .setAutoCancel(true)
            .setContentIntent(playPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_COMPLETE_ID, notification)
    }

    private fun showQueueCompletionNotification(
        completedCount: Int,
        failedCount: Int,
        playlistTitle: String?
    ) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            4,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = playlistTitle ?: "Playlist download"
        val message = "$completedCount done, $failedCount failed"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_COMPLETE_ID, notification)
    }

    private fun showErrorNotification(message: String) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            3,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Conversion Failed")
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ERROR_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun cleanupTemp(tempDir: File) {
        try {
            tempDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val CHANNEL_ID = "conversion_channel"
        const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_COMPLETE_ID = 1002
        const val NOTIFICATION_ERROR_ID = 1003

        const val ACTION_START = "com.linktoaudio.app.action.START"
        const val ACTION_START_QUEUE = "com.linktoaudio.app.action.START_QUEUE"
        const val ACTION_CANCEL = "com.linktoaudio.app.action.CANCEL"

        const val EXTRA_URL = "extra_url"
        const val EXTRA_FORMAT = "extra_format"
        const val EXTRA_QUALITY = "extra_quality"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_IS_SPOTIFY = "extra_is_spotify"
        const val EXTRA_FORCE_SINGLE = "extra_force_single"

        fun start(context: Context, url: String, format: AudioFormat) {
            startSingle(context, url, format, AudioQuality.BEST)
        }

        fun startSingle(
            context: Context,
            url: String,
            format: AudioFormat,
            quality: AudioQuality,
            title: String? = null,
            artist: String? = null,
            isSpotify: Boolean = false,
            forceSingleVideo: Boolean = true
        ) {
            val intent = Intent(context, ConversionForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_FORMAT, format.extension)
                putExtra(EXTRA_QUALITY, quality.value)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
                putExtra(EXTRA_IS_SPOTIFY, isSpotify)
                putExtra(EXTRA_FORCE_SINGLE, forceSingleVideo)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startQueue(
            context: Context,
            tracks: List<PlaylistTrack>,
            playlistTitle: String?,
            format: AudioFormat,
            quality: AudioQuality,
            isSpotify: Boolean
        ) {
            ConversionQueueHolder.queueTracks = tracks
            ConversionQueueHolder.playlistTitle = playlistTitle
            ConversionQueueHolder.isSpotify = isSpotify

            val intent = Intent(context, ConversionForegroundService::class.java).apply {
                action = ACTION_START_QUEUE
                putExtra(EXTRA_FORMAT, format.extension)
                putExtra(EXTRA_QUALITY, quality.value)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancel(context: Context) {
            val intent = Intent(context, ConversionForegroundService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }
    }
}
