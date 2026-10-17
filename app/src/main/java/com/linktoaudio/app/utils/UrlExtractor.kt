package com.linktoaudio.app.utils

import java.net.URI
import java.util.regex.Pattern

object UrlExtractor {

    // Regex to match URLs starting with http:// or https://
    private val URL_PATTERN: Pattern = Pattern.compile(
        "https?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]"
    )

    /**
     * Extracts the first valid URL from raw text (which may contain extra words/emojis).
     * Returns null if no URL is found or if the URL does not belong to YouTube or Instagram.
     */
    fun extractFirstSupportedUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null

        val matcher = URL_PATTERN.matcher(text)
        while (matcher.find()) {
            var candidate = matcher.group() ?: continue
            // Strip any trailing punctuation that may be part of surrounding sentence
            candidate = candidate.trimEnd('.', ',', ';', '!', '?', ')', ']')

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
        if (text.isNullOrBlank()) return null
        val matcher = URL_PATTERN.matcher(text)
        if (matcher.find()) {
            val candidate = matcher.group()
            return candidate?.trimEnd('.', ',', ';', '!', '?', ')', ']')
        }
        return null
    }

    /**
     * Validates whether a URL is from YouTube or Instagram.
     */
    fun isSupportedUrl(url: String): Boolean {
        return try {
            val host = URI(url).host?.lowercase() ?: return false
            isYouTubeHost(host) || isInstagramHost(host)
        } catch (e: Exception) {
            false
        }
    }

    private fun isYouTubeHost(host: String): Boolean {
        return host == "youtube.com" ||
                host.endsWith(".youtube.com") ||
                host == "youtu.be"
    }

    private fun isInstagramHost(host: String): Boolean {
        return host == "instagram.com" ||
                host.endsWith(".instagram.com") ||
                host == "instagr.am"
    }
}
