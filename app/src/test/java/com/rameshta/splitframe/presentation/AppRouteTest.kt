package com.rameshta.splitframe.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRouteTest {
    @Test
    fun videoDestinationAndProjectRoundTripTogether() {
        val route = AppRoute(
            screen = AppScreen.VideoEditor,
            activeVideoProjectId = ProjectId,
            createVideoProjectIfMissing = true,
        )

        val restored = restoreAppRoute(route.savedValues())

        assertEquals(AppScreen.VideoEditor, restored.screen)
        assertEquals(ProjectId, restored.activeVideoProjectId)
        assertTrue(restored.createVideoProjectIfMissing)
    }

    @Test
    fun photoEditorAndBackDestinationSurviveStateRestoration() {
        val route = AppRoute(
            screen = AppScreen.Editor,
            activeVideoProjectId = ProjectId,
            createVideoProjectIfMissing = false,
            photoBackDestination = AppScreen.ModeSelection,
        )

        val restored = restoreAppRoute(route.savedValues())

        assertEquals(AppScreen.Editor, restored.screen)
        assertEquals(ProjectId, restored.activeVideoProjectId)
        assertFalse(restored.createVideoProjectIfMissing)
        assertEquals(AppScreen.ModeSelection, restored.photoBackDestination)
    }

    @Test
    fun recentVideoProjectsDestinationSurvivesStateRestorationWithoutAProjectId() {
        val restored = restoreAppRoute(
            AppRoute(screen = AppScreen.VideoProjects).savedValues(),
        )

        assertEquals(AppScreen.VideoProjects, restored.screen)
        assertNull(restored.activeVideoProjectId)
        assertFalse(restored.createVideoProjectIfMissing)
    }

    @Test
    fun privacyDestinationSurvivesStateRestoration() {
        val restored = restoreAppRoute(
            AppRoute(screen = AppScreen.PrivacyPolicy).savedValues(),
        )

        assertEquals(AppScreen.PrivacyPolicy, restored.screen)
        assertNull(restored.activeVideoProjectId)
    }

    @Test
    fun corruptSavedVideoRouteFallsBackHomeWithoutAnId() {
        val restored = restoreAppRoute(
            listOf(AppScreen.VideoEditor.name, "deleted-or-malformed", true.toString()),
        )

        assertEquals(AppScreen.ModeSelection, restored.screen)
        assertNull(restored.activeVideoProjectId)
        assertFalse(restored.createVideoProjectIfMissing)
    }

    private companion object {
        const val ProjectId = "33333333-3333-4333-8333-333333333333"
    }
}
