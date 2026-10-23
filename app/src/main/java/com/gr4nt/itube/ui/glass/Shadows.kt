package com.gr4nt.itube.ui.glass

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gr4nt.itube.ui.theme.AppTheme

/**
 * Depth shadow for floating UI. In light mode Android's elevation shadows are very faint, so we
 * draw two blurred layers ourselves: a tight contact shadow hugging the edges and a soft, offset
 * drop shadow. Dark mode keeps the platform elevation shadow.
 */
@Composable
fun Modifier.depthShadow(shape: Shape, elevation: Dp, tint: Color? = null): Modifier {
    val c = AppTheme.colors
    if (elevation <= 0.dp) return this
    if (c.dark || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        val col = tint ?: c.shadow
        return this.shadow(elevation, shape, clip = false, ambientColor = col, spotColor = col)
    }
    val strength = (elevation.value / 14f).coerceIn(0.35f, 1.6f)
    val base = tint ?: Color.Black
    return this.drawBehind {
        // Soft ambient drop shadow
        blurredShape(shape, base.copy(alpha = (0.20f * strength).coerceAtMost(0.32f)), blur = elevation * 1.4f, dy = elevation * 0.45f, spread = 0.dp)
        // Tight contact shadow that outlines the edges
        blurredShape(shape, base.copy(alpha = (0.22f * strength).coerceAtMost(0.30f)), blur = 3.dp, dy = 1.dp, spread = 0.5.dp)
    }
}

private fun DrawScope.blurredShape(shape: Shape, color: Color, blur: Dp, dy: Dp, spread: Dp) {
    val outline = shape.createOutline(size, layoutDirection, this)
    val s = spread.toPx()
    val offset = dy.toPx()
    drawIntoCanvas { canvas ->
        val paint = Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
            if (blur.toPx() > 0f) maskFilter = BlurMaskFilter(blur.toPx(), BlurMaskFilter.Blur.NORMAL)
        }
        val native = canvas.nativeCanvas
        native.save()
        native.translate(0f, offset)
        when (outline) {
            is Outline.Rounded -> {
                val r = outline.roundRect
                native.drawRoundRect(
                    r.left - s, r.top - s, r.right + s, r.bottom + s,
                    r.topLeftCornerRadius.x + s, r.topLeftCornerRadius.y + s,
                    paint,
                )
            }
            is Outline.Rectangle -> {
                val r = outline.rect
                native.drawRect(r.left - s, r.top - s, r.right + s, r.bottom + s, paint)
            }
            is Outline.Generic -> {
                native.drawPath(outline.path.asAndroidPath(), paint)
            }
        }
        native.restore()
    }
}
