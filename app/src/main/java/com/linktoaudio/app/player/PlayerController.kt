package com.linktoaudio.app.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.io.File
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.linktoaudio.app.data.AppSettings
import com.linktoaudio.app.player.data.MusicStorageManager
import com.linktoaudio.app.player.data.SongArtworkLoader
import com.linktoaudio.app.player.model.PlayerRepeatMode
import com.linktoaudio.app.player.model.Song
import com.linktoaudio.app.player.service.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerController private constructor(private val context: Context) {

    private val storageManager = MusicStorageManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.Main)

    private var mediaController: MediaController? = null

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentQueueIndex = MutableStateFlow(0)
    val currentQueueIndex: StateFlow<Int> = _currentQueueIndex.asStateFlow()

    private val _repeatMode = MutableStateFlow(PlayerRepeatMode.OFF)
    val repeatMode: StateFlow<PlayerRepeatMode> = _repeatMode.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _sleepTimerRemainingSec = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingSec: StateFlow<Long?> = _sleepTimerRemainingSec.asStateFlow()

    private val _sleepTimerLabel = MutableStateFlow<String?>(null)
    val sleepTimerLabel: StateFlow<String?> = _sleepTimerLabel.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var sleepAtEndOfTrack: Boolean = false
    private var progressTickerJob: Job? = null
    private var lastRecordedPlaySongId: Long? = null
    private var currentSongPlayTimeMs: Long = 0L

    init {
        initializeMediaController()
    }

    private fun initializeMediaController() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                mediaController = controller
                setupPlayerListener(controller)
                syncFromController(controller)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun setupPlayerListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val mediaId = mediaItem?.mediaId?.toLongOrNull()
                val targetSong = _queue.value.firstOrNull { it.id == mediaId }
                    ?: mediaItem?.let { mapMediaItemToSong(it) }

                _currentSong.value = targetSong
                _currentQueueIndex.value = controller.currentMediaItemIndex

                // Handle sleep timer end of track
                if (sleepAtEndOfTrack && reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                    sleepAtEndOfTrack = false
                    _sleepTimerLabel.value = null
                    _sleepTimerRemainingSec.value = null
                    pause()
                    return
                }

                currentSongPlayTimeMs = 0L
                lastRecordedPlaySongId = null

                if (targetSong != null) {
                    _durationMs.value = targetSong.durationMs
                    scope.launch {
                        storageManager.saveLastPlayed(targetSong.id, 0L)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startProgressTicker()
                } else {
                    stopProgressTicker()
                    saveCurrentPosition()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    _currentSong.value?.let { song ->
                        scope.launch { storageManager.recordSongPlay(song.id) }
                    }
                    if (sleepAtEndOfTrack) {
                        sleepAtEndOfTrack = false
                        _sleepTimerLabel.value = null
                        _sleepTimerRemainingSec.value = null
                        pause()
                    }
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> PlayerRepeatMode.ONE
                    Player.REPEAT_MODE_ALL -> PlayerRepeatMode.ALL
                    else -> PlayerRepeatMode.OFF
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _isShuffleEnabled.value = shuffleModeEnabled
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _playbackSpeed.value = playbackParameters.speed
            }

            override fun onPlayerError(error: PlaybackException) {
                val failedSong = _currentSong.value
                if (failedSong != null) {
                    val currentList = _queue.value
                    val failedIndex = currentList.indexOfFirst { it.id == failedSong.id }
                    if (failedIndex >= 0) {
                        removeFromQueue(failedIndex)
                    }
                    Toast.makeText(
                        context,
                        "Skipping missing or unplayable track: ${failedSong.title}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
    }

    private fun syncFromController(controller: MediaController) {
        _isPlaying.value = controller.isPlaying
        _isShuffleEnabled.value = controller.shuffleModeEnabled
        _repeatMode.value = when (controller.repeatMode) {
            Player.REPEAT_MODE_ONE -> PlayerRepeatMode.ONE
            Player.REPEAT_MODE_ALL -> PlayerRepeatMode.ALL
            else -> PlayerRepeatMode.OFF
        }
        _playbackSpeed.value = controller.playbackParameters.speed
        _currentPositionMs.value = controller.currentPosition.coerceAtLeast(0L)
        _durationMs.value = controller.duration.coerceAtLeast(0L)

        val currentItem = controller.currentMediaItem
        if (currentItem != null) {
            val mediaId = currentItem.mediaId.toLongOrNull()
            _currentSong.value = _queue.value.firstOrNull { it.id == mediaId }
                ?: mapMediaItemToSong(currentItem)
            _currentQueueIndex.value = controller.currentMediaItemIndex
        }

        if (controller.isPlaying) {
            startProgressTicker()
        }
    }

    private fun mapMediaItemToSong(mediaItem: MediaItem): Song {
        val meta = mediaItem.mediaMetadata
        return Song(
            id = mediaItem.mediaId.toLongOrNull() ?: 0L,
            title = meta.title?.toString() ?: "Unknown Title",
            artist = meta.artist?.toString() ?: "Unknown Artist",
            album = meta.albumTitle?.toString() ?: "Unknown Album",
            durationMs = 0L,
            contentUri = mediaItem.requestMetadata.mediaUri?.toString() ?: "",
            albumArtUri = meta.artworkUri?.toString()
        )
    }

    private fun startProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = scope.launch {
            while (isActive && _isPlaying.value) {
                val controller = mediaController
                if (controller != null) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = controller.duration.coerceAtLeast(0L)
                    _currentPositionMs.value = pos
                    if (dur > 0) {
                        _durationMs.value = dur
                    }

                    currentSongPlayTimeMs += 500
                    // Count song as played if played for more than 20 seconds
                    val song = _currentSong.value
                    if (song != null && currentSongPlayTimeMs >= 20000 && lastRecordedPlaySongId != song.id) {
                        lastRecordedPlaySongId = song.id
                        storageManager.recordSongPlay(song.id)
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = null
    }

    private fun saveCurrentPosition() {
        val song = _currentSong.value
        val pos = _currentPositionMs.value
        if (song != null) {
            scope.launch {
                storageManager.saveLastPlayed(song.id, pos)
            }
        }
    }

    fun playSong(song: Song, newQueue: List<Song> = listOf(song)) {
        val controller = mediaController ?: return

        // Skip missing files and remove from queue
        if (song.filePath.isNotBlank() && !File(song.filePath).exists()) {
            val validQueue = newQueue.filter { it.id != song.id }
            _queue.value = validQueue
            Toast.makeText(
                context,
                "File not found on device: ${song.title}",
                Toast.LENGTH_SHORT
            ).show()
            if (validQueue.isNotEmpty()) {
                playSong(validQueue.first(), validQueue)
            }
            return
        }

        val startIndex = newQueue.indexOfFirst { it.id == song.id }.let { if (it >= 0) it else 0 }

        _queue.value = newQueue
        _currentSong.value = song
        _currentQueueIndex.value = startIndex
        _durationMs.value = song.durationMs
        _currentPositionMs.value = 0L

        // Auto-apply default sleep timer if set and not already active
        val defaultTimer = AppSettings.getInstance(context).defaultSleepTimerMinutes
        if (_sleepTimerRemainingSec.value == null && !sleepAtEndOfTrack && defaultTimer != 0) {
            if (defaultTimer == -1) {
                startSleepTimerEndOfTheTrack()
            } else if (defaultTimer > 0) {
                startSleepTimer(defaultTimer)
            }
        }

        val mediaItems = newQueue.map { it.toMediaItem() }
        controller.stop()
        controller.clearMediaItems()
        controller.setMediaItems(mediaItems, startIndex, 0L)
        controller.prepare()
        controller.play()
    }

    fun getAudioSessionId(): Int {
        return PlaybackService.audioSessionId
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val validIndex = startIndex.coerceIn(0, songs.size - 1)
        playSong(songs[validIndex], songs)
    }

    fun playNext(song: Song) {
        val controller = mediaController ?: return
        val currentQueue = _queue.value.toMutableList()
        val insertIndex = (_currentQueueIndex.value + 1).coerceAtMost(currentQueue.size)
        currentQueue.add(insertIndex, song)
        _queue.value = currentQueue

        controller.addMediaItem(insertIndex, song.toMediaItem())
    }

    fun addToQueue(song: Song) {
        val controller = mediaController ?: return
        val currentQueue = _queue.value.toMutableList()
        currentQueue.add(song)
        _queue.value = currentQueue

        controller.addMediaItem(song.toMediaItem())
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE || controller.mediaItemCount == 0) {
                val song = _currentSong.value
                val queueList = _queue.value
                if (song != null && queueList.isNotEmpty()) {
                    playSong(song, queueList)
                    return
                }
            }
            controller.play()
        }
    }

    fun pause() {
        mediaController?.pause()
    }

    fun resume() {
        mediaController?.play()
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        val target = positionMs.coerceIn(0L, _durationMs.value.coerceAtLeast(0L))
        _currentPositionMs.value = target
        controller.seekTo(target)
    }

    fun skipToNext() {
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
        }
    }

    fun skipToPrevious() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3000) {
            controller.seekTo(0L)
        } else if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
        } else {
            controller.seekTo(0L)
        }
    }

    fun cycleRepeatMode() {
        val nextMode = when (_repeatMode.value) {
            PlayerRepeatMode.OFF -> PlayerRepeatMode.ALL
            PlayerRepeatMode.ALL -> PlayerRepeatMode.ONE
            PlayerRepeatMode.ONE -> PlayerRepeatMode.OFF
        }
        setRepeatMode(nextMode)
    }

    fun setRepeatMode(mode: PlayerRepeatMode) {
        val controller = mediaController ?: return
        _repeatMode.value = mode
        controller.repeatMode = when (mode) {
            PlayerRepeatMode.OFF -> Player.REPEAT_MODE_OFF
            PlayerRepeatMode.ALL -> Player.REPEAT_MODE_ALL
            PlayerRepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        val newState = !_isShuffleEnabled.value
        _isShuffleEnabled.value = newState
        controller.shuffleModeEnabled = newState
    }

    fun setPlaybackSpeed(speed: Float) {
        val controller = mediaController ?: return
        _playbackSpeed.value = speed
        controller.setPlaybackSpeed(speed)
    }

    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        sleepAtEndOfTrack = false
        _sleepTimerLabel.value = "${minutes}m"
        val totalSeconds = minutes * 60L
        _sleepTimerRemainingSec.value = totalSeconds

        sleepTimerJob = scope.launch {
            var remaining = totalSeconds
            while (isActive && remaining > 0) {
                delay(1000)
                remaining -= 1
                _sleepTimerRemainingSec.value = remaining
            }
            if (isActive && remaining <= 0) {
                pause()
                _sleepTimerLabel.value = null
                _sleepTimerRemainingSec.value = null
            }
        }
    }

    fun startSleepTimerEndOfTheTrack() {
        cancelSleepTimer()
        sleepAtEndOfTrack = true
        _sleepTimerLabel.value = "End of track"
        _sleepTimerRemainingSec.value = null
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepAtEndOfTrack = false
        _sleepTimerLabel.value = null
        _sleepTimerRemainingSec.value = null
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val controller = mediaController ?: return
        val currentQueue = _queue.value.toMutableList()
        if (fromIndex in currentQueue.indices && toIndex in currentQueue.indices && fromIndex != toIndex) {
            val moved = currentQueue.removeAt(fromIndex)
            currentQueue.add(toIndex, moved)
            _queue.value = currentQueue

            controller.moveMediaItem(fromIndex, toIndex)
            _currentQueueIndex.value = controller.currentMediaItemIndex
        }
    }

    fun removeFromQueue(index: Int) {
        val controller = mediaController ?: return
        val currentQueue = _queue.value.toMutableList()
        if (index in currentQueue.indices) {
            currentQueue.removeAt(index)
            _queue.value = currentQueue
            controller.removeMediaItem(index)
            _currentQueueIndex.value = controller.currentMediaItemIndex
        }
    }

    fun resumeLastPlayed(allSongs: List<Song>) {
        if (allSongs.isEmpty() || _currentSong.value != null) return

        val lastSongId = storageManager.lastPlayedSongIdFlow.value ?: return
        val lastPos = storageManager.lastPlayedPositionFlow.value
        val matchedSong = allSongs.firstOrNull { it.id == lastSongId } ?: return

        _currentSong.value = matchedSong
        _currentPositionMs.value = lastPos
        _durationMs.value = matchedSong.durationMs
        _queue.value = listOf(matchedSong)

        val controller = mediaController ?: return
        val item = matchedSong.toMediaItem()
        controller.setMediaItem(item, lastPos)
        controller.prepare()
        // Do not auto-play, leave paused for user to resume
    }

    private fun Song.toMediaItem(): MediaItem {
        val artBytes = SongArtworkLoader.getEmbeddedOrCachedBytes(context, this)
        val artUri = SongArtworkLoader.getArtworkUri(context, this)

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .apply {
                if (artBytes != null) {
                    setArtworkData(artBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                } else if (artUri != null) {
                    setArtworkUri(artUri)
                }
            }
            .build()

        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(Uri.parse(contentUri))
            .setMediaMetadata(metadata)
            .build()
    }

    companion object {
        @Volatile
        private var INSTANCE: PlayerController? = null

        fun getInstance(context: Context): PlayerController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlayerController(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
