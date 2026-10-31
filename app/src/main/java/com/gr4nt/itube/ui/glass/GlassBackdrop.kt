package com.gr4nt.itube.ui.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.gr4nt.itube.ui.theme.AppTheme

/** Static color pools behind the glass. Never drawn over video playback. */
@Composable
fun GlassBackdrop() {
    val c = AppTheme.colors
    Canvas(Modifier.fillMaxSize()) {
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
    }
}
