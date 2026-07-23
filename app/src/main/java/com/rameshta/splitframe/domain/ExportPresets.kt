package com.rameshta.splitframe.domain

enum class SingleImageResizePreset {
    InstagramSquarePost,
    InstagramPortraitPost,
    InstagramStoryReel,
    WhatsAppStatus,
    YouTubeThumbnail,
    PinterestPin,
    DeviceWallpaper,
    Custom,
    LongEdge1080,
    LongEdge2K,
    LongEdge4K,
    Scale2x,
    Scale4x,
}

enum class ExportPresetGroup {
    SocialAndCommon,
    Resize,
}

sealed interface ExportCanvasRule {
    data class Fixed(val dimensions: ImageDimensions) : ExportCanvasRule
    data class OriginalLongEdge(val longEdgePx: Int) : ExportCanvasRule
    data class OriginalScale(val factor: Int) : ExportCanvasRule
    data object DeviceWallpaper : ExportCanvasRule
    data object Custom : ExportCanvasRule
}

data class ExportPresetDefinition(
    val id: SingleImageResizePreset,
    val group: ExportPresetGroup,
    val canvasRule: ExportCanvasRule,
)

/**
 * Canonical export-canvas definitions for the single-image tool.
 *
 * Format and encoding quality intentionally remain outside this catalog. A preset
 * chooses only canvas dimensions; [SingleImageResizeRequest] controls how content
 * is placed and encoded.
 */
object ExportPresetCatalog {
    val definitions: List<ExportPresetDefinition> = listOf(
        fixed(SingleImageResizePreset.InstagramSquarePost, 1080, 1080),
        fixed(SingleImageResizePreset.InstagramPortraitPost, 1080, 1350),
        fixed(SingleImageResizePreset.InstagramStoryReel, 1080, 1920),
        fixed(SingleImageResizePreset.WhatsAppStatus, 1080, 1920),
        fixed(SingleImageResizePreset.YouTubeThumbnail, 1280, 720),
        fixed(SingleImageResizePreset.PinterestPin, 1000, 1500),
        ExportPresetDefinition(
            id = SingleImageResizePreset.DeviceWallpaper,
            group = ExportPresetGroup.SocialAndCommon,
            canvasRule = ExportCanvasRule.DeviceWallpaper,
        ),
        ExportPresetDefinition(
            id = SingleImageResizePreset.Custom,
            group = ExportPresetGroup.SocialAndCommon,
            canvasRule = ExportCanvasRule.Custom,
        ),
        ExportPresetDefinition(
            id = SingleImageResizePreset.LongEdge1080,
            group = ExportPresetGroup.Resize,
            canvasRule = ExportCanvasRule.OriginalLongEdge(1080),
        ),
        ExportPresetDefinition(
            id = SingleImageResizePreset.LongEdge2K,
            group = ExportPresetGroup.Resize,
            canvasRule = ExportCanvasRule.OriginalLongEdge(2560),
        ),
        ExportPresetDefinition(
            id = SingleImageResizePreset.LongEdge4K,
            group = ExportPresetGroup.Resize,
            canvasRule = ExportCanvasRule.OriginalLongEdge(3840),
        ),
        ExportPresetDefinition(
            id = SingleImageResizePreset.Scale2x,
            group = ExportPresetGroup.Resize,
            canvasRule = ExportCanvasRule.OriginalScale(2),
        ),
        ExportPresetDefinition(
            id = SingleImageResizePreset.Scale4x,
            group = ExportPresetGroup.Resize,
            canvasRule = ExportCanvasRule.OriginalScale(4),
        ),
    )

    val socialAndCommon: List<ExportPresetDefinition> =
        definitions.filter { it.group == ExportPresetGroup.SocialAndCommon }

    val resize: List<ExportPresetDefinition> =
        definitions.filter { it.group == ExportPresetGroup.Resize }

    private val definitionsById = definitions.associateBy(ExportPresetDefinition::id)

    fun definition(id: SingleImageResizePreset): ExportPresetDefinition =
        checkNotNull(definitionsById[id]) { "Missing export preset definition for $id" }

    private fun fixed(
        id: SingleImageResizePreset,
        widthPx: Int,
        heightPx: Int,
    ): ExportPresetDefinition =
        ExportPresetDefinition(
            id = id,
            group = ExportPresetGroup.SocialAndCommon,
            canvasRule = ExportCanvasRule.Fixed(ImageDimensions(widthPx, heightPx)),
        )
}
