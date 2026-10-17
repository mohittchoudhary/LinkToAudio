package com.linktoaudio.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _autoConvertOnShareFlow =
        MutableStateFlow(prefs.getBoolean(KEY_AUTO_CONVERT_ON_SHARE, true))
    val autoConvertOnShareFlow: StateFlow<Boolean> = _autoConvertOnShareFlow.asStateFlow()

    private val _defaultFormatFlow = MutableStateFlow(
        AudioFormat.fromExtension(prefs.getString(KEY_DEFAULT_FORMAT, AudioFormat.MP3.extension))
    )
    val defaultFormatFlow: StateFlow<AudioFormat> = _defaultFormatFlow.asStateFlow()

    var autoConvertOnShare: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CONVERT_ON_SHARE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_CONVERT_ON_SHARE, value).apply()
            _autoConvertOnShareFlow.value = value
        }

    var defaultFormat: AudioFormat
        get() = AudioFormat.fromExtension(
            prefs.getString(KEY_DEFAULT_FORMAT, AudioFormat.MP3.extension)
        )
        set(value) {
            prefs.edit().putString(KEY_DEFAULT_FORMAT, value.extension).apply()
            _defaultFormatFlow.value = value
        }

    companion object {
        private const val PREFS_NAME = "link_to_audio_prefs"
        private const val KEY_AUTO_CONVERT_ON_SHARE = "key_auto_convert_on_share"
        private const val KEY_DEFAULT_FORMAT = "key_default_format"

        @Volatile
        private var INSTANCE: AppSettings? = null

        fun getInstance(context: Context): AppSettings {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettings(context).also { INSTANCE = it }
            }
        }
    }
}
