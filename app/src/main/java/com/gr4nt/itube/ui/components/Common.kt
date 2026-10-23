package com.gr4nt.itube.ui.components

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gr4nt.itube.data.ChannelItem
import com.gr4nt.itube.data.Item
import com.gr4nt.itube.data.PageResult
import com.gr4nt.itube.data.Repo
import com.gr4nt.itube.data.Store
import com.gr4nt.itube.data.VideoItem
import com.gr4nt.itube.download.DownloadOption
import com.gr4nt.itube.download.Downloads
import com.gr4nt.itube.player.PlaybackEngine
import com.gr4nt.itube.player.PopupPlayerService
import com.gr4nt.itube.ui.LocalAppState
import com.gr4nt.itube.ui.Screen
import com.gr4nt.itube.ui.glass.GlassIconButton
import com.gr4nt.itube.ui.glass.liquidGlass
import com.gr4nt.itube.ui.glass.pressScale
import com.gr4nt.itube.ui.theme.AppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfo

@Composable
fun SubscribeButton(channel: ChannelItem, modifier: Modifier = Modifier) {
    val subs by Store.subscriptions.collectAsState()
    val subscribed = subs.any { it.url == channel.url }
    val c = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(18.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .pressScale(interaction)
            .then(
                if (subscribed) Modifier.background(c.chip, shape)
                else Modifier.background(c.label, shape)
            )
            .clickable(interactionSource = interaction, indication = null) { Store.toggleSubscription(channel) }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            if (subscribed) "Subscribed" else "Subscribe",
            color = if (subscribed) c.label else c.background,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Icon(icon, null, tint = c.label, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = c.label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun SheetTitle(text: String, sub: String? = null) {
    val c = AppTheme.colors
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel)
    }
}

/** Long-press / overflow menu for a video. */
@Composable
fun VideoActionsSheet(video: VideoItem) {
    val app = LocalAppState.current
    val ctx = LocalContext.current
    Column {
        SheetTitle(video.title, video.uploader)
        SheetAction(Icons.Rounded.PlayArrow, "Play") { app.sheet = null; app.openVideo(video) }
        SheetAction(Icons.Rounded.Headphones, "Play in background") {
            app.sheet = null
            PlaybackEngine.playInBackground(video)
        }
        SheetAction(Icons.Rounded.PictureInPictureAlt, "Play in pop-up") {
            app.sheet = null
            if (!PopupPlayerService.canDrawOverlays(ctx)) {
                PlaybackEngine.emit("Allow \"Display over other apps\" for the pop-up player")
                ctx.startActivity(PopupPlayerService.overlayPermissionIntent(ctx))
            } else {
                PlaybackEngine.play(video)
                PopupPlayerService.start(ctx)
            }
        }
        SheetAction(Icons.Rounded.WatchLater, if (Store.inWatchLater(video.url)) "Remove from Watch later" else "Save to Watch later") {
            Store.toggleWatchLater(video)
            app.sheet = null
        }
        SheetAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist") { app.sheet = { AddToPlaylistSheet(video) } }
        SheetAction(Icons.Rounded.Download, "Download") {
            app.sheet = { DownloadLoaderSheet(video.url) }
        }
        SheetAction(Icons.Rounded.Share, "Share") {
            app.sheet = null
            ctx.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, video.url),
                    "Share",
                )
            )
        }
        video.uploaderUrl?.let { url ->
            SheetAction(Icons.Rounded.Person, "Go to channel") {
                app.sheet = null
                app.push(Screen.Channel(url))
            }
        }
    }
}

@Composable
fun AddToPlaylistSheet(video: VideoItem) {
    val app = LocalAppState.current
    val lists by Store.playlists.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    Column {
        SheetTitle("Add to playlist")
        lists.forEach { pl ->
            SheetAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "${pl.name} (${pl.videos.size})") {
                Store.addToPlaylist(pl.id, video)
                PlaybackEngine.emit("Added to ${pl.name}")
                app.sheet = null
            }
        }
        if (creating) {
            GlassTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Playlist name",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                onSubmit = {
                    if (name.isNotBlank()) {
                        val id = Store.createPlaylist(name.trim())
                        Store.addToPlaylist(id, video)
                        PlaybackEngine.emit("Added to ${name.trim()}")
                        app.sheet = null
                    }
                },
            )
        } else {
            SheetAction(Icons.Rounded.Add, "New playlist") { creating = true }
        }
    }
}

@Composable
fun DownloadLoaderSheet(url: String) {
    var info by remember { mutableStateOf<StreamInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(url) {
        try {
            info = PlaybackEngine.info.value?.takeIf { it.url == url } ?: Repo.stream(url)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = e.message ?: "Could not load formats"
        }
    }
    when {
        info != null -> DownloadSheet(info!!)
        error != null -> ErrorBox(error!!, Modifier.heightIn(min = 160.dp))
        else -> LoadingBox(Modifier.heightIn(min = 160.dp))
    }
}

@Composable
fun DownloadSheet(info: StreamInfo) {
    val app = LocalAppState.current
    val ctx = LocalContext.current
    val c = AppTheme.colors
    val options = remember(info) { Downloads.options(info) }
    Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
        SheetTitle("Download", info.name)
        if (options.isEmpty()) {
            Text("No downloadable formats for this video.", color = c.secondaryLabel, modifier = Modifier.padding(20.dp))
        }
        val (videos, audios) = options.partition { !it.audioOnly }
        if (videos.isNotEmpty()) SectionLabel("Video")
        videos.forEach { DownloadOptionRow(it) { Downloads.enqueue(ctx, info, it); PlaybackEngine.emit("Downloading ${it.label}…"); app.sheet = null } }
        if (audios.isNotEmpty()) SectionLabel("Audio only")
        audios.forEach { DownloadOptionRow(it) { Downloads.enqueue(ctx, info, it); PlaybackEngine.emit("Downloading audio…"); app.sheet = null } }
    }
}

@Composable
private fun DownloadOptionRow(o: DownloadOption, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Icon(if (o.audioOnly) Icons.Rounded.Headphones else Icons.Rounded.Download, null, tint = c.accent)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(o.label, color = c.label, style = MaterialTheme.typography.titleSmall)
            Text(o.detail, color = c.secondaryLabel, style = MaterialTheme.typography.bodySmall)
        }
        Text(Format.bytes(o.sizeBytes), color = c.secondaryLabel, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = AppTheme.colors.secondaryLabel,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AppTheme.colors.accent, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
    }
}

@Composable
fun ErrorBox(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    val c = AppTheme.colors
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, color = c.secondaryLabel, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        if (onRetry != null) {
            TextButton(onClick = onRetry) {
                Icon(Icons.Rounded.Refresh, null, tint = c.accent)
                Spacer(Modifier.width(6.dp))
                Text("Retry", color = c.accent)
            }
        }
    }
}

@Composable
fun EmptyBox(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.label, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.size(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel, textAlign = TextAlign.Center)
        }
    }
}

/** Infinite list fed by a [PageResult] loader, with pull-to-refresh. */
@Composable
fun PagedList(
    key: Any,
    load: suspend () -> PageResult<Item>,
    contentPadding: PaddingValues,
    large: Boolean = true,
    modifier: Modifier = Modifier,
    header: (LazyListScope.() -> Unit)? = null,
) {
    var items by remember(key) { mutableStateOf<List<Item>>(emptyList()) }
    var next by remember(key) { mutableStateOf<(suspend () -> PageResult<Item>)?>(null) }
    var loading by remember(key) { mutableStateOf(true) }
    var refreshing by remember(key) { mutableStateOf(false) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    var token by remember(key) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(key, token) {
        loading = true
        error = null
        try {
            val r = load()
            items = r.items
            next = r.next
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = e.message ?: e.javaClass.simpleName
        }
        loading = false
        refreshing = false
    }

    LaunchedEffect(listState, key) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 5
        }.collect { nearEnd ->
            val n = next
            if (nearEnd && n != null && !loading) {
                loading = true
                scope.launch {
                    try {
                        val r = n()
                        items = items + r.items
                        next = r.next
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        next = null
                    }
                    loading = false
                }
            }
        }
    }

    val videos = remember(items) { items.filterIsInstance<VideoItem>() }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refreshing = true; token++ },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            header?.invoke(this)
            items(items) { item -> ItemView(item, videos, large) }
            if (loading) item { LoadingBox() }
            val err = error
            if (err != null && items.isEmpty()) item { ErrorBox(err, onRetry = { token++ }) }
            if (!loading && err == null && items.isEmpty()) item { EmptyBox("Nothing here yet") }
        }
    }
}

/** Floating glass header used by pushed screens. */
@Composable
fun FloatingHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val c = AppTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (onBack != null) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(10.dp))
        }
        Box(
            Modifier
                .weight(1f)
                .liquidGlass(RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 11.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        actions()
    }
}

/** Glass bottom sheet rendered inside the app window so it can refract the content behind it. */
@Composable
fun GlassSheetHost() {
    val app = LocalAppState.current
    val content = app.sheet
    AnimatedVisibility(visible = content != null, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { app.sheet = null },
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = content != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            var last by remember { mutableStateOf(content) }
            if (content != null) last = content
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .navigationBarsPadding()
                    .liquidGlass(RoundedCornerShape(32.dp), elevation = 28.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, drag -> if (drag > 25f) app.sheet = null }
                    }
                    .padding(vertical = 10.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 6.dp)
                        .size(width = 40.dp, height = 5.dp)
                        .background(AppTheme.colors.tertiaryLabel, RoundedCornerShape(3.dp)),
                )
                last?.invoke()
            }
        }
    }
}
