package com.rameshta.splitframe.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOpenAdPolicyTest {
    @Test
    fun firstInstalledSessionBlocksAppOpenForEntireProcessAndRecordsCompletionOnce() {
        var completionWrites = 0
        val gate = FirstSessionAppOpenGate(
            hasCompletedFirstSession = false,
            markFirstSessionCompleted = { completionWrites++ },
        )

        assertFalse(gate.appOpenAdsAllowed)
        gate.onActivityStopped(changingConfigurations = true)
        assertEquals(0, completionWrites)

        gate.onActivityStopped(changingConfigurations = false)
        gate.onActivityStopped(changingConfigurations = false)

        assertEquals(1, completionWrites)
        assertFalse(gate.appOpenAdsAllowed)
    }

    @Test
    fun laterInstalledSessionAllowsAppOpenWithoutRewritingCompletion() {
        var completionWrites = 0
        val gate = FirstSessionAppOpenGate(
            hasCompletedFirstSession = true,
            markFirstSessionCompleted = { completionWrites++ },
        )

        gate.onActivityStopped(changingConfigurations = false)

        assertTrue(gate.appOpenAdsAllowed)
        assertEquals(0, completionWrites)
    }

    @Test
    fun coldLauncherStartOpensOneBoundedNonInteractiveOpportunity() {
        val controller = controller(coldWindowMillis = 1_500L)

        val opportunity = controller.onActivityCreated(
            nowElapsedMillis = 100L,
            restored = false,
            launcherStart = true,
            coldStartAllowed = true,
        )

        assertEquals(AppOpenTrigger.ColdStart, opportunity?.trigger)
        assertEquals(1_600L, opportunity?.expiresAtElapsedMillis)
        assertTrue(controller.loadingSurfaceVisible)
        assertEquals(opportunity, controller.onActivityResumed(200L))
        assertNull(controller.current(1_601L))
        assertFalse(controller.loadingSurfaceVisible)
    }

    @Test
    fun notificationRestorationAndSecondActivityCreationNeverBecomeColdStarts() {
        val notification = controller()
        assertNull(
            notification.onActivityCreated(
                nowElapsedMillis = 0L,
                restored = false,
                launcherStart = false,
                coldStartAllowed = true,
            ),
        )

        val restored = controller()
        assertNull(
            restored.onActivityCreated(
                nowElapsedMillis = 0L,
                restored = true,
                launcherStart = true,
                coldStartAllowed = true,
            ),
        )

        val created = controller()
        created.onActivityCreated(0L, restored = false, launcherStart = true, coldStartAllowed = false)
        assertNull(created.onActivityCreated(10L, restored = false, launcherStart = true, coldStartAllowed = true))
    }

    @Test
    fun meaningfulForegroundReturnUsesInclusiveThirtySecondBoundary() {
        val shortReturn = controller()
        shortReturn.onActivityCreated(0L, restored = false, launcherStart = true, coldStartAllowed = false)
        shortReturn.onActivityStopped(
            nowElapsedMillis = 1_000L,
            changingConfigurations = false,
            consentUiActive = false,
            fullScreenAdState = FullScreenAdState.Idle,
        )
        assertNull(shortReturn.onActivityResumed(30_999L))

        val eligibleReturn = controller()
        eligibleReturn.onActivityCreated(0L, restored = false, launcherStart = true, coldStartAllowed = false)
        eligibleReturn.onActivityStopped(1_000L, false, false, FullScreenAdState.Idle)
        val opportunity = eligibleReturn.onActivityResumed(31_000L)

        assertEquals(AppOpenTrigger.ForegroundReturn, opportunity?.trigger)
        assertEquals(30_000L, opportunity?.backgroundDurationMillis)
        assertTrue(eligibleReturn.loadingSurfaceVisible)
    }

    @Test
    fun configurationChangeAndClockRollbackAreSuppressed() {
        val configuration = controller()
        configuration.onActivityCreated(0L, false, true, false)
        configuration.onActivityStopped(1_000L, true, false, FullScreenAdState.Idle)
        assertNull(configuration.onActivityResumed(60_000L))

        val rollback = controller()
        rollback.onActivityCreated(0L, false, true, false)
        rollback.onActivityStopped(5_000L, false, false, FullScreenAdState.Idle)
        assertNull(rollback.onActivityResumed(4_000L))
    }

    @Test
    fun pickerFileFallbackShareCameraViewerAndPrivacySuppressExactlyOneReturn() {
        ExternalUiReason.entries.forEach { reason ->
            val controller = controller()
            controller.onActivityCreated(0L, false, true, false)
            controller.beginExternalUi(reason, 100L)
            controller.onActivityStopped(200L, false, false, FullScreenAdState.Idle)

            assertNull(reason.name, controller.onActivityResumed(60_000L))

            controller.onActivityStopped(70_000L, false, false, FullScreenAdState.Idle)
            assertEquals(
                reason.name,
                AppOpenTrigger.ForegroundReturn,
                controller.onActivityResumed(100_000L)?.trigger,
            )
        }
    }

    @Test
    fun failedExternalLaunchDoesNotMaskLaterGenuineReturn() {
        val controller = controller()
        controller.onActivityCreated(0L, false, true, false)
        val token = controller.beginExternalUi(ExternalUiReason.ExternalViewer, 100L)
        controller.cancelExternalUi(token)
        controller.onActivityStopped(1_000L, false, false, FullScreenAdState.Idle)

        assertEquals(
            AppOpenTrigger.ForegroundReturn,
            controller.onActivityResumed(31_000L)?.trigger,
        )
    }

    @Test
    fun consentUiOwnFullScreenAdAndRecoveryLaunchSuppressReturn() {
        listOf<(AppOpenOpportunityController) -> Unit>(
            { it.onActivityStopped(0L, false, true, FullScreenAdState.Idle) },
            { it.onActivityStopped(0L, false, false, FullScreenAdState.Interstitial) },
            {
                it.markRecoveryLaunch()
                it.onActivityStopped(0L, false, false, FullScreenAdState.Idle)
            },
        ).forEach { prepare ->
            val controller = controller()
            controller.onActivityCreated(0L, false, true, false)
            prepare(controller)
            assertNull(controller.onActivityResumed(60_000L))
        }
    }

    @Test
    fun userInteractionClosesColdOrForegroundWindowAndPreventsLateAd() {
        val cold = controller()
        cold.onActivityCreated(0L, false, true, true)
        cold.onUserInteraction()
        assertNull(cold.current(100L))
        assertFalse(cold.loadingSurfaceVisible)

        val foreground = controller()
        foreground.onActivityCreated(0L, false, true, false)
        foreground.onActivityStopped(0L, false, false, FullScreenAdState.Idle)
        foreground.onActivityResumed(30_000L)
        foreground.onUserInteraction()
        assertNull(foreground.current(30_100L))
    }

    @Test
    fun eligibilityRequiresConsentFreshAdSafeActivityAndNoExportOrCollision() {
        val opportunity = foregroundOpportunity()
        val eligible = eligibility(opportunity)
        assertTrue(AppOpenAdPolicy.canShow(eligible))

        listOf(
            eligible.copy(consentResolved = false),
            eligible.copy(adsEligible = false),
            eligible.copy(adLoaded = false),
            eligible.copy(adFresh = false),
            eligible.copy(exportInProgress = true),
            eligible.copy(fullScreenAdState = FullScreenAdState.Interstitial),
            eligible.copy(fullScreenAdState = FullScreenAdState.AppOpen),
            eligible.copy(frequencyCapElapsed = false),
            eligible.copy(interstitialSeparationElapsed = false),
            eligible.copy(activity = eligible.activity.copy(resumed = false)),
            eligible.copy(activity = eligible.activity.copy(windowFocused = false)),
            eligible.copy(activity = eligible.activity.copy(finishing = true)),
            eligible.copy(activity = eligible.activity.copy(destroyed = true)),
            eligible.copy(activity = eligible.activity.copy(changingConfigurations = true)),
        ).forEach { blocked ->
            assertFalse(blocked.toString(), AppOpenAdPolicy.canShow(blocked))
        }
    }

    @Test
    fun expiredOpportunityAndBriefForegroundDurationCannotShow() {
        val opportunity = foregroundOpportunity().copy(backgroundDurationMillis = 29_999L)
        assertFalse(AppOpenAdPolicy.canShow(eligibility(opportunity)))
        assertFalse(
            AppOpenAdPolicy.canShow(
                eligibility(opportunity.copy(backgroundDurationMillis = 30_000L))
                    .copy(nowElapsedMillis = opportunity.expiresAtElapsedMillis + 1L),
            ),
        )
    }

    @Test
    fun fourHourFrequencyAndFreshnessBoundariesAreStrictAndRollbackSafe() {
        val fourHours = 4L * 60L * 60L * 1_000L
        assertFalse(FullScreenAdEligibility.hasMinimumSeparation(fourHours - 1L, 1L, fourHours))
        assertTrue(FullScreenAdEligibility.hasMinimumSeparation(fourHours + 1L, 1L, fourHours))
        assertFalse(FullScreenAdEligibility.hasMinimumSeparation(500L, 1_000L, fourHours))

        assertTrue(AppOpenAdPolicy.isFresh(fourHours, 1L, fourHours))
        assertFalse(AppOpenAdPolicy.isFresh(fourHours + 1L, 1L, fourHours))
        assertFalse(AppOpenAdPolicy.isFresh(500L, 1_000L, fourHours))
    }

    @Test
    fun offlineOrLoadFailureReleasesForegroundAndCannotAppearLate() {
        val controller = controller()
        controller.onActivityCreated(0L, false, true, false)
        controller.onActivityStopped(0L, false, false, FullScreenAdState.Idle)
        val opportunity = controller.onActivityResumed(30_000L)!!

        assertFalse(AppOpenAdPolicy.canShow(eligibility(opportunity).copy(adLoaded = false)))
        controller.consumeWithoutShow(opportunity.id)

        assertNull(controller.current(30_100L))
        assertFalse(controller.loadingSurfaceVisible)
    }

    @Test
    fun displayFailureOrDismissalReleasesGateWithoutCreatingAnotherOpportunity() {
        val controller = controller()
        val opportunity = controller.onActivityCreated(0L, false, true, true)!!
        assertTrue(controller.consumeForShow(opportunity.id))
        assertTrue(controller.loadingSurfaceVisible)

        controller.releaseLoadingSurface()

        assertFalse(controller.loadingSurfaceVisible)
        assertNull(controller.current(100L))
        assertNull(controller.onActivityResumed(200L))
    }

    @Test
    fun staleExternalSuppressionExpiresWithoutMaskingFutureBackground() {
        val controller = AppOpenOpportunityController(
            coldStartWindowMillis = 100L,
            foregroundWindowMillis = 100L,
            minimumBackgroundMillis = 30L,
            externalTokenTtlMillis = 50L,
        )
        controller.onActivityCreated(0L, false, true, false)
        controller.beginExternalUi(ExternalUiReason.ShareSheet, 10L)

        assertNull(controller.onActivityResumed(61L))
        controller.onActivityStopped(100L, false, false, FullScreenAdState.Idle)
        assertEquals(AppOpenTrigger.ForegroundReturn, controller.onActivityResumed(130L)?.trigger)
    }

    private fun controller(
        coldWindowMillis: Long = 1_500L,
    ): AppOpenOpportunityController =
        AppOpenOpportunityController(
            coldStartWindowMillis = coldWindowMillis,
            foregroundWindowMillis = 750L,
            minimumBackgroundMillis = 30_000L,
        )

    private fun foregroundOpportunity(): AppOpenOpportunity =
        AppOpenOpportunity(
            id = 1L,
            trigger = AppOpenTrigger.ForegroundReturn,
            openedAtElapsedMillis = 30_000L,
            expiresAtElapsedMillis = 30_750L,
            backgroundDurationMillis = 30_000L,
        )

    private fun eligibility(opportunity: AppOpenOpportunity): AppOpenEligibility =
        AppOpenEligibility(
            opportunity = opportunity,
            nowElapsedMillis = opportunity.openedAtElapsedMillis,
            activity = AppOpenActivitySnapshot(
                resumed = true,
                windowFocused = true,
                finishing = false,
                destroyed = false,
                changingConfigurations = false,
            ),
            consentResolved = true,
            adsEligible = true,
            adLoaded = true,
            adFresh = true,
            exportInProgress = false,
            fullScreenAdState = FullScreenAdState.Idle,
            frequencyCapElapsed = true,
            interstitialSeparationElapsed = true,
        )
}
