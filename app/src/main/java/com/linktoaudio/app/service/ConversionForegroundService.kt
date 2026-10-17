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
import com.linktoaudio.app.data.AudioFormat
import com.linktoaudio.app.data.ConversionHistoryManager
import com.linktoaudio.app.data.ConversionItem
import com.linktoaudio.app.data.ConversionState
import com.linktoaudio.app.data.ConversionStateHolder
import com.linktoaudio.app.data.VideoMetadata
import com.linktoaudio.app.utils.ErrorParser
import com.linktoaudio.app.utils.MediaSaver
import com.yausername.youtubedl.YoutubeDL
import com.yausername.youtubedl.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

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
                val format = AudioFormat.fromExtension(formatStr)
                if (!url.isNullOrBlank()) {
                    startConversion(url, format)
                }
            }
            ACTION_CANCEL -> {
                cancelConversion()
            }
        }
        return START_NOT_STICKY
    }

    private fun startConversion(url: String, format: AudioFormat) {
        conversionJob?.cancel()

        // Start foreground immediately
        val initialNotification = buildProgressNotification(
            title = "Converting to ${format.displayName}",
            content = "Fetching video details...",
            progress = 0,
            isIndeterminate = true
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        conversionJob = serviceScope.launch {
            val processId = UUID.randomUUID().toString()
            ConversionStateHolder.currentProcessId = processId
            ConversionStateHolder.isCancelled = false

            val tempDir = File(cacheDir, "conversions").apply {
                if (!exists()) mkdirs()
            }
            // Clear prior temporary files in conversion cache
            tempDir.listFiles()?.forEach { it.delete() }

            try {
                // Stage 1: Fetch Video Info
                ConversionStateHolder.updateState(ConversionState.FetchingInfo(url))
                updateNotification(
                    title = "Converting to ${format.displayName}",
                    content = "Fetching info from source...",
                    progress = 0,
                    isIndeterminate = true
                )

                val videoInfo = try {
                    YoutubeDL.getInstance().getInfo(url)
                } catch (e: Exception) {
                    null
                }

                if (ConversionStateHolder.isCancelled) return@launch

                val videoTitle = videoInfo?.title ?: "Audio_${System.currentTimeMillis()}"
                val duration = videoInfo?.duration?.toLong() ?: 0L
                val thumbnail = videoInfo?.thumbnail
                val uploader = videoInfo?.uploader

                val metadata = VideoMetadata(
                    title = videoTitle,
                    durationSeconds = duration,
                    thumbnail = thumbnail,
                    uploader = uploader,
                    sourceUrl = url
                )

                // Stage 2: Download & Extract Audio using yt-dlp + ffmpeg
                ConversionStateHolder.updateState(
                    ConversionState.Downloading(
                        progress = 0f,
                        etaSeconds = 0L,
                        metadata = metadata
                    )
                )

                val request = YoutubeDLRequest(url).apply {
                    addOption("-x")
                    addOption("--audio-format", format.extension)
                    addOption("--audio-quality", "0")
                    addOption("--no-playlist")
                    addOption("-o", "${tempDir.absolutePath}/%(title)s.%(ext)s")
                }

                YoutubeDL.getInstance().execute(request, { progress, etaInSeconds ->
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
                                    etaSeconds = etaInSeconds,
                                    metadata = metadata
                                )
                            )
                            updateNotification(
                                title = "Downloading Audio (${progress.toInt()}%)",
                                content = "${metadata.title} - ETA: ${etaInSeconds}s",
                                progress = progress.toInt(),
                                isIndeterminate = false
                            )
                        }
                    }
                }, processId)

                if (ConversionStateHolder.isCancelled) return@launch

                // Stage 3: Save to MediaStore (Music/LinkToAudio)
                ConversionStateHolder.updateState(ConversionState.Saving(metadata))
                updateNotification(
                    title = "Saving Audio",
                    content = "Writing to Music/LinkToAudio...",
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
                    format = format
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

                // Save to persistent history
                ConversionHistoryManager.getInstance(applicationContext).addItem(historyItem)

                // Update UI state
                ConversionStateHolder.updateState(ConversionState.Success(historyItem))

                // Post completion notification
                showCompletionNotification(historyItem, format)

            } catch (e: Exception) {
                e.printStackTrace()
                if (!ConversionStateHolder.isCancelled) {
                    val friendlyError = ErrorParser.parse(e)
                    ConversionStateHolder.updateState(ConversionState.Error(friendlyError, url))
                    showErrorNotification(friendlyError)
                }
            } finally {
                // Clean temp directory
                try {
                    tempDir.listFiles()?.forEach { it.delete() }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun cancelConversion() {
        ConversionStateHolder.cancel()
        conversionJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
            .setContentText("${item.title}.${format.extension} saved to Music/LinkToAudio")
            .setAutoCancel(true)
            .setContentIntent(playPendingIntent)
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
        const val ACTION_CANCEL = "com.linktoaudio.app.action.CANCEL"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_FORMAT = "extra_format"

        fun start(context: Context, url: String, format: AudioFormat) {
            val intent = Intent(context, ConversionForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_FORMAT, format.extension)
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
