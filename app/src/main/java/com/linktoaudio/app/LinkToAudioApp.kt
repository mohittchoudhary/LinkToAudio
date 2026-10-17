package com.linktoaudio.app

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl.YoutubeDL
import com.yausername.youtubedl.YoutubeDLException

class LinkToAudioApp : Application() {

    override fun onCreate() {
        super.onCreate()

        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            Log.d(TAG, "YoutubeDL and FFmpeg initialized successfully.")
        } catch (e: YoutubeDLException) {
            Log.e(TAG, "Failed to initialize YoutubeDL", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during initialization", e)
        }
    }

    companion object {
        private const val TAG = "LinkToAudioApp"
    }
}
