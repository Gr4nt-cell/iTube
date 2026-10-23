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
 * Edge shadow for floating UI in light mode: a crisp contact line plus a soft halo drawn only
 * outside the element's outline. Dark mode keeps the platform elevation shadow.
 */
@Composable
fun Modifier.depthShadow(shape: Shape, elevation: Dp, tint: Color? = null): Modifier {
    val c = AppTheme.colors
    if (elevation <= 0.dp) return this
    if (c.dark || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        val col = tint ?: c.shadow
        return this.shadow(elevation, shape, clip = false, ambientColor = col, spotColor = col)
    }
    val strength = (elevation.value / 14f).coerceIn(0.4f, 1.5f)
    val base = tint ?: Color.Black
    return this.drawBehind {
        // Clip the element's own area out so the shadow only appears around its edges,
        // never showing through the translucent glass.
        val outline = shape.createOutline(size, layoutDirection, this)
        val clip = android.graphics.Path().apply {
            when (outline) {
                is Outline.Rounded -> {
                    val r = outline.roundRect
                    addRoundRect(r.left, r.top, r.right, r.bottom, r.topLeftCornerRadius.x, r.topLeftCornerRadius.y, android.graphics.Path.Direction.CW)
                }
                is Outline.Rectangle -> addRect(outline.rect.left, outline.rect.top, outline.rect.right, outline.rect.bottom, android.graphics.Path.Direction.CW)
                is Outline.Generic -> addPath(outline.path.asAndroidPath())
            }
        }
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.clipOutPath(clip)
            // Soft halo around the edges
            blurredShape(shape, base.copy(alpha = (0.16f * strength).coerceAtMost(0.26f)), blur = elevation * 0.9f, dy = elevation * 0.12f, spread = 0.dp)
            // Crisp contact line hugging the edge
            blurredShape(shape, base.copy(alpha = (0.26f * strength).coerceAtMost(0.34f)), blur = 2.dp, dy = 0.dp, spread = 0.6.dp)
            native.restore()
        }
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
