package com.muzora.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.muzora.ui.theme.LocalMuzoraColors
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class DebrisCube(
    val x: Float,
    val y: Float,
    val size: Float,
    val speed: Float,
    val amp: Float,
    val phase: Float,
    val fadeSpeed: Float,
    val fadePhase: Float,
)

/** Soft sine opacity: keep a faint ghost so the field never looks empty. */
private fun softFade(u: Float): Float {
    val s = u * u * (3f - 2f * u)
    return 0.08f + s * 0.72f
}

private fun seedCubes(): List<DebrisCube> {
    val left = listOf(
        floatArrayOf(2f, 18f, 8f),
        floatArrayOf(14f, 12f, 12f),
        floatArrayOf(28f, 22f, 6f),
        floatArrayOf(6f, 36f, 16f),
        floatArrayOf(22f, 42f, 10f),
        floatArrayOf(36f, 34f, 8f),
        floatArrayOf(4f, 56f, 12f),
        floatArrayOf(18f, 62f, 18f),
        floatArrayOf(32f, 58f, 7f),
        floatArrayOf(8f, 78f, 10f),
        floatArrayOf(24f, 84f, 14f),
        floatArrayOf(38f, 72f, 8f),
        floatArrayOf(2f, 96f, 16f),
        floatArrayOf(16f, 102f, 8f),
        floatArrayOf(30f, 98f, 12f),
        floatArrayOf(10f, 118f, 10f),
        floatArrayOf(26f, 124f, 16f),
        floatArrayOf(40f, 112f, 6f),
        floatArrayOf(4f, 140f, 12f),
        floatArrayOf(20f, 146f, 8f),
        floatArrayOf(34f, 138f, 14f),
        floatArrayOf(12f, 162f, 10f),
        floatArrayOf(28f, 168f, 12f),
        floatArrayOf(6f, 182f, 8f),
        floatArrayOf(22f, 188f, 16f),
        floatArrayOf(36f, 178f, 7f),
        floatArrayOf(14f, 200f, 10f),
        floatArrayOf(30f, 206f, 8f),
    )
    val right = listOf(
        floatArrayOf(286f, 14f, 10f),
        floatArrayOf(302f, 20f, 14f),
        floatArrayOf(318f, 12f, 8f),
        floatArrayOf(280f, 34f, 12f),
        floatArrayOf(296f, 42f, 8f),
        floatArrayOf(312f, 36f, 16f),
        floatArrayOf(284f, 56f, 7f),
        floatArrayOf(300f, 64f, 12f),
        floatArrayOf(316f, 58f, 10f),
        floatArrayOf(278f, 78f, 14f),
        floatArrayOf(294f, 86f, 8f),
        floatArrayOf(310f, 80f, 12f),
        floatArrayOf(288f, 98f, 10f),
        floatArrayOf(304f, 106f, 16f),
        floatArrayOf(320f, 100f, 6f),
        floatArrayOf(282f, 120f, 12f),
        floatArrayOf(298f, 128f, 8f),
        floatArrayOf(314f, 118f, 14f),
        floatArrayOf(286f, 142f, 10f),
        floatArrayOf(302f, 150f, 12f),
        floatArrayOf(318f, 144f, 8f),
        floatArrayOf(280f, 162f, 16f),
        floatArrayOf(296f, 170f, 7f),
        floatArrayOf(312f, 164f, 10f),
        floatArrayOf(288f, 184f, 12f),
        floatArrayOf(304f, 192f, 8f),
        floatArrayOf(320f, 186f, 14f),
        floatArrayOf(292f, 204f, 10f),
        floatArrayOf(308f, 210f, 8f),
    )

    fun mk(arr: List<FloatArray>): List<DebrisCube> =
        arr.mapIndexed { i, v ->
            DebrisCube(
                x = v[0],
                y = v[1],
                size = v[2],
                speed = 0.0035f + (i % 5) * 0.0012f,
                amp = 3f + (i % 4) * 1.5f,
                phase = i * 0.7f,
                fadeSpeed = 0.008f + (i % 7) * 0.0025f,
                fadePhase = i * 1.3f + Random.nextFloat() * 2f,
            )
        }

    return mk(left) + mk(right)
}

@Composable
fun CoverDebris(
    modifier: Modifier = Modifier,
) {
    val colors = LocalMuzoraColors.current
    val cubes = remember { seedCubes() }
    var t by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val strokePx = with(density) { 1.5.dp.toPx() }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis {
                // ~60fps tick like demo's requestAnimationFrame += 1
                t += 1f
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // Demo canvas is 328×220; scale if parent differs.
        val sx = size.width / 328f
        val sy = size.height / 220f
        cubes.forEach { c ->
            val dx = sin(t * c.speed + c.phase) * c.amp
            val dy = cos(t * c.speed * 0.85f + c.phase) * c.amp * 0.7f
            val wave = 0.5f + 0.5f * sin(t * c.fadeSpeed + c.fadePhase)
            val opacity = softFade(wave)
            val cubeSize = c.size * (0.85f + wave * 0.2f)
            val left = (c.x + dx) * sx
            val top = (c.y + dy) * sy
            val w = cubeSize * sx
            val h = cubeSize * sy
            drawRect(
                color = colors.accent.copy(alpha = opacity),
                topLeft = Offset(left, top),
                size = Size(w, h),
                style = Stroke(width = strokePx),
            )
        }
    }
}
