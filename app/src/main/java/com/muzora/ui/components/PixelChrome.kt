package com.muzora.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muzora.R
import com.muzora.ui.theme.DesignTokens
import com.muzora.ui.theme.JetBrainsMono
import com.muzora.ui.theme.LocalMuzoraColors
import com.muzora.ui.theme.PixelifySans
import com.muzora.ui.theme.PressStart2P
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun MuzoraStatusChip(
    online: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val pulseColor = if (online) DesignTokens.Online else DesignTokens.Offline
    val transition = rememberInfiniteTransition(label = "status")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot",
    )
    Row(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else Modifier,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .scale(scale)
                .background(pulseColor, CircleShape),
        )
        Text(
            text = stringResource(if (online) R.string.status_on else R.string.status_off),
            style = TextStyle(
                fontFamily = PressStart2P,
                fontSize = 10.sp,
                color = pulseColor,
            ),
        )
    }
}

@Composable
fun MuzoraHeader(
    title: String,
    online: Boolean,
    modifier: Modifier = Modifier,
    showBack: Boolean = false,
    onBack: (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
) {
    val colors = LocalMuzoraColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack && onBack != null) {
                Text(
                    text = "<",
                    style = TextStyle(
                        fontFamily = PressStart2P,
                        fontSize = 12.sp,
                        color = colors.accent,
                    ),
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBack,
                        )
                        .padding(end = 8.dp),
                )
            }
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = PressStart2P,
                    fontSize = 14.sp,
                    color = colors.accent,
                ),
                maxLines = 1,
                modifier = Modifier.clickable(
                    enabled = onTitleClick != null,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onTitleClick?.invoke() },
                ),
            )
        }
        MuzoraStatusChip(online = online)
    }
}

@Composable
fun PixelField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    focusRequester: FocusRequester? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalMuzoraColors.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                color = colors.muted,
            ),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                cursorBrush = SolidColor(colors.accent),
                textStyle = TextStyle(
                    fontFamily = PixelifySans,
                    fontSize = 13.sp,
                    color = colors.white,
                ),
                modifier = Modifier
                    .weight(1f)
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .background(colors.field)
                    .border(DesignTokens.BorderWidth, colors.border)
                    .padding(10.dp),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                text = placeholder,
                                style = TextStyle(
                                    fontFamily = PixelifySans,
                                    fontSize = 13.sp,
                                    color = colors.muted,
                                ),
                            )
                        }
                        inner()
                    }
                },
            )
            trailing?.invoke()
        }
    }
}

@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalMuzoraColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.accent.copy(alpha = 0.22f))
            .border(DesignTokens.ButtonBorderWidth, colors.accent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = PressStart2P,
                fontSize = 11.sp,
                color = colors.accent.copy(alpha = if (enabled) 1f else 0.5f),
            ),
        )
    }
}

@Composable
fun PixelSearchBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "Search library...",
) {
    val colors = LocalMuzoraColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.field)
            .border(DesignTokens.BorderWidth, colors.border)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = ">",
            style = TextStyle(
                fontFamily = JetBrainsMono,
                fontSize = 13.sp,
                color = colors.accentDim,
            ),
        )
        Text(
            text = hint,
            style = TextStyle(
                fontFamily = PixelifySans,
                fontSize = 14.sp,
                color = colors.muted,
            ),
        )
    }
}

@Composable
fun RoundTransportButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = DesignTokens.TransportSide,
    content: @Composable () -> Unit,
) {
    val colors = LocalMuzoraColors.current
    Box(
        modifier = modifier
            .size(size)
            .background(colors.field, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun SpectrumSeekBar(
    progress: Float,
    playing: Boolean,
    onScrub: (Float) -> Unit,
    onSeekFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
    scrubbing: Boolean = false,
    barCount: Int = DesignTokens.SpectrumBars,
) {
    val colors = LocalMuzoraColors.current
    val bars = remember { mutableStateListOf(*Array(barCount) { 20f + Random.nextFloat() * 40f }) }
    val fraction = progress.coerceIn(0f, 1f)
    val lit = (fraction * barCount).toInt().coerceIn(0, barCount)

    LaunchedEffect(playing, scrubbing) {
        if (!playing || scrubbing) return@LaunchedEffect
        while (true) {
            for (i in bars.indices) {
                bars[i] = (bars[i] + (Random.nextFloat() - 0.5f) * 28f).coerceIn(8f, 100f)
            }
            delay(48)
        }
    }

    // Tall hit area: tap/drag anywhere in the band seeks; bars drawn in the lower half.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    fun at(x: Float) = (x / width).coerceIn(0f, 1f)
                    var last = at(down.position.x)
                    onScrub(last)
                    drag(down.id) { change ->
                        change.consume()
                        last = at(change.position.x)
                        onScrub(last)
                    }
                    onSeekFinished(last)
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DesignTokens.SpectrumHeight),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEachIndexed { i, h ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(h / 100f)
                        .background(
                            if (i < lit) colors.accent else colors.accentDim.copy(alpha = 0.35f),
                        ),
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = maxWidth * fraction - 1.dp)
                .width(2.dp)
                .height(DesignTokens.SpectrumHeight)
                .background(colors.white.copy(alpha = if (scrubbing) 1f else 0.85f)),
        )
    }
}

@Composable
fun PixelNavBar(
    items: List<PixelNavItem>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMuzoraColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.border),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = item.route == selectedRoute
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(item.route) },
                        )
                        .padding(vertical = 4.dp),
                ) {
                    item.icon(selected)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.label,
                        style = TextStyle(
                            fontFamily = PressStart2P,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Normal,
                            color = if (selected) colors.accent else colors.muted,
                        ),
                    )
                }
            }
        }
    }
}

data class PixelNavItem(
    val route: String,
    val label: String,
    val icon: @Composable (selected: Boolean) -> Unit,
)
