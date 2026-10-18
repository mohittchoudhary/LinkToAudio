package com.linktoaudio.app.utils

import com.linktoaudio.app.data.PlaylistInfo
import com.linktoaudio.app.data.PlaylistTrack
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject
import java.util.UUID

object PlaylistDetector {

    /**
     * Executes yt-dlp with --flat-playlist -J to detect if the URL is a playlist.
     * Returns PlaylistInfo if it is a playlist with entries, or null if it's a single video.
     */
    fun detectPlaylist(url: String): PlaylistInfo? {
        val request = YoutubeDLRequest(url).apply {
            addOption("--flat-playlist")
            addOption("-J")
        }

        val response = YoutubeDL.getInstance().execute(request)
        val outJson = response.out
        if (outJson.isNullOrBlank()) return null

        val json = JSONObject(outJson)
        if (json.has("entries") && !json.isNull("entries")) {
            val entriesArray = json.getJSONArray("entries")
            if (entriesArray.length() == 0) return null

            val playlistTitle = json.optString("title").ifEmpty {
                json.optString("playlist_title", "Playlist")
            }.ifEmpty { "Playlist" }

            val tracks = mutableListOf<PlaylistTrack>()
            for (i in 0 until entriesArray.length()) {
                val entry = entriesArray.optJSONObject(i) ?: continue
                val trackTitle = entry.optString("title", "Track ${i + 1}").ifEmpty { "Track ${i + 1}" }
                val uploader = entry.optString("uploader").ifEmpty {
                    entry.optString("channel", "")
                }.takeIf { it.isNotBlank() }
                val durationSec = entry.optLong("duration", 0L)

                var entryUrl = entry.optString("url")
                if (!entryUrl.startsWith("http")) {
                    val webpageUrl = entry.optString("webpage_url")
                    if (webpageUrl.startsWith("http")) {
                        entryUrl = webpageUrl
                    } else {
                        val id = entry.optString("id")
                        entryUrl = if (id.isNotBlank()) {
                            "https://www.youtube.com/watch?v=$id"
                        } else {
                            ""
                        }
                    }
                }

                if (entryUrl.isBlank()) continue

                tracks.add(
                    PlaylistTrack(
                        id = UUID.randomUUID().toString(),
                        title = trackTitle,
                        artist = uploader,
                        durationSeconds = durationSec,
                        url = entryUrl,
                        isSelected = true
                    )
                )
            }

            return PlaylistInfo(
                title = playlistTitle,
                sourceUrl = url,
                tracks = tracks,
                isSpotify = false
            )
        }

        return null
    }
}
