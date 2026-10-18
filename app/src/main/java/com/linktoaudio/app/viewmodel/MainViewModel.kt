package com.linktoaudio.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.linktoaudio.app.data.AppSettings
import com.linktoaudio.app.data.AudioFormat
import com.linktoaudio.app.data.AudioQuality
import com.linktoaudio.app.data.ConversionHistoryManager
import com.linktoaudio.app.data.ConversionState
import com.linktoaudio.app.data.ConversionStateHolder
import com.linktoaudio.app.data.PlaylistInfo
import com.linktoaudio.app.service.ConversionForegroundService
import com.linktoaudio.app.utils.ErrorParser
import com.linktoaudio.app.utils.NetworkUtils
import com.linktoaudio.app.utils.PlaylistDetector
import com.linktoaudio.app.utils.SpotifyHelper
import com.linktoaudio.app.utils.UrlExtractor
import com.yausername.youtubedl_android.YoutubeDL
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

    private val _selectedAudioQuality = MutableStateFlow(appSettings.audioQuality)
    val selectedAudioQuality: StateFlow<AudioQuality> = _selectedAudioQuality.asStateFlow()

    val conversionState: StateFlow<ConversionState> = ConversionStateHolder.stateFlow
    val historyList = historyManager.historyFlow
    val autoConvertOnShare = appSettings.autoConvertOnShareFlow
    val skipAlreadyDownloaded = appSettings.skipAlreadyDownloadedFlow
    val spotifyClientId = appSettings.spotifyClientIdFlow
    val spotifyClientSecret = appSettings.spotifyClientSecretFlow

    private val _playlistPreview = MutableStateFlow<PlaylistInfo?>(null)
    val playlistPreview: StateFlow<PlaylistInfo?> = _playlistPreview.asStateFlow()

    private val _pendingChooserUrl = MutableStateFlow<String?>(null)
    val pendingChooserUrl: StateFlow<String?> = _pendingChooserUrl.asStateFlow()

    private val _isLoadingInfo = MutableStateFlow(false)
    val isLoadingInfo: StateFlow<Boolean> = _isLoadingInfo.asStateFlow()

    private val _loadingMessage = MutableStateFlow<String?>("Checking link...")
    val loadingMessage: StateFlow<String?> = _loadingMessage.asStateFlow()

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

    fun onAudioQualitySelected(quality: AudioQuality) {
        _selectedAudioQuality.value = quality
        appSettings.audioQuality = quality
    }

    fun setAutoConvertOnShare(enabled: Boolean) {
        appSettings.autoConvertOnShare = enabled
    }

    fun setSkipAlreadyDownloaded(enabled: Boolean) {
        appSettings.skipAlreadyDownloaded = enabled
    }

    fun setSpotifyCredentials(clientId: String, clientSecret: String) {
        appSettings.spotifyClientId = clientId
        appSettings.spotifyClientSecret = clientSecret
        _snackbarMessage.value = "Spotify settings saved."
    }

    fun onPaste(text: String?) {
        if (!text.isNullOrBlank()) {
            val supported = UrlExtractor.extractFirstSupportedUrl(text)
            if (supported != null) {
                _urlInput.value = supported
            } else {
                _urlInput.value = text.trim()
                _snackbarMessage.value = "Pasted text does not contain a valid web link."
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
                startConversion(customUrl = extractedUrl, fromShare = true)
            } else {
                _snackbarMessage.value = "Link ready to convert: $extractedUrl"
            }
        } else {
            _snackbarMessage.value = "No valid link found in shared text."
        }
    }

    fun startConversion(
        customUrl: String? = null,
        fromShare: Boolean = false,
        forceSingleVideo: Boolean = false
    ) {
        val targetUrl = (customUrl ?: _urlInput.value).trim()

        if (targetUrl.isBlank()) {
            _snackbarMessage.value = "Please enter or paste a link."
            return
        }

        if (!UrlExtractor.isSupportedUrl(targetUrl)) {
            _snackbarMessage.value = "Please provide a valid web link (http/https)."
            return
        }

        if (!NetworkUtils.isNetworkAvailable(getApplication())) {
            _snackbarMessage.value = "No internet connection. Please check your network."
            return
        }

        // Case 1: Video with list parameter
        if (!forceSingleVideo && UrlExtractor.hasVideoAndList(targetUrl)) {
            _pendingChooserUrl.value = targetUrl
            return
        }

        // Case 2: Spotify link
        if (UrlExtractor.isSpotifyUrl(targetUrl)) {
            processSpotifyLink(targetUrl, fromShare)
            return
        }

        // Case 3: Other URLs (check for playlist unless forced single)
        if (forceSingleVideo) {
            ConversionForegroundService.startSingle(
                context = getApplication(),
                url = targetUrl,
                format = _selectedFormat.value,
                quality = _selectedAudioQuality.value,
                forceSingleVideo = true
            )
        } else {
            processGenericLink(targetUrl, fromShare)
        }
    }

    private fun processSpotifyLink(url: String, fromShare: Boolean) {
        _isLoadingInfo.value = true
        _loadingMessage.value = "Analyzing Spotify link..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = SpotifyHelper.fetchSpotifyData(
                    url = url,
                    clientId = appSettings.spotifyClientId,
                    clientSecret = appSettings.spotifyClientSecret
                )

                withContext(Dispatchers.Main) {
                    _isLoadingInfo.value = false
                    when (result) {
                        is SpotifyHelper.SpotifyResult.SingleTrack -> {
                            ConversionForegroundService.startSingle(
                                context = getApplication(),
                                url = result.searchQuery,
                                format = _selectedFormat.value,
                                quality = _selectedAudioQuality.value,
                                title = result.title,
                                artist = result.artist,
                                isSpotify = true,
                                forceSingleVideo = true
                            )
                        }

                        is SpotifyHelper.SpotifyResult.Collection -> {
                            if (fromShare && appSettings.autoConvertOnShare) {
                                ConversionForegroundService.startQueue(
                                    context = getApplication(),
                                    tracks = result.playlistInfo.tracks,
                                    playlistTitle = result.playlistInfo.title,
                                    format = _selectedFormat.value,
                                    quality = _selectedAudioQuality.value,
                                    isSpotify = true
                                )
                            } else {
                                _playlistPreview.value = result.playlistInfo
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val friendly = ErrorParser.parse(e)
                withContext(Dispatchers.Main) {
                    _isLoadingInfo.value = false
                    _snackbarMessage.value = friendly
                    ConversionStateHolder.updateState(ConversionState.Error(friendly, url))
                }
            }
        }
    }

    private fun processGenericLink(url: String, fromShare: Boolean) {
        _isLoadingInfo.value = true
        _loadingMessage.value = "Checking link for playlist..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val playlistInfo = PlaylistDetector.detectPlaylist(url)

                withContext(Dispatchers.Main) {
                    _isLoadingInfo.value = false
                    if (playlistInfo != null && playlistInfo.tracks.isNotEmpty()) {
                        if (fromShare && appSettings.autoConvertOnShare) {
                            ConversionForegroundService.startQueue(
                                context = getApplication(),
                                tracks = playlistInfo.tracks,
                                playlistTitle = playlistInfo.title,
                                format = _selectedFormat.value,
                                quality = _selectedAudioQuality.value,
                                isSpotify = false
                            )
                        } else {
                            _playlistPreview.value = playlistInfo
                        }
                    } else {
                        // Single item
                        ConversionForegroundService.startSingle(
                            context = getApplication(),
                            url = url,
                            format = _selectedFormat.value,
                            quality = _selectedAudioQuality.value,
                            forceSingleVideo = false
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val friendly = ErrorParser.parse(e)
                withContext(Dispatchers.Main) {
                    _isLoadingInfo.value = false
                    _snackbarMessage.value = friendly
                    ConversionStateHolder.updateState(ConversionState.Error(friendly, url))
                }
            }
        }
    }

    fun onChooserChoice(justThisVideo: Boolean) {
        val url = _pendingChooserUrl.value ?: return
        _pendingChooserUrl.value = null
        if (justThisVideo) {
            startConversion(customUrl = url, forceSingleVideo = true)
        } else {
            processGenericLink(url, fromShare = false)
        }
    }

    fun dismissChooser() {
        _pendingChooserUrl.value = null
    }

    fun onTrackSelectionToggled(trackId: String) {
        val current = _playlistPreview.value ?: return
        val updatedTracks = current.tracks.map {
            if (it.id == trackId) it.copy(isSelected = !it.isSelected) else it
        }
        _playlistPreview.value = current.copy(tracks = updatedTracks)
    }

    fun selectAllTracks(selectAll: Boolean) {
        val current = _playlistPreview.value ?: return
        val updatedTracks = current.tracks.map { it.copy(isSelected = selectAll) }
        _playlistPreview.value = current.copy(tracks = updatedTracks)
    }

    fun convertSelectedPlaylist() {
        val current = _playlistPreview.value ?: return
        val selectedTracks = current.tracks.filter { it.isSelected }

        if (selectedTracks.isEmpty()) {
            _snackbarMessage.value = "Please select at least one track to convert."
            return
        }

        ConversionForegroundService.startQueue(
            context = getApplication(),
            tracks = selectedTracks,
            playlistTitle = current.title,
            format = _selectedFormat.value,
            quality = _selectedAudioQuality.value,
            isSpotify = current.isSpotify
        )

        _playlistPreview.value = null
    }

    fun clearPlaylistPreview() {
        _playlistPreview.value = null
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
                val ver = YoutubeDL.getInstance().version(getApplication()) ?: "Ready"
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
