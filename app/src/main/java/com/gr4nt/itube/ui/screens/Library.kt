package com.gr4nt.itube.ui.screens

import com.gr4nt.itube.data.regularVideos
import com.gr4nt.itube.data.isShortContent
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.gr4nt.itube.data.DownloadRecord
import com.gr4nt.itube.data.Store
import com.gr4nt.itube.download.DownloadWorker
import com.gr4nt.itube.download.Downloads
import com.gr4nt.itube.player.PlaybackEngine
import com.gr4nt.itube.ui.LocalAppState
import com.gr4nt.itube.ui.LocalChrome
import com.gr4nt.itube.ui.Screen
import com.gr4nt.itube.ui.components.EmptyBox
import com.gr4nt.itube.ui.components.Format
import com.gr4nt.itube.ui.components.SectionLabel
import com.gr4nt.itube.ui.components.SheetAction
import com.gr4nt.itube.ui.components.SheetTitle
import com.gr4nt.itube.ui.components.Thumb
import com.gr4nt.itube.ui.components.VideoRow
import com.gr4nt.itube.ui.glass.frosted
import com.gr4nt.itube.ui.theme.AppTheme
import kotlinx.coroutines.flow.Flow

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title, color = c.label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, color = c.secondaryLabel, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.tertiaryLabel)
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .frosted(RoundedCornerShape(28.dp), elevation = 6.dp),
    ) { content() }
}

@Composable
fun LibraryScreen() {
    val app = LocalAppState.current
    val chrome = LocalChrome.current
    val c = AppTheme.colors
    val storedHistory by Store.history.collectAsState()
    val history = remember(storedHistory) { storedHistory.filterNot { it.video.isShortContent } }
    val playlists by Store.playlists.collectAsState()
    val downloads by Store.downloads.collectAsState()

    LazyColumn(contentPadding = PaddingValues(top = chrome.top, bottom = chrome.bottom), modifier = Modifier.fillMaxSize()) {
        if (history.isNotEmpty()) {
            item { SectionLabel("Recently watched") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(history.take(20)) { h ->
                        Column(
                            Modifier
                                .width(180.dp)
                                .clickable { app.openVideo(h.video) },
                        ) {
                            Thumb(h.video.thumbnail, duration = Format.duration(h.video.durationSec), corner = 14.dp)
                            Text(
                                h.video.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = c.label,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }
        item { SectionLabel("Library") }
        item {
            Group {
                NavRow(Icons.Rounded.History, "History", "${history.size} videos") { app.push(Screen.History) }
                NavRow(Icons.Rounded.Download, "Downloads", "${downloads.size} files") { app.selectTab(3) }
                NavRow(Icons.Rounded.Settings, "Settings") { app.push(Screen.Settings) }
            }
        }
        item { SectionLabel("Playlists") }
        item {
            Group {
                playlists.forEach { pl ->
                    NavRow(
                        if (pl.id == Store.WATCH_LATER) Icons.Rounded.WatchLater else Icons.AutoMirrored.Rounded.PlaylistPlay,
                        pl.name,
                        "${pl.videos.regularVideos().size} videos",
                    ) { app.push(Screen.LocalList(pl.id)) }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen() {
    val chrome = LocalChrome.current
    val c = AppTheme.colors
    val storedHistory by Store.history.collectAsState()
    val history = remember(storedHistory) { storedHistory.filterNot { it.video.isShortContent } }
    val videos = remember(history) { history.map { it.video } }
    LazyColumn(contentPadding = PaddingValues(top = chrome.top, bottom = chrome.bottom), modifier = Modifier.fillMaxSize()) {
        if (history.isEmpty()) item { EmptyBox("No watch history") }
        else item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text(
                    "Clear all",
                    color = c.accent,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable { Store.clearHistory() }.padding(16.dp),
                )
            }
        }
        items(history, key = { it.video.url }) { h ->
            VideoRow(h.video, videos, trailing = {
                IconButton(onClick = { Store.removeHistory(h.video.url) }) {
                    Icon(Icons.Rounded.Close, "Remove", tint = c.secondaryLabel)
                }
            })
        }
    }
}

@Composable
fun DownloadsScreen() {
    val chrome = LocalChrome.current
    val c = AppTheme.colors
    val ctx = LocalContext.current
    val wm = remember { WorkManager.getInstance(ctx) }
    val flow: Flow<List<WorkInfo>> = remember { wm.getWorkInfosByTagFlow(Downloads.TAG) }
    val works by flow.collectAsState(initial = emptyList())
    val active = works.filter { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
    val failed = works.filter { it.state == WorkInfo.State.FAILED }.take(5)
    val records by Store.downloads.collectAsState()

    LazyColumn(contentPadding = PaddingValues(top = chrome.top, bottom = chrome.bottom), modifier = Modifier.fillMaxSize()) {
        if (active.isNotEmpty()) {
            item { SectionLabel("In progress") }
            items(active, key = { it.id.toString() }) { w ->
                val p = w.progress.getFloat(DownloadWorker.PROGRESS, 0f)
                val stage = w.progress.getString(DownloadWorker.STAGE) ?: if (w.state == WorkInfo.State.ENQUEUED) "Queued" else "Starting"
                val title = w.progress.getString(DownloadWorker.KEY_TITLE) ?: "Download"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                        .frosted(RoundedCornerShape(20.dp))
                        .padding(14.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, color = c.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$stage · ${(p * 100).toInt()}%", color = c.secondaryLabel, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.size(8.dp))
                        LinearProgressIndicator(
                            progress = { p },
                            color = c.accent,
                            trackColor = c.chip,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    IconButton(onClick = { wm.cancelWorkById(w.id) }) {
                        Icon(Icons.Rounded.Close, "Cancel", tint = c.secondaryLabel)
                    }
                }
            }
        }
        if (failed.isNotEmpty()) {
            item {
                Text(
                    "${failed.size} download(s) failed — ${failed.first().outputData.getString("error") ?: "network error"}",
                    color = c.accent,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
        item { SectionLabel("Saved to Downloads/iTube") }
        if (records.isEmpty() && active.isEmpty()) {
            item { EmptyBox("No downloads yet", "Open a video and tap Download to save it as video or audio for offline viewing.") }
        }
        items(records, key = { it.id }) { r -> DownloadRow(r) }
    }
}

@Composable
private fun DownloadRow(r: DownloadRecord) {
    val app = LocalAppState.current
    val c = AppTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                PlaybackEngine.playLocal(r)
                app.playerExpanded = !r.audioOnly
            }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Box(Modifier.width(132.dp)) {
            Thumb(r.thumbnail, corner = 12.dp)
            if (r.audioOnly) {
                Icon(
                    Icons.Rounded.Headphones, null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.align(Alignment.Center).size(28.dp),
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(r.title, color = c.label, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(Format.meta(r.uploader, Format.bytes(r.sizeBytes)), color = c.secondaryLabel, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = { app.showSheet { DownloadActions(r) } }) {
            Icon(Icons.Rounded.MoreVert, "More", tint = c.secondaryLabel)
        }
    }
}

@Composable
private fun DownloadActions(r: DownloadRecord) {
    val app = LocalAppState.current
    val ctx = LocalContext.current
    Column {
        SheetTitle(r.title, Format.bytes(r.sizeBytes))
        SheetAction(Icons.Rounded.PlayArrow, "Play") { app.sheet = null; PlaybackEngine.playLocal(r); app.playerExpanded = !r.audioOnly }
        SheetAction(Icons.Rounded.OpenInNew, "Open with…") {
            app.sheet = null
            runCatching {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(r.uri), r.mime)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
            }
        }
        SheetAction(Icons.Rounded.Share, "Share file") {
            app.sheet = null
            runCatching {
                ctx.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType(r.mime).putExtra(Intent.EXTRA_STREAM, Uri.parse(r.uri))
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        "Share",
                    )
                )
            }
        }
        SheetAction(Icons.Rounded.Delete, "Delete file") {
            app.sheet = null
            runCatching {
                val uri = Uri.parse(r.uri)
                if (uri.scheme == "file") java.io.File(uri.path!!).delete() else ctx.contentResolver.delete(uri, null, null)
            }
            Store.removeDownload(r.id)
        }
    }
}
