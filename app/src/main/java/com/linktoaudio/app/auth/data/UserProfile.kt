package com.linktoaudio.app.auth.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UserProfile(
    val uid: String,
    val name: String,
    val email: String,
    val role: String = "user",
    val createdAt: Long? = null,
    val lastLoginAt: Long? = null,
    val disabled: Boolean = false
) {
    val isAdmin: Boolean get() = role.equals("admin", ignoreCase = true)

    val formattedCreatedAt: String
        get() = formatDate(createdAt)

    val formattedLastLoginAt: String
        get() = formatDate(lastLoginAt)

    companion object {
        fun fromDocument(doc: DocumentSnapshot): UserProfile {
            val uid = doc.id
            val name = doc.getString("name").orEmpty()
            val email = doc.getString("email").orEmpty()
            val role = doc.getString("role") ?: "user"
            val disabled = doc.getBoolean("disabled") ?: false

            val createdAtLong = parseTimestamp(doc.get("createdAt"))
            val lastLoginAtLong = parseTimestamp(doc.get("lastLoginAt"))

            return UserProfile(
                uid = uid,
                name = name,
                email = email,
                role = role,
                createdAt = createdAtLong,
                lastLoginAt = lastLoginAtLong,
                disabled = disabled
            )
        }

        private fun parseTimestamp(raw: Any?): Long? {
            return when (raw) {
                is Timestamp -> raw.toDate().time
                is Date -> raw.time
                is Number -> raw.toLong()
                else -> null
            }
        }

        private fun formatDate(timestampMs: Long?): String {
            if (timestampMs == null || timestampMs <= 0L) return "Unknown"
            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            return sdf.format(Date(timestampMs))
        }
    }
}
