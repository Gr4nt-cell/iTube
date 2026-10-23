package com.gr4nt.itube.ui.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
        .shadow(elevation, shape, clip = false, ambientColor = c.shadow, spotColor = c.shadow)
        .then(glass)
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
        .shadow(elevation, shape, clip = false, ambientColor = c.shadow, spotColor = c.shadow)
        .background(c.elevated.copy(alpha = if (c.dark) 0.78f else 0.86f), shape)
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
    val shape = RoundedCornerShape(18.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .pressScale(interaction)
            .height(36.dp)
            .then(
                if (selected) {
                    Modifier
                        .shadow(10.dp, shape, ambientColor = c.accent, spotColor = c.accent)
                        .background(c.accent, shape)
                } else {
                    Modifier.frosted(shape, elevation = 4.dp)
                }
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text,
            color = if (selected) Color.White else c.label,
            fontWeight = FontWeight.SemiBold,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        )
    }
}

data class TabSpec(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/** Floating Liquid Glass tab bar. */
@Composable
fun GlassTabBar(
    tabs: List<TabSpec>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(32.dp)
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .liquidGlass(shape, elevation = 20.dp)
            .padding(horizontal = 6.dp),
    ) {
        tabs.forEachIndexed { i, tab ->
            val isSel = i == selected
            val interaction = remember { MutableInteractionSource() }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .pressScale(interaction)
                    .height(54.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(if (isSel) c.label.copy(alpha = 0.08f) else Color.Transparent)
                    .clickable(interactionSource = interaction, indication = null) { onSelect(i) },
            ) {
                Icon(
                    if (isSel) tab.selectedIcon else tab.icon,
                    contentDescription = tab.label,
                    tint = if (isSel) c.accent else c.label,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    tab.label,
                    color = if (isSel) c.accent else c.label,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
