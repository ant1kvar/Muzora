package com.muzora.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.muzora.R
import com.muzora.ui.theme.DesignTokens
import com.muzora.ui.theme.LocalMuzoraColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AnimatedDislikeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMuzoraColors.current
    val scope = rememberCoroutineScope()
    var pressing by remember { mutableStateOf(false) }
    val desc = stringResource(R.string.dislike)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DesignTokens.LikeHit)
            .alpha(if (pressing) 0.45f else 1f)
            .clickable(
                enabled = !pressing,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = desc,
            ) {
                scope.launch {
                    pressing = true
                    delay(180L)
                    onClick()
                    pressing = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        MuzoraDislikeIcon(
            size = DesignTokens.LikeIcon,
            tint = colors.accent,
        )
    }
}
