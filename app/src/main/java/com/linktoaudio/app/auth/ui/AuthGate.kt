package com.linktoaudio.app.auth.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.linktoaudio.app.auth.data.UserProfile
import com.linktoaudio.app.auth.viewmodel.AuthScreen
import com.linktoaudio.app.auth.viewmodel.AuthState
import com.linktoaudio.app.auth.viewmodel.AuthViewModel
import com.linktoaudio.app.ui.theme.SpotifyGreen

@Composable
fun AuthGate(
    authViewModel: AuthViewModel,
    mainContent: @Composable (UserProfile) -> Unit
) {
    val authState by authViewModel.authState.collectAsState()
    val currentScreen by authViewModel.currentScreen.collectAsState()

    Crossfade(targetState = authState, label = "AuthGateTransition") { state ->
        when (state) {
            is AuthState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = SpotifyGreen,
                        strokeWidth = 3.dp
                    )
                }
            }

            is AuthState.Unauthenticated -> {
                Crossfade(targetState = currentScreen, label = "AuthScreenTransition") { screen ->
                    when (screen) {
                        AuthScreen.WELCOME -> WelcomeScreen(authViewModel = authViewModel)
                        AuthScreen.LOGIN -> LoginScreen(authViewModel = authViewModel)
                        AuthScreen.REGISTER -> CreateAccountScreen(authViewModel = authViewModel)
                    }
                }
            }

            is AuthState.Authenticated -> {
                mainContent(state.userProfile)
            }
        }
    }
}
