package com.muzora.ui.theme

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.muzora.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberTrackAccent(
    song: Song?,
    coverUrl: String?,
): Color {
    val context = LocalContext.current
    var target by remember { mutableStateOf(DesignTokens.Accent) }

    LaunchedEffect(song?.id, coverUrl) {
        if (song == null) {
            target = DesignTokens.Accent
            return@LaunchedEffect
        }
        target = accentFromSongId(song.id)
        val fromCover = coverUrl?.let { extractAccentFromCover(context, it) }
        if (fromCover != null) {
            target = fromCover
        }
    }

    val animated by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 600),
        label = "trackAccent",
    )
    return animated
}

fun accentFromSongId(id: String): Color {
    val h = (id.hashCode().toUInt() % 360u).toFloat()
    val hsv = floatArrayOf(h, 0.82f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv)).toNeonAccent()
}

private suspend fun extractAccentFromCover(context: Context, url: String): Color? =
    withContext(Dispatchers.IO) {
        runCatching {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(128)
                .build()
            val result = loader.execute(request) as? SuccessResult ?: return@runCatching null
            val bitmap = result.drawable.toBitmap()
            val palette = Palette.from(bitmap).clearFilters().generate()
            val rgb = palette.vibrantSwatch?.rgb
                ?: palette.lightVibrantSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: return@runCatching null
            Color(rgb).toNeonAccent()
        }.getOrNull()
    }

/** Keep accents bright enough for neon cyberpunk UI on black. */
fun Color.toNeonAccent(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    // Skip near-gray / muddy — bump saturation & value
    if (hsv[1] < 0.35f) hsv[1] = 0.7f
    else hsv[1] = hsv[1].coerceIn(0.55f, 1f)
    hsv[2] = hsv[2].coerceIn(0.78f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}
