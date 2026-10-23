package com.gr4nt.itube.ui.screens

import android.content.Intent
import android.view.LayoutInflater
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.gr4nt.itube.R
import com.gr4nt.itube.data.ChannelItem
import com.gr4nt.itube.data.Dislikes
import com.gr4nt.itube.data.Repo
import com.gr4nt.itube.data.Settings
import com.gr4nt.itube.data.Store
import com.gr4nt.itube.data.VideoItem
import com.gr4nt.itube.data.best
import com.gr4nt.itube.player.ShortsPlayer
import com.gr4nt.itube.ui.LocalAppState
import com.gr4nt.itube.ui.LocalChrome
import com.gr4nt.itube.ui.Screen
import com.gr4nt.itube.ui.components.Avatar
import com.gr4nt.itube.ui.components.DownloadLoaderSheet
import com.gr4nt.itube.ui.components.ErrorBox
import com.gr4nt.itube.ui.components.Format
import com.gr4nt.itube.ui.components.SubscribeButton
import com.gr4nt.itube.ui.glass.liquidGlass
import com.gr4nt.itube.ui.glass.pressScale
import com.gr4nt.itube.ui.theme.AppTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ShortsScreen() {
    val subs by Store.subscriptions.collectAsState()
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var next by remember { mutableStateOf<(suspend () -> com.gr4nt.itube.data.PageResult<com.gr4nt.itube.data.Item>)?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(retry) {
        loading = true
        error = null
        try {
            val r = Repo.shorts(subs)
            items = r.items.filterIsInstance<VideoItem>()
            next = r.next
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = e.message ?: "Could not load Shorts"
        }
        loading = false
    }

    // Stop the Shorts player when leaving the tab or the app.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) ShortsPlayer.pause()
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            ShortsPlayer.stop()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            loading && items.isEmpty() -> CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
            error != null && items.isEmpty() -> Box(Modifier.align(Alignment.Center)) { ErrorBox(error!!, onRetry = { retry++ }) }
            else -> {
                val pager = rememberPagerState { items.size }
                LaunchedEffect(pager, items) {
                    snapshotFlow { pager.currentPage }.collect { page ->
                        items.getOrNull(page)?.let { ShortsPlayer.play(it) }
                        items.getOrNull(page + 1)?.let { ShortsPlayer.prefetch(it) }
                        val n = next
                        if (page >= items.size - 4 && n != null && !loadingMore) {
                            loadingMore = true
                            scope.launch {
                                runCatching { n() }.onSuccess { r ->
                                    items = (items + r.items.filterIsInstance<VideoItem>()).distinctBy { it.url }
                                    next = r.next
                                }
                                loadingMore = false
                            }
                        }
                    }
                }
                VerticalPager(
                    state = pager,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val video = items[page]
                    ShortPage(video, isCurrent = pager.currentPage == page)
                }
            }
        }
        // Title pill
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(top = LocalChrome.current.top - 56.dp, start = 16.dp),
        ) {
            Text(
                "Shorts",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ShortPage(video: VideoItem, isCurrent: Boolean) {
    val app = LocalAppState.current
    val ctx = LocalContext.current
    val chrome = LocalChrome.current
    val haze = rememberHazeState()
    val currentUrl by ShortsPlayer.currentUrl.collectAsState()
    val info by ShortsPlayer.info.collectAsState()
    val error by ShortsPlayer.error.collectAsState()
    val showDislikes by Settings.showDislikes.collectAsState()
    val active = isCurrent && currentUrl == video.url
    val pageInfo = info?.takeIf { active && it.url.contains(Repo.videoId(video.url) ?: "~") }
    var paused by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var dislikes by remember(video.url) { mutableStateOf<Long?>(null) }

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        paused = false
        val p = ShortsPlayer.player
        while (true) {
            val d = p.duration
            progress = if (d > 0) p.currentPosition.toFloat() / d else 0f
            delay(120)
        }
    }
    LaunchedEffect(video.url, isCurrent, showDislikes) {
        if (isCurrent && showDislikes) Repo.videoId(video.url)?.let { dislikes = Dislikes.votes(it)?.dislikes }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier
                .fillMaxSize()
                .hazeSource(haze),
        ) {
            AsyncImage(
                model = video.thumbnail?.replace("hqdefault", "oardefault") ?: video.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (active) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c ->
                        (LayoutInflater.from(c).inflate(R.layout.player_surface, null) as PlayerView).apply {
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        }
                    },
                    update = { v -> if (v.player !== ShortsPlayer.player) v.player = ShortsPlayer.player },
                    onRelease = { v -> v.player = null },
                )
            }
            // Bottom gradient for legible text
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))),
            )
        }

        // Tap = pause/resume, double-tap = like animation feel (seek restart)
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(active) {
                    detectTapGestures(
                        onTap = {
                            if (active) {
                                ShortsPlayer.toggle()
                                paused = !ShortsPlayer.player.playWhenReady
                            }
                        },
                        onDoubleTap = { ShortsPlayer.player.seekTo(0) },
                    )
                },
        )

        AnimatedVisibility(
            visible = paused && active,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                Modifier.size(84.dp).liquidGlass(CircleShape, state = haze, clear = true),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(46.dp)) }
        }
        if (active && error != null) {
            Text(
                error!!,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        }

        // Right-side glass action column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = chrome.bottom + 12.dp),
        ) {
            val likes = pageInfo?.likeCount ?: -1
            ShortAction(Icons.Rounded.ThumbUp, if (likes >= 0) Format.count(likes) else "Like", haze) {}
            if (showDislikes) ShortAction(Icons.Rounded.ThumbDown, dislikes?.let { Format.count(it) } ?: "—", haze) {}
            ShortAction(Icons.AutoMirrored.Rounded.Comment, "Comments", haze) {
                app.showSheet { CommentsSheet(video.url) }
            }
            ShortAction(Icons.Rounded.Share, "Share", haze) {
                ctx.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, "https://youtube.com/shorts/${Repo.videoId(video.url)}"),
                        "Share",
                    )
                )
            }
            ShortAction(Icons.Rounded.Download, "Save", haze) { app.showSheet { DownloadLoaderSheet(video.url) } }
            ShortAction(if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, if (muted) "Muted" else "Sound", haze) {
                muted = !muted
                ShortsPlayer.player.volume = if (muted) 0f else 1f
            }
        }

        // Title + channel
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, end = 90.dp, bottom = chrome.bottom + 16.dp),
        ) {
            val channelUrl = pageInfo?.uploaderUrl ?: video.uploaderUrl
            val channelName = pageInfo?.uploaderName ?: video.uploader
            val avatar = pageInfo?.uploaderAvatars?.best(96) ?: video.uploaderAvatar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(enabled = channelUrl != null) { channelUrl?.let { app.push(Screen.Channel(it)) } },
            ) {
                Avatar(avatar, 36.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    channelName.ifEmpty { "Channel" },
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (channelUrl != null) {
                    Spacer(Modifier.width(10.dp))
                    SubscribeButton(ChannelItem(url = channelUrl, title = channelName, thumbnail = avatar))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                pageInfo?.name ?: video.title,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Progress line
        if (active) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = chrome.bottom - 4.dp)
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AppTheme.colors.accent),
            )
        }
    }
}

@Composable
private fun ShortAction(icon: ImageVector, label: String, haze: dev.chrisbanes.haze.HazeState, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .pressScale(interaction)
                .size(50.dp)
                .liquidGlass(CircleShape, state = haze, clear = true, elevation = 12.dp)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp)) }
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
