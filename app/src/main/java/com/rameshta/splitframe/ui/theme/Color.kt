package com.rameshta.splitframe.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val BrandPrimaryLight = Color(0xFF0F766E)
val BrandOnPrimaryLight = Color(0xFFFFFFFF)
val BrandPrimaryContainerLight = Color(0xFFCCFBF1)
val BrandOnPrimaryContainerLight = Color(0xFF134E4A)
val BrandSecondaryLight = Color(0xFFC2415A)
val BrandOnSecondaryLight = Color(0xFFFFFFFF)
val BrandSecondaryContainerLight = Color(0xFFFFE4E6)
val BrandOnSecondaryContainerLight = Color(0xFF881337)
val BrandTertiaryLight = Color(0xFFB45309)
val BrandOnTertiaryLight = Color(0xFFFFFFFF)
val BrandTertiaryContainerLight = Color(0xFFFEF3C7)
val BrandOnTertiaryContainerLight = Color(0xFF78350F)
val BrandBackgroundLight = Color(0xFFF8FAF9)
val BrandOnBackgroundLight = Color(0xFF102A2A)
val BrandSurfaceLight = Color(0xFFFFFFFF)
val BrandSurfaceVariantLight = Color(0xFFE7F0EE)
val BrandOnSurfaceLight = Color(0xFF102A2A)
val BrandOnSurfaceVariantLight = Color(0xFF405C59)
val BrandOutlineLight = Color(0xFF718B87)
val BrandOutlineVariantLight = Color(0xFFC9D8D5)
val BrandErrorLight = Color(0xFFBA1A1A)
val BrandOnErrorLight = Color(0xFFFFFFFF)

val BrandPrimaryDark = Color(0xFF5EEAD4)
val BrandOnPrimaryDark = Color(0xFF042F2E)
val BrandPrimaryContainerDark = Color(0xFF115E59)
val BrandOnPrimaryContainerDark = Color(0xFFCCFBF1)
val BrandSecondaryDark = Color(0xFFFDA4AF)
val BrandOnSecondaryDark = Color(0xFF65001F)
val BrandSecondaryContainerDark = Color(0xFF881337)
val BrandOnSecondaryContainerDark = Color(0xFFFFE4E6)
val BrandTertiaryDark = Color(0xFFFCD34D)
val BrandOnTertiaryDark = Color(0xFF3D2C00)
val BrandTertiaryContainerDark = Color(0xFF78350F)
val BrandOnTertiaryContainerDark = Color(0xFFFEF3C7)
val BrandBackgroundDark = Color(0xFF071A1A)
val BrandOnBackgroundDark = Color(0xFFDDF4F0)
val BrandSurfaceDark = Color(0xFF0D2423)
val BrandSurfaceVariantDark = Color(0xFF173331)
val BrandOnSurfaceDark = Color(0xFFDDF4F0)
val BrandOnSurfaceVariantDark = Color(0xFFB7CCCA)
val BrandOutlineDark = Color(0xFF819794)
val BrandOutlineVariantDark = Color(0xFF344C49)
val BrandErrorDark = Color(0xFFFFB4AB)
val BrandOnErrorDark = Color(0xFF690005)

@Immutable
data class SplitFrameSemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val editorCanvas: Color,
    val editorCanvasGrid: Color,
    val selectedCell: Color,
    val selectedCellContainer: Color,
    val exportProgress: Color,
    val adContainer: Color,
    val disabledOverlay: Color,
)

val LightSemanticColors = SplitFrameSemanticColors(
    success = Color(0xFF047857),
    onSuccess = Color.White,
    successContainer = Color(0xFFD1FAE5),
    onSuccessContainer = Color(0xFF064E3B),
    warning = BrandTertiaryLight,
    onWarning = BrandOnTertiaryLight,
    warningContainer = BrandTertiaryContainerLight,
    onWarningContainer = BrandOnTertiaryContainerLight,
    editorCanvas = Color(0xFFEAF4F1),
    editorCanvasGrid = Color(0xFFC9D8D5),
    selectedCell = BrandPrimaryLight,
    selectedCellContainer = BrandPrimaryContainerLight,
    exportProgress = BrandPrimaryLight,
    adContainer = Color(0xFFF0F7F5),
    disabledOverlay = Color(0x66102A2A),
)

val DarkSemanticColors = SplitFrameSemanticColors(
    success = Color(0xFF6EE7B7),
    onSuccess = Color(0xFF052E2B),
    successContainer = Color(0xFF065F46),
    onSuccessContainer = Color(0xFFD1FAE5),
    warning = BrandTertiaryDark,
    onWarning = BrandOnTertiaryDark,
    warningContainer = BrandTertiaryContainerDark,
    onWarningContainer = BrandOnTertiaryContainerDark,
    editorCanvas = Color(0xFF12302E),
    editorCanvasGrid = Color(0xFF344C49),
    selectedCell = BrandPrimaryDark,
    selectedCellContainer = BrandPrimaryContainerDark,
    exportProgress = BrandPrimaryDark,
    adContainer = Color(0xFF102B2A),
    disabledOverlay = Color(0x99DDF4F0),
)

val LocalSplitFrameColors = staticCompositionLocalOf { LightSemanticColors }

@Immutable
data class EditorBackgroundSwatch(
    val argb: ULong,
    val color: Color,
)

val EditorBackgroundSwatches = listOf(
    EditorBackgroundSwatch(0xFFFFFFFFuL, Color.White),
    EditorBackgroundSwatch(0xFF102A2AuL, Color(0xFF102A2A)),
    EditorBackgroundSwatch(0xFFE7F0EEuL, Color(0xFFE7F0EE)),
    EditorBackgroundSwatch(0xFFFFE4E6uL, Color(0xFFFFE4E6)),
)
