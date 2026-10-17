package com.linktoaudio.app.data

import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ConversionStateHolder {
    private val _stateFlow = MutableStateFlow<ConversionState>(ConversionState.Idle)
    val stateFlow: StateFlow<ConversionState> = _stateFlow.asStateFlow()

    @Volatile
    var currentProcessId: String? = null

    @Volatile
    var isCancelled: Boolean = false

    fun updateState(state: ConversionState) {
        _stateFlow.value = state
    }

    fun resetToIdle() {
        currentProcessId = null
        isCancelled = false
        _stateFlow.value = ConversionState.Idle
    }

    fun cancel() {
        isCancelled = true
        val pid = currentProcessId
        if (!pid.isNullOrBlank()) {
            try {
                YoutubeDL.getInstance().destroyProcessById(pid)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _stateFlow.value = ConversionState.Error("Conversion cancelled by user.")
    }
}
