package com.rameshta.splitframe.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeProjectExportReadinessTest {
    private val template = LayoutTemplate(
        id = "two-cells",
        name = "Two cells",
        cells = listOf(
            LayoutCell(NormalizedRect(0f, 0f, 0.5f, 1f), 0),
            LayoutCell(NormalizedRect(0.5f, 0f, 1f, 1f), 1),
        ),
        defaultSpacingDp = 0f,
        defaultCornerRadiusDp = 0f,
    )

    @Test
    fun imageExportIsReadyOnlyWhenEveryTemplateCellHasAnImage() {
        val project = project(
            assignedImages = mapOf(
                0 to ImageSource.LocalUri("content://splitframe/one"),
                1 to ImageSource.LocalUri("content://splitframe/two"),
            ),
        )

        assertTrue(project.isReadyForImageExport)
    }

    @Test
    fun imageExportIsNotReadyForEmptyOrPartiallyFilledTemplates() {
        assertFalse(project(assignedImages = emptyMap()).isReadyForImageExport)
        assertFalse(
            project(
                assignedImages = mapOf(
                    0 to ImageSource.LocalUri("content://splitframe/one"),
                ),
            ).isReadyForImageExport,
        )
    }

    private fun project(assignedImages: Map<Int, ImageSource>): MergeProject =
        MergeProject(
            id = "project",
            template = template,
            assignedImages = assignedImages,
            spacingDp = 0f,
            cornerRadiusDp = 0f,
            backgroundColor = 0xFFFFFFFFuL,
            borderColor = 0x00000000uL,
            borderWidthDp = 0f,
        )
}
