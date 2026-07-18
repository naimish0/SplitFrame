package com.rameshta.splitframe.presentation

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rameshta.splitframe.R
import com.rameshta.splitframe.domain.LayoutTemplate
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
    titleResOrNull()?.let { stringResource(it) } ?: name

@Composable
fun LayoutTemplate.descriptionText(): String =
    descriptionResOrNull()?.let { stringResource(it) } ?: "${slotCount} image ${metadata.category.name.lowercase()} layout"
