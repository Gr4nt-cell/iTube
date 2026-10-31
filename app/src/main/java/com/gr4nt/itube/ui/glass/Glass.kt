package com.gr4nt.itube.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gr4nt.itube.ui.theme.AppTheme
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass

/** The backdrop that floating glass chrome refracts (the scrolling content of the app). */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Liquid Glass surface: refraction + depth blur of whatever scrolls behind it, a light tint,
 * a specular rim, and a soft drop shadow so the element floats above the content.
 */
@Composable
fun Modifier.liquidGlass(
    shape: RoundedCornerShape,
    state: HazeState? = LocalHazeState.current,
    clear: Boolean = false,
    elevation: Dp = 14.dp,
    tint: Color? = null,
): Modifier {
    val c = AppTheme.colors
    val style = remember(c, shape, clear, tint) {
        (if (clear) GlassStyle.clear else GlassStyle.regular) then GlassStyle {
            this.shape(shape)
            tint(tint ?: c.glassTint)
            edgeShadow(Color.Black.copy(alpha = if (c.dark) 0.30f else 0.10f))
        }
    }
    val rim = remember(c) {
        Brush.linearGradient(
            listOf(c.glassRim, c.glassRim.copy(alpha = 0.06f), c.glassRim.copy(alpha = 0.40f)),
        )
    }
    val glass = if (state != null) {
        Modifier.hazeGlass(input = HazeInput.Sources(state), style = style)
    } else {
        Modifier.background(c.elevated.copy(alpha = 0.94f), shape)
    }
    return this
        .depthShadow(shape, elevation)
        .then(glass)
        .background(
            Brush.verticalGradient(listOf(c.glassRim.copy(alpha = if (c.dark) 0.08f else 0.30f), Color.Transparent)),
            shape,
        )
        .then(if (!c.dark) Modifier.border(0.7.dp, Color.Black.copy(alpha = 0.10f), shape) else Modifier)
        .border(0.8.dp, rim, shape)
        .clip(shape)
}

/**
 * Lightweight frosted surface for content inside scrolling lists (no live backdrop sampling,
 * so it stays cheap while keeping the glass rim and depth shadow).
 */
@Composable
fun Modifier.frosted(shape: RoundedCornerShape, elevation: Dp = 8.dp): Modifier {
    val c = AppTheme.colors
    val rim = remember(c) {
        Brush.linearGradient(listOf(c.glassRim.copy(alpha = 0.9f), c.glassRim.copy(alpha = 0.05f)))
    }
    return this
        .depthShadow(shape, elevation)
        .background(Brush.linearGradient(listOf(
            c.elevated.copy(alpha = if (c.dark) 0.82f else 0.90f),
            c.elevated.copy(alpha = if (c.dark) 0.62f else 0.68f),
        )), shape)
        .then(if (!c.dark) Modifier.border(0.6.dp, Color.Black.copy(alpha = 0.08f), shape) else Modifier)
        .border(0.6.dp, rim, shape)
        .clip(shape)
}

/** Springy press feedback, like iOS controls. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
    return scale(s)
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    state: HazeState? = LocalHazeState.current,
    clear: Boolean = false,
    tint: Color = AppTheme.colors.label,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .pressScale(interaction)
            .size(size)
            .liquidGlass(CircleShape, state = state, clear = clear, elevation = 10.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    state: HazeState? = LocalHazeState.current,
    height: Dp = 44.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .then(if (onClick != null) Modifier.pressScale(interaction) else Modifier)
            .height(height)
            .liquidGlass(RoundedCornerShape(height / 2), state = state)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 14.dp),
        content = content,
    )
}

@Composable
fun GlassChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(22.dp)
    val bg by animateColorAsState(if (selected) c.accent else c.elevated.copy(alpha = if (c.dark) 0.78f else 0.86f), label = "chipBg")
    val fg by animateColorAsState(if (selected) Color.White else c.label, label = "chipFg")
    val glow = if (selected) c.accent.copy(alpha = 0.7f) else c.shadow
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .pressScale(interaction)
            .height(40.dp)
            .depthShadow(shape, if (selected) 10.dp else 6.dp, tint = if (selected) glow else null)
            .background(bg, shape)
            .border(0.6.dp, c.glassRim.copy(alpha = if (selected) 0.5f else 0.35f), shape)
            .clip(shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text,
            color = fg,
            fontWeight = FontWeight.SemiBold,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        )
    }
}

data class TabSpec(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/**
 * Floating Liquid Glass tab bar. The selection highlight is a glass lens you can drag across
 * the tabs; it magnifies while held and snaps to the nearest tab on release.
 */
@Composable
fun GlassTabBar(
    tabs: List<TabSpec>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(36.dp)
    val lensShape = RoundedCornerShape(29.dp)
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selected)
    BoxWithConstraints(
        modifier
            .widthIn(max = 620.dp)
            .fillMaxWidth()
            .height(72.dp)
            .liquidGlass(shape, elevation = 20.dp)
            .padding(6.dp),
    ) {
        val count = tabs.size.coerceAtLeast(1)
        val tabW = maxWidth / count
        val tabWpx = with(density) { tabW.toPx() }
        val maxOffset = (count - 1) * tabWpx
        val scope = rememberCoroutineScope()
        val offset = remember { Animatable(selected * tabWpx) }
        var dragging by remember { mutableStateOf(false) }
        val snapSpec = spring<Float>(dampingRatio = 0.68f, stiffness = 420f)

        LaunchedEffect(selected, tabWpx) {
            if (!dragging) offset.animateTo(selected * tabWpx, snapSpec)
        }
        val lens by animateFloatAsState(if (dragging) 1.14f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "lens")
        val hovered = ((offset.value + tabWpx / 2) / tabWpx).toInt().coerceIn(0, count - 1)
        LaunchedEffect(hovered) {
            if (dragging) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }

        fun settle() {
            dragging = false
            val target = ((offset.value + tabWpx / 2) / tabWpx).toInt().coerceIn(0, count - 1)
            scope.launch { offset.animateTo(target * tabWpx, snapSpec) }
            if (target != currentSelected) currentOnSelect(target)
        }

        // The glass lens
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .width(tabW)
                .fillMaxHeight()
                .graphicsLayer {
                    scaleX = lens
                    scaleY = lens
                }
                .depthShadow(lensShape, if (dragging) 14.dp else 4.dp)
                .background(c.label.copy(alpha = if (dragging) 0.14f else 0.06f), lensShape)
                .border(
                    0.8.dp,
                    Brush.linearGradient(listOf(c.glassRim, c.glassRim.copy(alpha = 0.08f), c.glassRim.copy(alpha = 0.5f))),
                    lensShape,
                ),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(count, tabWpx) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { settle() },
                        onDragCancel = { settle() },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            scope.launch { offset.snapTo((offset.value + amount).coerceIn(0f, maxOffset)) }
                        },
                    )
                },
        ) {
            tabs.forEachIndexed { i, tab ->
                val active = if (dragging) i == hovered else i == selected
                val interaction = remember { MutableInteractionSource() }
                val tint by animateColorAsState(if (active) c.accent else c.label, label = "tabTint")
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressScale(interaction)
                        .clickable(interactionSource = interaction, indication = null) { onSelect(i) },
                ) {
                    Icon(
                        if (active) tab.selectedIcon else tab.icon,
                        contentDescription = tab.label,
                        tint = tint,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        tab.label,
                        color = tint,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
