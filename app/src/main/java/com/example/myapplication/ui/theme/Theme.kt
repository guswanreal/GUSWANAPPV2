package com.example.myapplication.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val GuswanColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Ink950,
    primaryContainer = AccentDeep,
    onPrimaryContainer = Ink950,
    secondary = Blue,
    onSecondary = Ink950,
    secondaryContainer = Ink700,
    onSecondaryContainer = TextHigh,
    tertiary = Violet,
    onTertiary = Ink950,
    background = Ink900,
    onBackground = TextHigh,
    surface = Ink850,
    onSurface = TextHigh,
    surfaceVariant = Ink750,
    onSurfaceVariant = TextMid,
    surfaceContainerLowest = Ink950,
    surfaceContainerLow = Ink850,
    surfaceContainer = Ink800,
    surfaceContainerHigh = Ink750,
    surfaceContainerHighest = Ink700,
    outline = LineStrong,
    outlineVariant = Line,
    error = Danger,
    onError = Ink950,
)

/** Guswaneditor is a dark-only creative tool, like most pro video/3D software. */
@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GuswanColorScheme,
        typography = Typography,
        content = content
    )
}

