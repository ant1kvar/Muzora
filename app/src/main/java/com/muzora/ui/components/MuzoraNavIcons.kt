package com.muzora.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import com.muzora.ui.theme.LocalMuzoraColors

@Composable
fun NavNowIcon(size: Dp = 22.dp, tint: Color = LocalMuzoraColors.current.accent) {
    Icon(MuzoraNavIcons.Now, contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

@Composable
fun NavStarIcon(size: Dp = 22.dp, tint: Color = LocalMuzoraColors.current.accent) {
    Icon(MuzoraNavIcons.Star, contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

@Composable
fun NavAntennaIcon(size: Dp = 22.dp, tint: Color = LocalMuzoraColors.current.accent) {
    Icon(MuzoraNavIcons.Antenna, contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

@Composable
fun NavChipIcon(size: Dp = 22.dp, tint: Color = LocalMuzoraColors.current.accent) {
    Icon(MuzoraNavIcons.Chip, contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

object MuzoraNavIcons {
    val Now: ImageVector by lazy {
        ImageVector.Builder(
            name = "NavNow",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // → play-forward arrow
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Miter,
            ) {
                moveTo(4f, 12f)
                horizontalLineTo(16f)
                moveTo(12f, 6f)
                lineTo(18f, 12f)
                lineTo(12f, 18f)
            }
        }.build()
    }

    val Star: ImageVector by lazy {
        ImageVector.Builder(
            name = "NavStar",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.White),
                stroke = null,
            ) {
                moveTo(12f, 2.5f)
                lineTo(14.6f, 8.6f)
                lineTo(21.2f, 9.3f)
                lineTo(16.3f, 13.7f)
                lineTo(17.7f, 20.2f)
                lineTo(12f, 17f)
                lineTo(6.3f, 20.2f)
                lineTo(7.7f, 13.7f)
                lineTo(2.8f, 9.3f)
                lineTo(9.4f, 8.6f)
                close()
            }
        }.build()
    }

    val Antenna: ImageVector by lazy {
        ImageVector.Builder(
            name = "NavAntenna",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Miter,
            ) {
                // mast
                moveTo(12f, 21f)
                verticalLineTo(11f)
                // V prongs
                moveTo(12f, 11f)
                lineTo(5f, 4f)
                moveTo(12f, 11f)
                lineTo(19f, 4f)
                // signal arcs (pixel-ish polyline)
                moveTo(8f, 15f)
                lineTo(12f, 13f)
                lineTo(16f, 15f)
                moveTo(6f, 18f)
                lineTo(12f, 15.5f)
                lineTo(18f, 18f)
            }
        }.build()
    }

    val Chip: ImageVector by lazy {
        ImageVector.Builder(
            name = "NavChip",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Miter,
            ) {
                // body
                moveTo(8f, 8f)
                horizontalLineTo(16f)
                verticalLineTo(16f)
                horizontalLineTo(8f)
                close()
                // pins top/bottom
                moveTo(10f, 8f)
                verticalLineTo(5f)
                moveTo(14f, 8f)
                verticalLineTo(5f)
                moveTo(10f, 19f)
                verticalLineTo(16f)
                moveTo(14f, 19f)
                verticalLineTo(16f)
                // pins left/right
                moveTo(8f, 10f)
                horizontalLineTo(5f)
                moveTo(8f, 14f)
                horizontalLineTo(5f)
                moveTo(19f, 10f)
                horizontalLineTo(16f)
                moveTo(19f, 14f)
                horizontalLineTo(16f)
                // die
                moveTo(10.5f, 10.5f)
                horizontalLineTo(13.5f)
                verticalLineTo(13.5f)
                horizontalLineTo(10.5f)
                close()
            }
        }.build()
    }
}
