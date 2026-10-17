package com.linktoaudio.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class ConversionHistoryManager(private val context: Context) {
    private val gson = Gson()
    private val historyFile: File = File(context.filesDir, "conversion_history.json")
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _historyFlow = MutableStateFlow<List<ConversionItem>>(emptyList())
    val historyFlow: StateFlow<List<ConversionItem>> = _historyFlow.asStateFlow()

    init {
        scope.launch {
            loadFromDisk()
        }
    }

    private suspend fun loadFromDisk() {
        mutex.withLock {
            try {
                if (historyFile.exists()) {
                    val json = historyFile.readText()
                    val type = object : TypeToken<List<ConversionItem>>() {}.type
                    val list: List<ConversionItem>? = gson.fromJson(json, type)
                    _historyFlow.value = list ?: emptyList()
                } else {
                    _historyFlow.value = emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _historyFlow.value = emptyList()
            }
        }
    }

    private suspend fun saveToDisk(list: List<ConversionItem>) {
        mutex.withLock {
            try {
                val json = gson.toJson(list)
                historyFile.writeText(json)
                _historyFlow.value = list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun addItem(item: ConversionItem) {
        val current = _historyFlow.value.toMutableList()
        current.removeAll { it.id == item.id }
        current.add(0, item) // latest on top
        saveToDisk(current)
    }

    suspend fun deleteItem(id: String) {
        val current = _historyFlow.value.toMutableList()
        val item = current.find { it.id == id }
        if (item != null) {
            current.remove(item)
            // Attempt to remove physical file if accessible
            try {
                val file = File(item.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            saveToDisk(current)
        }
    }

    suspend fun clearHistory() {
        saveToDisk(emptyList())
    }

    companion object {
        @Volatile
        private var INSTANCE: ConversionHistoryManager? = null

        fun getInstance(context: Context): ConversionHistoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ConversionHistoryManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
