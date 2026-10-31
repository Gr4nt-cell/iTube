package com.gr4nt.itube.ui

import com.gr4nt.itube.ui.glass.GlassBackdrop
import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.gr4nt.itube.data.LeaveAction
import com.gr4nt.itube.data.Repo
import com.gr4nt.itube.data.Settings
import com.gr4nt.itube.data.VideoItem
import com.gr4nt.itube.player.PlaybackEngine
import com.gr4nt.itube.player.PopupPlayerService
import com.gr4nt.itube.player.SurfaceOwner
import com.gr4nt.itube.ui.components.FloatingHeader
import com.gr4nt.itube.ui.components.GlassSheetHost
import com.gr4nt.itube.ui.glass.GlassTabBar
import com.gr4nt.itube.ui.glass.LocalHazeState
import com.gr4nt.itube.ui.glass.TabSpec
import com.gr4nt.itube.ui.glass.liquidGlass
import com.gr4nt.itube.ui.screens.ChannelScreen
import com.gr4nt.itube.ui.screens.DownloadsScreen
import com.gr4nt.itube.ui.screens.HistoryScreen
import com.gr4nt.itube.ui.screens.HomeScreen
import com.gr4nt.itube.ui.screens.LibraryScreen
import com.gr4nt.itube.ui.screens.LocalListScreen
import com.gr4nt.itube.ui.screens.MiniPlayer
import com.gr4nt.itube.ui.screens.PlayerSurface
import com.gr4nt.itube.ui.screens.PlaylistScreen
import com.gr4nt.itube.ui.screens.RootHeader
import com.gr4nt.itube.ui.screens.SearchHeader
import com.gr4nt.itube.ui.screens.SearchScreen
import com.gr4nt.itube.ui.screens.SettingsScreen
import com.gr4nt.itube.ui.screens.SubscriptionsScreen
import com.gr4nt.itube.ui.screens.WatchScreen
import com.gr4nt.itube.ui.theme.AppTheme
import com.gr4nt.itube.ui.theme.ITubeTheme
import com.gr4nt.itube.ui.theme.isDark
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val app = AppState()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        PlaybackEngine.ensureSession(this)
        requestRuntimePermissions()
        handleIntent(intent)
        keepScreenOnWhilePlaying()
        setContent { Root(app, onExit = { finish() }) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            ACTION_OPEN_PLAYER -> if (PlaybackEngine.current.value != null) app.playerExpanded = true
            Intent.ACTION_VIEW -> intent.dataString?.let(::route)
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let(::route)
        }
    }

    private fun route(text: String) {
        val url = Regex("https?://\\S+").find(text)?.value ?: text
        when {
            Repo.isPlaylistUrl(url) && !url.contains("watch") -> app.push(Screen.Playlist(url))
            Repo.isChannelUrl(url) -> app.push(Screen.Channel(url))
            else -> Repo.videoUrlFrom(url)?.let { app.openVideo(VideoItem(url = it, title = "", thumbnail = null, isShort = com.gr4nt.itube.data.isShortUrl(url))) }
        }
    }

    /**
     * Stops the screen from turning off while a video is playing (main player, PiP or Shorts).
     * Audio-only/background playback lets the screen sleep as normal.
     */
    private fun keepScreenOnWhilePlaying() {
        lifecycleScope.launch {
            combine(
                PlaybackEngine.isPlaying,
                PlaybackEngine.audioOnly,
                PlaybackEngine.surfaceOwner,
                com.gr4nt.itube.player.ShortsPlayer.isPlaying,
            ) { playing, audioOnly, owner, shortsPlaying ->
                (playing && !audioOnly && owner == SurfaceOwner.MAIN) || shortsPlaying
            }.distinctUntilChanged().collect { keepOn ->
                if (keepOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }

    private fun requestRuntimePermissions() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT <= 28) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), 7)
    }

    private fun hasActiveVideo(): Boolean =
        PlaybackEngine.current.value != null && PlaybackEngine.player.isPlaying &&
            !PlaybackEngine.audioOnly.value && PlaybackEngine.surfaceOwner.value == SurfaceOwner.MAIN

    /** User pressed Home / switched apps: PiP, pop-up, or keep playing in the background. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!hasActiveVideo()) return
        when (Settings.leaveAction.value) {
            LeaveAction.PIP -> enterPip()
            LeaveAction.POPUP -> if (!PopupPlayerService.start(this)) PlaybackEngine.setBackgrounded(true)
            LeaveAction.BACKGROUND, LeaveAction.PAUSE -> Unit
        }
    }

    private fun enterPip() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        runCatching {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        app.inPip = isInPictureInPictureMode
        // PiP window closed with the X: stop like other players do.
        if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            PlaybackEngine.player.pause()
        }
    }

    override fun onStart() {
        super.onStart()
        PlaybackEngine.setBackgrounded(false)
    }

    /** Screen off or app hidden: background play continues (audio only) unless set to pause. */
    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations || isInPictureInPictureMode) return
        if (Settings.leaveAction.value == LeaveAction.PAUSE && PlaybackEngine.surfaceOwner.value == SurfaceOwner.MAIN) {
            PlaybackEngine.player.pause()
        } else {
            PlaybackEngine.setBackgrounded(true)
        }
    }

    fun setFullscreen(on: Boolean) {
        requestedOrientation = if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun setLightBars(light: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
    }

    companion object {
        const val ACTION_OPEN_PLAYER = "com.gr4nt.itube.OPEN_PLAYER"
    }
}

private val tabs = listOf(
    TabSpec("Home", Icons.Outlined.Home, Icons.Rounded.Home),
    TabSpec("Shorts", Icons.Outlined.Slideshow, Icons.Rounded.Slideshow),
    TabSpec("Subscriptions", Icons.Outlined.Subscriptions, Icons.Rounded.Subscriptions),
    TabSpec("Downloads", Icons.Outlined.Download, Icons.Rounded.Download),
    TabSpec("Library", Icons.Outlined.VideoLibrary, Icons.Rounded.VideoLibrary),
)

@Composable
private fun MainActivity.Root(app: AppState, onExit: () -> Unit) {
    val themeMode by Settings.theme.collectAsState()
    val dark = isDark(themeMode)
    val current by PlaybackEngine.current.collectAsState()

    LaunchedEffect(app.fullscreen) { setFullscreen(app.fullscreen) }
    LaunchedEffect(current) { if (current == null) { app.playerExpanded = false; app.fullscreen = false } }
    SideEffect { setLightBars(!dark && !app.playerExpanded && app.current != Screen.Shorts) }

    ITubeTheme(dark) {
        CompositionLocalProvider(LocalAppState provides app) {
            BackHandler { if (!app.back()) onExit() }

            if (app.inPip) {
                Box(Modifier.fillMaxSize().background(Color.Black)) { PlayerSurface(Modifier.fillMaxSize()) }
                return@CompositionLocalProvider
            }

            val c = AppTheme.colors
            val haze = rememberHazeState()
            val watchHaze = rememberHazeState()
            val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val chrome = Chrome(
                top = statusTop + if (app.current == Screen.Home || app.current == Screen.Subscriptions ||
                    app.current == Screen.Downloads || app.current == Screen.Library) 126.dp else 64.dp,
                bottom = navBottom + 100.dp + if (current != null) 80.dp else 0.dp,
            )

            Box(Modifier.fillMaxSize().background(c.background)) {
                CompositionLocalProvider(LocalHazeState provides haze, LocalChrome provides chrome) {
                    Box(Modifier.fillMaxSize().hazeSource(haze)) {
                        if (app.current != Screen.Shorts) GlassBackdrop()
                        Crossfade(targetState = app.current, label = "screen") { screen -> ScreenContent(screen) }
                    }
                    // Soft scrim so the floating header stays legible over content.
                    if (app.current != Screen.Shorts) Box(
                        Modifier
                            .fillMaxWidth()
                            .height(statusTop + 24.dp)
                            .background(Brush.verticalGradient(listOf(c.background.copy(alpha = 0.9f), Color.Transparent))),
                    )
                    ScreenHeader(app.current)

                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
                    ) {
                        if (current != null && !app.playerExpanded && app.current != Screen.Shorts) MiniPlayer(Modifier.padding(bottom = 10.dp))
                        if (app.current == Screen.Shorts) {
                            ITubeTheme(dark = true) { GlassTabBar(tabs, app.tab, { app.selectTab(it) }) }
                        } else {
                            GlassTabBar(tabs, app.tab, { app.selectTab(it) })
                        }
                    }
                }

                AnimatedVisibility(
                    visible = app.playerExpanded && current != null,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    Box(Modifier.fillMaxSize().hazeSource(watchHaze)) { WatchScreen() }
                }

                CompositionLocalProvider(LocalHazeState provides if (app.playerExpanded) watchHaze else haze) {
                    GlassSheetHost()
                    ToastHost()
                }
            }
        }
    }
}

@Composable
private fun ScreenContent(screen: Screen) {
    when (screen) {
        Screen.Home -> HomeScreen()
        Screen.Shorts -> ITubeTheme(dark = true) { com.gr4nt.itube.ui.screens.ShortsScreen() }
        Screen.Subscriptions -> SubscriptionsScreen()
        Screen.Downloads -> DownloadsScreen()
        Screen.Library -> LibraryScreen()
        Screen.Settings -> SettingsScreen()
        Screen.History -> HistoryScreen()
        is Screen.Search -> SearchScreen(screen)
        is Screen.Channel -> ChannelScreen(screen.url)
        is Screen.Playlist -> PlaylistScreen(screen.url)
        is Screen.LocalList -> LocalListScreen(screen.id)
        Screen.Profile -> com.gr4nt.itube.ui.screens.ProfileScreen()
        is Screen.AccountFeed -> com.gr4nt.itube.ui.screens.AccountFeedScreen(screen.kind)
    }
}

@Composable
private fun ScreenHeader(screen: Screen) {
    val app = LocalAppState.current
    val back = { app.back(); Unit }
    when (screen) {
        Screen.Home -> RootHeader("iTube")
        Screen.Shorts -> Unit
        Screen.Subscriptions -> RootHeader("Subs")
        Screen.Downloads -> RootHeader("Saved")
        Screen.Library -> RootHeader("Library")
        Screen.Settings -> FloatingHeader("Settings", onBack = back)
        Screen.History -> FloatingHeader("History", onBack = back)
        is Screen.Search -> SearchHeader(screen)
        is Screen.Channel -> FloatingHeader("Channel", onBack = back)
        is Screen.Playlist -> FloatingHeader("Playlist", onBack = back)
        is Screen.LocalList -> FloatingHeader("Playlist", onBack = back)
        Screen.Profile -> FloatingHeader("Account", onBack = back)
        is Screen.AccountFeed -> FloatingHeader(com.gr4nt.itube.ui.screens.accountFeedTitle(screen.kind), onBack = back)
    }
}

/** Glass capsule for short notices such as "Skipped sponsor". */
@Composable
private fun ToastHost() {
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        PlaybackEngine.events.collect {
            message = it
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(2200)
            message = null
        }
    }
    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = message != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            var last by remember { mutableStateOf("") }
            message?.let { last = it }
            Text(
                last,
                color = AppTheme.colors.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .liquidGlass(RoundedCornerShape(22.dp), elevation = 16.dp)
                    .padding(horizontal = 18.dp, vertical = 11.dp),
            )
        }
    }
}
