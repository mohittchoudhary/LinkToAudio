package com.linktoaudio.app

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.linktoaudio.app.player.model.Song
import com.linktoaudio.app.ui.components.MiniPlayer
import com.linktoaudio.app.ui.screens.HomeScreen
import com.linktoaudio.app.ui.screens.LibraryScreen
import com.linktoaudio.app.ui.screens.NowPlayingScreen
import com.linktoaudio.app.ui.screens.SearchScreen
import com.linktoaudio.app.ui.screens.SettingsScreen
import com.linktoaudio.app.ui.theme.LinkToAudioTheme
import com.linktoaudio.app.viewmodel.MainViewModel
import com.linktoaudio.app.viewmodel.MusicViewModel
import kotlinx.coroutines.launch

enum class NavTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    LIBRARY("Library", Icons.Default.LibraryMusic),
    SEARCH("Search", Icons.Default.Search),
    SETTINGS("Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val musicViewModel: MusicViewModel by viewModels()
    private var pendingDeleteSong: Song? = null

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        musicViewModel.onPermissionResult(granted)
        requestNotificationPermissionIfNeeded()
    }

    private val mediaStoreDeleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        pendingDeleteSong?.let { song ->
            musicViewModel.onMediaStoreDeleteResult(song, result.resultCode == RESULT_OK)
        }
        pendingDeleteSong = null
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission result handled automatically by system notification manager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestAudioPermissionIfNeeded()
        handleIncomingIntent(intent)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                musicViewModel.deleteRequests.collect { request ->
                    pendingDeleteSong = request.song
                    try {
                        mediaStoreDeleteLauncher.launch(
                            IntentSenderRequest.Builder(request.intentSender).build()
                        )
                    } catch (e: Exception) {
                        pendingDeleteSong = null
                        musicViewModel.onMediaStoreDeleteResult(request.song, false)
                    }
                }
            }
        }

        setContent {
            LinkToAudioTheme {
                MainAppScreen(
                    viewModel = viewModel,
                    musicViewModel = musicViewModel,
                    onRequestAudioPermission = { requestAudioPermissionIfNeeded() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        musicViewModel.onPermissionResult(hasAudioPermission())
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermissionIfNeeded() {
        if (hasAudioPermission()) {
            musicViewModel.onPermissionResult(true)
            requestNotificationPermissionIfNeeded()
            return
        }
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        audioPermissionLauncher.launch(permission)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        if (Intent.ACTION_SEND == intent.action && "text/plain" == intent.type) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            if (!sharedText.isNullOrBlank()) {
                viewModel.handleSharedContent(sharedText)
            }
        } else if (Intent.ACTION_VIEW == intent.action) {
            val dataString = intent.dataString
            if (!dataString.isNullOrBlank()) {
                viewModel.handleSharedContent(dataString)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: MainViewModel,
    musicViewModel: MusicViewModel,
    onRequestAudioPermission: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(NavTab.HOME) }
    var isNowPlayingOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()

    // Show snackbar messages when emitted
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    val pendingChooserUrl by viewModel.pendingChooserUrl.collectAsState()
    LaunchedEffect(pendingChooserUrl) {
        if (pendingChooserUrl != null) {
            selectedTab = NavTab.SETTINGS
        }
    }

    Scaffold(
        bottomBar = {
            Column {
                MiniPlayer(
                    playerController = musicViewModel.playerController,
                    onExpand = { isNowPlayingOpen = true }
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavTab.values().forEach { tab ->
                        val isSelected = selectedTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selectedTab = tab },
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = {
                                Text(
                                    text = tab.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(durationMillis = 180),
                label = "main_tab_transition"
            ) { tab ->
                when (tab) {
                NavTab.HOME -> HomeScreen(
                    musicViewModel = musicViewModel,
                    onNavigateToConverter = { selectedTab = NavTab.SETTINGS },
                    onNavigateToLibrary = { selectedTab = NavTab.LIBRARY }
                )
                NavTab.LIBRARY -> LibraryScreen(
                    musicViewModel = musicViewModel,
                    onRequestAudioPermission = onRequestAudioPermission,
                    onNavigateToHome = { selectedTab = NavTab.SETTINGS }
                )
                NavTab.SEARCH -> SearchScreen(
                    musicViewModel = musicViewModel,
                    onNavigateToLibrary = { selectedTab = NavTab.LIBRARY }
                )
                NavTab.SETTINGS -> SettingsScreen(
                    viewModel = viewModel
                )
                }
            }
        }
    }

    // Now Playing Dialog
    if (isNowPlayingOpen) {
        Dialog(
            onDismissRequest = { isNowPlayingOpen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            NowPlayingScreen(
                musicViewModel = musicViewModel,
                onCollapse = { isNowPlayingOpen = false }
            )
        }
    }
}
