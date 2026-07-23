package com.rameshta.splitframe

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.rameshta.splitframe.ads.AdsConfigRepository
import com.rameshta.splitframe.ads.ExternalUiLauncher
import com.rameshta.splitframe.ads.LocalExternalUiLauncher
import com.rameshta.splitframe.ads.SplitFrameAdManager
import com.rameshta.splitframe.data.VideoProjectStore
import com.rameshta.splitframe.export.VideoExportRecoveryCoordinator
import com.rameshta.splitframe.export.ExportPublicationReconciler
import com.rameshta.splitframe.presentation.SplitFrameApp
import com.rameshta.splitframe.ui.theme.SplitFrameTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : AppCompatActivity() {
    private val adsConfigRepository: AdsConfigRepository by inject()
    private val adManager: SplitFrameAdManager by inject()
    private val videoProjectStore: VideoProjectStore by inject()
    private val videoExportRecoveryCoordinator: VideoExportRecoveryCoordinator by inject()
    private val exportPublicationReconciler: ExportPublicationReconciler by inject()
    private var quickEntryAction by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        quickEntryAction = intent.action?.takeIf(::isSplitFrameQuickEntryAction)
        adManager.onActivityCreated(
            restored = savedInstanceState != null,
            launcherStart = intent.isLauncherStart(),
        )
        enableEdgeToEdge()
        if (savedInstanceState == null || !adsConfigRepository.hasRequestedConsentInfoThisProcess) {
            adsConfigRepository.gatherConsent(this)
        }
        lifecycleScope.launch {
            videoProjectStore.observeHasActiveExport()
                .distinctUntilChanged()
                .collect(adManager::updateVideoExportInProgress)
        }
        lifecycleScope.launch {
            // Let any same-process enqueue transaction settle before auditing durable rows.
            delay(VideoExportReconciliationDelayMillis)
            try {
                exportPublicationReconciler.reconcile()
                videoExportRecoveryCoordinator.reconcileActiveRows()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // Recovery is best-effort and must never block app startup or offline editing.
            }
        }
        setContent {
            val appOpenLoadingVisible by
                adManager.appOpenLoadingSurfaceVisible.collectAsStateWithLifecycle()
            val appOpenAdAvailable by adManager.appOpenAdAvailable.collectAsStateWithLifecycle()
            val externalUiLauncher = remember(adManager) {
                ExternalUiLauncher(adManager::runExternalUiLaunch)
            }
            LaunchedEffect(appOpenLoadingVisible, appOpenAdAvailable) {
                if (appOpenLoadingVisible && appOpenAdAvailable) {
                    adManager.showColdStartAppOpenIfAvailable(this@MainActivity)
                }
            }
            SplitFrameTheme {
                CompositionLocalProvider(LocalExternalUiLauncher provides externalUiLauncher) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = if (appOpenLoadingVisible) {
                                Modifier
                                    .fillMaxSize()
                                    .clearAndSetSemantics { }
                            } else {
                                Modifier.fillMaxSize()
                            },
                        ) {
                            SplitFrameApp(
                                quickEntryAction = quickEntryAction,
                                onQuickEntryConsumed = { quickEntryAction = null },
                            )
                        }
                        if (appOpenLoadingVisible) AppOpenLoadingSurface()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        adManager.onActivityResumed(this)
    }

    override fun onStop() {
        adManager.onActivityStopped(isChangingConfigurations)
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        adManager.onWindowFocusChanged(this, hasFocus)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        adManager.onUserInteraction()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        adManager.onUserInteraction()
        setIntent(intent)
        quickEntryAction = intent.action?.takeIf(::isSplitFrameQuickEntryAction)
    }

    private companion object {
        const val VideoExportReconciliationDelayMillis = 2_000L
    }
}

private fun isSplitFrameQuickEntryAction(action: String): Boolean =
    action == "com.rameshta.splitframe.action.RESIZE" ||
        action == "com.rameshta.splitframe.action.COLLAGE" ||
        action == "com.rameshta.splitframe.action.VIDEO"

@Composable
private fun AppOpenLoadingSurface() {
    val loadingDescription = stringResource(R.string.app_open_loading)
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                }
            }
            .clearAndSetSemantics { contentDescription = loadingDescription },
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_splitframe_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(22.dp)),
            )
            CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
            Text(
                text = loadingDescription,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

private fun Intent?.isLauncherStart(): Boolean =
    this?.action == Intent.ACTION_MAIN &&
        hasCategory(Intent.CATEGORY_LAUNCHER) &&
        data == null
