package com.gr4nt.itube.ui.screens

import android.view.LayoutInflater
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.gr4nt.itube.R
import com.gr4nt.itube.data.SbCategory
import com.gr4nt.itube.player.PlaybackEngine
import com.gr4nt.itube.player.SurfaceOwner
import com.gr4nt.itube.ui.LocalAppState
import com.gr4nt.itube.ui.components.Format
import com.gr4nt.itube.ui.glass.GlassIconButton
import com.gr4nt.itube.ui.glass.liquidGlass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

/** The video surface. Gives the surface up while the floating pop-up owns the video. */
@Composable
fun PlayerSurface(modifier: Modifier = Modifier, zoom: Boolean = false) {
    val owner by PlaybackEngine.surfaceOwner.collectAsState()
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.player_surface, null) as PlayerView)
        },
        update = { view ->
            val target = if (owner == SurfaceOwner.MAIN) PlaybackEngine.player else null
            if (view.player !== target) view.player = target
            view.resizeMode = if (zoom) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
        },
        onRelease = { view -> view.player = null },
    )
}

/** Polls the player for progress values. */
@Composable
private fun rememberProgress(): Triple<Long, Long, Long> {
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var buf by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val p = PlaybackEngine.player
        while (true) {
            pos = p.currentPosition
            dur = p.duration.coerceAtLeast(0)
            buf = p.bufferedPosition
            delay(250)
        }
    }
    return Triple(pos, dur, buf)
}

/** Seek bar with buffered range and SponsorBlock segment markers. */
@Composable
fun SeekBar(position: Long, duration: Long, buffered: Long, modifier: Modifier = Modifier, onSeek: (Long) -> Unit) {
    val segments by PlaybackEngine.segments.collectAsState()
    var dragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableFloatStateOf(0f) }
    val frac = if (dragging) dragFrac else if (duration > 0) position.toFloat() / duration else 0f
    Canvas(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(duration) {
                detectTapGestures { o ->
                    if (duration > 0) onSeek((o.x / size.width * duration).toLong())
                }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragging = true; dragFrac = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { dragging = false; if (duration > 0) onSeek((dragFrac * duration).toLong()) },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { change, _ -> dragFrac = (change.position.x / size.width).coerceIn(0f, 1f) },
                )
            },
    ) {
        val h = if (dragging) 6.dp.toPx() else 4.dp.toPx()
        val y = size.height / 2 - h / 2
        val r = CornerRadius(h / 2)
        drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(0f, y), Size(size.width, h), r)
        if (duration > 0) {
            drawRoundRect(Color.White.copy(alpha = 0.40f), Offset(0f, y), Size(size.width * (buffered.toFloat() / duration).coerceIn(0f, 1f), h), r)
            segments.forEach { s ->
                val x0 = size.width * s.startMs / duration
                val x1 = size.width * s.endMs / duration
                drawRect(Color(SbCategory.of(s.category)?.color ?: 0xFF00D400), Offset(x0, y), Size((x1 - x0).coerceAtLeast(2f), h))
            }
        }
        drawRoundRect(Color(0xFFFF2D55), Offset(0f, y), Size(size.width * frac.coerceIn(0f, 1f), h), r)
        val thumb = if (dragging) 9.dp.toPx() else 7.dp.toPx()
        drawCircle(Color.White, thumb, Offset(size.width * frac.coerceIn(0f, 1f), size.height / 2))
    }
}

/**
 * The player with glass controls. The glass refracts the live video because the surface is a
 * TextureView captured as a haze source.
 */
@Composable
fun PlayerBox(modifier: Modifier = Modifier, fullscreen: Boolean, onSettings: () -> Unit, onCaptions: () -> Unit) {
    val app = LocalAppState.current
    val playerHaze = rememberHazeState()
    val loading by PlaybackEngine.loading.collectAsState()
    val error by PlaybackEngine.error.collectAsState()
    val isPlaying by PlaybackEngine.isPlaying.collectAsState()
    val current by PlaybackEngine.current.collectAsState()
    val height by PlaybackEngine.height.collectAsState()
    var visible by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val (pos, dur, buf) = rememberProgress()
    val player = PlaybackEngine.player

    LaunchedEffect(pos) { buffering = player.playbackState == Player.STATE_BUFFERING }
    LaunchedEffect(visible, isPlaying, lastInteraction) {
        if (visible && isPlaying) {
            delay(3500)
            visible = false
        }
    }
    fun poke() { lastInteraction = System.currentTimeMillis() }

    Box(modifier.background(Color.Black)) {
        PlayerSurface(
            Modifier
                .fillMaxSize()
                .hazeSource(playerHaze),
        )
        // Gesture layer: tap toggles controls, double-tap sides seeks 10 s.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { visible = !visible; poke() },
                        onDoubleTap = { o ->
                            if (o.x < size.width / 2) player.seekBack() else player.seekForward()
                            PlaybackEngine.emit(if (o.x < size.width / 2) "−10 s" else "+10 s")
                        },
                    )
                },
        )
        if (loading || buffering) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.align(Alignment.Center).size(42.dp))
        }
        error?.let {
            Text(
                it,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .background(Color(0x99000000), RoundedCornerShape(12.dp))
                    .padding(12.dp),
            )
        }
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Controls(
                playerHaze = playerHaze,
                fullscreen = fullscreen,
                title = current?.title.orEmpty(),
                isPlaying = isPlaying,
                pos = pos, dur = dur, buf = buf,
                quality = height,
                onMinimize = { if (fullscreen) app.fullscreen = false else app.playerExpanded = false },
                onSettings = { poke(); onSettings() },
                onCaptions = { poke(); onCaptions() },
                onToggle = { poke(); PlaybackEngine.togglePlay() },
                onPrev = { poke(); PlaybackEngine.previous() },
                onNext = { poke(); PlaybackEngine.next() },
                onSeek = { poke(); player.seekTo(it) },
                onFullscreen = { app.fullscreen = !app.fullscreen },
            )
        }
    }
}

@Composable
private fun Controls(
    playerHaze: HazeState,
    fullscreen: Boolean,
    title: String,
    isPlaying: Boolean,
    pos: Long,
    dur: Long,
    buf: Long,
    quality: Int,
    onMinimize: () -> Unit,
    onSettings: () -> Unit,
    onCaptions: () -> Unit,
    onToggle: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onFullscreen: () -> Unit,
) {
    val small = if (fullscreen) 46.dp else 38.dp
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f))) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(10.dp),
        ) {
            GlassIconButton(Icons.Rounded.KeyboardArrowDown, "Minimize", onMinimize, size = small, state = playerHaze, clear = true, tint = Color.White)
            Text(
                if (fullscreen) title else "",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            GlassIconButton(Icons.Rounded.ClosedCaption, "Captions", onCaptions, size = small, state = playerHaze, clear = true, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            GlassIconButton(Icons.Rounded.Settings, "Quality and speed", onSettings, size = small, state = playerHaze, clear = true, tint = Color.White)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (fullscreen) 36.dp else 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.Center),
        ) {
            GlassIconButton(Icons.Rounded.SkipPrevious, "Previous", onPrev, size = small + 4.dp, state = playerHaze, clear = true, tint = Color.White)
            GlassIconButton(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (isPlaying) "Pause" else "Play",
                onToggle,
                size = if (fullscreen) 76.dp else 64.dp,
                state = playerHaze,
                clear = true,
                tint = Color.White,
            )
            GlassIconButton(Icons.Rounded.SkipNext, "Next", onNext, size = small + 4.dp, state = playerHaze, clear = true, tint = Color.White)
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .liquidGlass(RoundedCornerShape(14.dp), state = playerHaze, clear = true, elevation = 6.dp)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        "${Format.time(pos)} / ${Format.time(dur)}" + if (quality > 0) "  ·  ${quality}p" else "",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.weight(1f))
                GlassIconButton(
                    if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                    "Fullscreen",
                    onFullscreen,
                    size = small,
                    state = playerHaze,
                    clear = true,
                    tint = Color.White,
                )
            }
            SeekBar(pos, dur, buf, onSeek = onSeek)
        }
    }
}
