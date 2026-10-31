package com.linktoaudio.app.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.linktoaudio.app.MainActivity
import com.linktoaudio.app.data.AppSettings

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private val pauseTimeoutHandler = Handler(Looper.getMainLooper())
    private val pauseTimeoutRunnable = Runnable {
        val player = mediaSession?.player
        if (player == null || !player.isPlaying) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Ensure public lockscreen-visible notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                PLAYBACK_CHANNEL_ID,
                "Mohit Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls and information"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }

        try {
            setMediaNotificationProvider(
                DefaultMediaNotificationProvider.Builder(this)
                    .setChannelId(PLAYBACK_CHANNEL_ID)
                    .build()
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        exoPlayer.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(sessionId: Int) {
                audioSessionId = sessionId
                updateLoudnessEnhancer(sessionId)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    pauseTimeoutHandler.removeCallbacks(pauseTimeoutRunnable)
                } else {
                    pauseTimeoutHandler.removeCallbacks(pauseTimeoutRunnable)
                    // When paused for more than 5 minutes, stop foreground and notification
                    pauseTimeoutHandler.postDelayed(pauseTimeoutRunnable, PAUSE_TIMEOUT_MS)
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady) {
                    pauseTimeoutHandler.removeCallbacks(pauseTimeoutRunnable)
                } else {
                    pauseTimeoutHandler.removeCallbacks(pauseTimeoutRunnable)
                    pauseTimeoutHandler.postDelayed(pauseTimeoutRunnable, PAUSE_TIMEOUT_MS)
                }
            }
        })

        instance = this
        audioSessionId = exoPlayer.audioSessionId
        currentVolumeBoost = AppSettings.getInstance(this).volumeBoostPercent

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivity)
            .build()

        updateLoudnessEnhancer(audioSessionId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    /**
     * Playback must STOP when the user swipes the app away from recent apps.
     * We pause and release the player, cancel the notification, and call stopSelf().
     * Independent downloads (in ConversionForegroundService) will continue on their own.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseTimeoutHandler.removeCallbacks(pauseTimeoutRunnable)
        try {
            loudnessEnhancer?.release()
        } catch (ignored: Exception) {}
        loudnessEnhancer = null

        mediaSession?.player?.run {
            pause()
            release()
        }
        mediaSession?.release()
        mediaSession = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        pauseTimeoutHandler.removeCallbacks(pauseTimeoutRunnable)
        try {
            loudnessEnhancer?.release()
        } catch (ignored: Exception) {}
        loudnessEnhancer = null
        audioSessionId = 0
        instance = null

        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private fun updateLoudnessEnhancer(sessionId: Int) {
        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = null
            if (sessionId > 0) {
                loudnessEnhancer = LoudnessEnhancer(sessionId)
            }
            applyVolumeBoost(currentVolumeBoost)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun applyVolumeBoost(boost: Int) {
        val player = mediaSession?.player ?: return
        if (boost <= 100) {
            player.volume = boost / 100f
            try {
                loudnessEnhancer?.enabled = false
                loudnessEnhancer?.setTargetGain(0)
            } catch (ignored: Exception) {}
        } else {
            player.volume = 1.0f
            try {
                val enhancer = loudnessEnhancer
                if (enhancer != null) {
                    // Safe cap around 1000 mB. 200% = 600 mB target gain.
                    val gainMb = ((boost - 100) * 6).coerceIn(0, 1000)
                    enhancer.setTargetGain(gainMb)
                    enhancer.enabled = true
                }
            } catch (ignored: Exception) {}
        }
    }

    companion object {
        const val PLAYBACK_CHANNEL_ID = "playback_channel"
        private const val PAUSE_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes

        @Volatile
        var audioSessionId: Int = 0
            private set

        @Volatile
        private var currentVolumeBoost: Int = 100

        @Volatile
        var instance: PlaybackService? = null
            private set

        fun setVolumeBoost(boostPercent: Int) {
            currentVolumeBoost = boostPercent.coerceIn(0, 200)
            instance?.applyVolumeBoost(currentVolumeBoost)
        }

        fun getVolumeBoost(): Int = currentVolumeBoost
    }
}
