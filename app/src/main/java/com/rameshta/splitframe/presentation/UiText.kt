package com.rameshta.splitframe.presentation

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.rameshta.splitframe.R
import com.rameshta.splitframe.domain.LayoutTemplate
import com.rameshta.splitframe.domain.ExportResolution
import com.rameshta.splitframe.domain.TemplateCategory
import com.rameshta.splitframe.domain.TemplateIds

@StringRes
fun LayoutTemplate.titleRes(): Int =
    titleResOrNull() ?: R.string.app_name

@StringRes
fun LayoutTemplate.titleResOrNull(): Int? =
    when (id) {
        TemplateIds.SIDE_BY_SIDE -> R.string.template_side_by_side
        TemplateIds.TOP_BOTTOM -> R.string.template_top_bottom
        TemplateIds.GRID_2X2 -> R.string.template_grid_2x2
        TemplateIds.GRID_3X3 -> R.string.template_grid_3x3
        TemplateIds.BEFORE_AFTER -> R.string.template_before_after
        TemplateIds.TRIPTYCH_VERTICAL -> R.string.template_triptych_vertical
        TemplateIds.TRIPTYCH_HORIZONTAL -> R.string.template_triptych_horizontal
        TemplateIds.POSTCARD -> R.string.template_postcard
        TemplateIds.STORY_STACK -> R.string.template_story_stack
        TemplateIds.MOSAIC_5 -> R.string.template_mosaic_5
        TemplateIds.GRID_2X3 -> R.string.template_grid_2x3
        TemplateIds.GRID_3X2 -> R.string.template_grid_3x2
        TemplateIds.COVER_LEFT -> R.string.template_cover_left
        TemplateIds.COVER_TOP -> R.string.template_cover_top
        TemplateIds.FRAME_5 -> R.string.template_frame_5
        TemplateIds.PANORAMA_STACK -> R.string.template_panorama_stack
        TemplateIds.QUAD_STRIPS -> R.string.template_quad_strips
        TemplateIds.MAGAZINE_6 -> R.string.template_magazine_6
        TemplateIds.LARGE_SMALL_2 -> R.string.template_large_small_2
        TemplateIds.TWO_TOP_ONE_BOTTOM -> R.string.template_two_top_one_bottom
        TemplateIds.ONE_TOP_TWO_BOTTOM -> R.string.template_one_top_two_bottom
        TemplateIds.TWO_PLUS_THREE -> R.string.template_two_plus_three
        TemplateIds.ONE_LARGE_FOUR -> R.string.template_one_large_four
        TemplateIds.ADAPTIVE_GRID_7 -> R.string.template_adaptive_grid_7
        TemplateIds.ADAPTIVE_GRID_8 -> R.string.template_adaptive_grid_8
        TemplateIds.ADAPTIVE_GRID_9 -> R.string.template_adaptive_grid_9
        TemplateIds.ADAPTIVE_GRID_10 -> R.string.template_adaptive_grid_10
        TemplateIds.ADAPTIVE_GRID_11 -> R.string.template_adaptive_grid_11
        TemplateIds.ADAPTIVE_GRID_12 -> R.string.template_adaptive_grid_12
        TemplateIds.ADAPTIVE_GRID_13 -> R.string.template_adaptive_grid_13
        TemplateIds.ADAPTIVE_GRID_14 -> R.string.template_adaptive_grid_14
        TemplateIds.ADAPTIVE_GRID_15 -> R.string.template_adaptive_grid_15
        TemplateIds.BALANCED_MOSAIC_7 -> R.string.template_balanced_mosaic_7
        TemplateIds.BALANCED_MOSAIC_8 -> R.string.template_balanced_mosaic_8
        TemplateIds.BALANCED_MOSAIC_9 -> R.string.template_balanced_mosaic_9
        else -> null
    }

@StringRes
fun LayoutTemplate.descriptionRes(): Int =
    descriptionResOrNull() ?: R.string.app_tagline

@StringRes
fun LayoutTemplate.descriptionResOrNull(): Int? =
    when (id) {
        TemplateIds.SIDE_BY_SIDE -> R.string.template_side_by_side_desc
        TemplateIds.TOP_BOTTOM -> R.string.template_top_bottom_desc
        TemplateIds.GRID_2X2 -> R.string.template_grid_2x2_desc
        TemplateIds.GRID_3X3 -> R.string.template_grid_3x3_desc
        TemplateIds.BEFORE_AFTER -> R.string.template_before_after_desc
        TemplateIds.TRIPTYCH_VERTICAL -> R.string.template_triptych_vertical_desc
        TemplateIds.TRIPTYCH_HORIZONTAL -> R.string.template_triptych_horizontal_desc
        TemplateIds.POSTCARD -> R.string.template_postcard_desc
        TemplateIds.STORY_STACK -> R.string.template_story_stack_desc
        TemplateIds.MOSAIC_5 -> R.string.template_mosaic_5_desc
        TemplateIds.GRID_2X3 -> R.string.template_grid_2x3_desc
        TemplateIds.GRID_3X2 -> R.string.template_grid_3x2_desc
        TemplateIds.COVER_LEFT -> R.string.template_cover_left_desc
        TemplateIds.COVER_TOP -> R.string.template_cover_top_desc
        TemplateIds.FRAME_5 -> R.string.template_frame_5_desc
        TemplateIds.PANORAMA_STACK -> R.string.template_panorama_stack_desc
        TemplateIds.QUAD_STRIPS -> R.string.template_quad_strips_desc
        TemplateIds.MAGAZINE_6 -> R.string.template_magazine_6_desc
        TemplateIds.LARGE_SMALL_2 -> R.string.template_large_small_2_desc
        TemplateIds.TWO_TOP_ONE_BOTTOM -> R.string.template_two_top_one_bottom_desc
        TemplateIds.ONE_TOP_TWO_BOTTOM -> R.string.template_one_top_two_bottom_desc
        TemplateIds.TWO_PLUS_THREE -> R.string.template_two_plus_three_desc
        TemplateIds.ONE_LARGE_FOUR -> R.string.template_one_large_four_desc
        TemplateIds.ADAPTIVE_GRID_7 -> R.string.template_adaptive_grid_7_desc
        TemplateIds.ADAPTIVE_GRID_8 -> R.string.template_adaptive_grid_8_desc
        TemplateIds.ADAPTIVE_GRID_9 -> R.string.template_adaptive_grid_9_desc
        TemplateIds.ADAPTIVE_GRID_10 -> R.string.template_adaptive_grid_10_desc
        TemplateIds.ADAPTIVE_GRID_11 -> R.string.template_adaptive_grid_11_desc
        TemplateIds.ADAPTIVE_GRID_12 -> R.string.template_adaptive_grid_12_desc
        TemplateIds.ADAPTIVE_GRID_13 -> R.string.template_adaptive_grid_13_desc
        TemplateIds.ADAPTIVE_GRID_14 -> R.string.template_adaptive_grid_14_desc
        TemplateIds.ADAPTIVE_GRID_15 -> R.string.template_adaptive_grid_15_desc
        TemplateIds.BALANCED_MOSAIC_7 -> R.string.template_balanced_mosaic_7_desc
        TemplateIds.BALANCED_MOSAIC_8 -> R.string.template_balanced_mosaic_8_desc
        TemplateIds.BALANCED_MOSAIC_9 -> R.string.template_balanced_mosaic_9_desc
        else -> null
    }

@Composable
fun LayoutTemplate.titleText(): String =
    titleResOrNull()?.let { stringResource(it) }
        ?: stringResource(R.string.generated_template_title, categoryText(), slotCount)

@Composable
fun LayoutTemplate.descriptionText(): String =
    descriptionResOrNull()?.let { stringResource(it) }
        ?: pluralStringResource(
            R.plurals.generated_template_description,
            slotCount,
            slotCount,
            categoryText(),
        )

@Composable
private fun LayoutTemplate.categoryText(): String = stringResource(
    when (metadata.category) {
        TemplateCategory.Recommended -> R.string.template_group_recommended
        TemplateCategory.Grid -> R.string.template_group_grid
        TemplateCategory.Magazine -> R.string.template_group_magazine
        TemplateCategory.Mosaic -> R.string.template_group_mosaic
        TemplateCategory.Symmetrical -> R.string.template_group_symmetrical
        TemplateCategory.Asymmetrical -> R.string.template_group_asymmetrical
        TemplateCategory.Portrait -> R.string.template_group_portrait
        TemplateCategory.Landscape -> R.string.template_group_landscape
    },
)

@Composable
fun ExportResolution.labelText(): String =
    if (this == ExportResolution.ORIGINAL) stringResource(R.string.original_resolution) else label

@Composable
fun localizedRuntimeMessage(message: String): String {
    val exactResource = when (message) {
        "Media access is no longer available. Choose the affected media again and retry." -> R.string.error_media_access_revoked
        "Not enough storage is available. Free some space and try again." -> R.string.error_storage_full
        "Not enough memory for this image size." -> R.string.error_memory_image
        "Not enough memory for this export size." -> R.string.error_memory_export
        "Not enough memory to export this video." -> R.string.error_memory_video
        "Could not decode source image.", "Could not decode the source image." -> R.string.error_decode_image
        "Image processing failed.", "Image export failed. Try again." -> R.string.error_image_processing
        "Preset names must contain 1 to 40 characters." -> R.string.error_preset_name
        "Saved presets could not be loaded." -> R.string.error_presets_load
        "Saved presets could not be updated." -> R.string.error_presets_update
        "Could not inspect this image. Choose it again or try another photo." -> R.string.error_image_inspect
        "Saved export settings could not be restored. Defaults are in use." -> R.string.error_settings_restore
        "Export settings could not be remembered. You can still export this image." -> R.string.error_settings_remember
        "Please wait while the output size is checked." -> R.string.error_size_check_wait
        "Choose a supported output size first." -> R.string.error_choose_output_size
        "Processing cancelled." -> R.string.error_processing_cancelled
        "Select photos for the batch first." -> R.string.error_batch_select
        "No images were exported. Check the selected files and settings." -> R.string.error_batch_none
        "Batch export failed." -> R.string.error_batch_export
        "Batch export cancelled." -> R.string.error_batch_cancelled
        "Image saved, but recent export history could not be updated." -> R.string.error_history_update
        "Stopping the export safely…" -> R.string.error_stopping_export
        "This image could not be read.", "INVALID_DIMENSIONS" -> R.string.error_invalid_image
        "Enter a positive width and height for the custom canvas.", "INVALID_CUSTOM_DIMENSIONS" -> R.string.error_custom_dimensions
        "Wallpaper dimensions are unavailable. Choose another preset or Custom.", "WALLPAPER_UNAVAILABLE" -> R.string.error_wallpaper_dimensions
        "Use dimensions up to 8192 px per edge and 24 megapixels total.", "OUTPUT_TOO_LARGE" -> R.string.error_dimension_limit
        "Choose a target file size between 10 KB and 50 MB." -> R.string.error_target_size_range
        "The target file size is too small for this image. Choose a larger target or smaller dimensions." -> R.string.error_target_too_small
        "Preserved photo details exceed the target size. Choose a larger target or remove metadata." -> R.string.error_metadata_target_size
        "A selected video changed since it was added. Choose it again." -> R.string.error_video_changed
        "A selected video is missing or access was revoked. Choose it again." -> R.string.error_video_access_revoked
        "A selected video cannot be decoded on this device. Choose a different clip." -> R.string.error_video_decode
        "This device could not encode the video at the selected size. Try 720p or a shorter project." -> R.string.error_video_encode_size
        "A selected clip uses a codec this device cannot decode. Choose a different clip." -> R.string.error_video_codec
        "Saved video is unavailable." -> R.string.error_video_saved_unavailable
        "Video export failed." -> R.string.error_video_export_generic
        "Could not start video export." -> R.string.error_video_export_start
        "Unknown export error." -> R.string.error_export_unknown
        else -> null
    }
    if (exactResource != null) return stringResource(exactResource)

    val batchMatch = Regex("""(\d+) of (\d+) images could not be exported\.""").matchEntire(message)
    return if (batchMatch != null) {
        stringResource(
            R.string.error_batch_some,
            batchMatch.groupValues[1].toInt(),
            batchMatch.groupValues[2].toInt(),
        )
    } else {
        message
    }
}
