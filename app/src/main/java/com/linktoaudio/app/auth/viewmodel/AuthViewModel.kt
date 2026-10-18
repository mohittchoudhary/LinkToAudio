package com.linktoaudio.app.auth.viewmodel

import android.app.Application
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.linktoaudio.app.auth.data.AccountDisabledException
import com.linktoaudio.app.auth.data.AuthRepository
import com.linktoaudio.app.auth.data.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.UnknownHostException

sealed interface AuthState {
    object Loading : AuthState
    object Unauthenticated : AuthState
    data class Authenticated(val userProfile: UserProfile) : AuthState
}

enum class AuthScreen {
    WELCOME,
    LOGIN,
    REGISTER
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository.getInstance()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentScreen = MutableStateFlow(AuthScreen.WELCOME)
    val currentScreen: StateFlow<AuthScreen> = _currentScreen.asStateFlow()

    private val _currentUserProfile = MutableStateFlow<UserProfile?>(null)
    val currentUserProfile: StateFlow<UserProfile?> = _currentUserProfile.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    // Admin State
    private val _adminUsers = MutableStateFlow<List<UserProfile>>(emptyList())
    val adminUsers: StateFlow<List<UserProfile>> = _adminUsers.asStateFlow()

    private val _isAdminLoading = MutableStateFlow(false)
    val isAdminLoading: StateFlow<Boolean> = _isAdminLoading.asStateFlow()

    private val _adminErrorMessage = MutableStateFlow<String?>(null)
    val adminErrorMessage: StateFlow<String?> = _adminErrorMessage.asStateFlow()

    private val _adminSuccessMessage = MutableStateFlow<String?>(null)
    val adminSuccessMessage: StateFlow<String?> = _adminSuccessMessage.asStateFlow()

    init {
        checkSession()
    }

    fun checkSession() {
        viewModelScope.launch {
            _isLoading.value = true
            val user = authRepository.currentUser
            if (user == null) {
                _currentUserProfile.value = null
                _authState.value = AuthState.Unauthenticated
                _isLoading.value = false
            } else {
                authRepository.getCurrentUserProfile().fold(
                    onSuccess = { profile ->
                        if (profile != null) {
                            _currentUserProfile.value = profile
                            _authState.value = AuthState.Authenticated(profile)
                        } else {
                            _currentUserProfile.value = null
                            _authState.value = AuthState.Unauthenticated
                        }
                        _isLoading.value = false
                    },
                    onFailure = { error ->
                        _currentUserProfile.value = null
                        _authState.value = AuthState.Unauthenticated
                        _errorMessage.value = getFriendlyErrorMessage(error)
                        _isLoading.value = false
                    }
                )
            }
        }
    }

    fun navigateTo(screen: AuthScreen) {
        _errorMessage.value = null
        _successMessage.value = null
        _currentScreen.value = screen
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
        _adminErrorMessage.value = null
        _adminSuccessMessage.value = null
    }

    fun signUp(name: String, email: String, password: String, confirmPassword: String) {
        val trimmedName = name.trim()
        val trimmedEmail = email.trim()

        if (trimmedName.isBlank()) {
            _errorMessage.value = "Please enter your name."
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            _errorMessage.value = "Please enter a valid email address."
            return
        }

        if (password.length < 8) {
            _errorMessage.value = "Password must be at least 8 characters long."
            return
        }

        if (password != confirmPassword) {
            _errorMessage.value = "Passwords do not match."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            authRepository.signUp(trimmedName, trimmedEmail, password).fold(
                onSuccess = { profile ->
                    _currentUserProfile.value = profile
                    _authState.value = AuthState.Authenticated(profile)
                    _isLoading.value = false
                },
                onFailure = { error ->
                    _errorMessage.value = getFriendlyErrorMessage(error)
                    _isLoading.value = false
                }
            )
        }
    }

    fun signIn(email: String, password: String) {
        val trimmedEmail = email.trim()

        if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            _errorMessage.value = "Please enter a valid email address."
            return
        }

        if (password.isBlank()) {
            _errorMessage.value = "Please enter your password."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            authRepository.signIn(trimmedEmail, password).fold(
                onSuccess = { profile ->
                    _currentUserProfile.value = profile
                    _authState.value = AuthState.Authenticated(profile)
                    _isLoading.value = false
                },
                onFailure = { error ->
                    _errorMessage.value = getFriendlyErrorMessage(error)
                    _isLoading.value = false
                }
            )
        }
    }

    fun sendPasswordReset(email: String) {
        val trimmedEmail = email.trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            _errorMessage.value = "Please enter a valid email address."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            authRepository.sendPasswordResetEmail(trimmedEmail).fold(
                onSuccess = {
                    _successMessage.value = "Password reset email sent to $trimmedEmail."
                    _isLoading.value = false
                },
                onFailure = { error ->
                    _errorMessage.value = getFriendlyErrorMessage(error)
                    _isLoading.value = false
                }
            )
        }
    }

    fun signOut() {
        authRepository.signOut()
        _currentUserProfile.value = null
        _authState.value = AuthState.Unauthenticated
        _currentScreen.value = AuthScreen.WELCOME
    }

    fun deleteAccount(password: String, onSuccess: () -> Unit = {}) {
        if (password.isBlank()) {
            _errorMessage.value = "Please enter your password to confirm account deletion."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            authRepository.deleteAccount(password).fold(
                onSuccess = {
                    _currentUserProfile.value = null
                    _authState.value = AuthState.Unauthenticated
                    _currentScreen.value = AuthScreen.WELCOME
                    _isLoading.value = false
                    onSuccess()
                },
                onFailure = { error ->
                    _errorMessage.value = getFriendlyErrorMessage(error)
                    _isLoading.value = false
                }
            )
        }
    }

    // Admin Panel Actions
    fun loadAdminData(onNotAdmin: () -> Unit = {}) {
        val currentUid = authRepository.currentUid
        if (currentUid == null) {
            onNotAdmin()
            return
        }

        viewModelScope.launch {
            _isAdminLoading.value = true
            _adminErrorMessage.value = null

            // Security check: re-verify role directly from Firestore document
            val isAdmin = authRepository.checkIsAdmin(currentUid)
            if (!isAdmin) {
                _isAdminLoading.value = false
                onNotAdmin()
                return@launch
            }

            authRepository.getAllUsers().fold(
                onSuccess = { users ->
                    _adminUsers.value = users
                    _isAdminLoading.value = false
                },
                onFailure = { error ->
                    _adminErrorMessage.value = "Failed to load users: ${getFriendlyErrorMessage(error)}"
                    _isAdminLoading.value = false
                }
            )
        }
    }

    fun adminToggleUserDisabled(targetUser: UserProfile) {
        val currentUid = authRepository.currentUid
        if (targetUser.uid == currentUid) {
            _adminErrorMessage.value = "You cannot disable your own admin account."
            return
        }

        viewModelScope.launch {
            _isAdminLoading.value = true
            _adminErrorMessage.value = null
            val newDisabled = !targetUser.disabled

            authRepository.updateUserDisabled(targetUser.uid, newDisabled).fold(
                onSuccess = {
                    _adminSuccessMessage.value = if (newDisabled) {
                        "Account for ${targetUser.name} disabled."
                    } else {
                        "Account for ${targetUser.name} enabled."
                    }
                    loadAdminData()
                },
                onFailure = { error ->
                    _adminErrorMessage.value = getFriendlyErrorMessage(error)
                    _isAdminLoading.value = false
                }
            )
        }
    }

    fun adminToggleUserRole(targetUser: UserProfile) {
        val currentUid = authRepository.currentUid
        if (targetUser.uid == currentUid) {
            _adminErrorMessage.value = "You cannot modify your own admin role."
            return
        }

        viewModelScope.launch {
            _isAdminLoading.value = true
            _adminErrorMessage.value = null
            val newRole = if (targetUser.isAdmin) "user" else "admin"

            authRepository.updateUserRole(targetUser.uid, newRole).fold(
                onSuccess = {
                    _adminSuccessMessage.value = "Role updated for ${targetUser.name} to $newRole."
                    loadAdminData()
                },
                onFailure = { error ->
                    _adminErrorMessage.value = getFriendlyErrorMessage(error)
                    _isAdminLoading.value = false
                }
            )
        }
    }

    fun adminDeleteUserData(targetUser: UserProfile) {
        val currentUid = authRepository.currentUid
        if (targetUser.uid == currentUid) {
            _adminErrorMessage.value = "You cannot delete your own account from the admin panel."
            return
        }

        viewModelScope.launch {
            _isAdminLoading.value = true
            _adminErrorMessage.value = null

            authRepository.deleteUserData(targetUser.uid).fold(
                onSuccess = {
                    _adminSuccessMessage.value = "User document for ${targetUser.name} deleted."
                    loadAdminData()
                },
                onFailure = { error ->
                    _adminErrorMessage.value = getFriendlyErrorMessage(error)
                    _isAdminLoading.value = false
                }
            )
        }
    }

    fun adminSendPasswordReset(targetUser: UserProfile) {
        viewModelScope.launch {
            _isAdminLoading.value = true
            _adminErrorMessage.value = null

            authRepository.sendPasswordResetEmail(targetUser.email).fold(
                onSuccess = {
                    _adminSuccessMessage.value = "Password reset email sent to ${targetUser.email}."
                    _isAdminLoading.value = false
                },
                onFailure = { error ->
                    _adminErrorMessage.value = getFriendlyErrorMessage(error)
                    _isAdminLoading.value = false
                }
            )
        }
    }

    private fun getFriendlyErrorMessage(error: Throwable): String {
        return when (error) {
            is FirebaseAuthUserCollisionException -> "An account with this email already exists."
            is FirebaseAuthWeakPasswordException -> "Password is too weak. Please choose a stronger password (at least 8 characters)."
            is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password."
            is FirebaseAuthInvalidUserException -> "No account found with this email, or it has been disabled."
            is FirebaseNetworkException, is UnknownHostException -> "No internet connection. Please check your network and try again."
            is AccountDisabledException -> error.message ?: "This account has been disabled."
            else -> error.localizedMessage?.takeIf { it.isNotBlank() } ?: "An unexpected error occurred. Please try again."
        }
    }
}
