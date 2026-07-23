package com.rameshta.splitframe.ads

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.rameshta.splitframe.BuildConfig
import kotlin.math.floor

internal enum class BannerAdLoadState {
    Loading,
    Loaded,
    Failed,
}

@Composable
internal fun BannerAd(
    modifier: Modifier = Modifier,
    onLoadStateChanged: (BannerAdLoadState) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val externalUiLauncher = LocalExternalUiLauncher.current
    val currentLoadStateCallback = rememberUpdatedState(onLoadStateChanged)
    val currentExternalUiLauncher = rememberUpdatedState(externalUiLauncher)
    val lifecycleController = remember {
        EmbeddedAdViewLifecycleController<AdView>(
            load = { view -> view.loadAd(AdRequest.Builder().build()) },
            resume = AdView::resume,
            pause = AdView::pause,
            destroy = AdView::destroy,
        )
    }

    DisposableEffect(lifecycleOwner, lifecycleController) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> lifecycleController.onResume()
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                -> lifecycleController.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleController.onResume()
        } else {
            lifecycleController.onPause()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            lifecycleController.onPause()
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availableWidthDp = floor(maxWidth.value.toDouble()).toInt()
        if (availableWidthDp <= 0) return@BoxWithConstraints
        val adSize = remember(context, availableWidthDp) {
            AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, availableWidthDp)
        }

        key(availableWidthDp) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(adSize.height.dp),
                factory = { viewContext ->
                    currentLoadStateCallback.value(BannerAdLoadState.Loading)
                    AdView(viewContext).apply {
                        setAdSize(adSize)
                        adUnitId = BuildConfig.BANNER_AD_UNIT_ID
                        adListener = object : AdListener() {
                            override fun onAdLoaded() {
                                currentLoadStateCallback.value(BannerAdLoadState.Loaded)
                            }

                            override fun onAdFailedToLoad(error: LoadAdError) {
                                currentLoadStateCallback.value(BannerAdLoadState.Failed)
                            }

                            override fun onAdClicked() {
                                currentExternalUiLauncher.value.launch(ExternalUiReason.AdClick) {}
                            }
                        }
                        lifecycleController.attach(this)
                    }
                },
                onRelease = lifecycleController::release,
            )
        }
    }
}
