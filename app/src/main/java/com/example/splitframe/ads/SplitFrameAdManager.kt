package com.example.splitframe.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SplitFrameAdManager(
    private val context: Context,
) {
    private var interstitialAd: InterstitialAd? = null
    private var appOpenAd: AppOpenAd? = null
    private var appOpenAdLoadedAtMillis: Long = 0L
    private var isLoadingAppOpenAd = false
    private var isShowingAppOpenAd = false
    private var isShowingInterstitial = false
    private var pendingAppOpenShowToken: Int? = null
    private var nextAppOpenShowToken = 0
    private val pendingAppOpenAdLoadedCallbacks = mutableListOf<(AppOpenAd) -> Unit>()
    private val pendingAppOpenAdFailedCallbacks = mutableListOf<() -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _appOpenAdLoadingVisible = MutableStateFlow(false)
    val appOpenAdLoadingVisible: StateFlow<Boolean> = _appOpenAdLoadingVisible.asStateFlow()

    fun preloadInterstitial() {
        if (interstitialAd != null) return
        InterstitialAd.load(
            context,
            TestInterstitialAdUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
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
        if (isShowingAppOpenAd) {
            onComplete()
            return false
        }
        if (isShowingInterstitial) {
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
        if (isShowingAppOpenAd || isShowingInterstitial) return
        val cachedAd = appOpenAd.takeIf { isAppOpenAdFresh() }
        if (cachedAd != null) {
            showLoadedAppOpenAd(activity, cachedAd)
            return
        }

        _appOpenAdLoadingVisible.value = true
        val token = ++nextAppOpenShowToken
        pendingAppOpenShowToken = token
        mainHandler.postDelayed(
            {
                if (pendingAppOpenShowToken == token && !isShowingAppOpenAd) {
                    pendingAppOpenShowToken = null
                    _appOpenAdLoadingVisible.value = false
                }
            },
            AppOpenAdLoadOverlayTimeoutMs,
        )
        loadAppOpenAd(
            onLoaded = { ad ->
                if (pendingAppOpenShowToken == token) {
                    showLoadedAppOpenAd(activity, ad)
                }
            },
            onFailed = {
                if (pendingAppOpenShowToken == token) {
                    pendingAppOpenShowToken = null
                    _appOpenAdLoadingVisible.value = false
                }
            },
        )
    }

    private fun loadAppOpenAd(
        onLoaded: ((AppOpenAd) -> Unit)? = null,
        onFailed: (() -> Unit)? = null,
    ) {
        val cachedAd = appOpenAd.takeIf { isAppOpenAdFresh() }
        if (cachedAd != null) {
            onLoaded?.invoke(cachedAd)
            return
        }
        onLoaded?.let(pendingAppOpenAdLoadedCallbacks::add)
        onFailed?.let(pendingAppOpenAdFailedCallbacks::add)
        if (isLoadingAppOpenAd) return
        isLoadingAppOpenAd = true
        AppOpenAd.load(
            context,
            TestAppOpenAdUnitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    appOpenAdLoadedAtMillis = SystemClock.elapsedRealtime()
                    isLoadingAppOpenAd = false
                    val callbacks = pendingAppOpenAdLoadedCallbacks.toList()
                    pendingAppOpenAdLoadedCallbacks.clear()
                    pendingAppOpenAdFailedCallbacks.clear()
                    callbacks.forEach { it(ad) }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    appOpenAd = null
                    appOpenAdLoadedAtMillis = 0L
                    isLoadingAppOpenAd = false
                    val callbacks = pendingAppOpenAdFailedCallbacks.toList()
                    pendingAppOpenAdLoadedCallbacks.clear()
                    pendingAppOpenAdFailedCallbacks.clear()
                    callbacks.forEach { it() }
                }
            },
        )
    }

    private fun showLoadedAppOpenAd(activity: Activity, ad: AppOpenAd) {
        if (isShowingAppOpenAd || isShowingInterstitial) return
        pendingAppOpenShowToken = null
        appOpenAd = null
        _appOpenAdLoadingVisible.value = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                isShowingAppOpenAd = true
            }

            override fun onAdDismissedFullScreenContent() {
                isShowingAppOpenAd = false
                _appOpenAdLoadingVisible.value = false
                preloadAppOpenAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                isShowingAppOpenAd = false
                _appOpenAdLoadingVisible.value = false
                preloadAppOpenAd()
            }
        }
        runCatching {
            ad.show(activity)
        }.onFailure {
            isShowingAppOpenAd = false
            _appOpenAdLoadingVisible.value = false
            preloadAppOpenAd()
        }
    }

    private fun isAppOpenAdFresh(): Boolean =
        appOpenAd != null &&
            SystemClock.elapsedRealtime() - appOpenAdLoadedAtMillis < AppOpenAdFreshnessWindowMs

    companion object {
        const val TestBannerAdUnitId = "ca-app-pub-3940256099942544/6300978111"
        const val TestNativeAdvancedAdUnitId = "ca-app-pub-3940256099942544/2247696110"
        private const val TestInterstitialAdUnitId = "ca-app-pub-3940256099942544/1033173712"
        private const val TestAppOpenAdUnitId = "ca-app-pub-3940256099942544/9257395921"
        private const val AppOpenAdFreshnessWindowMs = 4L * 60L * 60L * 1000L
        private const val AppOpenAdLoadOverlayTimeoutMs = 1_200L
    }
}
