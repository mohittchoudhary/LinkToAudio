package com.linktoaudio.app.utils

import android.util.Base64
import com.linktoaudio.app.data.PlaylistInfo
import com.linktoaudio.app.data.PlaylistTrack
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object SpotifyHelper {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val SPOTIFY_LINK_REGEX = Pattern.compile(
        "(?:open\\.spotify\\.com/(?:intl-[a-z]{2}/)?|spotify:)(track|album|playlist)[/:]([a-zA-Z0-9]+)"
    )

    private val NEXT_DATA_REGEX = Pattern.compile(
        "<script[^>]*id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>",
        Pattern.DOTALL
    )

    data class SpotifyParsedUrl(
        val type: String,
        val id: String
    )

    sealed class SpotifyResult {
        data class SingleTrack(
            val title: String,
            val artist: String,
            val durationSeconds: Long,
            val thumbnail: String?,
            val searchQuery: String
        ) : SpotifyResult()

        data class Collection(
            val playlistInfo: PlaylistInfo
        ) : SpotifyResult()
    }

    fun resolveRedirectIfNeeded(url: String): String {
        if (url.contains("spotify.link") || url.contains("spotify.app.link")) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    )
                    .build()
                client.newCall(req).execute().use { response ->
                    return response.request.url.toString()
                }
            } catch (e: Exception) {
                // Return original url if redirect resolution fails
            }
        }
        return url
    }

    fun parseUrl(rawUrl: String): SpotifyParsedUrl? {
        val resolved = resolveRedirectIfNeeded(rawUrl)
        val matcher = SPOTIFY_LINK_REGEX.matcher(resolved)
        if (matcher.find()) {
            val type = matcher.group(1) ?: return null
            val id = matcher.group(2) ?: return null
            return SpotifyParsedUrl(type, id)
        }
        return null
    }

    fun fetchSpotifyData(
        url: String,
        clientId: String?,
        clientSecret: String?
    ): SpotifyResult {
        val parsed = parseUrl(url) ?: throw IllegalArgumentException("Not a recognized Spotify link.")
        return try {
            fetchViaEmbed(parsed.type, parsed.id, url)
        } catch (embedError: Exception) {
            if (!clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()) {
                try {
                    fetchViaWebApi(clientId, clientSecret, parsed.type, parsed.id, url)
                } catch (apiError: Exception) {
                    throw IOException(
                        "Spotify embed failed, and Web API fallback also failed: ${apiError.message}",
                        apiError
                    )
                }
            } else {
                throw IOException(
                    "Could not load Spotify tracks from public embed page. You can set Spotify Client ID & Secret in Settings as a fallback (free from developer.spotify.com).",
                    embedError
                )
            }
        }
    }

    private fun fetchViaEmbed(type: String, id: String, originalUrl: String): SpotifyResult {
        val embedUrl = "https://open.spotify.com/embed/$type/$id"
        val request = Request.Builder()
            .url(embedUrl)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            .build()

        val html = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to fetch Spotify embed page: HTTP ${response.code}")
            }
            response.body?.string() ?: throw IOException("Empty response from Spotify embed page")
        }

        val matcher = NEXT_DATA_REGEX.matcher(html)
        if (!matcher.find()) {
            throw IllegalStateException("Spotify embed page data could not be parsed.")
        }

        val scriptJson = matcher.group(1) ?: throw IllegalStateException("Spotify embed page data is empty.")
        val rootJson = JSONObject(scriptJson)
        val entity = rootJson.getJSONObject("props")
            .getJSONObject("pageProps")
            .getJSONObject("state")
            .getJSONObject("data")
            .getJSONObject("entity")

        val entityType = entity.optString("type", type)
        if (entityType.equals("track", ignoreCase = true)) {
            val trackName = entity.optString("name").ifEmpty { entity.optString("title", "Track") }
            val artistsArray = entity.optJSONArray("artists")
            val artistNames = mutableListOf<String>()
            if (artistsArray != null) {
                for (i in 0 until artistsArray.length()) {
                    val artObj = artistsArray.optJSONObject(i)
                    val aName = artObj?.optString("name")
                    if (!aName.isNullOrBlank()) artistNames.add(aName)
                }
            }
            val artist = if (artistNames.isNotEmpty()) {
                artistNames.joinToString(", ")
            } else {
                entity.optString("subtitle", "Unknown Artist")
            }

            val durationMs = entity.optLong("duration", 0L)
            val durationSec = durationMs / 1000L
            val thumbnail = extractImage(entity)
            val query = "ytsearch1:$artist - $trackName audio"

            return SpotifyResult.SingleTrack(
                title = trackName,
                artist = artist,
                durationSeconds = durationSec,
                thumbnail = thumbnail,
                searchQuery = query
            )
        } else {
            // Album or Playlist
            val collectionTitle = entity.optString("name").ifEmpty {
                entity.optString("title", "Spotify Collection")
            }
            val trackListArray = entity.optJSONArray("trackList") ?: JSONArray()
            val tracks = mutableListOf<PlaylistTrack>()

            for (i in 0 until trackListArray.length()) {
                val trackObj = trackListArray.optJSONObject(i) ?: continue
                val tTitle = trackObj.optString("title", "Track ${i + 1}")
                val tArtist = trackObj.optString("subtitle", "")
                val tDurationMs = trackObj.optLong("duration", 0L)
                val tDurationSec = tDurationMs / 1000L
                val searchQuery = if (tArtist.isNotBlank()) {
                    "ytsearch1:$tArtist - $tTitle audio"
                } else {
                    "ytsearch1:$tTitle audio"
                }

                tracks.add(
                    PlaylistTrack(
                        id = UUID.randomUUID().toString(),
                        title = tTitle,
                        artist = tArtist.ifBlank { null },
                        durationSeconds = tDurationSec,
                        url = searchQuery,
                        isSelected = true
                    )
                )
            }

            return SpotifyResult.Collection(
                PlaylistInfo(
                    title = collectionTitle,
                    sourceUrl = originalUrl,
                    tracks = tracks,
                    isSpotify = true
                )
            )
        }
    }

    private fun fetchViaWebApi(
        clientId: String,
        clientSecret: String,
        type: String,
        id: String,
        originalUrl: String
    ): SpotifyResult {
        // 1. Client credentials token request
        val authHeader = "Basic " + Base64.encodeToString(
            "$clientId:$clientSecret".toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
        val tokenBody = FormBody.Builder()
            .add("grant_type", "client_credentials")
            .build()
        val tokenRequest = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .header("Authorization", authHeader)
            .post(tokenBody)
            .build()

        val tokenJsonStr = client.newCall(tokenRequest).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("Spotify API auth failed (HTTP ${resp.code}). Check Client ID & Secret.")
            }
            resp.body?.string() ?: throw IOException("Empty auth response from Spotify API")
        }
        val accessToken = JSONObject(tokenJsonStr).getString("access_token")

        // 2. Fetch endpoint
        val endpoint = when (type.lowercase()) {
            "track" -> "https://api.spotify.com/v1/tracks/$id"
            "album" -> "https://api.spotify.com/v1/albums/$id"
            else -> "https://api.spotify.com/v1/playlists/$id"
        }
        val apiRequest = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $accessToken")
            .build()

        val apiJsonStr = client.newCall(apiRequest).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("Spotify Web API request failed (HTTP ${resp.code})")
            }
            resp.body?.string() ?: throw IOException("Empty response from Spotify Web API")
        }
        val apiJson = JSONObject(apiJsonStr)

        if (type.equals("track", ignoreCase = true)) {
            val name = apiJson.optString("name", "Track")
            val artistsArr = apiJson.optJSONArray("artists")
            val artistsList = mutableListOf<String>()
            if (artistsArr != null) {
                for (i in 0 until artistsArr.length()) {
                    artistsList.add(artistsArr.getJSONObject(i).optString("name"))
                }
            }
            val artist = artistsList.joinToString(", ").ifBlank { "Unknown Artist" }
            val durationMs = apiJson.optLong("duration_ms", 0L)
            val albumObj = apiJson.optJSONObject("album")
            val imagesArr = albumObj?.optJSONArray("images")
            val thumbnail = imagesArr?.optJSONObject(0)?.optString("url")
            val query = "ytsearch1:$artist - $name audio"

            return SpotifyResult.SingleTrack(
                title = name,
                artist = artist,
                durationSeconds = durationMs / 1000L,
                thumbnail = thumbnail,
                searchQuery = query
            )
        } else if (type.equals("album", ignoreCase = true)) {
            val albumName = apiJson.optString("name", "Album")
            val tracksObj = apiJson.optJSONObject("tracks")
            val itemsArr = tracksObj?.optJSONArray("items") ?: JSONArray()
            val tracks = mutableListOf<PlaylistTrack>()

            for (i in 0 until itemsArr.length()) {
                val item = itemsArr.getJSONObject(i)
                val tName = item.optString("name", "Track ${i + 1}")
                val tArtistsArr = item.optJSONArray("artists")
                val tArtistsList = mutableListOf<String>()
                if (tArtistsArr != null) {
                    for (j in 0 until tArtistsArr.length()) {
                        tArtistsList.add(tArtistsArr.getJSONObject(j).optString("name"))
                    }
                }
                val tArtist = tArtistsList.joinToString(", ")
                val tDurationMs = item.optLong("duration_ms", 0L)
                val query = if (tArtist.isNotBlank()) {
                    "ytsearch1:$tArtist - $tName audio"
                } else {
                    "ytsearch1:$tName audio"
                }

                tracks.add(
                    PlaylistTrack(
                        id = UUID.randomUUID().toString(),
                        title = tName,
                        artist = tArtist.ifBlank { null },
                        durationSeconds = tDurationMs / 1000L,
                        url = query,
                        isSelected = true
                    )
                )
            }

            return SpotifyResult.Collection(
                PlaylistInfo(
                    title = albumName,
                    sourceUrl = originalUrl,
                    tracks = tracks,
                    isSpotify = true
                )
            )
        } else {
            // Playlist
            val playlistName = apiJson.optString("name", "Playlist")
            val tracksObj = apiJson.optJSONObject("tracks")
            val itemsArr = tracksObj?.optJSONArray("items") ?: JSONArray()
            val tracks = mutableListOf<PlaylistTrack>()

            for (i in 0 until itemsArr.length()) {
                val itemWrapper = itemsArr.getJSONObject(i)
                val trackObj = itemWrapper.optJSONObject("track") ?: continue
                val tName = trackObj.optString("name", "Track ${i + 1}")
                val tArtistsArr = trackObj.optJSONArray("artists")
                val tArtistsList = mutableListOf<String>()
                if (tArtistsArr != null) {
                    for (j in 0 until tArtistsArr.length()) {
                        tArtistsList.add(tArtistsArr.getJSONObject(j).optString("name"))
                    }
                }
                val tArtist = tArtistsList.joinToString(", ")
                val tDurationMs = trackObj.optLong("duration_ms", 0L)
                val query = if (tArtist.isNotBlank()) {
                    "ytsearch1:$tArtist - $tName audio"
                } else {
                    "ytsearch1:$tName audio"
                }

                tracks.add(
                    PlaylistTrack(
                        id = UUID.randomUUID().toString(),
                        title = tName,
                        artist = tArtist.ifBlank { null },
                        durationSeconds = tDurationMs / 1000L,
                        url = query,
                        isSelected = true
                    )
                )
            }

            return SpotifyResult.Collection(
                PlaylistInfo(
                    title = playlistName,
                    sourceUrl = originalUrl,
                    tracks = tracks,
                    isSpotify = true
                )
            )
        }
    }

    private fun extractImage(entity: JSONObject): String? {
        val visual = entity.optJSONObject("visualIdentity") ?: return null
        val images = visual.optJSONArray("image") ?: return null
        if (images.length() > 0) {
            val lastImg = images.optJSONObject(images.length() - 1)
            val url = lastImg?.optString("url")
            if (!url.isNullOrBlank()) return url
            val firstImg = images.optJSONObject(0)
            return firstImg?.optString("url")
        }
        return null
    }
}
