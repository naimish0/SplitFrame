package com.rameshta.splitframe.ads

import android.content.SharedPreferences

internal class FirstSessionAppOpenGate(
    hasCompletedFirstSession: Boolean,
    private val markFirstSessionCompleted: () -> Unit,
) {
    val appOpenAdsAllowed: Boolean = hasCompletedFirstSession
    private var completionRecorded = hasCompletedFirstSession

    fun onActivityStopped(changingConfigurations: Boolean) {
        if (appOpenAdsAllowed || completionRecorded || changingConfigurations) return
        completionRecorded = true
        markFirstSessionCompleted()
    }
}

internal fun createFirstSessionAppOpenGate(
    preferences: SharedPreferences,
): FirstSessionAppOpenGate =
    FirstSessionAppOpenGate(
        hasCompletedFirstSession =
            preferences.getBoolean(FirstSessionCompletedPreferenceKey, false),
        markFirstSessionCompleted = {
            preferences.edit()
                .putBoolean(FirstSessionCompletedPreferenceKey, true)
                .apply()
        },
    )

internal enum class AppOpenTrigger {
    ColdStart,
    ForegroundReturn,
}

internal enum class ExternalUiReason {
    MediaPicker,
    Camera,
    ShareSheet,
    ExternalViewer,
    Permission,
    Privacy,
    AdClick,
}

@JvmInline
internal value class ExternalUiToken(val value: Long)

internal data class AppOpenOpportunity(
    val id: Long,
    val trigger: AppOpenTrigger,
    val openedAtElapsedMillis: Long,
    val expiresAtElapsedMillis: Long,
    val backgroundDurationMillis: Long? = null,
)

internal data class AppOpenActivitySnapshot(
    val resumed: Boolean,
    val windowFocused: Boolean,
    val finishing: Boolean,
    val destroyed: Boolean,
    val changingConfigurations: Boolean,
)

internal data class AppOpenEligibility(
    val opportunity: AppOpenOpportunity,
    val nowElapsedMillis: Long,
    val activity: AppOpenActivitySnapshot,
    val consentResolved: Boolean,
    val adsEligible: Boolean,
    val adLoaded: Boolean,
    val adFresh: Boolean,
    val exportInProgress: Boolean,
    val fullScreenAdState: FullScreenAdState,
    val frequencyCapElapsed: Boolean,
    val interstitialSeparationElapsed: Boolean,
)

internal object AppOpenAdPolicy {
    fun canShow(eligibility: AppOpenEligibility): Boolean =
        eligibility.nowElapsedMillis <= eligibility.opportunity.expiresAtElapsedMillis &&
            eligibility.activity.resumed &&
            eligibility.activity.windowFocused &&
            !eligibility.activity.finishing &&
            !eligibility.activity.destroyed &&
            !eligibility.activity.changingConfigurations &&
            eligibility.consentResolved &&
            eligibility.adsEligible &&
            eligibility.adLoaded &&
            eligibility.adFresh &&
            !eligibility.exportInProgress &&
            eligibility.fullScreenAdState == FullScreenAdState.Idle &&
            eligibility.frequencyCapElapsed &&
            eligibility.interstitialSeparationElapsed &&
            when (eligibility.opportunity.trigger) {
                AppOpenTrigger.ColdStart -> true
                AppOpenTrigger.ForegroundReturn ->
                    (eligibility.opportunity.backgroundDurationMillis ?: -1L) >=
                        AppOpenOpportunityController.DefaultMinimumBackgroundMillis
            }

    fun isFresh(
        nowElapsedMillis: Long,
        loadedAtElapsedMillis: Long,
        freshnessMillis: Long,
    ): Boolean {
        if (loadedAtElapsedMillis <= 0L || freshnessMillis <= 0L) return false
        val age = nowElapsedMillis - loadedAtElapsedMillis
        return age in 0 until freshnessMillis
    }
}

internal class AppOpenOpportunityController(
    private val coldStartWindowMillis: Long = DefaultColdStartWindowMillis,
    private val foregroundWindowMillis: Long = DefaultForegroundWindowMillis,
    private val minimumBackgroundMillis: Long = DefaultMinimumBackgroundMillis,
    private val externalTokenTtlMillis: Long = DefaultExternalTokenTtlMillis,
) {
    private var firstActivityCreated = false
    private var nextOpportunityId = 0L
    private var nextExternalTokenId = 0L
    private var currentOpportunity: AppOpenOpportunity? = null
    private var backgroundedAtElapsedMillis: Long? = null
    private var stopSuppression: StopSuppression? = null
    private var recoveryLaunchPending = false
    private val externalLaunches = linkedMapOf<ExternalUiToken, ExternalLaunch>()

    var loadingSurfaceVisible: Boolean = false
        private set

    fun onActivityCreated(
        nowElapsedMillis: Long,
        restored: Boolean,
        launcherStart: Boolean,
        coldStartAllowed: Boolean,
    ): AppOpenOpportunity? {
        if (firstActivityCreated) return current(nowElapsedMillis)
        firstActivityCreated = true
        if (restored || !launcherStart || !coldStartAllowed) return null

        return newOpportunity(
            trigger = AppOpenTrigger.ColdStart,
            nowElapsedMillis = nowElapsedMillis,
            durationMillis = coldStartWindowMillis,
            backgroundDurationMillis = null,
        )
    }

    fun onActivityStopped(
        nowElapsedMillis: Long,
        changingConfigurations: Boolean,
        consentUiActive: Boolean,
        fullScreenAdState: FullScreenAdState,
    ) {
        purgeExpiredExternalLaunches(nowElapsedMillis)
        backgroundedAtElapsedMillis = nowElapsedMillis
        stopSuppression = when {
            changingConfigurations -> StopSuppression.ConfigurationChange
            consentUiActive -> StopSuppression.ConsentUi
            fullScreenAdState != FullScreenAdState.Idle -> StopSuppression.OwnFullScreenAd
            recoveryLaunchPending -> StopSuppression.RecoveryLaunch
            externalLaunches.isNotEmpty() -> StopSuppression.ExternalUi
            else -> null
        }
        if (fullScreenAdState != FullScreenAdState.AppOpen) {
            cancelCurrentOpportunity()
        } else {
            currentOpportunity = null
        }
    }

    fun onActivityResumed(nowElapsedMillis: Long): AppOpenOpportunity? {
        purgeExpiredExternalLaunches(nowElapsedMillis)

        if (recoveryLaunchPending || externalLaunches.isNotEmpty() || stopSuppression != null) {
            recoveryLaunchPending = false
            externalLaunches.clear()
            backgroundedAtElapsedMillis = null
            stopSuppression = null
            cancelCurrentOpportunity()
            return null
        }

        current(nowElapsedMillis)?.let { return it }
        val backgroundedAt = backgroundedAtElapsedMillis ?: return null
        backgroundedAtElapsedMillis = null
        val duration = nowElapsedMillis - backgroundedAt
        if (duration < minimumBackgroundMillis) return null

        return newOpportunity(
            trigger = AppOpenTrigger.ForegroundReturn,
            nowElapsedMillis = nowElapsedMillis,
            durationMillis = foregroundWindowMillis,
            backgroundDurationMillis = duration,
        )
    }

    fun beginExternalUi(
        reason: ExternalUiReason,
        nowElapsedMillis: Long,
    ): ExternalUiToken {
        purgeExpiredExternalLaunches(nowElapsedMillis)
        val token = ExternalUiToken(++nextExternalTokenId)
        externalLaunches[token] = ExternalLaunch(reason, nowElapsedMillis)
        cancelCurrentOpportunity()
        return token
    }

    fun cancelExternalUi(token: ExternalUiToken) {
        externalLaunches.remove(token)
    }

    fun markRecoveryLaunch() {
        recoveryLaunchPending = true
        cancelCurrentOpportunity()
    }

    fun onUserInteraction() {
        cancelCurrentOpportunity()
    }

    fun current(nowElapsedMillis: Long): AppOpenOpportunity? {
        val opportunity = currentOpportunity ?: return null
        if (nowElapsedMillis > opportunity.expiresAtElapsedMillis) {
            cancelCurrentOpportunity()
            return null
        }
        return opportunity
    }

    fun consumeForShow(opportunityId: Long): Boolean {
        val opportunity = currentOpportunity?.takeIf { it.id == opportunityId } ?: return false
        currentOpportunity = null
        loadingSurfaceVisible = true
        return opportunity.trigger == AppOpenTrigger.ColdStart ||
            opportunity.trigger == AppOpenTrigger.ForegroundReturn
    }

    fun consumeWithoutShow(opportunityId: Long) {
        if (currentOpportunity?.id != opportunityId) return
        cancelCurrentOpportunity()
    }

    fun releaseLoadingSurface() {
        loadingSurfaceVisible = false
    }

    private fun newOpportunity(
        trigger: AppOpenTrigger,
        nowElapsedMillis: Long,
        durationMillis: Long,
        backgroundDurationMillis: Long?,
    ): AppOpenOpportunity {
        val opportunity = AppOpenOpportunity(
            id = ++nextOpportunityId,
            trigger = trigger,
            openedAtElapsedMillis = nowElapsedMillis,
            expiresAtElapsedMillis = nowElapsedMillis + durationMillis,
            backgroundDurationMillis = backgroundDurationMillis,
        )
        currentOpportunity = opportunity
        loadingSurfaceVisible = true
        return opportunity
    }

    private fun cancelCurrentOpportunity() {
        currentOpportunity = null
        loadingSurfaceVisible = false
    }

    private fun purgeExpiredExternalLaunches(nowElapsedMillis: Long) {
        externalLaunches.entries.removeAll { (_, launch) ->
            val age = nowElapsedMillis - launch.launchedAtElapsedMillis
            age < 0L || age > externalTokenTtlMillis
        }
    }

    private data class ExternalLaunch(
        val reason: ExternalUiReason,
        val launchedAtElapsedMillis: Long,
    )

    private enum class StopSuppression {
        ConfigurationChange,
        ConsentUi,
        OwnFullScreenAd,
        ExternalUi,
        RecoveryLaunch,
    }

    companion object {
        const val DefaultColdStartWindowMillis = 1_500L
        const val DefaultForegroundWindowMillis = 750L
        const val DefaultMinimumBackgroundMillis = 30_000L
        const val DefaultExternalTokenTtlMillis = 24L * 60L * 60L * 1000L
    }
}
