package com.gr4nt.itube.ui.screens

import android.app.Activity
import android.content.Intent
import android.text.Html
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gr4nt.itube.data.ChannelItem
import com.gr4nt.itube.data.Comment
import com.gr4nt.itube.data.Dislikes
import com.gr4nt.itube.data.PageResult
import com.gr4nt.itube.data.Repo
import com.gr4nt.itube.data.Settings
import com.gr4nt.itube.data.Store
import com.gr4nt.itube.data.VideoItem
import com.gr4nt.itube.data.best
import com.gr4nt.itube.data.toItems
import com.gr4nt.itube.player.PlaybackEngine
import com.gr4nt.itube.player.PopupPlayerService
import com.gr4nt.itube.player.SourceBuilder
import com.gr4nt.itube.ui.LocalAppState
import com.gr4nt.itube.ui.Screen
import com.gr4nt.itube.ui.components.AddToPlaylistSheet
import com.gr4nt.itube.ui.components.Avatar
import com.gr4nt.itube.ui.components.DownloadSheet
import com.gr4nt.itube.ui.components.ErrorBox
import com.gr4nt.itube.ui.components.Format
import com.gr4nt.itube.ui.components.LoadingBox
import com.gr4nt.itube.ui.components.SectionLabel
import com.gr4nt.itube.ui.components.SheetTitle
import com.gr4nt.itube.ui.components.SubscribeButton
import com.gr4nt.itube.ui.components.VideoRow
import com.gr4nt.itube.ui.glass.frosted
import com.gr4nt.itube.ui.glass.liquidGlass
import com.gr4nt.itube.ui.glass.pressScale
import com.gr4nt.itube.ui.theme.AppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Starts the floating pop-up and sends the app to the background. */
fun startPopup(activity: Activity?) {
    val ctx = activity ?: return
    if (!PopupPlayerService.canDrawOverlays(ctx)) {
        PlaybackEngine.emit("Allow \"Display over other apps\" to use the pop-up player")
        ctx.startActivity(PopupPlayerService.overlayPermissionIntent(ctx))
        return
    }
    if (PopupPlayerService.start(ctx)) {
        PlaybackEngine.player.play()
        activity.moveTaskToBack(true)
    }
}

@Composable
fun WatchScreen() {
    val app = LocalAppState.current
    val fullscreen = app.fullscreen
    val info by PlaybackEngine.info.collectAsState()
    val current by PlaybackEngine.current.collectAsState()

    if (fullscreen) {
        PlayerBox(
            Modifier.fillMaxSize(),
            fullscreen = true,
            onSettings = { app.showSheet { PlayerSettingsSheet() } },
            onCaptions = { app.showSheet { CaptionsSheet() } },
        )
        return
    }

    Column(Modifier.fillMaxSize().background(AppTheme.colors.background)) {
        Box(Modifier.background(androidx.compose.ui.graphics.Color.Black).statusBarsPadding()) {
            PlayerBox(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                fullscreen = false,
                onSettings = { app.showSheet { PlayerSettingsSheet() } },
                onCaptions = { app.showSheet { CaptionsSheet() } },
            )
        }
        val i = info
        val v = current
        if (v == null) return@Column
        WatchDetails(v, i)
    }
}

@Composable
private fun WatchDetails(video: VideoItem, info: org.schabi.newpipe.extractor.stream.StreamInfo?) {
    val app = LocalAppState.current
    val c = AppTheme.colors
    val ctx = LocalContext.current
    val showDislikes by Settings.showDislikes.collectAsState()
    var expanded by remember(video.url) { mutableStateOf(false) }
    var dislikes by remember(video.url) { mutableStateOf<Long?>(null) }
    val related = remember(info) { info?.relatedItems?.toItems()?.filterIsInstance<VideoItem>().orEmpty() }
    val playlists by Store.playlists.collectAsState()
    val inWatchLater = playlists.firstOrNull { it.id == Store.WATCH_LATER }?.videos?.any { it.url == video.url } == true

    LaunchedEffect(video.url, showDislikes) {
        if (showDislikes) Repo.videoId(video.url)?.let { id -> dislikes = Dislikes.votes(id)?.dislikes }
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 40.dp),
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).animateContentSize()) {
                Text(
                    info?.name ?: video.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = c.label,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (expanded) 6 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    Format.meta(Format.views(info?.viewCount ?: video.views), info?.textualUploadDate ?: video.uploaded),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.secondaryLabel,
                )
            }
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 4.dp),
            ) {
                val likes = info?.likeCount ?: -1
                ActionChip(Icons.Rounded.ThumbUp, if (likes >= 0) Format.count(likes) else "Like") {}
                if (showDislikes) ActionChip(Icons.Rounded.ThumbDown, dislikes?.let { Format.count(it) } ?: "—") {}
                ActionChip(Icons.Rounded.Headphones, "Background") {
                    PlaybackEngine.setAudioOnly(true)
                    PlaybackEngine.emit("Playing audio in background — you can leave the app")
                    app.playerExpanded = false
                }
                ActionChip(Icons.Rounded.PictureInPictureAlt, "Pop-up") { startPopup(ctx as? Activity) }
                ActionChip(Icons.Rounded.Download, "Download") {
                    if (info != null) app.showSheet { DownloadSheet(info) } else PlaybackEngine.emit("Still loading…")
                }
                ActionChip(if (inWatchLater) Icons.Rounded.Check else Icons.Rounded.WatchLater, "Save") {
                    app.showSheet { AddToPlaylistSheet(video) }
                }
                ActionChip(Icons.Rounded.Share, "Share") {
                    ctx.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, info?.url ?: video.url),
                            "Share",
                        )
                    )
                }
            }
        }
        if (info != null) {
            item {
                val channel = ChannelItem(
                    url = info.uploaderUrl ?: "",
                    title = info.uploaderName ?: "",
                    thumbnail = info.uploaderAvatars.best(176),
                    subscribers = info.uploaderSubscriberCount,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { if (channel.url.isNotEmpty()) app.push(Screen.Channel(channel.url)) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Avatar(channel.thumbnail, 44.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(channel.title, style = MaterialTheme.typography.titleSmall, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Format.subs(channel.subscribers), style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel)
                    }
                    if (channel.url.isNotEmpty()) SubscribeButton(channel)
                }
            }
            item {
                val desc = remember(info) {
                    val raw = info.description?.content ?: ""
                    Html.fromHtml(raw, Html.FROM_HTML_MODE_COMPACT).toString().trim()
                }
                Column(
                    Modifier
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .frosted(RoundedCornerShape(20.dp))
                        .clickable { expanded = !expanded }
                        .padding(14.dp)
                        .animateContentSize(),
                ) {
                    Text(
                        if (desc.isBlank()) "No description" else desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.label,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val chapters = info.streamSegments
                    if (expanded && chapters.isNotEmpty()) {
                        Spacer(Modifier.size(10.dp))
                        Text("Chapters", style = MaterialTheme.typography.titleSmall, color = c.label)
                        chapters.forEach { ch ->
                            Text(
                                "${Format.duration(ch.startTimeSeconds.toLong()).ifEmpty { "0:00" }}  ${ch.title}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.accent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { PlaybackEngine.player.seekTo(ch.startTimeSeconds * 1000L) }
                                    .padding(vertical = 5.dp),
                            )
                        }
                    }
                    Text(
                        if (expanded) "Show less" else "Show more",
                        style = MaterialTheme.typography.labelLarge,
                        color = c.secondaryLabel,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .fillMaxWidth()
                        .frosted(RoundedCornerShape(20.dp))
                        .clickable { app.showSheet { CommentsSheet(info.url) } }
                        .padding(14.dp),
                ) {
                    Text("Comments", style = MaterialTheme.typography.titleSmall, color = c.label, modifier = Modifier.weight(1f))
                    Text("View", style = MaterialTheme.typography.labelLarge, color = c.accent)
                }
            }
        } else {
            item { LoadingBox() }
        }
        if (related.isNotEmpty()) {
            item { SectionLabel("Up next") }
            items(related) { r -> VideoRow(r, null) }
        }
    }
}

@Composable
private fun ActionChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .pressScale(interaction)
            .frosted(RoundedCornerShape(18.dp), elevation = 5.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Icon(icon, null, tint = c.label, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = c.label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun PlayerSettingsSheet() {
    val app = LocalAppState.current
    val c = AppTheme.colors
    val heights by PlaybackEngine.heights.collectAsState()
    val height by PlaybackEngine.height.collectAsState()
    val audioOnly by PlaybackEngine.audioOnly.collectAsState()
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f)
    val currentSpeed = PlaybackEngine.player.playbackParameters.speed
    Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
        SheetTitle("Quality")
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            heights.forEach { h ->
                com.gr4nt.itube.ui.glass.GlassChip("${h}p", h == height && !audioOnly, {
                    PlaybackEngine.setQuality(h)
                    if (audioOnly) PlaybackEngine.setAudioOnly(false)
                    app.sheet = null
                })
            }
            com.gr4nt.itube.ui.glass.GlassChip("Audio only", audioOnly, {
                PlaybackEngine.setAudioOnly(!audioOnly)
                app.sheet = null
            })
        }
        SheetTitle("Speed")
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            speeds.forEach { s ->
                com.gr4nt.itube.ui.glass.GlassChip(if (s == 1f) "Normal" else "${s}×", s == currentSpeed, {
                    PlaybackEngine.setSpeed(s)
                    app.sheet = null
                })
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(
            "Default quality and speed can be changed in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = c.secondaryLabel,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun CaptionsSheet() {
    val app = LocalAppState.current
    val info by PlaybackEngine.info.collectAsState()
    val selected by PlaybackEngine.subtitle.collectAsState()
    val subs = remember(info) { info?.let { SourceBuilder.subtitles(it) }.orEmpty() }
    Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
        SheetTitle("Captions")
        com.gr4nt.itube.ui.components.SheetAction(if (selected == null) Icons.Rounded.Check else Icons.Rounded.Close, "Off") {
            PlaybackEngine.setSubtitle(null); app.sheet = null
        }
        if (subs.isEmpty()) {
            Text("No captions available", color = AppTheme.colors.secondaryLabel, modifier = Modifier.padding(20.dp))
        }
        subs.forEach { s ->
            val label = s.displayLanguageName + if (s.isAutoGenerated) " (auto)" else ""
            com.gr4nt.itube.ui.components.SheetAction(
                if (selected?.content == s.content) Icons.Rounded.Check else Icons.Rounded.ClosedCaption,
                label,
            ) { PlaybackEngine.setSubtitle(s); app.sheet = null }
        }
    }
}

@Composable
fun CommentsSheet(videoUrl: String) {
    val c = AppTheme.colors
    val scope = rememberCoroutineScope()
    var comments by remember(videoUrl) { mutableStateOf<List<Comment>>(emptyList()) }
    var next by remember(videoUrl) { mutableStateOf<(suspend () -> PageResult<Comment>)?>(null) }
    var loading by remember(videoUrl) { mutableStateOf(true) }
    var error by remember(videoUrl) { mutableStateOf<String?>(null) }
    var disabled by remember(videoUrl) { mutableStateOf(false) }

    LaunchedEffect(videoUrl) {
        try {
            val (off, page) = Repo.comments(videoUrl)
            disabled = off
            comments = page.items
            next = page.next
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = e.message ?: "Could not load comments"
        }
        loading = false
    }

    Column(Modifier.heightIn(max = 600.dp)) {
        SheetTitle("Comments")
        LazyColumn(Modifier.fillMaxWidth()) {
            if (disabled) item { ErrorBox("Comments are turned off") }
            error?.let { item { ErrorBox(it) } }
            items(comments) { cm -> CommentRow(cm, videoUrl) }
            if (loading) item { LoadingBox() }
            val n = next
            if (n != null && !loading) {
                item {
                    Text(
                        "Load more",
                        color = c.accent,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                loading = true
                                scope.launch {
                                    runCatching { n() }.onSuccess { comments = comments + it.items; next = it.next }
                                        .onFailure { next = null }
                                    loading = false
                                }
                            }
                            .padding(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentRow(cm: Comment, videoUrl: String) {
    val c = AppTheme.colors
    val scope = rememberCoroutineScope()
    var replies by remember { mutableStateOf<List<Comment>?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val text = remember(cm.text) { Html.fromHtml(cm.text, Html.FROM_HTML_MODE_COMPACT).toString().trim() }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Avatar(cm.authorAvatar, 34.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (cm.pinned) Icon(Icons.Rounded.PushPin, "Pinned", tint = c.secondaryLabel, modifier = Modifier.size(14.dp).padding(end = 2.dp))
                Text(Format.meta(cm.author, cm.date), style = MaterialTheme.typography.labelMedium, color = c.secondaryLabel, maxLines = 1)
            }
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = c.label,
                maxLines = if (expanded) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { expanded = !expanded },
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Rounded.ThumbUp, null, tint = c.secondaryLabel, modifier = Modifier.size(14.dp))
                Text(if (cm.likes > 0) " ${Format.count(cm.likes.toLong())}" else "", style = MaterialTheme.typography.labelMedium, color = c.secondaryLabel)
                if (cm.hearted) Icon(Icons.Rounded.Favorite, "Hearted", tint = c.accent, modifier = Modifier.padding(start = 10.dp).size(14.dp))
                val page = cm.replies
                if (cm.replyCount > 0 && page != null && replies == null) {
                    Text(
                        "  ${cm.replyCount} replies",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.accent,
                        modifier = Modifier.clickable {
                            scope.launch { replies = runCatching { Repo.replies(videoUrl, page).items }.getOrDefault(emptyList()) }
                        },
                    )
                }
            }
            replies?.forEach { r ->
                Row(Modifier.padding(top = 10.dp)) {
                    Avatar(r.authorAvatar, 24.dp)
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(Format.meta(r.author, r.date), style = MaterialTheme.typography.labelSmall, color = c.secondaryLabel)
                        Text(Html.fromHtml(r.text, Html.FROM_HTML_MODE_COMPACT).toString().trim(), style = MaterialTheme.typography.bodySmall, color = c.label)
                    }
                }
            }
        }
    }
}

/** Floating glass mini player shown above the tab bar while browsing. */
@Composable
fun MiniPlayer(modifier: Modifier = Modifier) {
    val app = LocalAppState.current
    val c = AppTheme.colors
    val current by PlaybackEngine.current.collectAsState()
    val isPlaying by PlaybackEngine.isPlaying.collectAsState()
    val audioOnly by PlaybackEngine.audioOnly.collectAsState()
    val v = current ?: return
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .liquidGlass(RoundedCornerShape(24.dp), elevation = 18.dp)
            .clickable(interactionSource = interaction, indication = null) {
                if (audioOnly) PlaybackEngine.setAudioOnly(false)
                app.playerExpanded = true
            }
            .padding(6.dp),
    ) {
        Box(
            Modifier
                .width(104.dp)
                .aspectRatio(16f / 9f)
                .background(androidx.compose.ui.graphics.Color.Black, RoundedCornerShape(16.dp))
                .liquidGlass(RoundedCornerShape(16.dp), state = null, elevation = 0.dp),
        ) {
            if (audioOnly) {
                com.gr4nt.itube.ui.components.Thumb(v.thumbnail, corner = 16.dp)
            } else {
                PlayerSurface(Modifier.fillMaxSize(), zoom = true)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(v.title, style = MaterialTheme.typography.titleSmall, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (audioOnly) "${v.uploader} · audio" else v.uploader,
                style = MaterialTheme.typography.bodySmall,
                color = c.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { PlaybackEngine.togglePlay() }) {
            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/pause", tint = c.label)
        }
        IconButton(onClick = { PlaybackEngine.stop() }) {
            Icon(Icons.Rounded.Close, "Close", tint = c.label)
        }
    }
}
