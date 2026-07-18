package com.rameshta.splitframe.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class SplitFrameDimens(
    val space2: Dp = 2.dp,
    val space4: Dp = 4.dp,
    val space8: Dp = 8.dp,
    val space12: Dp = 12.dp,
    val space16: Dp = 16.dp,
    val space20: Dp = 20.dp,
    val space24: Dp = 24.dp,
    val space32: Dp = 32.dp,
    val touchTarget: Dp = 48.dp,
    val iconSmall: Dp = 18.dp,
    val icon: Dp = 24.dp,
    val iconLarge: Dp = 32.dp,
    val cardRadius: Dp = 16.dp,
    val sheetRadius: Dp = 24.dp,
)

val LocalSplitFrameDimens = staticCompositionLocalOf { SplitFrameDimens() }
