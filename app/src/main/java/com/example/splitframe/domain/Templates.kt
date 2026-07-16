package com.example.splitframe.domain

object TemplateIds {
    const val SIDE_BY_SIDE = "side_by_side"
    const val TOP_BOTTOM = "top_bottom"
    const val GRID_2X2 = "grid_2x2"
    const val GRID_3X3 = "grid_3x3"
    const val BEFORE_AFTER = "before_after"
    const val TRIPTYCH_VERTICAL = "triptych_vertical"
    const val TRIPTYCH_HORIZONTAL = "triptych_horizontal"
    const val POSTCARD = "postcard"
    const val STORY_STACK = "story_stack"
    const val MOSAIC_5 = "mosaic_5"
    const val GRID_2X3 = "grid_2x3"
    const val GRID_3X2 = "grid_3x2"
    const val COVER_LEFT = "cover_left"
    const val COVER_TOP = "cover_top"
    const val FRAME_5 = "frame_5"
    const val PANORAMA_STACK = "panorama_stack"
    const val QUAD_STRIPS = "quad_strips"
    const val MAGAZINE_6 = "magazine_6"
    const val LARGE_SMALL_2 = "large_small_2"
    const val TWO_TOP_ONE_BOTTOM = "two_top_one_bottom"
    const val ONE_TOP_TWO_BOTTOM = "one_top_two_bottom"
    const val TWO_PLUS_THREE = "two_plus_three"
    const val ONE_LARGE_FOUR = "one_large_four"
    const val ADAPTIVE_GRID_7 = "adaptive_grid_7"
    const val ADAPTIVE_GRID_8 = "adaptive_grid_8"
    const val ADAPTIVE_GRID_9 = "adaptive_grid_9"
    const val BALANCED_MOSAIC_7 = "balanced_mosaic_7"
    const val BALANCED_MOSAIC_8 = "balanced_mosaic_8"
    const val BALANCED_MOSAIC_9 = "balanced_mosaic_9"
}

class TemplateRepository {
    fun templates(): List<LayoutTemplate> = listOf(
        LayoutTemplate(
            id = TemplateIds.SIDE_BY_SIDE,
            name = TemplateIds.SIDE_BY_SIDE,
            cells = listOf(
                LayoutCell(NormalizedRect(0f, 0f, 0.5f, 1f), 0),
                LayoutCell(NormalizedRect(0.5f, 0f, 0.5f, 1f), 1),
            ),
            defaultSpacingDp = 8f,
            defaultCornerRadiusDp = 18f,
        ),
        LayoutTemplate(
            id = TemplateIds.TOP_BOTTOM,
            name = TemplateIds.TOP_BOTTOM,
            cells = listOf(
                LayoutCell(NormalizedRect(0f, 0f, 1f, 0.5f), 0),
                LayoutCell(NormalizedRect(0f, 0.5f, 1f, 0.5f), 1),
            ),
            defaultSpacingDp = 8f,
            defaultCornerRadiusDp = 18f,
        ),
        LayoutTemplate(
            id = TemplateIds.LARGE_SMALL_2,
            name = TemplateIds.LARGE_SMALL_2,
            cells = listOf(
                LayoutCell(NormalizedRect(0f, 0f, 0.66f, 1f), 0),
                LayoutCell(NormalizedRect(0.66f, 0f, 0.34f, 1f), 1),
            ),
            defaultSpacingDp = 8f,
            defaultCornerRadiusDp = 18f,
            aspectRatio = 5f / 4f,
        ),
        LayoutTemplate(
            id = TemplateIds.GRID_2X2,
            name = TemplateIds.GRID_2X2,
            cells = gridCells(columns = 2, rows = 2),
            defaultSpacingDp = 8f,
            defaultCornerRadiusDp = 16f,
        ),
        LayoutTemplate(
            id = TemplateIds.TRIPTYCH_VERTICAL,
            name = TemplateIds.TRIPTYCH_VERTICAL,
            cells = gridCells(columns = 3, rows = 1),
            defaultSpacingDp = 8f,
            defaultCornerRadiusDp = 16f,
        ),
        LayoutTemplate(
            id = TemplateIds.TRIPTYCH_HORIZONTAL,
            name = TemplateIds.TRIPTYCH_HORIZONTAL,
            cells = gridCells(columns = 1, rows = 3),
            defaultSpacingDp = 8f,
            defaultCornerRadiusDp = 16f,
            aspectRatio = 4f / 5f,
        ),
        LayoutTemplate(
            id = TemplateIds.POSTCARD,
            name = TemplateIds.POSTCARD,
            cells = listOf(
                LayoutCell(NormalizedRect(0f, 0f, 0.62f, 1f), 0),
                LayoutCell(NormalizedRect(0.62f, 0f, 0.38f, 0.5f), 1),
                LayoutCell(NormalizedRect(0.62f, 0.5f, 0.38f, 0.5f), 2),
            ),
            defaultSpacingDp = 8f,
            defaultCornerRadiusDp = 16f,
            aspectRatio = 16f / 9f,
        ),
        LayoutTemplate(
            id = TemplateIds.TWO_TOP_ONE_BOTTOM,
            name = TemplateIds.TWO_TOP_ONE_BOTTOM,
            cells = listOf(
                LayoutCell(NormalizedRect(0f, 0f, 0.5f, 0.5f), 0),
                LayoutCell(NormalizedRect(0.5f, 0f, 0.5f, 0.5f), 1),
                LayoutCell(NormalizedRect(0f, 0.5f, 1f, 0.5f), 2),
            ),
            defaultSpacingDp = 8f,
            defaultCornerRadiusDp = 16f,
        ),
        LayoutTemplate(
            id = TemplateIds.ONE_TOP_TWO_BOTTOM,
            name = TemplateIds.ONE_TOP_TWO_BOTTOM,
            cells = listOf(
                LayoutCell(NormalizedRect(0f, 0f, 1f, 0.5f), 0),
                LayoutCell(NormalizedRect(0f, 0.5f, 0.5f, 0.5f), 1),
                LayoutCell(NormalizedRect(0.5f, 0.5f, 0.5f, 0.5f), 2),
            ),
            defaultSpacingDp = 8f,
            defaultCornerRadiusDp = 16f,
        ),
        LayoutTemplate(
            id = TemplateIds.STORY_STACK,
            name = TemplateIds.STORY_STACK,
            cells = listOf(
                LayoutCell(NormalizedRect(0f, 0f, 1f, 0.62f), 0),
                LayoutCell(NormalizedRect(0f, 0.62f, 0.5f, 0.38f), 1),
                LayoutCell(NormalizedRect(0.5f, 0.62f, 0.5f, 0.38f), 2),
            ),
            defaultSpacingDp = 8f,
            defaultCornerRadiusDp = 16f,
            aspectRatio = 4f / 5f,
        ),
        LayoutTemplate(
            id = TemplateIds.MOSAIC_5,
            name = TemplateIds.MOSAIC_5,
            cells = listOf(
                LayoutCell(NormalizedRect(0f, 0f, 0.6f, 0.6f), 0),
                LayoutCell(NormalizedRect(0.6f, 0f, 0.4f, 0.3f), 1),
                LayoutCell(NormalizedRect(0.6f, 0.3f, 0.4f, 0.3f), 2),
                LayoutCell(NormalizedRect(0f, 0.6f, 0.32f, 0.4f), 3),
                LayoutCell(NormalizedRect(0.32f, 0.6f, 0.68f, 0.4f), 4),
            ),
            defaultSpacingDp = 7f,
            defaultCornerRadiusDp = 14f,
        ),
        LayoutTemplate(
            id = TemplateIds.TWO_PLUS_THREE,
            name = TemplateIds.TWO_PLUS_THREE,
            cells = listOf(
                LayoutCell(NormalizedRect(0f, 0f, 0.5f, 0.5f), 0),
                LayoutCell(NormalizedRect(0.5f, 0f, 0.5f, 0.5f), 1),
                LayoutCell(NormalizedRect(0f, 0.5f, 1f / 3f, 0.5f), 2),
                LayoutCell(NormalizedRect(1f / 3f, 0.5f, 1f / 3f, 0.5f), 3),
                LayoutCell(NormalizedRect(2f / 3f, 0.5f, 1f / 3f, 0.5f), 4),
            ),
            defaultSpacingDp = 7f,
            defaultCornerRadiusDp = 14f,
        ),
        LayoutTemplate(
            id = TemplateIds.ONE_LARGE_FOUR,
            name = TemplateIds.ONE_LARGE_FOUR,
            cells = listOf(
                LayoutCell(NormalizedRect(0f, 0f, 0.6f, 1f), 0),
                LayoutCell(NormalizedRect(0.6f, 0f, 0.4f, 0.25f), 1),
                LayoutCell(NormalizedRect(0.6f, 0.25f, 0.4f, 0.25f), 2),
                LayoutCell(NormalizedRect(0.6f, 0.5f, 0.4f, 0.25f), 3),
                LayoutCell(NormalizedRect(0.6f, 0.75f, 0.4f, 0.25f), 4),
            ),
            defaultSpacingDp = 7f,
            defaultCornerRadiusDp = 14f,
            aspectRatio = 5f / 4f,
        ),
        LayoutTemplate(
            id = TemplateIds.COVER_LEFT,
            name = TemplateIds.COVER_LEFT,
            cells = listOf(
                LayoutCell(NormalizedRect(0f, 0f, 0.58f, 1f), 0),
                LayoutCell(NormalizedRect(0.58f, 0f, 0.42f, 1f / 3f), 1),
                LayoutCell(NormalizedRect(0.58f, 1f / 3f, 0.42f, 1f / 3f), 2),
                LayoutCell(NormalizedRect(0.58f, 2f / 3f, 0.42f, 1f / 3f), 3),
            ),
            defaultSpacingDp = 7f,
            defaultCornerRadiusDp = 15f,
            aspectRatio = 5f / 4f,
        ),
        LayoutTemplate(
            id = TemplateIds.COVER_TOP,
            name = TemplateIds.COVER_TOP,
            cells = listOf(
                LayoutCell(NormalizedRect(0f, 0f, 1f, 0.58f), 0),
                LayoutCell(NormalizedRect(0f, 0.58f, 1f / 3f, 0.42f), 1),
                LayoutCell(NormalizedRect(1f / 3f, 0.58f, 1f / 3f, 0.42f), 2),
                LayoutCell(NormalizedRect(2f / 3f, 0.58f, 1f / 3f, 0.42f), 3),
            ),
            defaultSpacingDp = 7f,
            defaultCornerRadiusDp = 15f,
            aspectRatio = 4f / 5f,
        ),
        LayoutTemplate(
            id = TemplateIds.FRAME_5,
            name = TemplateIds.FRAME_5,
            cells = listOf(
                LayoutCell(NormalizedRect(0f, 0f, 0.28f, 0.5f), 0),
                LayoutCell(NormalizedRect(0f, 0.5f, 0.28f, 0.5f), 1),
                LayoutCell(NormalizedRect(0.28f, 0f, 0.44f, 1f), 2),
                LayoutCell(NormalizedRect(0.72f, 0f, 0.28f, 0.5f), 3),
                LayoutCell(NormalizedRect(0.72f, 0.5f, 0.28f, 0.5f), 4),
            ),
            defaultSpacingDp = 7f,
            defaultCornerRadiusDp = 14f,
        ),
        LayoutTemplate(
            id = TemplateIds.PANORAMA_STACK,
            name = TemplateIds.PANORAMA_STACK,
            cells = listOf(
                LayoutCell(NormalizedRect(0f, 0f, 1f, 0.46f), 0),
                LayoutCell(NormalizedRect(0f, 0.46f, 1f, 0.27f), 1),
                LayoutCell(NormalizedRect(0f, 0.73f, 0.5f, 0.27f), 2),
                LayoutCell(NormalizedRect(0.5f, 0.73f, 0.5f, 0.27f), 3),
            ),
            defaultSpacingDp = 7f,
            defaultCornerRadiusDp = 14f,
            aspectRatio = 16f / 9f,
        ),
        LayoutTemplate(
            id = TemplateIds.QUAD_STRIPS,
            name = TemplateIds.QUAD_STRIPS,
            cells = gridCells(columns = 4, rows = 1),
            defaultSpacingDp = 6f,
            defaultCornerRadiusDp = 14f,
            aspectRatio = 16f / 9f,
        ),
        LayoutTemplate(
            id = TemplateIds.MAGAZINE_6,
            name = TemplateIds.MAGAZINE_6,
            cells = listOf(
                LayoutCell(NormalizedRect(0f, 0f, 0.64f, 0.56f), 0),
                LayoutCell(NormalizedRect(0.64f, 0f, 0.36f, 0.28f), 1),
                LayoutCell(NormalizedRect(0.64f, 0.28f, 0.36f, 0.28f), 2),
                LayoutCell(NormalizedRect(0f, 0.56f, 0.34f, 0.44f), 3),
                LayoutCell(NormalizedRect(0.34f, 0.56f, 0.33f, 0.44f), 4),
                LayoutCell(NormalizedRect(0.67f, 0.56f, 0.33f, 0.44f), 5),
            ),
            defaultSpacingDp = 6f,
            defaultCornerRadiusDp = 13f,
            aspectRatio = 5f / 4f,
        ),
        LayoutTemplate(
            id = TemplateIds.GRID_2X3,
            name = TemplateIds.GRID_2X3,
            cells = gridCells(columns = 2, rows = 3),
            defaultSpacingDp = 6f,
            defaultCornerRadiusDp = 12f,
            aspectRatio = 4f / 5f,
        ),
        LayoutTemplate(
            id = TemplateIds.GRID_3X2,
            name = TemplateIds.GRID_3X2,
            cells = gridCells(columns = 3, rows = 2),
            defaultSpacingDp = 6f,
            defaultCornerRadiusDp = 12f,
            aspectRatio = 5f / 4f,
        ),
        LayoutTemplate(
            id = TemplateIds.GRID_3X3,
            name = TemplateIds.GRID_3X3,
            cells = gridCells(columns = 3, rows = 3),
            defaultSpacingDp = 6f,
            defaultCornerRadiusDp = 12f,
        ),
        LayoutTemplate(
            id = TemplateIds.ADAPTIVE_GRID_7,
            name = TemplateIds.ADAPTIVE_GRID_7,
            cells = adaptiveGridCells(count = 7, columns = 3),
            defaultSpacingDp = 6f,
            defaultCornerRadiusDp = 12f,
            aspectRatio = 4f / 5f,
        ),
        LayoutTemplate(
            id = TemplateIds.ADAPTIVE_GRID_8,
            name = TemplateIds.ADAPTIVE_GRID_8,
            cells = adaptiveGridCells(count = 8, columns = 3),
            defaultSpacingDp = 6f,
            defaultCornerRadiusDp = 12f,
            aspectRatio = 4f / 5f,
        ),
        LayoutTemplate(
            id = TemplateIds.ADAPTIVE_GRID_9,
            name = TemplateIds.ADAPTIVE_GRID_9,
            cells = gridCells(columns = 3, rows = 3),
            defaultSpacingDp = 6f,
            defaultCornerRadiusDp = 12f,
        ),
        LayoutTemplate(
            id = TemplateIds.BALANCED_MOSAIC_7,
            name = TemplateIds.BALANCED_MOSAIC_7,
            cells = listOf(
                LayoutCell(NormalizedRect(0f, 0f, 0.5f, 0.42f), 0),
                LayoutCell(NormalizedRect(0.5f, 0f, 0.5f, 0.42f), 1),
                LayoutCell(NormalizedRect(0f, 0.42f, 1f / 3f, 0.29f), 2),
                LayoutCell(NormalizedRect(1f / 3f, 0.42f, 1f / 3f, 0.29f), 3),
                LayoutCell(NormalizedRect(2f / 3f, 0.42f, 1f / 3f, 0.29f), 4),
                LayoutCell(NormalizedRect(0f, 0.71f, 0.5f, 0.29f), 5),
                LayoutCell(NormalizedRect(0.5f, 0.71f, 0.5f, 0.29f), 6),
            ),
            defaultSpacingDp = 6f,
            defaultCornerRadiusDp = 12f,
            aspectRatio = 4f / 5f,
        ),
        LayoutTemplate(
            id = TemplateIds.BALANCED_MOSAIC_8,
            name = TemplateIds.BALANCED_MOSAIC_8,
            cells = listOf(
                LayoutCell(NormalizedRect(0f, 0f, 0.5f, 0.38f), 0),
                LayoutCell(NormalizedRect(0.5f, 0f, 0.5f, 0.38f), 1),
                LayoutCell(NormalizedRect(0f, 0.38f, 0.25f, 0.31f), 2),
                LayoutCell(NormalizedRect(0.25f, 0.38f, 0.25f, 0.31f), 3),
                LayoutCell(NormalizedRect(0.5f, 0.38f, 0.25f, 0.31f), 4),
                LayoutCell(NormalizedRect(0.75f, 0.38f, 0.25f, 0.31f), 5),
                LayoutCell(NormalizedRect(0f, 0.69f, 0.5f, 0.31f), 6),
                LayoutCell(NormalizedRect(0.5f, 0.69f, 0.5f, 0.31f), 7),
            ),
            defaultSpacingDp = 6f,
            defaultCornerRadiusDp = 12f,
            aspectRatio = 4f / 5f,
        ),
        LayoutTemplate(
            id = TemplateIds.BALANCED_MOSAIC_9,
            name = TemplateIds.BALANCED_MOSAIC_9,
            cells = listOf(
                LayoutCell(NormalizedRect(0f, 0f, 0.64f, 0.48f), 0),
                LayoutCell(NormalizedRect(0.64f, 0f, 0.36f, 0.24f), 1),
                LayoutCell(NormalizedRect(0.64f, 0.24f, 0.36f, 0.24f), 2),
                LayoutCell(NormalizedRect(0f, 0.48f, 1f / 3f, 0.26f), 3),
                LayoutCell(NormalizedRect(1f / 3f, 0.48f, 1f / 3f, 0.26f), 4),
                LayoutCell(NormalizedRect(2f / 3f, 0.48f, 1f / 3f, 0.26f), 5),
                LayoutCell(NormalizedRect(0f, 0.74f, 1f / 3f, 0.26f), 6),
                LayoutCell(NormalizedRect(1f / 3f, 0.74f, 1f / 3f, 0.26f), 7),
                LayoutCell(NormalizedRect(2f / 3f, 0.74f, 1f / 3f, 0.26f), 8),
            ),
            defaultSpacingDp = 6f,
            defaultCornerRadiusDp = 12f,
            aspectRatio = 4f / 5f,
        ),
        LayoutTemplate(
            id = TemplateIds.BEFORE_AFTER,
            name = TemplateIds.BEFORE_AFTER,
            cells = listOf(
                LayoutCell(NormalizedRect(0f, 0f, 1f, 1f), 0),
                LayoutCell(NormalizedRect(0f, 0f, 1f, 1f), 1),
            ),
            defaultSpacingDp = 0f,
            defaultCornerRadiusDp = 18f,
            kind = TemplateKind.BeforeAfter,
        ),
    )

    private fun gridCells(columns: Int, rows: Int): List<LayoutCell> =
        buildList {
            var index = 0
            repeat(rows) { row ->
                repeat(columns) { column ->
                    add(
                        LayoutCell(
                            rect = NormalizedRect(
                                x = column / columns.toFloat(),
                                y = row / rows.toFloat(),
                                width = 1f / columns,
                                height = 1f / rows,
                            ),
                            index = index,
                        ),
                    )
                    index += 1
                }
            }
        }

    private fun adaptiveGridCells(count: Int, columns: Int): List<LayoutCell> =
        buildList {
            val rows = (count + columns - 1) / columns
            var index = 0
            repeat(rows) { row ->
                val remaining = count - index
                val cellsInRow = minOf(columns, remaining)
                repeat(cellsInRow) { column ->
                    add(
                        LayoutCell(
                            rect = NormalizedRect(
                                x = column / cellsInRow.toFloat(),
                                y = row / rows.toFloat(),
                                width = 1f / cellsInRow,
                                height = 1f / rows,
                            ),
                            index = index,
                        ),
                    )
                    index += 1
                }
            }
        }
}
