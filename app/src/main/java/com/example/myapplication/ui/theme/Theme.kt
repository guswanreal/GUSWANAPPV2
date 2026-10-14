package com.example.myapplication.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val GuswanColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Ink950,
    primaryContainer = AccentDeep,
    onPrimaryContainer = TextHigh,
    secondary = Blue,
    onSecondary = TextHigh,
    secondaryContainer = BlueDeep,
    onSecondaryContainer = TextHigh,
    tertiary = Violet,
    onTertiary = TextHigh,
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
    onError = TextHigh,
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
