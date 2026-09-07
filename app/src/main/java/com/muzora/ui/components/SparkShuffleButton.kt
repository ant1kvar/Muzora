package com.muzora.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.muzora.R
import com.muzora.ui.theme.DesignTokens
import com.muzora.ui.theme.LocalMuzoraColors
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private data class SparkParticle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val size: Float,
    val life: Float,
    val maxLife: Float,
    val color: Color,
)

private val FireColors = listOf(
    Color(0xFFC43C00),
    Color(0xFFFF6A00),
    Color(0xFFFF9F1C),
    Color(0xFFFFD60A),
    Color(0xFFFFF3B0),
)

private val SparkColors = listOf(
    Color(0xFFFFE066),
    Color(0xFFFFB703),
    Color(0xFFFB8500),
    Color(0xFFFF5400),
    Color(0xFFFFFFFF),
)

@Composable
fun SparkShuffleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = DesignTokens.ShuffleButtonSize,
    showBorder: Boolean = false,
) {
    val colors = LocalMuzoraColors.current
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }
    val btnR = sizePx / 2f
    val ringR = btnR + sizePx * 0.06f
    val pixel = with(density) { 2.5.dp.toPx() }

    val flames = remember { mutableStateListOf<SparkParticle>() }
    val sparks = remember { mutableStateListOf<SparkParticle>() }
    var lastFrame by remember { mutableLongStateOf(0L) }
    var spawnAcc by remember { mutableFloatStateOf(0f) }
    var pressScale by remember { mutableFloatStateOf(1f) }
    var pressT by remember { mutableFloatStateOf(0f) }

    val contentDesc = stringResource(R.string.shuffle)

    fun spawnFlame() {
        val a = Random.nextFloat() * (Math.PI * 2).toFloat()
        val r = ringR + Random.nextFloat() * pixel
        val out = 18f + Random.nextFloat() * 42f
        val life = 0.2f + Random.nextFloat() * 0.3f
        flames += SparkParticle(
            x = cos(a) * r,
            y = sin(a) * r,
            vx = cos(a) * out + (Random.nextFloat() - 0.5f) * 20f,
            vy = sin(a) * out - (14f + Random.nextFloat() * 28f),
            size = if (Random.nextFloat() < 0.35f) pixel * 2f else pixel,
            life = life,
            maxLife = life,
            color = FireColors[1 + Random.nextInt(FireColors.size - 1)],
        )
    }

    fun spawnSparksBurst() {
        repeat(48) {
            val a = Random.nextFloat() * (Math.PI * 2).toFloat()
            val speed = 140f + Random.nextFloat() * 240f
            val life = 0.4f + Random.nextFloat() * 0.55f
            sparks += SparkParticle(
                x = cos(a) * (btnR + pixel),
                y = sin(a) * (btnR + pixel),
                vx = cos(a) * speed,
                vy = sin(a) * speed,
                size = if (Random.nextFloat() < 0.3f) pixel * 2f else pixel,
                life = life,
                maxLife = life,
                color = SparkColors[Random.nextInt(SparkColors.size)],
            )
        }
    }

    fun updateList(list: MutableList<SparkParticle>, dt: Float, gravity: Float) {
        for (i in list.lastIndex downTo 0) {
            val p = list[i]
            val life = p.life - dt
            if (life <= 0f) {
                list.removeAt(i)
            } else {
                list[i] = p.copy(
                    x = p.x + p.vx * dt,
                    y = p.y + p.vy * dt,
                    vx = p.vx * 0.97f,
                    vy = p.vy * 0.97f + gravity * dt,
                    life = life,
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { now ->
                if (lastFrame == 0L) lastFrame = now
                val dt = ((now - lastFrame) / 1_000_000_000f).coerceAtMost(0.05f)
                lastFrame = now

                if (pressT > 0f) {
                    pressT = (pressT - dt).coerceAtLeast(0f)
                    pressScale = 0.92f + 0.08f * (1f - pressT / 0.18f).coerceIn(0f, 1f)
                } else {
                    pressScale = 1f
                }

                spawnAcc += dt
                while (spawnAcc > 0.012f) {
                    spawnAcc -= 0.012f
                    spawnFlame()
                    if (Random.nextFloat() < 0.4f) spawnFlame()
                }
                updateList(flames, dt, -30f)
                updateList(sparks, dt, 70f)
            }
        }
    }

    // Layout size == button size so Prev / Play / Shuffle align; fire draws unbounded.
    Box(
        modifier = modifier
            .size(size)
            .scale(pressScale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = contentDesc,
            ) {
                pressT = 0.18f
                spawnSparksBurst()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .wrapContentSize(unbounded = true)
                .size(size * 1.55f),
        ) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            fun snap(v: Float) = (v / pixel).roundToInt() * pixel
            fun drawPixel(x: Float, y: Float, sz: Float, color: Color, alpha: Float) {
                drawRect(
                    color = color.copy(alpha = alpha.coerceIn(0f, 1f)),
                    topLeft = Offset(cx + snap(x) - sz / 2f, cy + snap(y) - sz / 2f),
                    size = Size(sz, sz),
                )
            }
            fun drawParticles(list: List<SparkParticle>) {
                list.forEach { p ->
                    val t = (p.life / p.maxLife).coerceIn(0f, 1f)
                    drawPixel(p.x, p.y, p.size, p.color, t)
                    if (t > 0.45f) {
                        drawPixel(
                            p.x - p.vx * 0.03f,
                            p.y - p.vy * 0.03f,
                            pixel,
                            p.color,
                            t * 0.4f,
                        )
                    }
                }
            }
            drawParticles(flames)
            drawParticles(sparks)
        }

        Box(
            modifier = Modifier
                .size(size)
                .background(colors.field, CircleShape)
                .then(
                    if (showBorder) {
                        Modifier.border(2.dp, colors.accent, CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            MuzoraShuffleIcon(
                size = if (showBorder) 36.dp else DesignTokens.TransportIcon,
                tint = colors.accent,
            )
        }
    }
}
