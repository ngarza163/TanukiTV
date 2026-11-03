package com.darknet.animetv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.darknet.animetv.ui.theme.TvAccentPurple
import com.darknet.animetv.ui.theme.TvCardBorder
import com.darknet.animetv.ui.theme.TvPrimaryRed
import com.darknet.animetv.ui.theme.TvSurfaceColor
import com.darknet.animetv.ui.theme.TvTextPrimary
import com.darknet.animetv.ui.theme.TvTextSecondary

/**
 * Android TV Remote-friendly category / genre chip filter.
 * Supports D-pad navigation, selected highlight state, and focus glow ring.
 */
@Composable
fun CategoryChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val backgroundColor = when {
        isFocused -> TvAccentPurple
        isSelected -> TvPrimaryRed
        else -> TvSurfaceColor
    }

    val textColor = when {
        isFocused || isSelected -> Color.White
        else -> TvTextSecondary
    }

    val borderColor = when {
        isFocused -> TvAccentPurple
        isSelected -> TvPrimaryRed
        else -> TvCardBorder
    }

    Box(
        modifier = modifier
            .tvFocusableItem(
                onClick = onClick,
                focusedScale = 1.10f,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = borderColor,
                borderWidth = 2.5.dp,
                shape = RoundedCornerShape(20.dp),
                onFocusChanged = { isFocused = it }
            )
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
        )
    }
}
