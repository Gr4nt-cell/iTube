package com.gr4nt.itube.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.gr4nt.itube.data.ThemeMode

/** iOS-inspired palette plus the extra tokens the Liquid Glass surfaces need. */
@Immutable
data class AppColors(
    val dark: Boolean,
    val background: Color,
    val elevated: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val accent: Color,
    val glassTint: Color,
    val glassRim: Color,
    val shadow: Color,
    val chip: Color,
)

private val Light = AppColors(
    dark = false,
    background = Color(0xFFF2F2F7),
    elevated = Color(0xFFFFFFFF),
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    separator = Color(0x293C3C43),
    accent = Color(0xFFFF2D55),
    glassTint = Color(0x8CFFFFFF),
    glassRim = Color(0xCCFFFFFF),
    shadow = Color(0x40000000),
    chip = Color(0x14000000),
)

private val Dark = AppColors(
    dark = true,
    background = Color(0xFF000000),
    elevated = Color(0xFF1C1C1E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    separator = Color(0x3D545458),
    accent = Color(0xFFFF375F),
    glassTint = Color(0x661C1C1E),
    glassRim = Color(0x59FFFFFF),
    shadow = Color(0xB3000000),
    chip = Color(0x24FFFFFF),
)

val LocalAppColors = staticCompositionLocalOf { Light }

object AppTheme {
    val colors: AppColors @Composable get() = LocalAppColors.current
}

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

private val Type = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun ITubeTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) Dark else Light
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = Color.White,
            background = c.background, onBackground = c.label,
            surface = c.elevated, onSurface = c.label,
            surfaceVariant = c.elevated, onSurfaceVariant = c.secondaryLabel,
            outline = c.separator,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = Color.White,
            background = c.background, onBackground = c.label,
            surface = c.elevated, onSurface = c.label,
            surfaceVariant = c.elevated, onSurfaceVariant = c.secondaryLabel,
            outline = c.separator,
        )
    }
    CompositionLocalProvider(LocalAppColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = Type, content = content)
    }
}
