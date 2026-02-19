package com.brk718.tracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary            = Indigo40,
    onPrimary          = Color.White,
    primaryContainer   = Indigo90,
    onPrimaryContainer = Indigo20,

    secondary            = Slate40,
    onSecondary          = Color.White,
    secondaryContainer   = Slate90,
    onSecondaryContainer = Slate10,

    tertiary            = Teal40,
    onTertiary          = Color.White,
    tertiaryContainer   = Teal90,
    onTertiaryContainer = Teal10,

    error            = Red40,
    onError          = Color.White,
    errorContainer   = Red90,
    onErrorContainer = Red10,

    background   = Neutral99,
    onBackground = Neutral10,

    surface          = Neutral99,
    onSurface        = Neutral10,
    surfaceVariant   = NeutralVar90,
    onSurfaceVariant = NeutralVar30,
    outline          = NeutralVar50,
    outlineVariant   = NeutralVar80,

    surfaceContainerLowest  = Color.White,
    surfaceContainerLow     = Neutral95,
    surfaceContainer        = Neutral90,
    surfaceContainerHigh    = NeutralVar90,
    surfaceContainerHighest = NeutralVar80,
)

private val DarkColorScheme = darkColorScheme(
    primary            = Indigo80,
    onPrimary          = Indigo10,
    primaryContainer   = Indigo20,
    onPrimaryContainer = Indigo90,

    secondary            = Slate80,
    onSecondary          = Slate10,
    secondaryContainer   = Slate40,
    onSecondaryContainer = Slate90,

    tertiary            = Teal80,
    onTertiary          = Teal10,
    tertiaryContainer   = Teal40,
    onTertiaryContainer = Teal90,

    error            = Red80,
    onError          = Red10,
    errorContainer   = Red40,
    onErrorContainer = Red90,

    background   = Neutral10,
    onBackground = Neutral90,

    surface          = Neutral10,
    onSurface        = Neutral90,
    surfaceVariant   = NeutralVar30,
    onSurfaceVariant = NeutralVar80,
    outline          = NeutralVar50,
    outlineVariant   = NeutralVar30,
)

@Composable
fun TrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
