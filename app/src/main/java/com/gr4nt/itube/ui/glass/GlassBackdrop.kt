package com.gr4nt.itube.ui.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.withTransform
import com.gr4nt.itube.ui.theme.AppTheme
import kotlin.math.max

/** Static color pools behind the glass. Never drawn over video playback. */
@Composable
fun GlassBackdrop() {
    val c = AppTheme.colors
    Canvas(Modifier.fillMaxSize()) {
        if (c.dark) {
            drawRect(Brush.radialGradient(
                listOf(c.glowPrimary.copy(alpha = 0.85f), Color.Transparent),
                center = Offset(size.width * 0.05f, size.height * 0.12f),
                radius = size.width * 1.05f,
            ))
            drawRect(Brush.radialGradient(
                listOf(c.glowSecondary.copy(alpha = 0.8f), Color.Transparent),
                center = Offset(size.width, size.height * 0.66f),
                radius = size.width * 1.2f,
            ))
            drawRect(Brush.radialGradient(
                listOf(c.accent.copy(alpha = 0.30f), Color.Transparent),
                center = Offset(size.width * 0.15f, size.height * 0.95f),
                radius = size.width * 0.95f,
            ))
        } else {
            drawRect(Color(0xFFF1FAF7))
            drawRect(
                Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFFD6FFF0),
                        0.5f to Color(0xFFF4FBFA),
                        1f to Color(0xFFFFF0DF),
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
            )
            // Stretch circular Compose gradients into ellipses to match the HTML radial glows.
            val extent = max(size.width, size.height).coerceAtLeast(1f)
            val sx = size.width / extent
            val sy = size.height / extent
            withTransform({ scale(sx, sy, pivot = Offset.Zero) }) {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0xA663F4CC), Color.Transparent),
                        center = Offset.Zero,
                        radius = extent * 0.65f,
                    ),
                    size = Size(extent, extent),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0xA6FFB37C), Color.Transparent),
                        center = Offset(extent, extent),
                        radius = extent * 0.65f,
                    ),
                    size = Size(extent, extent),
                )
            }
        }
    }
}
