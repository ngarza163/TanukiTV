package com.darknet.animetv.ui.components

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.darknet.animetv.audio.TvSoundManager
import com.darknet.animetv.ui.theme.TvAccentPurple
import com.darknet.animetv.ui.theme.TvCardBorder

const val TV_FOCUS_SCALE = 1.08f
const val TV_UNFOCUSED_SCALE = 1.0f

/**
 * TV-optimized focus modifier that applies:
 * - Focus scale animation with spring/tween dynamics
 * - High-contrast focus border ring (e.g. Color.White / TvPrimaryRed / TvAccentPurple)
 * - Soft ambient elevation & shadow glow when focused
 * - z-index elevation (10f) so expanded focused cards render on top of adjacent items
 * - Procedural living-room D-pad focus tick & selection sound effects
 * - D-pad remote navigation & click event routing (KEYCODE_DPAD_CENTER, ENTER)
 */
fun Modifier.tvFocusableItem(
    onClick: (() -> Unit)? = null,
    focusedScale: Float = TV_FOCUS_SCALE,
    unfocusedScale: Float = TV_UNFOCUSED_SCALE,
    focusedBorderColor: Color = Color.White,
    unfocusedBorderColor: Color = Color.Transparent,
    borderWidth: Dp = 3.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    onFocusChanged: ((Boolean) -> Unit)? = null
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) focusedScale else unfocusedScale,
        animationSpec = tween(durationMillis = 180),
        label = "tv_focus_scale"
    )

    val glowElevation by animateFloatAsState(
        targetValue = if (isFocused) 18f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "tv_focus_glow"
    )

    this
        .zIndex(if (isFocused) 10f else 0f)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            shadowElevation = glowElevation
            ambientShadowColor = focusedBorderColor
            spotShadowColor = focusedBorderColor
            clip = false
        }
        .border(
            width = if (isFocused) borderWidth else 1.dp,
            color = if (isFocused) focusedBorderColor else unfocusedBorderColor,
            shape = shape
        )
        .onFocusChanged { focusState ->
            val nowFocused = focusState.isFocused
            if (nowFocused && !isFocused) {
                TvSoundManager.playFocusTick()
            }
            isFocused = nowFocused
            onFocusChanged?.invoke(nowFocused)
        }
        .onKeyEvent { keyEvent ->
            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP &&
                (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
                        keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
            ) {
                if (onClick != null) {
                    TvSoundManager.playActionSelect()
                    onClick.invoke()
                }
                true
            } else {
                false
            }
        }
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        TvSoundManager.playActionSelect()
                        onClick.invoke()
                    }
                )
            } else {
                Modifier.focusable(interactionSource = interactionSource)
            }
        )
}
