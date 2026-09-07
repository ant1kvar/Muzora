package com.muzora.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class MuzoraColors(
    val bg: Color = DesignTokens.Bg,
    val field: Color = DesignTokens.Field,
    val accent: Color = DesignTokens.Accent,
    val accentDim: Color = DesignTokens.AccentDim,
    val border: Color = DesignTokens.Border,
    val white: Color = DesignTokens.White,
    val muted: Color = DesignTokens.Muted,
    val online: Color = DesignTokens.Online,
    val offline: Color = DesignTokens.Offline,
)

val LocalMuzoraColors = staticCompositionLocalOf { MuzoraColors() }

private val MuzoraColorScheme = darkColorScheme(
    primary = DesignTokens.Accent,
    onPrimary = DesignTokens.Bg,
    secondary = DesignTokens.AccentDim,
    onSecondary = DesignTokens.White,
    background = DesignTokens.Bg,
    onBackground = DesignTokens.White,
    surface = DesignTokens.Bg,
    onSurface = DesignTokens.White,
    surfaceVariant = DesignTokens.Field,
    onSurfaceVariant = DesignTokens.Muted,
    outline = DesignTokens.Border,
    error = DesignTokens.Offline,
    onError = DesignTokens.White,
)

@Composable
fun MuzoraTheme(
    accent: Color = DesignTokens.Accent,
    content: @Composable () -> Unit,
) {
    val colors = MuzoraColors(
        accent = accent,
        accentDim = accent.copy(alpha = 0.45f).compositeOnBg(),
        border = accent.copy(alpha = 0.85f).compositeOnBg(),
        muted = accent.copy(alpha = 0.55f).compositeOnBg(),
        field = Color(
            red = (accent.red * 0.06f).coerceAtLeast(0.02f),
            green = (accent.green * 0.08f).coerceAtLeast(0.04f),
            blue = (accent.blue * 0.08f).coerceAtLeast(0.03f),
            alpha = 1f,
        ),
    )
    val scheme = MuzoraColorScheme.copy(
        primary = accent,
        secondary = colors.accentDim,
        surfaceVariant = colors.field,
        onSurfaceVariant = colors.muted,
        outline = colors.border,
    )
    CompositionLocalProvider(LocalMuzoraColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MuzoraTypography,
            content = content,
        )
    }
}

/** Rough composite of translucent accent onto near-black for readable dim tones. */
private fun Color.compositeOnBg(): Color {
    val bg = DesignTokens.Bg
    val a = alpha
    return Color(
        red = red * a + bg.red * (1f - a),
        green = green * a + bg.green * (1f - a),
        blue = blue * a + bg.blue * (1f - a),
        alpha = 1f,
    )
}
