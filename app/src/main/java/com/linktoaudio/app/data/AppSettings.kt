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

    private val _audioQualityFlow = MutableStateFlow(
        AudioQuality.fromValue(prefs.getString(KEY_AUDIO_QUALITY, AudioQuality.BEST.value))
    )
    val audioQualityFlow: StateFlow<AudioQuality> = _audioQualityFlow.asStateFlow()

    private val _skipAlreadyDownloadedFlow =
        MutableStateFlow(prefs.getBoolean(KEY_SKIP_ALREADY_DOWNLOADED, false))
    val skipAlreadyDownloadedFlow: StateFlow<Boolean> = _skipAlreadyDownloadedFlow.asStateFlow()

    private val _spotifyClientIdFlow =
        MutableStateFlow(prefs.getString(KEY_SPOTIFY_CLIENT_ID, "") ?: "")
    val spotifyClientIdFlow: StateFlow<String> = _spotifyClientIdFlow.asStateFlow()

    private val _spotifyClientSecretFlow =
        MutableStateFlow(prefs.getString(KEY_SPOTIFY_CLIENT_SECRET, "") ?: "")
    val spotifyClientSecretFlow: StateFlow<String> = _spotifyClientSecretFlow.asStateFlow()

    private val _scanEntireMusicFolderFlow =
        MutableStateFlow(prefs.getBoolean(KEY_SCAN_ENTIRE_MUSIC_FOLDER, false))
    val scanEntireMusicFolderFlow: StateFlow<Boolean> = _scanEntireMusicFolderFlow.asStateFlow()

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

    var audioQuality: AudioQuality
        get() = AudioQuality.fromValue(
            prefs.getString(KEY_AUDIO_QUALITY, AudioQuality.BEST.value)
        )
        set(value) {
            prefs.edit().putString(KEY_AUDIO_QUALITY, value.value).apply()
            _audioQualityFlow.value = value
        }

    var skipAlreadyDownloaded: Boolean
        get() = prefs.getBoolean(KEY_SKIP_ALREADY_DOWNLOADED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SKIP_ALREADY_DOWNLOADED, value).apply()
            _skipAlreadyDownloadedFlow.value = value
        }

    var spotifyClientId: String
        get() = prefs.getString(KEY_SPOTIFY_CLIENT_ID, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_SPOTIFY_CLIENT_ID, value.trim()).apply()
            _spotifyClientIdFlow.value = value.trim()
        }

    var spotifyClientSecret: String
        get() = prefs.getString(KEY_SPOTIFY_CLIENT_SECRET, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_SPOTIFY_CLIENT_SECRET, value.trim()).apply()
            _spotifyClientSecretFlow.value = value.trim()
        }

    var scanEntireMusicFolder: Boolean
        get() = prefs.getBoolean(KEY_SCAN_ENTIRE_MUSIC_FOLDER, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SCAN_ENTIRE_MUSIC_FOLDER, value).apply()
            _scanEntireMusicFolderFlow.value = value
        }

    companion object {
        private const val PREFS_NAME = "link_to_audio_prefs"
        private const val KEY_AUTO_CONVERT_ON_SHARE = "key_auto_convert_on_share"
        private const val KEY_DEFAULT_FORMAT = "key_default_format"
        private const val KEY_AUDIO_QUALITY = "key_audio_quality"
        private const val KEY_SKIP_ALREADY_DOWNLOADED = "key_skip_already_downloaded"
        private const val KEY_SPOTIFY_CLIENT_ID = "key_spotify_client_id"
        private const val KEY_SPOTIFY_CLIENT_SECRET = "key_spotify_client_secret"
        private const val KEY_SCAN_ENTIRE_MUSIC_FOLDER = "key_scan_entire_music_folder"

        @Volatile
        private var INSTANCE: AppSettings? = null

        fun getInstance(context: Context): AppSettings {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettings(context).also { INSTANCE = it }
            }
        }
    }
}
