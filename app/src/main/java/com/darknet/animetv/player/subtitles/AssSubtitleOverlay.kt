package com.darknet.animetv.player.subtitles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/**
 * Android TV Overlay for styled .ASS / SSA anime subtitles with sign positioning and drop shadows.
 */
@Composable
fun AssSubtitleOverlay(
    cues: List<AssCue>,
    currentPositionMs: Long,
    fontSizeMultiplier: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    if (cues.isEmpty()) return

    val activeCues = remember(currentPositionMs, cues) {
        cues.filter { currentPositionMs in it.startMs..it.endMs }
    }

    if (activeCues.isEmpty()) return

    Box(modifier = modifier.fillMaxSize().padding(32.dp)) {
        activeCues.forEach { cue ->
            val textColor = cue.overrideColor ?: cue.style.primaryColor
            val fontSize = (cue.style.fontSize * fontSizeMultiplier).coerceIn(16f, 54f).sp

            val alignment = when (cue.alignment) {
                1, 4, 7 -> Alignment.TopStart
                3, 6, 9 -> Alignment.TopEnd
                8 -> Alignment.TopCenter
                5 -> Alignment.Center
                else -> Alignment.BottomCenter // Default: 2 (bottom center)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (cue.alignment in listOf(1, 2, 3)) 28.dp else 0.dp),
                contentAlignment = alignment
            ) {
                // Drop Shadow / Outline Text
                Text(
                    text = cue.text,
                    style = TextStyle(
                        color = textColor,
                        fontSize = fontSize,
                        fontWeight = if (cue.style.isBold) FontWeight.Bold else FontWeight.Medium,
                        fontStyle = if (cue.style.isItalic) FontStyle.Italic else FontStyle.Normal,
                        fontFamily = FontFamily.SansSerif,
                        textAlign = TextAlign.Center,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.9f),
                            blurRadius = 6f
                        )
                    )
                )
            }
        }
    }
}
