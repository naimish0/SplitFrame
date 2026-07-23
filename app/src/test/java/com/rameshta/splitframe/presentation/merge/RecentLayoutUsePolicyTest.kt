package com.rameshta.splitframe.presentation.merge

import com.rameshta.splitframe.domain.ImageSource
import com.rameshta.splitframe.domain.LayoutCell
import com.rameshta.splitframe.domain.LayoutTemplate
import com.rameshta.splitframe.domain.MergeProject
import com.rameshta.splitframe.domain.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentLayoutUsePolicyTest {
    private val templateA = template("layout-a")
    private val templateB = template("layout-b")

    @Test
    fun selectingCompleteLayoutDoesNotRecordUntilFirstUserMutation() {
        val tracker = RecentLayoutUseTracker()
        val before = project(templateA, assignedCount = 2, projectId = "before")
        val selected = project(templateB, assignedCount = 2, projectId = "selected")

        assertNull(tracker.templateIdToRecord(LayoutUseOrigin.Selection, before, selected))
        assertEquals(
            templateB.id,
            tracker.templateIdToRecord(
                LayoutUseOrigin.UserMutation,
                selected,
                selected.copy(spacingDp = 12f),
            ),
        )
    }

    @Test
    fun successfulPartialMediaActionRecordsActualLayoutOnce() {
        val tracker = RecentLayoutUseTracker()
        val blank = project(templateA, assignedCount = 0)
        val partial = project(templateA, assignedCount = 1)

        assertEquals(
            templateA.id,
            tracker.templateIdToRecord(LayoutUseOrigin.UserMutation, blank, partial),
        )
        assertNull(
            tracker.templateIdToRecord(
                LayoutUseOrigin.UserMutation,
                partial,
                partial.copy(spacingDp = 10f),
            ),
        )
    }

    @Test
    fun noOpBlankInternalAndHistoryChangesDoNotRecord() {
        val tracker = RecentLayoutUseTracker()
        val blank = project(templateA, assignedCount = 0)
        val partial = project(templateA, assignedCount = 1)
        val ready = project(templateA, assignedCount = 2)

        assertNull(tracker.templateIdToRecord(LayoutUseOrigin.UserMutation, blank, blank.copy(spacingDp = 8f)))
        assertNull(tracker.templateIdToRecord(LayoutUseOrigin.UserMutation, partial, blank))
        assertNull(tracker.templateIdToRecord(LayoutUseOrigin.UserMutation, ready, ready.copy()))
        assertNull(tracker.templateIdToRecord(LayoutUseOrigin.Internal, ready, ready.copy(spacingDp = 8f)))
        assertNull(tracker.templateIdToRecord(LayoutUseOrigin.History, ready, ready.copy(template = templateB)))
    }

    @Test
    fun successfulExportRecordsCapturedReadyLayoutButFailureOriginDoesNot() {
        val tracker = RecentLayoutUseTracker()
        val ready = project(templateB, assignedCount = 2, projectId = "exported")

        assertNull(tracker.templateIdToRecord(LayoutUseOrigin.Internal, ready, ready))
        assertEquals(
            templateB.id,
            tracker.templateIdToRecord(LayoutUseOrigin.ExportSuccess, ready, ready),
        )
        assertNull(tracker.templateIdToRecord(LayoutUseOrigin.ExportSuccess, ready, ready))
    }

    @Test
    fun sameTemplateCanRecordAgainForDifferentProjectActivation() {
        val tracker = RecentLayoutUseTracker()
        val first = project(templateA, assignedCount = 1, projectId = "first")
        val second = project(templateA, assignedCount = 1, projectId = "second")

        assertEquals(
            templateA.id,
            tracker.templateIdToRecord(LayoutUseOrigin.UserMutation, first.copy(assignedImages = emptyMap()), first),
        )
        assertEquals(
            templateA.id,
            tracker.templateIdToRecord(LayoutUseOrigin.UserMutation, second.copy(assignedImages = emptyMap()), second),
        )
    }

    private fun template(id: String) = LayoutTemplate(
        id = id,
        name = id,
        cells = listOf(
            LayoutCell(NormalizedRect(0f, 0f, 0.5f, 1f), 0),
            LayoutCell(NormalizedRect(0.5f, 0f, 0.5f, 1f), 1),
        ),
        defaultSpacingDp = 0f,
        defaultCornerRadiusDp = 0f,
    )

    private fun project(
        template: LayoutTemplate,
        assignedCount: Int,
        projectId: String = "project",
    ) = MergeProject(
        id = projectId,
        template = template,
        assignedImages = template.cells.take(assignedCount).associate { cell ->
            cell.index to ImageSource.LocalUri("content://splitframe/${cell.index}")
        },
        spacingDp = 0f,
        cornerRadiusDp = 0f,
        backgroundColor = 0xFFFFFFFFuL,
        borderColor = 0x00000000uL,
        borderWidthDp = 0f,
    )
}
