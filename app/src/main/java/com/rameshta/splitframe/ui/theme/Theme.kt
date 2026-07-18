package com.rameshta.splitframe.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = BrandOnPrimaryDark,
    primaryContainer = BrandPrimaryContainerDark,
    onPrimaryContainer = BrandOnPrimaryContainerDark,
    secondary = BrandSecondaryDark,
    onSecondary = BrandOnSecondaryDark,
    secondaryContainer = BrandSecondaryContainerDark,
    onSecondaryContainer = BrandOnSecondaryContainerDark,
    tertiary = BrandTertiaryDark,
    onTertiary = BrandOnTertiaryDark,
    tertiaryContainer = BrandTertiaryContainerDark,
    onTertiaryContainer = BrandOnTertiaryContainerDark,
    background = BrandBackgroundDark,
    onBackground = BrandOnBackgroundDark,
    surface = BrandSurfaceDark,
    surfaceVariant = BrandSurfaceVariantDark,
    surfaceContainer = BrandSurfaceDark,
    surfaceContainerLow = ColorWithAlpha(BrandSurfaceDark, 0.82f),
    surfaceContainerHighest = BrandSurfaceVariantDark,
    onSurface = BrandOnSurfaceDark,
    onSurfaceVariant = BrandOnSurfaceVariantDark,
    outline = BrandOutlineDark,
    outlineVariant = BrandOutlineVariantDark,
    error = BrandErrorDark,
    onError = BrandOnErrorDark,
)

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimaryLight,
    onPrimary = BrandOnPrimaryLight,
    primaryContainer = BrandPrimaryContainerLight,
    onPrimaryContainer = BrandOnPrimaryContainerLight,
    secondary = BrandSecondaryLight,
    onSecondary = BrandOnSecondaryLight,
    secondaryContainer = BrandSecondaryContainerLight,
    onSecondaryContainer = BrandOnSecondaryContainerLight,
    tertiary = BrandTertiaryLight,
    onTertiary = BrandOnTertiaryLight,
    tertiaryContainer = BrandTertiaryContainerLight,
    onTertiaryContainer = BrandOnTertiaryContainerLight,
    background = BrandBackgroundLight,
    onBackground = BrandOnBackgroundLight,
    surface = BrandSurfaceLight,
    surfaceVariant = BrandSurfaceVariantLight,
    surfaceContainer = BrandSurfaceLight,
    surfaceContainerLow = BrandSurfaceLight,
    surfaceContainerHighest = BrandSurfaceVariantLight,
    onSurface = BrandOnSurfaceLight,
    onSurfaceVariant = BrandOnSurfaceVariantLight,
    outline = BrandOutlineLight,
    outlineVariant = BrandOutlineVariantLight,
    error = BrandErrorLight,
    onError = BrandOnErrorLight,
)

@Composable
fun SplitFrameTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(
        LocalSplitFrameColors provides semanticColors,
        LocalSplitFrameDimens provides SplitFrameDimens(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SplitFrameTypography,
            shapes = SplitFrameShapes,
            content = content,
        )
    }
}

@Composable
fun splitFrameColors(): SplitFrameSemanticColors = LocalSplitFrameColors.current

@Composable
fun splitFrameDimens(): SplitFrameDimens = LocalSplitFrameDimens.current

@Suppress("FunctionName")
private fun ColorWithAlpha(color: androidx.compose.ui.graphics.Color, alpha: Float): androidx.compose.ui.graphics.Color =
    color.copy(alpha = alpha)
