package com.linktoaudio.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.linktoaudio.app.data.AppSettings
import com.linktoaudio.app.service.ConversionForegroundService
import com.linktoaudio.app.utils.UrlExtractor

class ShareReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: intent?.dataString

        if (!sharedText.isNullOrBlank()) {
            val url = UrlExtractor.extractFirstSupportedUrl(sharedText)
            if (url != null) {
                val appSettings = AppSettings.getInstance(this)
                if (appSettings.autoConvertOnShare) {
                    ConversionForegroundService.startSingle(
                        context = this,
                        url = url,
                        format = appSettings.defaultFormat,
                        quality = appSettings.audioQuality
                    )
                    Toast.makeText(this, "Mohit Music: Converting audio...", Toast.LENGTH_SHORT).show()
                } else {
                    val mainIntent = Intent(this, MainActivity::class.java).apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, sharedText)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(mainIntent)
                }
            } else {
                Toast.makeText(this, "No valid audio link found.", Toast.LENGTH_SHORT).show()
            }
        }

        finish()
    }
}
