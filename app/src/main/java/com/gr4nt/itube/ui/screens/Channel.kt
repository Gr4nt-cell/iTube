package com.gr4nt.itube.ui.screens

import com.gr4nt.itube.data.regularVideos
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.gr4nt.itube.data.ChannelDetails
import com.gr4nt.itube.data.ChannelItem
import com.gr4nt.itube.data.Item
import com.gr4nt.itube.data.PageResult
import com.gr4nt.itube.data.Repo
import com.gr4nt.itube.data.Store
import com.gr4nt.itube.data.VideoItem
import com.gr4nt.itube.data.best
import com.gr4nt.itube.data.largest
import com.gr4nt.itube.ui.LocalAppState
import com.gr4nt.itube.ui.LocalChrome
import com.gr4nt.itube.ui.components.Avatar
import com.gr4nt.itube.ui.components.ErrorBox
import com.gr4nt.itube.ui.components.Format
import com.gr4nt.itube.ui.components.LoadingBox
import com.gr4nt.itube.ui.components.PagedList
import com.gr4nt.itube.ui.components.SubscribeButton
import com.gr4nt.itube.ui.components.VideoRow
import com.gr4nt.itube.ui.glass.GlassChip
import com.gr4nt.itube.ui.glass.GlassPill
import com.gr4nt.itube.ui.theme.AppTheme
import kotlinx.coroutines.CancellationException
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.playlist.PlaylistInfo

private fun tabLabel(filter: String?) = when (filter) {
    ChannelTabs.VIDEOS -> "Videos"
    ChannelTabs.SHORTS -> "Shorts"
    ChannelTabs.LIVESTREAMS -> "Live"
    ChannelTabs.PLAYLISTS -> "Playlists"
    ChannelTabs.CHANNELS -> "Channels"
    ChannelTabs.ALBUMS -> "Albums"
    ChannelTabs.TRACKS -> "Tracks"
    else -> filter?.replaceFirstChar { it.uppercase() } ?: "Tab"
}

@Composable
fun ChannelScreen(url: String) {
    val chrome = LocalChrome.current
    val c = AppTheme.colors
    var details by remember(url) { mutableStateOf<ChannelDetails?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var tabIndex by rememberSaveable(url) { mutableIntStateOf(0) }

    LaunchedEffect(url, retry) {
        error = null
        try {
            details = Repo.channel(url)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = e.message ?: "Could not load channel"
        }
    }

    val d = details
    if (d == null) {
        Box(Modifier.fillMaxSize().padding(top = chrome.top)) {
            if (error != null) ErrorBox(error!!, onRetry = { retry++ }) else LoadingBox()
        }
        return
    }
    val tabs = d.tabs.filterNot { ChannelTabs.SHORTS in it.contentFilters }
    val tab = tabs.getOrNull(tabIndex)
    val channel = ChannelItem(
        url = d.info.url,
        title = d.info.name,
        thumbnail = d.info.avatars.best(176),
        subscribers = d.info.subscriberCount,
    )

    PagedList(
        key = url to tabIndex,
        load = { if (tab != null) Repo.channelTab(tab) else PageResult(emptyList<Item>(), null) },
        contentPadding = PaddingValues(top = chrome.top, bottom = chrome.bottom),
        large = true,
    ) {
        item {
            Column {
                val banner = d.info.banners.largest()
                if (banner != null) {
                    AsyncImage(
                        model = banner,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
                            .fillMaxWidth()
                            .aspectRatio(5.2f)
                            .clip(RoundedCornerShape(20.dp)),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Avatar(channel.thumbnail, 72.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(d.info.name, style = MaterialTheme.typography.titleLarge, color = c.label, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(Format.subs(d.info.subscriberCount), style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel)
                    }
                    SubscribeButton(channel)
                }
                val desc = d.info.description
                if (!desc.isNullOrBlank()) {
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.secondaryLabel,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(tabs.size) { i ->
                        GlassChip(tabLabel(tabs[i].contentFilters.firstOrNull()), i == tabIndex, { tabIndex = i })
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistScreen(url: String) {
    val app = LocalAppState.current
    val chrome = LocalChrome.current
    val c = AppTheme.colors
    var info by remember(url) { mutableStateOf<PlaylistInfo?>(null) }
    var firstPage by remember(url) { mutableStateOf<PageResult<Item>?>(null) }

    PagedList(
        key = url,
        load = {
            val (i, p) = Repo.playlist(url)
            info = i
            firstPage = p
            p
        },
        contentPadding = PaddingValues(top = chrome.top, bottom = chrome.bottom),
        large = false,
    ) {
        val i = info
        if (i != null) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(i.name, style = MaterialTheme.typography.titleLarge, color = c.label, fontWeight = FontWeight.Bold)
                    Text(
                        Format.meta(i.uploaderName, if (i.streamCount >= 0) "${i.streamCount} videos" else null),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.secondaryLabel,
                    )
                    Spacer(Modifier.size(12.dp))
                    val videos = firstPage?.items?.filterIsInstance<VideoItem>().orEmpty().regularVideos()
                    PlayAllRow(
                        onPlay = { videos.firstOrNull()?.let { app.openVideo(it, videos) } },
                        onShuffle = { videos.shuffled().let { s -> s.firstOrNull()?.let { app.openVideo(it, s) } } },
                    )
                }
            }
        }
    }
}

@Composable
fun PlayAllRow(onPlay: () -> Unit, onShuffle: () -> Unit) {
    val c = AppTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassPill(Modifier.weight(1f), onClick = onPlay) {
            Icon(Icons.Rounded.PlayArrow, null, tint = c.label)
            Spacer(Modifier.width(6.dp))
            Text("Play all", color = c.label, style = MaterialTheme.typography.labelLarge)
        }
        GlassPill(Modifier.weight(1f), onClick = onShuffle) {
            Icon(Icons.Rounded.Shuffle, null, tint = c.label)
            Spacer(Modifier.width(6.dp))
            Text("Shuffle", color = c.label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun LocalListScreen(id: String) {
    val app = LocalAppState.current
    val chrome = LocalChrome.current
    val c = AppTheme.colors
    val lists by Store.playlists.collectAsState()
    val pl = lists.firstOrNull { it.id == id }
    androidx.compose.foundation.lazy.LazyColumn(
        contentPadding = PaddingValues(top = chrome.top, bottom = chrome.bottom),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (pl == null) return@LazyColumn
        val videos = pl.videos.regularVideos()
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(pl.name, style = MaterialTheme.typography.titleLarge, color = c.label, fontWeight = FontWeight.Bold)
                Text("${videos.size} videos", style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel)
                Spacer(Modifier.size(12.dp))
                PlayAllRow(
                    onPlay = { videos.firstOrNull()?.let { app.openVideo(it, videos) } },
                    onShuffle = { videos.shuffled().let { s -> s.firstOrNull()?.let { app.openVideo(it, s) } } },
                )
            }
        }
        items(videos, key = { it.url }) { v ->
            VideoRow(v, videos, trailing = {
                androidx.compose.material3.IconButton(onClick = { Store.removeFromPlaylist(id, v.url) }) {
                    Icon(Icons.Rounded.Close, "Remove", tint = c.secondaryLabel)
                }
            })
        }
        if (videos.isEmpty()) item { com.gr4nt.itube.ui.components.EmptyBox("This playlist is empty", "Long-press any video to add it.") }
    }
}
