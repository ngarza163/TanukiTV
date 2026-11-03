package com.darknet.animetv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
val TvDarkColorScheme = darkColorScheme(
    primary = TvBrandPurple,
    onPrimary = Color.White,
    primaryContainer = TvBrandPurpleDark,
    onPrimaryContainer = Color.White,
    secondary = TvBrandCyan,
    onSecondary = Color(0xFF0C0D14),
    tertiary = TvBrandAmber,
    onTertiary = Color(0xFF0C0D14),
    background = TvDarkBackground,
    onBackground = TvTextPrimary,
    surface = TvSurfaceColor,
    onSurface = TvTextPrimary,
    surfaceVariant = TvSurfaceFocusedColor,
    onSurfaceVariant = TvTextSecondary,
    border = TvCardBorder
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TanukiTvMaterialTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TvDarkColorScheme,
        typography = TvTypography,
        content = content
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AnimeTvMaterialTheme(
    content: @Composable () -> Unit
) = TanukiTvMaterialTheme(content = content)
