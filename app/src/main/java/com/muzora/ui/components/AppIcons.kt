package com.muzora.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.muzora.R

object AppIcons {
    @DrawableRes val Shuffle = R.drawable.ic_shuffle
    @DrawableRes val Play = R.drawable.ic_play
    @DrawableRes val Pause = R.drawable.ic_pause
    @DrawableRes val SkipNext = R.drawable.ic_skip_next
    @DrawableRes val SkipPrevious = R.drawable.ic_skip_previous
    @DrawableRes val Like = R.drawable.ic_like
    @DrawableRes val Dislike = R.drawable.ic_dislike
    @DrawableRes val DogDislikeIdle = R.drawable.ic_dog_dislike_0
    @DrawableRes val DogDislike = R.drawable.ic_dog_dislike
    @DrawableRes val Repeat = R.drawable.ic_repeat
    @DrawableRes val RepeatOne = R.drawable.ic_repeat_one
    @DrawableRes val Save = R.drawable.ic_save
}

@Composable
fun AppIcon(
    @DrawableRes id: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = Color.Unspecified,
) {
    val bitmap = ImageBitmap.imageResource(id)
    Image(
        painter = BitmapPainter(bitmap, filterQuality = FilterQuality.None),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = if (tint == Color.Unspecified) null else ColorFilter.tint(tint),
    )
}
