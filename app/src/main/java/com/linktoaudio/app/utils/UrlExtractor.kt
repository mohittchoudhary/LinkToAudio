package com.linktoaudio.app.utils

import java.net.URI
import java.util.regex.Pattern

object UrlExtractor {

    // Regex to match URLs starting with http:// or https://
    private val URL_PATTERN: Pattern = Pattern.compile(
        "https?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]"
    )

    /**
     * Extracts the first valid HTTP/HTTPS URL from raw text.
     * Returns null if no valid URL is found.
     */
    fun extractFirstSupportedUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null

        val matcher = URL_PATTERN.matcher(text)
        while (matcher.find()) {
            var candidate = matcher.group() ?: continue
            // Strip any trailing punctuation that may be part of surrounding sentence
            candidate = candidate.trimEnd('.', ',', ';', '!', '?', ')', ']', '"', '\'')

            if (isSupportedUrl(candidate)) {
                return candidate
            }
        }
        return null
    }

    /**
     * Extracts the first URL regardless of domain (used for friendly error reporting).
     */
    fun extractFirstAnyUrl(text: String?): String? {
        return extractFirstSupportedUrl(text)
    }

    /**
     * Validates whether a URL is a valid HTTP or HTTPS web link.
     */
    fun isSupportedUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val uri = URI(url.trim())
            val scheme = uri.scheme?.lowercase()
            (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if a URL is a Spotify link (open.spotify.com, spotify.link, or spotify.app.link).
     */
    fun isSpotifyUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val host = URI(url.trim()).host?.lowercase() ?: return false
            host == "open.spotify.com" ||
                    host.endsWith(".spotify.com") ||
                    host == "spotify.link" ||
                    host.endsWith(".spotify.link") ||
                    host == "spotify.app.link" ||
                    host.endsWith(".spotify.app.link")
        } catch (e: Exception) {
            url.contains("spotify.com") || url.contains("spotify.link")
        }
    }

    /**
     * Checks if a URL is a single video that also carries a playlist parameter (e.g. YouTube watch?v=...&list=...).
     */
    fun hasVideoAndList(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        val hasListParam = lower.contains("list=")
        val hasVideoParam = (lower.contains("v=") || lower.contains("youtu.be/")) && !lower.contains("youtube.com/playlist")
        return hasListParam && hasVideoParam
    }
}
