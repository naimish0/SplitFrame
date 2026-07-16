package com.example.splitframe.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class SplitFrameAdManager(
    private val context: Context,
) {
    private var interstitialAd: InterstitialAd? = null

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
        val ad = interstitialAd ?: run {
            preloadInterstitial()
            return
        }
        interstitialAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preloadInterstitial()
            }
        }
        ad.show(activity)
    }

    companion object {
        const val TestBannerAdUnitId = "ca-app-pub-3940256099942544/6300978111"
        private const val TestInterstitialAdUnitId = "ca-app-pub-3940256099942544/1033173712"
    }
}
