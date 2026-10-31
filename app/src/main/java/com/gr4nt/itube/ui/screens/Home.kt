package com.gr4nt.itube.ui.screens

import androidx.compose.foundation.layout.height
import com.gr4nt.itube.data.regularVideos
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.AccountCircle
import com.gr4nt.itube.data.Account
import com.gr4nt.itube.data.Repo
import com.gr4nt.itube.net.Innertube
import com.gr4nt.itube.ui.glass.liquidGlass
import com.gr4nt.itube.ui.glass.pressScale
import com.gr4nt.itube.data.Store
import com.gr4nt.itube.data.VideoItem
import com.gr4nt.itube.ui.LocalAppState
import com.gr4nt.itube.ui.LocalChrome
import com.gr4nt.itube.ui.Screen
import com.gr4nt.itube.ui.components.Avatar
import com.gr4nt.itube.ui.components.EmptyBox
import com.gr4nt.itube.ui.components.LoadingBox
import com.gr4nt.itube.ui.components.PagedList
import com.gr4nt.itube.ui.components.VideoCard
import com.gr4nt.itube.ui.glass.GlassChip
import com.gr4nt.itube.ui.glass.GlassIconButton
import com.gr4nt.itube.ui.glass.GlassPill
import com.gr4nt.itube.ui.theme.AppTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Top chrome shared by the tab roots: big title, glass search pill and settings button. */
@Composable
fun RootHeader(title: String) {
    val app = LocalAppState.current
    val c = AppTheme.colors
    Column(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineLarge, color = c.label)
                Text(
                    when (title) {
                        "iTube" -> "A little more worth watching."
                        "Subs" -> "New from your favourite creators."
                        "Saved" -> "Your videos, wherever you go."
                        else -> "Everything you keep coming back to."
                    },
                    color = c.secondaryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ProfileButton { app.push(Screen.Profile) }
        }
        GlassPill(modifier = Modifier.fillMaxWidth(), height = 44.dp, onClick = { app.push(Screen.Search()) }) {
            Icon(Icons.Rounded.Search, null, tint = c.secondaryLabel, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Search YouTube", color = c.secondaryLabel, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
    }
}

/** Glass avatar button: your Google profile photo when signed in. */
@Composable
fun ProfileButton(onClick: () -> Unit) {
    val profile by Account.profile.collectAsState()
    val cookies by Account.cookies.collectAsState()
    val avatar = profile?.avatar
    if (cookies != null && avatar != null) {
        val interaction = remember { MutableInteractionSource() }
        Box(
            Modifier
                .pressScale(interaction)
                .size(44.dp)
                .liquidGlass(CircleShape, elevation = 10.dp)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(3.dp),
        ) { Avatar(avatar, 38.dp) }
    } else {
        GlassIconButton(Icons.Rounded.AccountCircle, "Profile", onClick)
    }
}

private data class HomeTab(val id: String, val label: String)

@Composable
fun HomeScreen() {
    val chrome = LocalChrome.current
    val subs by Store.subscriptions.collectAsState()
    val cookies by Account.cookies.collectAsState()
    val tabs = remember(cookies) {
        buildList {
            add(HomeTab("trending", "Trending"))
            if (cookies != null) add(HomeTab("for_you", "For you"))
            Repo.kiosks.forEach { add(HomeTab(it.id, it.label)) }
        }
    }
    // Trending (index 0) is what the app opens on.
    var selectedId by rememberSaveable { mutableStateOf("trending") }
    val tab = tabs.firstOrNull { it.id == selectedId } ?: tabs.first()
    val subsKey = subs.map { it.url }.toSet()
    Box(Modifier.fillMaxSize()) {
        PagedList(
            key = if (tab.id == "trending") tab.id to subsKey else tab.id,
            load = {
                when (tab.id) {
                    "trending" -> Repo.trending(subs)
                    "for_you" -> Innertube.recommendations()
                    else -> Repo.kiosk(tab.id)
                }
            },
            contentPadding = PaddingValues(top = chrome.top, bottom = chrome.bottom),
        ) {
            item {
                Text(
                    if (tab.id == "trending") "Trending now" else tab.label,
                    style = MaterialTheme.typography.headlineSmall,
                    color = AppTheme.colors.label,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 10.dp),
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(tabs.size) { i ->
                        GlassChip(tabs[i].label, tabs[i].id == tab.id, { selectedId = tabs[i].id })
                    }
                }
            }
            if (tab.id == "trending") {
                item {
                    Text(
                        if (subs.isEmpty()) "Top charts right now · subscribe to channels to personalise"
                        else "Rising fastest across your ${subs.size} subscriptions",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.secondaryLabel,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun SubscriptionsScreen() {
    val app = LocalAppState.current
    val chrome = LocalChrome.current
    val c = AppTheme.colors
    val subs by Store.subscriptions.collectAsState()
    val cookies by Account.cookies.collectAsState()
    if (cookies != null) {
        PagedList(
            key = "account_subs",
            load = { Innertube.subscriptionFeed() },
            contentPadding = PaddingValues(top = chrome.top, bottom = chrome.bottom),
        ) {
            if (subs.isNotEmpty()) item { SubsAvatarRow(subs) }
        }
        return
    }
    var feed by remember { mutableStateOf<List<VideoItem>?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(subs.map { it.url }.toSet(), refresh) {
        if (subs.isEmpty()) {
            feed = emptyList(); return@LaunchedEffect
        }
        feed = null
        val gate = Semaphore(6)
        feed = coroutineScope {
            subs.map { ch ->
                async {
                    gate.withPermit {
                        runCatching { Repo.feed(ch.url) }.getOrDefault(emptyList())
                            .map { it.copy(uploaderAvatar = it.uploaderAvatar ?: ch.thumbnail, uploaderUrl = it.uploaderUrl ?: ch.url) }
                    }
                }
            }.awaitAll().flatten()
        }.regularVideos().sortedByDescending { it.timestamp }.distinctBy { it.url }.take(300)
    }

    LazyColumn(
        contentPadding = PaddingValues(top = chrome.top, bottom = chrome.bottom),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (subs.isNotEmpty()) item { SubsAvatarRow(subs) }
        val f = feed
        when {
            subs.isEmpty() -> item {
                EmptyBox("No subscriptions yet", "Subscribe to channels and their new videos will show up here — no account needed.")
            }
            f == null -> item { LoadingBox() }
            f.isEmpty() -> item { EmptyBox("No new videos") }
            else -> items(f) { v -> VideoCard(v, f) }
        }
    }
}

@Composable
private fun SubsAvatarRow(subs: List<com.gr4nt.itube.data.ChannelItem>) {
    val app = LocalAppState.current
    val c = AppTheme.colors
    LazyRow(
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(subs) { ch ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(68.dp)
                    .clickable { app.push(Screen.Channel(ch.url)) },
            ) {
                Avatar(ch.thumbnail, 58.dp)
                Text(
                    ch.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
