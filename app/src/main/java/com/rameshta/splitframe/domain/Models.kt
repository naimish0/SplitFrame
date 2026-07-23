package com.rameshta.splitframe.domain

data class LayoutTemplate(
    val id: String,
    val name: String,
    val cells: List<LayoutCell>,
    val defaultSpacingDp: Float,
    val defaultCornerRadiusDp: Float,
    val aspectRatio: Float = 1f,
    val kind: TemplateKind = TemplateKind.Standard,
    val metadata: TemplateMetadata = TemplateMetadata(),
) {
    val slotCount: Int get() = cells.size
}

data class LayoutCell(
    val rect: NormalizedRect,
    val index: Int,
)

enum class TemplateKind {
    Standard,
    BeforeAfter,
}

enum class TemplateCategory {
    Recommended,
    Grid,
    Magazine,
    Mosaic,
    Symmetrical,
    Asymmetrical,
    Portrait,
    Landscape,
}

enum class TemplateFilter {
    ALL,
    SYMMETRICAL,
    ASYMMETRICAL,
    PORTRAIT,
    LANDSCAPE,
}

enum class TemplateOrientation {
    Square,
    Portrait,
    Landscape,
}

data class TemplateMetadata(
    val category: TemplateCategory = TemplateCategory.Recommended,
    val previewAsset: String? = null,
    val supportedOrientations: Set<TemplateOrientation> = setOf(
        TemplateOrientation.Square,
        TemplateOrientation.Portrait,
        TemplateOrientation.Landscape,
    ),
)

object CollageLimits {
    const val MaxImages = 15
}

data class MergeProject(
    val id: String,
    val template: LayoutTemplate,
    val assignedImages: Map<Int, ImageSource>,
    val imageTransforms: Map<Int, ImageTransform> = emptyMap(),
    val spacingDp: Float,
    val cornerRadiusDp: Float,
    val backgroundColor: ULong,
    val borderColor: ULong,
    val borderWidthDp: Float,
    val exportResolution: ExportResolution = ExportResolution.FHD_1080,
    val beforeAfterSlider: Float = 0.5f,
    val backgroundGradient: CollageGradient = CollageGradient.Neutral,
    val backgroundStyle: CollageBackgroundStyle = CollageBackgroundStyle(),
    val borderStyle: CollageBorderStyle = CollageBorderStyle(),
    val cropShapes: Map<Int, CropShape> = emptyMap(),
    val textLayers: List<CollageTextLayer> = emptyList(),
) {
    val isReadyForImageExport: Boolean
        get() = assignedImages.isNotEmpty() &&
            template.cells.all { assignedImages.containsKey(it.index) }
}

data class CollageGradient(
    val startColor: ULong,
    val centerColor: ULong,
    val endColor: ULong,
    val isFallback: Boolean = false,
) {
    companion object {
        val Neutral = CollageGradient(
            startColor = 0xFFF7FAF9uL,
            centerColor = 0xFFEAF4F1uL,
            endColor = 0xFFE7F0EEuL,
            isFallback = true,
        )

        fun solid(argb: ULong): CollageGradient =
            CollageGradient(
                startColor = argb,
                centerColor = argb,
                endColor = argb,
                isFallback = false,
            )
    }
}

data class ImageTransform(
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
) {
    fun normalized(): ImageTransform =
        copy(
            zoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM),
            panX = panX.coerceIn(-1f, 1f),
            panY = panY.coerceIn(-1f, 1f),
        )

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 5f
        val Default = ImageTransform()
    }
}

data class ImageEditState(
    val cropRect: NormalizedRect = NormalizedRect.Full,
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
)

data class CollageImage(
    val id: String,
    val source: ImageSource,
    val editState: ImageEditState = ImageEditState(),
)

sealed interface ImageSource {
    data class LocalUri(val uri: String) : ImageSource
}

enum class ExportResolution(val label: String, val longEdgePx: Int) {
    SD_480(label = "480p", longEdgePx = 854),
    HD_720(label = "720p", longEdgePx = 1280),
    FHD_1080(label = "1080p", longEdgePx = 1920),
    QHD_1440(label = "1440p (2K)", longEdgePx = 2560),
    UHD_2160(label = "2160p (4K)", longEdgePx = 3840),
    ORIGINAL(label = "Original", longEdgePx = -1),
}

sealed interface ExportResult {
    data class Success(
        val savedUri: String,
        val sizeBytes: Long? = null,
    ) : ExportResult
    data class Failure(val reason: String) : ExportResult
}
