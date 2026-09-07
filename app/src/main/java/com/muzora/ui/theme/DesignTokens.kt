package com.muzora.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object DesignTokens {
    val Bg = Color(0xFF050505)
    val Field = Color(0xFF041213)
    val Accent = Color(0xFF00F0FF)
    val AccentDim = Color(0xFF007A80)
    val Border = Color(0xFF00C8D4)
    val White = Color(0xFFE6F2F2)
    val Muted = Color(0xFF5A8A8C)
    val Online = Color(0xFF2ECC40)
    val Offline = Color(0xFFE53935)
    val DislikeFlash = Color(0xFFE53935)

    /** Зелёная «дискета» = трек в кеше */
    val CachedIconGreen = Color(0xFF2E7D32)

    val NowPlayingTitleBackground = Field
    val NowPlayingTitleCorner = 0.dp
    val NowPlayingTitlePaddingH = 0.dp
    val NowPlayingTitlePaddingV = 0.dp

    val CoverCorner = 0.dp
    val ScreenPadding = 16.dp
    val ListPaddingH = 16.dp
    val BorderWidth = 1.dp
    val ButtonBorderWidth = 2.dp

    /** Figma transport: Prev/Shuffle 58, Play/Pause 76 */
    val TransportSide = 58.dp
    val TransportPlay = 76.dp
    val TransportIcon = 28.dp
    val LikeHit = 64.dp
    val LikeIcon = 36.dp

    val ShuffleButtonSize = 58.dp
    val ShuffleButtonSizeLarge = 96.dp
    val TransportButtonSize = 58.dp
    val CoverSize = 180.dp
    val SpectrumHeight = 48.dp
    val SpectrumBars = 40
}
