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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gr4nt.itube.data.Repo
import com.gr4nt.itube.data.Store
import com.gr4nt.itube.data.VideoItem
import com.gr4nt.itube.ui.LocalAppState
import com.gr4nt.itube.ui.LocalChrome
import com.gr4nt.itube.ui.Screen
import com.gr4nt.itube.ui.SearchModel
import com.gr4nt.itube.ui.components.GlassTextField
import com.gr4nt.itube.ui.components.PagedList
import com.gr4nt.itube.ui.glass.GlassChip
import com.gr4nt.itube.ui.glass.GlassIconButton
import com.gr4nt.itube.ui.theme.AppTheme
import kotlinx.coroutines.delay

private val filters = listOf(
    "all" to "All",
    "videos" to "Videos",
    "channels" to "Channels",
    "playlists" to "Playlists",
    "music_songs" to "Songs",
    "music_videos" to "Music videos",
)

/** Submits a query, opening pasted links directly. */
private fun submitSearch(app: com.gr4nt.itube.ui.AppState, m: SearchModel, q: String) {
    val query = q.trim()
    if (query.isEmpty()) return
    if (query.startsWith("http")) {
        Repo.videoUrlFrom(query)?.let {
            if (!Repo.isPlaylistUrl(query) || query.contains("watch")) {
                app.openVideo(VideoItem(url = it, title = "", thumbnail = null, isShort = com.gr4nt.itube.data.isShortUrl(query))); return
            }
        }
        if (Repo.isChannelUrl(query)) { app.push(Screen.Channel(query)); return }
        if (Repo.isPlaylistUrl(query)) { app.push(Screen.Playlist(query)); return }
    }
    m.text = query
    m.submitted = query
    Store.addSearch(query)
}

@Composable
fun SearchHeader(screen: Screen.Search) {
    val app = LocalAppState.current
    val m = screen.model
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(screen) {
        if (m.submitted.isEmpty()) runCatching { focus.requestFocus() }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", { app.back() })
        Spacer(Modifier.width(10.dp))
        GlassTextField(
            value = m.text,
            onValueChange = { m.text = it },
            placeholder = "Search YouTube",
            leadingSearchIcon = true,
            focusRequester = focus,
            onSubmit = { submitSearch(app, m, m.text); focusManager.clearFocus() },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun SearchScreen(screen: Screen.Search) {
    val app = LocalAppState.current
    val chrome = LocalChrome.current
    val c = AppTheme.colors
    val focusManager = LocalFocusManager.current
    val m = screen.model
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    val history by Store.searches.collectAsState()
    val editing = m.text != m.submitted || m.submitted.isEmpty()
    val text = m.text
    val submitted = m.submitted
    val filter = m.filter

    fun submit(q: String) {
        submitSearch(app, m, q)
        focusManager.clearFocus()
    }

    LaunchedEffect(text) {
        if (text.isBlank()) { suggestions = emptyList(); return@LaunchedEffect }
        delay(220)
        suggestions = Repo.suggestions(text)
    }

    Box(Modifier.fillMaxSize()) {
        if (!editing) {
            PagedList(
                key = submitted to filter,
                load = { Repo.search(submitted, filter) },
                contentPadding = PaddingValues(top = chrome.top, bottom = chrome.bottom),
                large = filter == "all" || filter == "videos",
            ) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filters) { (key, label) -> GlassChip(label, key == filter, { m.filter = key }) }
                    }
                }
            }
        } else {
            val rows = if (text.isBlank()) history.map { it to true } else suggestions.map { it to false }
            LazyColumn(contentPadding = PaddingValues(top = chrome.top, bottom = chrome.bottom)) {
                items(rows) { (q, fromHistory) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { submit(q) }
                            .padding(horizontal = 20.dp, vertical = 13.dp),
                    ) {
                        Icon(if (fromHistory) Icons.Rounded.History else Icons.Rounded.Search, null, tint = c.secondaryLabel, modifier = Modifier.size(20.dp))
                        Text(
                            q,
                            color = c.label,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                        )
                        if (fromHistory) {
                            Icon(Icons.Rounded.Close, "Remove", tint = c.tertiaryLabel, modifier = Modifier.size(20.dp).clickable { Store.removeSearch(q) })
                        } else {
                            Icon(Icons.Rounded.NorthWest, "Fill", tint = c.tertiaryLabel, modifier = Modifier.size(20.dp).clickable { m.text = "$q " })
                        }
                    }
                }
                if (text.isBlank() && history.isNotEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Clear search history",
                                color = c.accent,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.clickable { Store.clearSearches() }.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
