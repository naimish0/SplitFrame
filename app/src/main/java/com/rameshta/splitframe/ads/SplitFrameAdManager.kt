package com.rameshta.splitframe.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.rameshta.splitframe.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SplitFrameAdManager(
    private val context: Context,
    private val adsConfigRepository: AdsConfigRepository,
) {
    private var interstitialAd: InterstitialAd? = null
    private var isLoadingInterstitial = false
    private var interstitialLoadGeneration = 0
    private var appOpenAd: AppOpenAd? = null
    private var appOpenAdLoadedAtMillis: Long = 0L
    private var isLoadingAppOpenAd = false
    private var appOpenLoadGeneration = 0
    private var isShowingAppOpenAd = false
    private var isShowingInterstitial = false
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferences = context.getSharedPreferences(AdPreferencesName, Context.MODE_PRIVATE)

    private val _isAppOpenAdVisible = MutableStateFlow(false)
    val isAppOpenAdVisible: StateFlow<Boolean> = _isAppOpenAdVisible.asStateFlow()
    val appOpenAdVisible: StateFlow<Boolean> = isAppOpenAdVisible

    init {
        managerScope.launch {
            adsConfigRepository.isAdsEnabled.collect { isEnabled ->
                if (!isEnabled) clearCachedAds()
            }
        }
    }

    fun preloadInterstitial() {
        if (!canRequestAds() || interstitialAd != null || isLoadingInterstitial) return

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
                    interstitialAd = ad.takeIf { canRequestAds() }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (loadGeneration != interstitialLoadGeneration) return
                    isLoadingInterstitial = false
                    interstitialAd = null
                }
            },
        )
    }

    fun showInterstitialAfterExport(activity: Activity) {
        showInterstitialForUserAction(activity)
    }

    fun showInterstitialForUserAction(
        activity: Activity,
        onComplete: () -> Unit = {},
    ): Boolean {
        if (
            !canRequestAds() ||
            isShowingAppOpenAd ||
            isShowingInterstitial ||
            !isInterstitialShowIntervalElapsed()
        ) {
            onComplete()
            return false
        }
        val ad = interstitialAd ?: run {
            preloadInterstitial()
            onComplete()
            return false
        }
        interstitialAd = null
        var didComplete = false
        fun completeOnce() {
            if (didComplete) return
            didComplete = true
            onComplete()
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                isShowingInterstitial = true
                preferences.edit()
                    .putLong(LastInterstitialAdShownAtKey, System.currentTimeMillis())
                    .apply()
            }

            override fun onAdDismissedFullScreenContent() {
                isShowingInterstitial = false
                preloadInterstitial()
                completeOnce()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                isShowingInterstitial = false
                preloadInterstitial()
                completeOnce()
            }
        }
        isShowingInterstitial = true
        return runCatching {
            ad.show(activity)
            true
        }.getOrElse {
            isShowingInterstitial = false
            preloadInterstitial()
            completeOnce()
            false
        }
    }

    fun preloadAppOpenAd() {
        loadAppOpenAd()
    }

    fun showAppOpenAdOverLoadingScreen(activity: Activity) {
        showAppOpenAdIfAvailable(activity)
    }

    fun showAppOpenAdIfAvailable(activity: Activity): Boolean {
        if (!canRequestAds() || isShowingAppOpenAd || isShowingInterstitial) return false
        if (!isAppOpenShowIntervalElapsed()) {
            if (!isAppOpenAdFresh()) {
                discardAppOpenAd()
                preloadAppOpenAd()
            }
            return false
        }

        val cachedAd = appOpenAd.takeIf { isAppOpenAdFresh() }
        if (cachedAd == null) {
            discardAppOpenAd()
            preloadAppOpenAd()
            return false
        }
        return showLoadedAppOpenAd(activity, cachedAd)
    }

    fun clearCachedAds() {
        interstitialAd = null
        isLoadingInterstitial = false
        interstitialLoadGeneration++
        discardAppOpenAd()
        isLoadingAppOpenAd = false
        appOpenLoadGeneration++
    }

    private fun loadAppOpenAd() {
        if (!canRequestAds()) return
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
                    if (!canRequestAds()) return
                    appOpenAd = ad
                    appOpenAdLoadedAtMillis = SystemClock.elapsedRealtime()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (loadGeneration != appOpenLoadGeneration) return
                    isLoadingAppOpenAd = false
                    discardAppOpenAd()
                }
            },
        )
    }

    private fun showLoadedAppOpenAd(activity: Activity, ad: AppOpenAd): Boolean {
        if (
            !canRequestAds() ||
            isShowingAppOpenAd ||
            isShowingInterstitial ||
            !isAppOpenShowIntervalElapsed()
        ) {
            return false
        }

        discardAppOpenAd()
        isShowingAppOpenAd = true
        _isAppOpenAdVisible.value = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                _isAppOpenAdVisible.value = true
                preferences.edit()
                    .putLong(LastAppOpenAdShownAtKey, System.currentTimeMillis())
                    .apply()
            }

            override fun onAdDismissedFullScreenContent() {
                isShowingAppOpenAd = false
                _isAppOpenAdVisible.value = false
                preloadAppOpenAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                isShowingAppOpenAd = false
                _isAppOpenAdVisible.value = false
                preloadAppOpenAd()
            }
        }
        return runCatching {
            ad.show(activity)
            true
        }.getOrElse {
            isShowingAppOpenAd = false
            _isAppOpenAdVisible.value = false
            preloadAppOpenAd()
            false
        }
    }

    private fun discardAppOpenAd() {
        appOpenAd = null
        appOpenAdLoadedAtMillis = 0L
    }

    private fun isAppOpenAdFresh(): Boolean =
        appOpenAd != null &&
            SystemClock.elapsedRealtime() - appOpenAdLoadedAtMillis < AppOpenAdFreshnessWindowMs

    private fun isAppOpenShowIntervalElapsed(): Boolean {
        val lastShownAtMillis = preferences.getLong(LastAppOpenAdShownAtKey, 0L)
        if (lastShownAtMillis == 0L) return true
        return System.currentTimeMillis() - lastShownAtMillis >= AppOpenAdShowIntervalMs
    }

    private fun isInterstitialShowIntervalElapsed(): Boolean {
        val lastShownAtMillis = preferences.getLong(LastInterstitialAdShownAtKey, 0L)
        if (lastShownAtMillis == 0L) return true
        return System.currentTimeMillis() - lastShownAtMillis >= InterstitialAdShowIntervalMs
    }

    private fun canRequestAds(): Boolean = adsConfigRepository.isAdsEnabled.value

    companion object {
        private const val AdPreferencesName = "splitframe_ads"
        private const val LastInterstitialAdShownAtKey = "last_interstitial_ad_shown_at"
        private const val LastAppOpenAdShownAtKey = "last_app_open_ad_shown_at"
        private const val InterstitialAdShowIntervalMs = 2L * 60L * 1000L
        private const val AppOpenAdFreshnessWindowMs = 4L * 60L * 60L * 1000L
        private const val AppOpenAdShowIntervalMs = 4L * 60L * 60L * 1000L
    }
}
