package com.muzora.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.muzora.R
import com.muzora.ui.theme.DesignTokens
import com.muzora.ui.theme.LocalMuzoraColors
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private data class BurstSpark(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val size: Float,
    val life: Float,
    val maxLife: Float,
    val color: Color,
)

@Composable
fun SparkLikeButton(
    liked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMuzoraColors.current
    val density = LocalDensity.current
    val pixel = with(density) { 2.5.dp.toPx() }
    val sparks = remember { mutableStateListOf<BurstSpark>() }
    var lastFrame by remember { mutableLongStateOf(0L) }
    var prevLiked by remember { mutableStateOf(liked) }
    val sparkPalette = remember(colors.accent) {
        listOf(
            colors.accent,
            Color(0xFFE53935),
            Color(0xFFFF6B6B),
            Color(0xFFFFFFFF),
            colors.accent.copy(alpha = 0.7f),
        )
    }

    fun spawnBurst() {
        repeat(36) {
            val a = Random.nextFloat() * (Math.PI * 2).toFloat()
            val speed = 90f + Random.nextFloat() * 180f
            val life = 0.35f + Random.nextFloat() * 0.45f
            sparks += BurstSpark(
                x = 0f,
                y = 0f,
                vx = cos(a) * speed,
                vy = sin(a) * speed,
                size = if (Random.nextFloat() < 0.3f) pixel * 2f else pixel,
                life = life,
                maxLife = life,
                color = sparkPalette[Random.nextInt(sparkPalette.size)],
            )
        }
    }

    LaunchedEffect(liked) {
        if (liked && !prevLiked) spawnBurst()
        prevLiked = liked
    }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { now ->
                if (lastFrame == 0L) lastFrame = now
                val dt = ((now - lastFrame) / 1_000_000_000f).coerceAtMost(0.05f)
                lastFrame = now
                for (i in sparks.lastIndex downTo 0) {
                    val p = sparks[i]
                    val life = p.life - dt
                    if (life <= 0f) {
                        sparks.removeAt(i)
                    } else {
                        sparks[i] = p.copy(
                            x = p.x + p.vx * dt,
                            y = p.y + p.vy * dt,
                            vx = p.vx * 0.97f,
                            vy = p.vy * 0.97f + 50f * dt,
                            life = life,
                        )
                    }
                }
            }
        }
    }

    val desc = stringResource(R.string.like)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DesignTokens.LikeHit)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = desc,
            ) {
                if (!liked) spawnBurst()
                onToggle()
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(56.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            fun snap(v: Float) = (v / pixel).roundToInt() * pixel
            sparks.forEach { p ->
                val t = (p.life / p.maxLife).coerceIn(0f, 1f)
                val sz = p.size
                drawRect(
                    color = p.color.copy(alpha = t),
                    topLeft = Offset(cx + snap(p.x) - sz / 2f, cy + snap(p.y) - sz / 2f),
                    size = Size(sz, sz),
                )
            }
        }
        MuzoraLikeIcon(
            filled = liked,
            size = DesignTokens.LikeIcon,
            tint = colors.accent,
        )
    }
}
