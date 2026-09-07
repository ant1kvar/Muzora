package com.muzora.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.muzora.ui.theme.LocalMuzoraColors

@Composable
fun MuzoraDislikeIcon(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    tint: Color = LocalMuzoraColors.current.accent,
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = size.toPx() * (2.5f / 36f), cap = StrokeCap.Round)
        val r = this.size.minDimension / 2f - stroke.width
        drawCircle(color = tint, radius = r, style = stroke)
        val inset = this.size.minDimension * (12f / 36f)
        val end = this.size.minDimension - inset
        drawLine(tint, Offset(inset, inset), Offset(end, end), stroke.width, StrokeCap.Round)
        drawLine(tint, Offset(end, inset), Offset(inset, end), stroke.width, StrokeCap.Round)
    }
}

@Composable
fun MuzoraLikeIcon(
    filled: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    tint: Color = LocalMuzoraColors.current.accent,
) {
    Canvas(modifier = modifier.size(size)) {
        val scale = this.size.minDimension / 36f
        val path = Path().apply {
            // Figma heart path (36×36)
            moveTo(18f * scale, 30f * scale)
            cubicTo(
                18f * scale, 30f * scale,
                6f * scale, 22.5f * scale,
                6f * scale, 14.5f * scale,
            )
            cubicTo(
                5.99217f * scale, 13.0874f * scale,
                6.4447f * scale, 11.7108f * scale,
                7.28911f * scale, 10.5784f * scale,
            )
            cubicTo(
                8.13352f * scale, 9.44596f * scale,
                9.32384f * scale, 8.61945f * scale,
                10.6799f * scale, 8.22393f * scale,
            )
            cubicTo(
                12.036f * scale, 7.82841f * scale,
                13.484f * scale, 7.88541f * scale,
                14.8048f * scale, 8.3863f * scale,
            )
            cubicTo(
                16.1256f * scale, 8.88719f * scale,
                17.2472f * scale, 9.80471f * scale,
                18f * scale, 11f * scale,
            )
            cubicTo(
                18.7528f * scale, 9.80471f * scale,
                19.8745f * scale, 8.88719f * scale,
                21.1952f * scale, 8.3863f * scale,
            )
            cubicTo(
                22.516f * scale, 7.88541f * scale,
                23.964f * scale, 7.82841f * scale,
                25.3201f * scale, 8.22393f * scale,
            )
            cubicTo(
                26.6762f * scale, 8.61945f * scale,
                27.8665f * scale, 9.44596f * scale,
                28.7109f * scale, 10.5784f * scale,
            )
            cubicTo(
                29.5553f * scale, 11.7108f * scale,
                30.0078f * scale, 13.0874f * scale,
                30f * scale, 14.5f * scale,
            )
            cubicTo(
                30f * scale, 22.5f * scale,
                18f * scale, 30f * scale,
                18f * scale, 30f * scale,
            )
            close()
        }
        val stroke = Stroke(
            width = size.toPx() * (2.5f / 36f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        if (filled) {
            drawPath(path, color = tint, style = Fill)
        }
        drawPath(path, color = tint, style = stroke)
    }
}

@Composable
fun MuzoraShuffleIcon(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    tint: Color = LocalMuzoraColors.current.accent,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension / 28f
        val stroke = Stroke(
            width = size.toPx() * (2.33333f / 28f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        fun p(x: Float, y: Float) = Offset(x * s, y * s)

        // chevrons
        drawLine(tint, p(21f, 16.3333f), p(25.6667f, 21f), stroke.width, StrokeCap.Round)
        drawLine(tint, p(25.6667f, 21f), p(21f, 25.6666f), stroke.width, StrokeCap.Round)
        drawLine(tint, p(21f, 2.33331f), p(25.6667f, 6.99998f), stroke.width, StrokeCap.Round)
        drawLine(tint, p(25.6667f, 6.99998f), p(21f, 11.6666f), stroke.width, StrokeCap.Round)

        // long crossing path (upper→lower)
        val longPath = Path().apply {
            moveTo(2.33334f * s, 21f * s)
            lineTo(4.63518f * s, 21f * s)
            cubicTo(
                5.38942f * s, 21.0051f * s,
                6.13367f * s, 20.8274f * s,
                6.80418f * s, 20.482f * s,
            )
            cubicTo(
                7.4747f * s, 20.1366f * s,
                8.0515f * s, 19.6338f * s,
                8.48518f * s, 19.0167f * s,
            )
            lineTo(14.8482f * s, 8.98332f * s)
            cubicTo(
                15.2819f * s, 8.36621f * s,
                15.8587f * s, 7.86341f * s,
                16.5292f * s, 7.518f * s,
            )
            cubicTo(
                17.1997f * s, 7.17258f * s,
                17.9439f * s, 6.99484f * s,
                18.6982f * s, 6.99999f * s,
            )
            lineTo(25.6667f * s, 6.99999f * s)
        }
        drawPath(longPath, tint, style = stroke)

        // short bottom-left stub
        val stub = Path().apply {
            moveTo(2.33334f * s, 6.99999f * s)
            lineTo(4.63401f * s, 6.99999f * s)
            cubicTo(
                5.50371f * s, 6.99394f * s,
                6.3578f * s, 7.23105f * s,
                7.09992f * s, 7.68456f * s,
            )
            cubicTo(
                7.84204f * s, 8.13808f * s,
                8.44266f * s, 8.78996f * s,
                8.83401f * s, 9.56666f * s,
            )
        }
        drawPath(stub, tint, style = stroke)

        // lower right path
        val lower = Path().apply {
            moveTo(25.6667f * s, 21f * s)
            lineTo(18.6188f * s, 21f * s)
            cubicTo(
                17.8542f * s, 20.9922f * s,
                17.1031f * s, 20.7966f * s,
                16.4318f * s, 20.4304f * s,
            )
            cubicTo(
                15.7605f * s, 20.0642f * s,
                15.1894f * s, 19.5387f * s,
                14.7688f * s, 18.9f * s,
            )
            lineTo(14.35f * s, 18.2373f * s)
        }
        drawPath(lower, tint, style = stroke)
    }
}

@Composable
fun MuzoraPrevIcon(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    tint: Color = LocalMuzoraColors.current.accent,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension / 28f
        // bar
        drawRect(
            color = tint,
            topLeft = Offset(3f * s, 6f * s),
            size = androidx.compose.ui.geometry.Size(3.5f * s, 16f * s),
        )
        // triangle pointing left
        val tri = Path().apply {
            moveTo(24f * s, 6f * s)
            lineTo(8f * s, 14f * s)
            lineTo(24f * s, 22f * s)
            close()
        }
        drawPath(tri, tint, style = Fill)
    }
}

@Composable
fun MuzoraPlayIcon(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    tint: Color = LocalMuzoraColors.current.accent,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension / 28f
        val tri = Path().apply {
            moveTo(7f * s, 4f * s)
            lineTo(24f * s, 14f * s)
            lineTo(7f * s, 24f * s)
            close()
        }
        drawPath(tri, tint, style = Fill)
    }
}

@Composable
fun MuzoraPauseIcon(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    tint: Color = LocalMuzoraColors.current.accent,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension / 28f
        drawRect(
            color = tint,
            topLeft = Offset(6f * s, 4f * s),
            size = androidx.compose.ui.geometry.Size(5f * s, 20f * s),
        )
        drawRect(
            color = tint,
            topLeft = Offset(17f * s, 4f * s),
            size = androidx.compose.ui.geometry.Size(5f * s, 20f * s),
        )
    }
}
