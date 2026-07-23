package com.rameshta.splitframe.ads

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedPreferencesWorkflowInterstitialStateStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    @After
    fun clearState() {
        context.getSharedPreferences(SplitFrameAdPreferencesName, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun countDedupeAndPresentationClaimSurviveStateStoreReopen() {
        val first = success("first")
        val second = success("second")
        val initialTracker = newTracker()

        assertEquals(WorkflowInterstitialDecision.Counted, initialTracker.record(first))
        assertEquals(WorkflowInterstitialDecision.Eligible, initialTracker.record(second))
        assertTrue(initialTracker.claimPresentation(second))

        val beforeDisplayReopen = newTracker()
        assertEquals(2, beforeDisplayReopen.pendingSuccessfulWorkflowCount())
        assertEquals(WorkflowInterstitialDecision.Duplicate, beforeDisplayReopen.record(first))
        assertFalse(beforeDisplayReopen.claimPresentation(second))

        beforeDisplayReopen.onDisplayStarted()

        val afterDisplayReopen = newTracker()
        assertEquals(0, afterDisplayReopen.pendingSuccessfulWorkflowCount())
        assertEquals(WorkflowInterstitialDecision.Duplicate, afterDisplayReopen.record(second))
        assertFalse(afterDisplayReopen.claimPresentation(second))
        assertEquals(
            WorkflowInterstitialDecision.Counted,
            afterDisplayReopen.record(success("after-display")),
        )
    }

    private fun newTracker(): WorkflowInterstitialTracker =
        WorkflowInterstitialTracker(SharedPreferencesWorkflowInterstitialStateStore(context))

    private fun success(id: String): WorkflowCompletionEvent =
        WorkflowCompletionEvent(
            kind = WorkflowKind.PhotoCollage,
            stableWorkflowId = id,
            outcome = WorkflowOutcome.Succeeded,
        )
}
