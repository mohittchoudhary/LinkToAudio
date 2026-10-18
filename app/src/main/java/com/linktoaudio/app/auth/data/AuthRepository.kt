package com.linktoaudio.app.auth.data

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AccountDisabledException(message: String = "This account has been disabled") : Exception(message)

class AuthRepository private constructor() {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val currentUid: String?
        get() = auth.currentUser?.uid

    suspend fun getCurrentUserProfile(): Result<UserProfile?> = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext Result.success(null)
        try {
            val doc = firestore.collection(USERS_COLLECTION).document(user.uid).get().await()
            if (!doc.exists()) {
                // Initialise document if missing
                val newProfile = UserProfile(
                    uid = user.uid,
                    name = user.displayName.orEmpty().ifBlank { user.email?.substringBefore("@") ?: "User" },
                    email = user.email.orEmpty(),
                    role = "user",
                    createdAt = System.currentTimeMillis(),
                    lastLoginAt = System.currentTimeMillis(),
                    disabled = false
                )
                val userMap = hashMapOf<String, Any>(
                    "name" to newProfile.name,
                    "email" to newProfile.email,
                    "role" to newProfile.role,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "lastLoginAt" to FieldValue.serverTimestamp(),
                    "disabled" to false
                )
                firestore.collection(USERS_COLLECTION).document(user.uid).set(userMap, SetOptions.merge()).await()
                return@withContext Result.success(newProfile)
            }

            val profile = UserProfile.fromDocument(doc)
            if (profile.disabled) {
                auth.signOut()
                return@withContext Result.failure(AccountDisabledException("This account has been disabled"))
            }

            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signUp(name: String, email: String, password: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val trimmedName = name.trim()
            val trimmedEmail = email.trim()

            // Passwords are sent directly and ONLY to Firebase Auth which securely hashes them
            val authResult = auth.createUserWithEmailAndPassword(trimmedEmail, password).await()
            val user = authResult.user ?: throw Exception("Failed to create user account.")

            // Update user profile display name
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(trimmedName)
                .build()
            user.updateProfile(profileUpdates).await()

            // Create Firestore user document (no password ever stored)
            val userMap = hashMapOf<String, Any>(
                "name" to trimmedName,
                "email" to trimmedEmail,
                "role" to "user",
                "createdAt" to FieldValue.serverTimestamp(),
                "lastLoginAt" to FieldValue.serverTimestamp(),
                "disabled" to false
            )
            firestore.collection(USERS_COLLECTION).document(user.uid).set(userMap).await()

            val profile = UserProfile(
                uid = user.uid,
                name = trimmedName,
                email = trimmedEmail,
                role = "user",
                createdAt = System.currentTimeMillis(),
                lastLoginAt = System.currentTimeMillis(),
                disabled = false
            )
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signIn(email: String, password: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val trimmedEmail = email.trim()
            val authResult = auth.signInWithEmailAndPassword(trimmedEmail, password).await()
            val user = authResult.user ?: throw Exception("Failed to sign in.")

            val doc = firestore.collection(USERS_COLLECTION).document(user.uid).get().await()
            if (doc.exists()) {
                val profile = UserProfile.fromDocument(doc)
                if (profile.disabled) {
                    auth.signOut()
                    return@withContext Result.failure(AccountDisabledException("This account has been disabled"))
                }

                // Update lastLoginAt server timestamp
                firestore.collection(USERS_COLLECTION).document(user.uid)
                    .update("lastLoginAt", FieldValue.serverTimestamp()).await()

                Result.success(profile.copy(lastLoginAt = System.currentTimeMillis()))
            } else {
                val name = user.displayName.orEmpty().ifBlank { trimmedEmail.substringBefore("@") }
                val newProfile = UserProfile(
                    uid = user.uid,
                    name = name,
                    email = trimmedEmail,
                    role = "user",
                    createdAt = System.currentTimeMillis(),
                    lastLoginAt = System.currentTimeMillis(),
                    disabled = false
                )
                val userMap = hashMapOf<String, Any>(
                    "name" to name,
                    "email" to trimmedEmail,
                    "role" to "user",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "lastLoginAt" to FieldValue.serverTimestamp(),
                    "disabled" to false
                )
                firestore.collection(USERS_COLLECTION).document(user.uid).set(userMap).await()
                Result.success(newProfile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            auth.sendPasswordResetEmail(email.trim()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAccount(password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = auth.currentUser ?: throw Exception("No authenticated user.")
            val userEmail = user.email.orEmpty()
            if (userEmail.isBlank()) throw Exception("User email is not available.")

            // Re-authenticate user before critical delete
            val credential = EmailAuthProvider.getCredential(userEmail, password)
            user.reauthenticate(credential).await()

            // Delete Firestore user document
            firestore.collection(USERS_COLLECTION).document(user.uid).delete().await()

            // Delete Firebase Auth account
            user.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        auth.signOut()
    }

    // Admin Operations
    suspend fun checkIsAdmin(uid: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val doc = firestore.collection(USERS_COLLECTION).document(uid).get().await()
            doc.getString("role")?.equals("admin", ignoreCase = true) == true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getAllUsers(): Result<List<UserProfile>> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection(USERS_COLLECTION).get().await()
            val users = snapshot.documents.map { UserProfile.fromDocument(it) }
                .sortedByDescending { it.createdAt ?: 0L }
            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserDisabled(uid: String, disabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection(USERS_COLLECTION).document(uid)
                .update("disabled", disabled).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserRole(uid: String, role: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection(USERS_COLLECTION).document(uid)
                .update("role", role).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteUserData(uid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection(USERS_COLLECTION).document(uid).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val USERS_COLLECTION = "users"

        @Volatile
        private var INSTANCE: AuthRepository? = null

        fun getInstance(): AuthRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthRepository().also { INSTANCE = it }
            }
        }
    }
}
