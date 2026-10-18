package com.linktoaudio.app.utils

object ErrorParser {

    fun parse(throwable: Throwable?): String {
        val rawMessage = throwable?.message ?: ""
        return parse(rawMessage)
    }

    fun parse(rawMessage: String): String {
        val lower = rawMessage.lowercase()

        return when {
            lower.contains("unable to resolve host") ||
                    lower.contains("no address associated") ||
                    lower.contains("network is unreachable") ||
                    lower.contains("timed out") ||
                    lower.contains("connection refused") ||
                    lower.contains("sockettimeoutexception") ||
                    lower.contains("failed to connect") -> {
                "No internet connection or network timed out. Please check your connection."
            }

            lower.contains("login required") ||
                    lower.contains("rate-limit reached or login required") ||
                    lower.contains("private account") ||
                    lower.contains("please log in") ||
                    lower.contains("checkpoint_required") ||
                    (lower.contains("instagram") && (lower.contains("login") || lower.contains("private"))) -> {
                "This Instagram post is private or requires an account login to view."
            }

            lower.contains("confirm your age") ||
                    lower.contains("age-restricted") ||
                    lower.contains("sign in to confirm your age") -> {
                "This video is age-restricted and requires account verification."
            }

            lower.contains("not available in your country") ||
                    lower.contains("geo-blocked") ||
                    lower.contains("geographic restriction") ||
                    lower.contains("country") && lower.contains("available") -> {
                "This video is geo-blocked and not available in your region."
            }

            lower.contains("video unavailable") ||
                    lower.contains("this video has been removed") ||
                    lower.contains("private video") -> {
                "This video is unavailable, deleted, or marked as private."
            }

            lower.contains("unsupported url") ||
                    lower.contains("is not a valid url") ||
                    lower.contains("incomplete youtube id") -> {
                "Unsupported link. yt-dlp cannot extract audio from this URL. Please check the link."
            }

            lower.contains("cancelled") || lower.contains("interrupted") -> {
                "Conversion was cancelled."
            }

            else -> {
                // If it's a specific short line, use it; otherwise generic friendly message
                val firstSignificantLine = rawMessage.lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.startsWith("ERROR:") || it.isNotEmpty() }
                    ?.removePrefix("ERROR:")
                    ?.trim()

                if (!firstSignificantLine.isNullOrBlank() && firstSignificantLine.length < 150) {
                    firstSignificantLine
                } else {
                    "Unable to convert this video. Please check the link or try updating the engine in Settings."
                }
            }
        }
    }
}
