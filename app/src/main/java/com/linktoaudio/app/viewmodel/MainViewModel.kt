package com.linktoaudio.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.linktoaudio.app.data.AppSettings
import com.linktoaudio.app.data.AudioFormat
import com.linktoaudio.app.data.ConversionHistoryManager
import com.linktoaudio.app.data.ConversionState
import com.linktoaudio.app.data.ConversionStateHolder
import com.linktoaudio.app.service.ConversionForegroundService
import com.linktoaudio.app.utils.ErrorParser
import com.linktoaudio.app.utils.UrlExtractor
import com.yausername.youtubedl.YoutubeDL
import com.yausername.youtubedl.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appSettings = AppSettings.getInstance(application)
    private val historyManager = ConversionHistoryManager.getInstance(application)

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _selectedFormat = MutableStateFlow(appSettings.defaultFormat)
    val selectedFormat: StateFlow<AudioFormat> = _selectedFormat.asStateFlow()

    val conversionState: StateFlow<ConversionState> = ConversionStateHolder.stateFlow
    val historyList = historyManager.historyFlow
    val autoConvertOnShare = appSettings.autoConvertOnShareFlow

    private val _isUpdatingEngine = MutableStateFlow(false)
    val isUpdatingEngine: StateFlow<Boolean> = _isUpdatingEngine.asStateFlow()

    private val _engineUpdateMessage = MutableStateFlow<String?>(null)
    val engineUpdateMessage: StateFlow<String?> = _engineUpdateMessage.asStateFlow()

    private val _engineVersion = MutableStateFlow<String?>("Checking...")
    val engineVersion: StateFlow<String?> = _engineVersion.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        refreshEngineVersion()
    }

    fun onUrlChanged(newUrl: String) {
        _urlInput.value = newUrl
    }

    fun onFormatSelected(format: AudioFormat) {
        _selectedFormat.value = format
        appSettings.defaultFormat = format
    }

    fun setAutoConvertOnShare(enabled: Boolean) {
        appSettings.autoConvertOnShare = enabled
    }

    fun onPaste(text: String?) {
        if (!text.isNullOrBlank()) {
            val supported = UrlExtractor.extractFirstSupportedUrl(text)
            if (supported != null) {
                _urlInput.value = supported
            } else {
                _urlInput.value = text.trim()
                _snackbarMessage.value = "Pasted text doesn't look like a valid YouTube or Instagram link."
            }
        } else {
            _snackbarMessage.value = "Clipboard is empty."
        }
    }

    fun handleSharedContent(sharedText: String?) {
        if (sharedText.isNullOrBlank()) {
            _snackbarMessage.value = "No content received from share."
            return
        }

        val extractedUrl = UrlExtractor.extractFirstSupportedUrl(sharedText)
        if (extractedUrl != null) {
            _urlInput.value = extractedUrl
            if (appSettings.autoConvertOnShare) {
                startConversion(extractedUrl)
            } else {
                _snackbarMessage.value = "Link ready to convert: $extractedUrl"
            }
        } else {
            val anyUrl = UrlExtractor.extractFirstAnyUrl(sharedText)
            if (anyUrl != null) {
                _snackbarMessage.value = "Unsupported link. Only YouTube and Instagram links are supported."
            } else {
                _snackbarMessage.value = "No valid link found in shared text."
            }
        }
    }

    fun startConversion(customUrl: String? = null) {
        val targetUrl = (customUrl ?: _urlInput.value).trim()

        if (targetUrl.isBlank()) {
            _snackbarMessage.value = "Please enter or paste a video link."
            return
        }

        if (!UrlExtractor.isSupportedUrl(targetUrl)) {
            _snackbarMessage.value = "Please provide a valid YouTube or Instagram link."
            return
        }

        ConversionForegroundService.start(
            context = getApplication(),
            url = targetUrl,
            format = _selectedFormat.value
        )
    }

    fun cancelConversion() {
        ConversionForegroundService.cancel(getApplication())
    }

    fun resetConversion() {
        ConversionStateHolder.resetToIdle()
    }

    fun deleteHistoryItem(id: String) {
        viewModelScope.launch {
            historyManager.deleteItem(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyManager.clearHistory()
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun refreshEngineVersion() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val req = YoutubeDLRequest().apply {
                    addOption("--version")
                }
                val response = YoutubeDL.getInstance().execute(req)
                val ver = response.out?.trim() ?: "Unknown"
                _engineVersion.value = ver
            } catch (e: Exception) {
                _engineVersion.value = "Ready"
            }
        }
    }

    fun updateEngine() {
        if (_isUpdatingEngine.value) return
        _isUpdatingEngine.value = true
        _engineUpdateMessage.value = "Downloading latest yt-dlp release..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val status = YoutubeDL.getInstance().updateYoutubeDL(getApplication())
                withContext(Dispatchers.Main) {
                    _engineUpdateMessage.value = "Engine updated successfully: $status"
                    _snackbarMessage.value = "Engine updated: $status"
                    refreshEngineVersion()
                }
            } catch (e: Exception) {
                val friendly = ErrorParser.parse(e)
                withContext(Dispatchers.Main) {
                    _engineUpdateMessage.value = "Update failed: $friendly"
                    _snackbarMessage.value = "Engine update failed: $friendly"
                }
            } finally {
                _isUpdatingEngine.value = false
            }
        }
    }
}
