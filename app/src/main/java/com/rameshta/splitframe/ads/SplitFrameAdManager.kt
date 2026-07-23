package com.rameshta.splitframe.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.rameshta.splitframe.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SplitFrameAdManager internal constructor(
    private val context: Context,
    private val adsConfigRepository: AdsConfigRepository,
    private val workflowInterstitialCoordinator: WorkflowInterstitialCoordinator,
) {
    private var interstitialAd: InterstitialAd? = null
    private var isLoadingInterstitial = false
    private var interstitialLoadGeneration = 0
    private var appOpenAd: AppOpenAd? = null
    private var appOpenAdLoadedAtMillis: Long = 0L
    private var isLoadingAppOpenAd = false
    private var appOpenLoadGeneration = 0
    private var appOpenRefreshJob: Job? = null
    private var appOpenOpportunityExpiryJob: Job? = null
    private val appOpenOpportunityController = AppOpenOpportunityController()
    private var foregroundExportInProgress = false
    private var videoExportInProgress = false
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferences =
        context.getSharedPreferences(SplitFrameAdPreferencesName, Context.MODE_PRIVATE)
    private val firstSessionAppOpenGate = createFirstSessionAppOpenGate(preferences)

    private val _fullScreenAdState = MutableStateFlow(FullScreenAdState.Idle)
    internal val fullScreenAdState: StateFlow<FullScreenAdState> = _fullScreenAdState.asStateFlow()

    private val _appOpenAdAvailable = MutableStateFlow(false)
    internal val appOpenAdAvailable: StateFlow<Boolean> = _appOpenAdAvailable.asStateFlow()

    private val _appOpenLoadingSurfaceVisible = MutableStateFlow(false)
    internal val appOpenLoadingSurfaceVisible: StateFlow<Boolean> =
        _appOpenLoadingSurfaceVisible.asStateFlow()

    init {
        managerScope.launch {
            adsConfigRepository.canPreloadAds.collect { canPreload ->
                if (canPreload) {
                    preloadInterstitial()
                    preloadAppOpenAd()
                } else {
                    clearCachedAds()
                }
            }
        }
    }

    fun preloadInterstitial() {
        if (!canPreloadAds() || interstitialAd != null || isLoadingInterstitial) return

        isLoadingInterstitial = true
        val loadGeneration = ++interstitialLoadGeneration
        InterstitialAd.load(
            context,
            BuildConfig.INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    if (loadGeneration != interstitialLoadGeneration) return
                    isLoadingInterstitial = false
                    interstitialAd = ad.takeIf { canPreloadAds() }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (loadGeneration != interstitialLoadGeneration) return
                    isLoadingInterstitial = false
                    interstitialAd = null
                }
            },
        )
    }

    internal fun recordWorkflowCompletion(
        activity: Activity?,
        completion: WorkflowCompletionEvent,
        workflowInProgress: Boolean,
        naturalBreak: Boolean = true,
    ): Boolean {
        val activityResumed = activity?.isResumedForFullScreenAd() == true
        val eligibility = InterstitialEligibility(
            adsEligible = canShowAds(),
            adLoaded = interstitialAd != null,
            fullScreenAdState = _fullScreenAdState.value,
            activityResumed = activityResumed,
            windowFocused = activityResumed && activity?.window?.decorView?.hasWindowFocus() == true,
            workflowInProgress = workflowInProgress,
            naturalBreak = naturalBreak,
            interstitialIntervalElapsed = isInterstitialShowIntervalElapsed(),
            appOpenSeparationElapsed = isAppOpenSeparationElapsed(),
            appOpenWindowActive = appOpenOpportunityController.loadingSurfaceVisible,
        )
        if (
            workflowInterstitialCoordinator.onCompletion(completion, eligibility) !=
            WorkflowInterstitialAction.Show ||
            activity == null
        ) {
            if (canPreloadAds() && interstitialAd == null) preloadInterstitial()
            return false
        }

        val ad = interstitialAd ?: return false
        interstitialAd = null
        _fullScreenAdState.value = FullScreenAdState.Interstitial
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                workflowInterstitialCoordinator.onDisplayStarted()
                preferences.edit()
                    .putLong(LastInterstitialAdShownAtKey, System.currentTimeMillis())
                    .apply()
            }

            override fun onAdDismissedFullScreenContent() {
                finishFullScreenAd(FullScreenAdState.Interstitial)
                preloadInterstitial()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                finishFullScreenAd(FullScreenAdState.Interstitial)
                preloadInterstitial()
            }
        }
        return runCatching {
            ad.show(activity)
            true
        }.getOrElse {
            finishFullScreenAd(FullScreenAdState.Interstitial)
            preloadInterstitial()
            false
        }
    }

    fun preloadAppOpenAd() {
        loadAppOpenAd()
    }

    internal fun onActivityCreated(
        restored: Boolean,
        launcherStart: Boolean,
    ) {
        val opportunity = appOpenOpportunityController.onActivityCreated(
            nowElapsedMillis = SystemClock.elapsedRealtime(),
            restored = restored,
            launcherStart = launcherStart,
            coldStartAllowed =
                firstSessionAppOpenGate.appOpenAdsAllowed &&
                    isAppOpenShowIntervalElapsed() &&
                    isInterstitialSeparationElapsed() &&
                    _fullScreenAdState.value == FullScreenAdState.Idle,
        )
        syncAppOpenLoadingSurface()
        opportunity?.let(::scheduleOpportunityExpiry)
    }

    internal fun onActivityResumed(activity: Activity) {
        if (!firstSessionAppOpenGate.appOpenAdsAllowed) {
            syncAppOpenLoadingSurface()
            return
        }
        val opportunity = appOpenOpportunityController.onActivityResumed(SystemClock.elapsedRealtime())
        syncAppOpenLoadingSurface()
        opportunity?.let(::scheduleOpportunityExpiry)
        showAppOpenAdIfAvailable(activity)
    }

    internal fun onActivityStopped(changingConfigurations: Boolean) {
        appOpenOpportunityController.onActivityStopped(
            nowElapsedMillis = SystemClock.elapsedRealtime(),
            changingConfigurations = changingConfigurations,
            consentUiActive = adsConfigRepository.isConsentFlowInProgress.value,
            fullScreenAdState = _fullScreenAdState.value,
        )
        firstSessionAppOpenGate.onActivityStopped(changingConfigurations)
        appOpenOpportunityExpiryJob?.cancel()
        syncAppOpenLoadingSurface()
    }

    internal fun onWindowFocusChanged(
        activity: Activity,
        hasFocus: Boolean,
    ) {
        if (hasFocus) showAppOpenAdIfAvailable(activity)
    }

    internal fun onUserInteraction() {
        appOpenOpportunityController.onUserInteraction()
        appOpenOpportunityExpiryJob?.cancel()
        syncAppOpenLoadingSurface()
    }

    internal fun markRecoveryLaunch() {
        appOpenOpportunityController.markRecoveryLaunch()
        appOpenOpportunityExpiryJob?.cancel()
        syncAppOpenLoadingSurface()
    }

    internal fun runExternalUiLaunch(
        reason: ExternalUiReason,
        action: () -> Unit,
    ) {
        val token = appOpenOpportunityController.beginExternalUi(
            reason = reason,
            nowElapsedMillis = SystemClock.elapsedRealtime(),
        )
        appOpenOpportunityExpiryJob?.cancel()
        syncAppOpenLoadingSurface()
        try {
            action()
        } catch (_: Throwable) {
            appOpenOpportunityController.cancelExternalUi(token)
        }
    }

    internal fun updateForegroundExportInProgress(inProgress: Boolean) {
        foregroundExportInProgress = inProgress
        if (inProgress) cancelPendingAppOpenOpportunity()
    }

    internal fun updateVideoExportInProgress(inProgress: Boolean) {
        videoExportInProgress = inProgress
        if (inProgress) cancelPendingAppOpenOpportunity()
    }

    internal fun showColdStartAppOpenIfAvailable(activity: Activity): Boolean =
        tryShowAppOpenAd(activity = activity, allowForegroundOpportunity = false)

    fun showAppOpenAdOverLoadingScreen(activity: Activity) {
        showColdStartAppOpenIfAvailable(activity)
    }

    fun showAppOpenAdIfAvailable(activity: Activity): Boolean =
        tryShowAppOpenAd(activity = activity, allowForegroundOpportunity = true)

    fun clearCachedAds() {
        interstitialAd = null
        isLoadingInterstitial = false
        interstitialLoadGeneration++
        discardAppOpenAd()
        isLoadingAppOpenAd = false
        appOpenLoadGeneration++
    }

    private fun loadAppOpenAd() {
        if (!firstSessionAppOpenGate.appOpenAdsAllowed || !canPreloadAds()) return
        if (isAppOpenAdFresh() || isLoadingAppOpenAd) return

        discardAppOpenAd()
        isLoadingAppOpenAd = true
        val loadGeneration = ++appOpenLoadGeneration
        AppOpenAd.load(
            context,
            BuildConfig.APP_OPEN_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    if (loadGeneration != appOpenLoadGeneration) return
                    isLoadingAppOpenAd = false
                    if (!canPreloadAds()) return
                    appOpenAd = ad
                    appOpenAdLoadedAtMillis = SystemClock.elapsedRealtime()
                    _appOpenAdAvailable.value = true
                    scheduleAppOpenAdRefresh(appOpenAdLoadedAtMillis)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (loadGeneration != appOpenLoadGeneration) return
                    isLoadingAppOpenAd = false
                    discardAppOpenAd()
                }
            },
        )
    }

    private fun tryShowAppOpenAd(
        activity: Activity,
        allowForegroundOpportunity: Boolean,
    ): Boolean {
        if (!firstSessionAppOpenGate.appOpenAdsAllowed) return false
        val nowElapsedMillis = SystemClock.elapsedRealtime()
        val opportunity = appOpenOpportunityController.current(nowElapsedMillis)
            ?: run {
                syncAppOpenLoadingSurface()
                return false
            }
        if (
            opportunity.trigger == AppOpenTrigger.ForegroundReturn &&
            !allowForegroundOpportunity
        ) {
            return false
        }

        if (appOpenAd != null && !isAppOpenAdFresh()) {
            discardAppOpenAd()
        }
        val activitySnapshot = activity.appOpenActivitySnapshot()
        val eligibility = AppOpenEligibility(
            opportunity = opportunity,
            nowElapsedMillis = nowElapsedMillis,
            activity = activitySnapshot,
            consentResolved = !adsConfigRepository.isConsentFlowInProgress.value,
            adsEligible = canShowAds(),
            adLoaded = appOpenAd != null,
            adFresh = isAppOpenAdFresh(),
            exportInProgress = foregroundExportInProgress || videoExportInProgress,
            fullScreenAdState = _fullScreenAdState.value,
            frequencyCapElapsed = isAppOpenShowIntervalElapsed(),
            interstitialSeparationElapsed = isInterstitialSeparationElapsed(),
        )
        if (AppOpenAdPolicy.canShow(eligibility)) {
            val ad = appOpenAd ?: return false
            if (!appOpenOpportunityController.consumeForShow(opportunity.id)) return false
            syncAppOpenLoadingSurface()
            return showLoadedAppOpenAd(activity, ad)
        }

        val activityCanBecomeReady =
            !activitySnapshot.finishing &&
                !activitySnapshot.destroyed &&
                !activitySnapshot.changingConfigurations &&
                (!activitySnapshot.resumed || !activitySnapshot.windowFocused)
        if (activityCanBecomeReady) return false

        if (opportunity.trigger == AppOpenTrigger.ColdStart) {
            val consentMayResolve = adsConfigRepository.isConsentFlowInProgress.value
            val adMayBecomeReady = canPreloadAds() && appOpenAd == null
            if (consentMayResolve || adMayBecomeReady) {
                if (canPreloadAds()) preloadAppOpenAd()
                return false
            }
        }

        appOpenOpportunityController.consumeWithoutShow(opportunity.id)
        syncAppOpenLoadingSurface()
        if (canPreloadAds() && appOpenAd == null) preloadAppOpenAd()
        return false
    }

    private fun showLoadedAppOpenAd(activity: Activity, ad: AppOpenAd): Boolean {
        if (
            !canShowAds() ||
            _fullScreenAdState.value != FullScreenAdState.Idle ||
            !activity.isResumedForFullScreenAd() ||
            activity.window?.decorView?.hasWindowFocus() != true ||
            !isAppOpenShowIntervalElapsed() ||
            !isInterstitialSeparationElapsed() ||
            foregroundExportInProgress ||
            videoExportInProgress
        ) {
            appOpenOpportunityController.releaseLoadingSurface()
            syncAppOpenLoadingSurface()
            return false
        }

        discardAppOpenAd()
        _fullScreenAdState.value = FullScreenAdState.AppOpen
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                preferences.edit()
                    .putLong(LastAppOpenAdShownAtPreferenceKey, System.currentTimeMillis())
                    .apply()
            }

            override fun onAdDismissedFullScreenContent() {
                finishAppOpenPresentation()
                preloadAppOpenAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                finishAppOpenPresentation()
                preloadAppOpenAd()
            }
        }
        return runCatching {
            ad.show(activity)
            true
        }.getOrElse {
            finishAppOpenPresentation()
            preloadAppOpenAd()
            false
        }
    }

    private fun finishAppOpenPresentation() {
        finishFullScreenAd(FullScreenAdState.AppOpen)
        appOpenOpportunityController.releaseLoadingSurface()
        syncAppOpenLoadingSurface()
    }

    private fun scheduleOpportunityExpiry(opportunity: AppOpenOpportunity) {
        appOpenOpportunityExpiryJob?.cancel()
        val delayMillis =
            (opportunity.expiresAtElapsedMillis - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        appOpenOpportunityExpiryJob = managerScope.launch {
            delay(delayMillis)
            appOpenOpportunityController.current(SystemClock.elapsedRealtime() + 1L)
            syncAppOpenLoadingSurface()
        }
    }

    private fun scheduleAppOpenAdRefresh(loadedAtElapsedMillis: Long) {
        appOpenRefreshJob?.cancel()
        appOpenRefreshJob = managerScope.launch {
            delay(AppOpenAdRefreshIntervalMs)
            if (
                appOpenAdLoadedAtMillis == loadedAtElapsedMillis &&
                canPreloadAds()
            ) {
                discardAppOpenAd()
                loadAppOpenAd()
            }
        }
    }

    private fun cancelPendingAppOpenOpportunity() {
        appOpenOpportunityController.onUserInteraction()
        appOpenOpportunityExpiryJob?.cancel()
        syncAppOpenLoadingSurface()
    }

    private fun syncAppOpenLoadingSurface() {
        _appOpenLoadingSurfaceVisible.value = appOpenOpportunityController.loadingSurfaceVisible
    }

    private fun finishFullScreenAd(expected: FullScreenAdState) {
        if (_fullScreenAdState.value == expected) {
            _fullScreenAdState.value = FullScreenAdState.Idle
        }
    }

    private fun discardAppOpenAd() {
        appOpenRefreshJob?.cancel()
        appOpenRefreshJob = null
        appOpenAd = null
        appOpenAdLoadedAtMillis = 0L
        _appOpenAdAvailable.value = false
    }

    private fun isAppOpenAdFresh(): Boolean =
        appOpenAd != null && AppOpenAdPolicy.isFresh(
            nowElapsedMillis = SystemClock.elapsedRealtime(),
            loadedAtElapsedMillis = appOpenAdLoadedAtMillis,
            freshnessMillis = AppOpenAdFreshnessWindowMs,
        )

    private fun isAppOpenShowIntervalElapsed(): Boolean =
        FullScreenAdEligibility.hasMinimumSeparation(
            nowMillis = System.currentTimeMillis(),
            lastShownAtMillis = preferences.getLong(LastAppOpenAdShownAtPreferenceKey, 0L),
            minimumSeparationMillis = AppOpenAdShowIntervalMs,
        )

    private fun isInterstitialShowIntervalElapsed(): Boolean =
        FullScreenAdEligibility.hasMinimumSeparation(
            nowMillis = System.currentTimeMillis(),
            lastShownAtMillis = preferences.getLong(LastInterstitialAdShownAtKey, 0L),
            minimumSeparationMillis = InterstitialAdShowIntervalMs,
        )

    private fun isAppOpenSeparationElapsed(): Boolean =
        FullScreenAdEligibility.hasMinimumSeparation(
            nowMillis = System.currentTimeMillis(),
            lastShownAtMillis = preferences.getLong(LastAppOpenAdShownAtPreferenceKey, 0L),
            minimumSeparationMillis = CrossFormatFullScreenAdSeparationMs,
        )

    private fun isInterstitialSeparationElapsed(): Boolean =
        FullScreenAdEligibility.hasMinimumSeparation(
            nowMillis = System.currentTimeMillis(),
            lastShownAtMillis = preferences.getLong(LastInterstitialAdShownAtKey, 0L),
            minimumSeparationMillis = CrossFormatFullScreenAdSeparationMs,
        )

    private fun canPreloadAds(): Boolean = adsConfigRepository.canPreloadAds.value

    private fun canShowAds(): Boolean =
        adsConfigRepository.isAdsEnabled.value &&
            !adsConfigRepository.isConsentFlowInProgress.value

    private fun Activity.isResumedForFullScreenAd(): Boolean =
        !isFinishing &&
            !isDestroyed &&
            !isChangingConfigurations &&
            (this as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true

    private fun Activity.appOpenActivitySnapshot(): AppOpenActivitySnapshot =
        AppOpenActivitySnapshot(
            resumed = isResumedForFullScreenAd(),
            windowFocused = window?.decorView?.hasWindowFocus() == true,
            finishing = isFinishing,
            destroyed = isDestroyed,
            changingConfigurations = isChangingConfigurations,
        )

    private companion object {
        const val LastInterstitialAdShownAtKey = "last_interstitial_ad_shown_at"
        const val InterstitialAdShowIntervalMs = 2L * 60L * 1000L
        const val CrossFormatFullScreenAdSeparationMs = 2L * 60L * 1000L
        const val AppOpenAdFreshnessWindowMs = 4L * 60L * 60L * 1000L
        const val AppOpenAdShowIntervalMs = 4L * 60L * 60L * 1000L
        const val AppOpenAdRefreshIntervalMs = 3L * 60L * 60L * 1000L
    }
}

internal const val LastAppOpenAdShownAtPreferenceKey = "last_app_open_ad_shown_at"
internal const val FirstSessionCompletedPreferenceKey = "first_session_completed"
