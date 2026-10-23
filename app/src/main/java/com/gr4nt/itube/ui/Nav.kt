package com.gr4nt.itube.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gr4nt.itube.data.VideoItem
import com.gr4nt.itube.player.PlaybackEngine

sealed interface Screen {
    data object Home : Screen
    data object Shorts : Screen
    data object Subscriptions : Screen
    data object Downloads : Screen
    data object Library : Screen
    data object Settings : Screen
    data object History : Screen
    /** Identity-based so each pushed search keeps its own state. */
    class Search(val query: String = "") : Screen {
        val model = SearchModel(query)
    }
    data class Channel(val url: String) : Screen
    data class Playlist(val url: String) : Screen
    data class LocalList(val id: String) : Screen
    data object Profile : Screen
    /** Signed-in YouTube lists: "history", "watch_later", "liked". */
    data class AccountFeed(val kind: String) : Screen
}

class SearchModel(initial: String) {
    var text by mutableStateOf(initial)
    var submitted by mutableStateOf(initial)
    var filter by mutableStateOf("all")
}

/** UI-wide navigation and overlay state. Each tab keeps its own back stack. */
class AppState {
    private val roots = listOf(Screen.Home, Screen.Shorts, Screen.Subscriptions, Screen.Downloads, Screen.Library)
    val stacks = roots.map { mutableStateListOf<Screen>(it) }

    var tab by mutableIntStateOf(0)
    var playerExpanded by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)
    var inPip by mutableStateOf(false)

    /** Content of the glass bottom sheet, or null when hidden. */
    var sheet by mutableStateOf<(@Composable () -> Unit)?>(null)

    val current: Screen get() = stacks[tab].last()

    fun push(screen: Screen) {
        playerExpanded = false
        stacks[tab].add(screen)
    }

    fun selectTab(i: Int) {
        if (i == tab) {
            // Re-selecting a tab pops to its root.
            while (stacks[i].size > 1) stacks[i].removeAt(stacks[i].lastIndex)
        }
        tab = i
    }

    /** Returns true if something was dismissed. */
    fun back(): Boolean {
        if (sheet != null) { sheet = null; return true }
        if (fullscreen) { fullscreen = false; return true }
        if (playerExpanded) { playerExpanded = false; return true }
        if (stacks[tab].size > 1) { stacks[tab].removeAt(stacks[tab].lastIndex); return true }
        if (tab != 0) { tab = 0; return true }
        return false
    }

    fun openVideo(video: VideoItem, queue: List<VideoItem>? = null) {
        if (PlaybackEngine.current.value?.url != video.url) PlaybackEngine.play(video, queue)
        playerExpanded = true
    }

    fun showSheet(content: @Composable () -> Unit) {
        sheet = content
    }
}

val LocalAppState = staticCompositionLocalOf<AppState> { error("No AppState") }

/** Space taken by the floating glass chrome, so content can scroll underneath it. */
data class Chrome(val top: Dp, val bottom: Dp)

val LocalChrome = staticCompositionLocalOf { Chrome(0.dp, 0.dp) }
