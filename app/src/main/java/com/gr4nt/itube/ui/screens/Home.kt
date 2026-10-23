package com.gr4nt.itube.ui.screens

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
import com.gr4nt.itube.data.Repo
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = c.label,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 12.dp),
        )
        GlassPill(modifier = Modifier.weight(1f), onClick = { app.push(Screen.Search()) }) {
            Icon(Icons.Rounded.Search, null, tint = c.secondaryLabel, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Search YouTube", color = c.secondaryLabel, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        GlassIconButton(Icons.Rounded.Settings, "Settings", { app.push(Screen.Settings) })
    }
}

@Composable
fun HomeScreen() {
    val chrome = LocalChrome.current
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val kiosk = Repo.kiosks[selected]
    Box(Modifier.fillMaxSize()) {
        PagedList(
            key = kiosk.id,
            load = { Repo.kiosk(kiosk.id) },
            contentPadding = PaddingValues(top = chrome.top, bottom = chrome.bottom),
        ) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(Repo.kiosks.size) { i ->
                        GlassChip(Repo.kiosks[i].label, i == selected, { selected = i })
                    }
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
        }.sortedByDescending { it.timestamp }.distinctBy { it.url }.take(300)
    }

    LazyColumn(
        contentPadding = PaddingValues(top = chrome.top, bottom = chrome.bottom),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (subs.isNotEmpty()) {
            item {
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
        }
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
