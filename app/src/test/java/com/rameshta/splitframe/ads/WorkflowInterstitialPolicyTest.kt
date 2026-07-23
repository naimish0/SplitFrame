package com.rameshta.splitframe.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowInterstitialPolicyTest {
    @Test
    fun firstSuccessCountsAndSecondSuccessBecomesEligible() {
        val tracker = tracker()

        assertEquals(WorkflowInterstitialDecision.Counted, tracker.record(success("first")))
        assertEquals(1, tracker.pendingSuccessfulWorkflowCount())
        assertEquals(WorkflowInterstitialDecision.Eligible, tracker.record(success("second")))
        assertEquals(2, tracker.pendingSuccessfulWorkflowCount())
    }

    @Test
    fun counterResetsOnlyAfterDisplayActuallyStarts() {
        val tracker = tracker()
        tracker.record(success("first"))
        tracker.record(success("second"))

        assertEquals(
            WorkflowInterstitialDecision.Eligible,
            tracker.record(success("ad-unavailable-third")),
        )
        assertEquals(2, tracker.pendingSuccessfulWorkflowCount())

        tracker.onDisplayStarted()

        assertEquals(0, tracker.pendingSuccessfulWorkflowCount())
        assertEquals(WorkflowInterstitialDecision.Duplicate, tracker.record(success("second")))
        assertEquals(0, tracker.pendingSuccessfulWorkflowCount())
        assertEquals(WorkflowInterstitialDecision.Counted, tracker.record(success("after-display")))
    }

    @Test
    fun eachCompletionOffersAtMostOnePresentationOpportunity() {
        val store = MemoryWorkflowInterstitialStore()
        val tracker = WorkflowInterstitialTracker(store)
        val first = success("first")
        val second = success("second")
        tracker.record(first)
        assertFalse(tracker.claimPresentation(first))

        tracker.record(second)
        assertTrue(tracker.claimPresentation(second))
        assertFalse(tracker.claimPresentation(second))

        val recreated = WorkflowInterstitialTracker(store)
        assertFalse(recreated.claimPresentation(second))
    }

    @Test
    fun duplicateEmissionsAndBatchFilesWithOneWorkflowIdCountOnce() {
        val tracker = tracker()
        val batchCompletion = success("batch-attempt-1")

        assertEquals(WorkflowInterstitialDecision.Counted, tracker.record(batchCompletion))
        assertEquals(WorkflowInterstitialDecision.Duplicate, tracker.record(batchCompletion))
        assertEquals(WorkflowInterstitialDecision.Duplicate, tracker.record(batchCompletion))
        assertEquals(1, tracker.pendingSuccessfulWorkflowCount())
    }

    @Test
    fun failedCancelledAndInvalidCompletionsNeverCount() {
        val tracker = tracker()

        assertEquals(
            WorkflowInterstitialDecision.Ignored,
            tracker.record(event("failed", WorkflowOutcome.Failed)),
        )
        assertEquals(
            WorkflowInterstitialDecision.Ignored,
            tracker.record(event("cancelled", WorkflowOutcome.Cancelled)),
        )
        assertEquals(
            WorkflowInterstitialDecision.Ignored,
            tracker.record(success("   ")),
        )
        assertEquals(0, tracker.pendingSuccessfulWorkflowCount())
    }

    @Test
    fun persistedStateSurvivesTrackerRecreationAndPreventsReplay() {
        val store = MemoryWorkflowInterstitialStore()
        val firstProcess = WorkflowInterstitialTracker(store)
        assertEquals(WorkflowInterstitialDecision.Counted, firstProcess.record(success("first")))

        val recreatedProcess = WorkflowInterstitialTracker(store)

        assertEquals(WorkflowInterstitialDecision.Duplicate, recreatedProcess.record(success("first")))
        assertEquals(WorkflowInterstitialDecision.Eligible, recreatedProcess.record(success("second")))
        assertEquals(2, recreatedProcess.pendingSuccessfulWorkflowCount())
    }

    @Test
    fun sameExternalIdInDifferentWorkflowKindsIsNotAFalseDuplicate() {
        val tracker = tracker()

        assertEquals(
            WorkflowInterstitialDecision.Counted,
            tracker.record(success("content://media/1", WorkflowKind.PhotoCollage)),
        )
        assertEquals(
            WorkflowInterstitialDecision.Eligible,
            tracker.record(success("content://media/1", WorkflowKind.ImageResize)),
        )
    }

    @Test
    fun completionFactoriesUseCommittedArtifactIdsAndCanonicalVideoWorkIds() {
        assertEquals(
            WorkflowCompletionEvent(
                kind = WorkflowKind.PhotoCollage,
                stableWorkflowId = "content://media/photo/1",
                outcome = WorkflowOutcome.Succeeded,
            ),
            WorkflowCompletionEvents.photoCollage(" content://media/photo/1 "),
        )
        assertEquals(
            WorkflowKind.ImageResize,
            WorkflowCompletionEvents.imageResize("content://media/image/2")?.kind,
        )
        assertEquals(
            "77777777-7777-4777-8777-777777777777",
            WorkflowCompletionEvents.videoComposition(
                "77777777-7777-4777-8777-777777777777",
            )?.stableWorkflowId,
        )
        assertEquals(null, WorkflowCompletionEvents.photoCollage("   "))
        assertEquals(null, WorkflowCompletionEvents.videoComposition("not-a-work-id"))
        assertEquals(null, WorkflowCompletionEvents.videoComposition("1-1-1-1-1"))
        assertEquals(null, WorkflowCompletionEvents.videoComposition(null))
    }

    @Test
    fun persistedHistoryIsBoundedAndDoesNotStoreRawWorkflowIds() {
        val store = MemoryWorkflowInterstitialStore()
        val tracker = WorkflowInterstitialTracker(
            store = store,
            successfulWorkflowsPerInterstitial = 2,
            historyLimit = 2,
        )

        tracker.record(success("content://private/photo/1"))
        tracker.record(success("content://private/photo/2"))
        tracker.record(success("content://private/photo/3"))

        assertEquals(2, store.state.processedWorkflowKeys.size)
        assertTrue(store.state.processedWorkflowKeys.all { it.length == 64 })
        assertFalse(store.state.processedWorkflowKeys.any { "content://" in it })
        assertNotEquals(store.state.processedWorkflowKeys[0], store.state.processedWorkflowKeys[1])
    }

    @Test
    fun interstitialRequiresLoadedAdConsentNaturalBreakAndSafeLifecycle() {
        val eligible = InterstitialEligibility(
            adsEligible = true,
            adLoaded = true,
            fullScreenAdState = FullScreenAdState.Idle,
            activityResumed = true,
            windowFocused = true,
            workflowInProgress = false,
            naturalBreak = true,
            interstitialIntervalElapsed = true,
            appOpenSeparationElapsed = true,
        )

        assertTrue(FullScreenAdEligibility.canShowInterstitial(eligible))
        listOf(
            eligible.copy(adsEligible = false),
            eligible.copy(adLoaded = false),
            eligible.copy(fullScreenAdState = FullScreenAdState.AppOpen),
            eligible.copy(fullScreenAdState = FullScreenAdState.Interstitial),
            eligible.copy(activityResumed = false),
            eligible.copy(windowFocused = false),
            eligible.copy(workflowInProgress = true),
            eligible.copy(naturalBreak = false),
            eligible.copy(interstitialIntervalElapsed = false),
            eligible.copy(appOpenSeparationElapsed = false),
            eligible.copy(appOpenWindowActive = true),
        ).forEach { unsafe ->
            assertFalse(unsafe.toString(), FullScreenAdEligibility.canShowInterstitial(unsafe))
        }
    }

    @Test
    fun fullScreenSpacingUsesInclusiveBoundaryAndBlocksClockRollback() {
        assertTrue(FullScreenAdEligibility.hasMinimumSeparation(1_000L, 0L, 500L))
        assertFalse(FullScreenAdEligibility.hasMinimumSeparation(1_499L, 1_000L, 500L))
        assertTrue(FullScreenAdEligibility.hasMinimumSeparation(1_500L, 1_000L, 500L))
        assertFalse(FullScreenAdEligibility.hasMinimumSeparation(900L, 1_000L, 500L))
    }

    @Test
    fun productionCoordinatorHandlesProducerRecordedCompletions() {
        val tracker = tracker()
        val coordinator = WorkflowInterstitialCoordinator(tracker)
        val first = success("worker-first", WorkflowKind.VideoComposition)
        val second = success("worker-second", WorkflowKind.VideoComposition)

        tracker.record(first)
        assertEquals(
            WorkflowInterstitialAction.Continue,
            coordinator.onCompletion(first, eligiblePresentation()),
        )
        tracker.record(second)
        assertEquals(
            WorkflowInterstitialAction.Show,
            coordinator.onCompletion(second, eligiblePresentation()),
        )
    }

    @Test
    fun unavailableOrUnsafeOpportunityContinuesAndNeverPopsUpLate() {
        val tracker = tracker()
        val coordinator = WorkflowInterstitialCoordinator(tracker)
        coordinator.onCompletion(success("first"), eligiblePresentation())
        val second = success("second")

        assertEquals(
            WorkflowInterstitialAction.Continue,
            coordinator.onCompletion(second, eligiblePresentation().copy(adLoaded = false)),
        )
        assertEquals(
            WorkflowInterstitialAction.Continue,
            coordinator.onCompletion(second, eligiblePresentation()),
        )
        assertEquals(
            WorkflowInterstitialAction.Continue,
            coordinator.onCompletion(
                success("third"),
                eligiblePresentation().copy(activityResumed = false),
            ),
        )
        assertEquals(
            WorkflowInterstitialAction.Show,
            coordinator.onCompletion(success("fourth"), eligiblePresentation()),
        )
    }

    @Test
    fun failedShowKeepsQuotaUntilARealDisplayCallback() {
        val tracker = tracker()
        val coordinator = WorkflowInterstitialCoordinator(tracker)
        coordinator.onCompletion(success("first"), eligiblePresentation())
        assertEquals(
            WorkflowInterstitialAction.Show,
            coordinator.onCompletion(success("second"), eligiblePresentation()),
        )

        assertEquals(
            WorkflowInterstitialAction.Show,
            coordinator.onCompletion(success("after-show-failure"), eligiblePresentation()),
        )
        coordinator.onDisplayStarted()
        assertEquals(0, tracker.pendingSuccessfulWorkflowCount())
        assertEquals(
            WorkflowInterstitialAction.Continue,
            coordinator.onCompletion(success("after-real-display"), eligiblePresentation()),
        )
    }

    private fun tracker(): WorkflowInterstitialTracker =
        WorkflowInterstitialTracker(MemoryWorkflowInterstitialStore())

    private fun eligiblePresentation(): InterstitialEligibility =
        InterstitialEligibility(
            adsEligible = true,
            adLoaded = true,
            fullScreenAdState = FullScreenAdState.Idle,
            activityResumed = true,
            windowFocused = true,
            workflowInProgress = false,
            naturalBreak = true,
            interstitialIntervalElapsed = true,
            appOpenSeparationElapsed = true,
        )

    private fun success(
        id: String,
        kind: WorkflowKind = WorkflowKind.PhotoCollage,
    ): WorkflowCompletionEvent = event(id, WorkflowOutcome.Succeeded, kind)

    private fun event(
        id: String,
        outcome: WorkflowOutcome,
        kind: WorkflowKind = WorkflowKind.PhotoCollage,
    ): WorkflowCompletionEvent =
        WorkflowCompletionEvent(
            kind = kind,
            stableWorkflowId = id,
            outcome = outcome,
        )

    private class MemoryWorkflowInterstitialStore(
        var state: WorkflowInterstitialState = WorkflowInterstitialState(),
    ) : WorkflowInterstitialStateStore {
        override fun read(): WorkflowInterstitialState = state

        override fun write(state: WorkflowInterstitialState) {
            this.state = state
        }
    }
}
