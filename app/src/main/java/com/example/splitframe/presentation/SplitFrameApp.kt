package com.example.splitframe.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.splitframe.ads.BannerAd
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.splitframe.R
import com.example.splitframe.ads.SplitFrameAdManager
import com.example.splitframe.domain.ExportResult
import com.example.splitframe.domain.TemplateFilter
import com.example.splitframe.presentation.merge.EditorScreen
import com.example.splitframe.presentation.merge.MergeIntent
import com.example.splitframe.presentation.merge.MergeViewModel
import com.example.splitframe.presentation.merge.TemplatePickerScreen
import com.example.splitframe.presentation.single.SingleImageIntent
import com.example.splitframe.presentation.single.SingleImageScreen
import com.example.splitframe.presentation.single.SingleImageViewModel
import com.example.splitframe.presentation.video.VideoEditorScreen
import com.example.splitframe.presentation.video.VideoMergeViewModel
import com.example.splitframe.ui.components.AdContainer
import kotlin.random.Random
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private enum class AppScreen {
    ModeSelection,
    Templates,
    Editor,
    SingleImage,
    VideoEditor,
}

@Composable
fun SplitFrameApp(
    viewModel: MergeViewModel = koinViewModel(),
    adManager: SplitFrameAdManager = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val appOpenAdLoadingVisible by adManager.appOpenAdLoadingVisible.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(AppScreen.ModeSelection) }
    var lastShownAdUri by rememberSaveable { mutableStateOf<String?>(null) }
    var lastShownVideoAdUri by rememberSaveable { mutableStateOf<String?>(null) }
    var suppressNextPhotoExportAd by rememberSaveable { mutableStateOf(false) }
    var showAppOpenOnNextStart by remember { mutableStateOf(false) }
    val templateGridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    var templateFilter by rememberSaveable { mutableStateOf(TemplateFilter.ALL) }
    val templatePaletteSeed = rememberSaveable { Random.nextInt() }

    LaunchedEffect(Unit) {
        adManager.preloadInterstitial()
        context.findActivity()?.let(adManager::showAppOpenAdOverLoadingScreen)
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            val activity = context.findActivity()
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    showAppOpenOnNextStart = activity?.isChangingConfigurations != true
                }
                Lifecycle.Event.ON_START -> {
                    if (showAppOpenOnNextStart) {
                        showAppOpenOnNextStart = false
                        activity?.let(adManager::showAppOpenAdOverLoadingScreen)
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun showInterstitialForAction(action: () -> Unit): Boolean {
        val activity = context.findActivity()
        return if (activity == null) {
            action()
            false
        } else {
            adManager.showInterstitialForUserAction(activity, action)
        }
    }

    fun showInterstitialBeforePhotoExport(action: () -> Unit) {
        suppressNextPhotoExportAd = showInterstitialForAction(action)
    }

    LaunchedEffect(state.exportResult) {
        val exportResult = state.exportResult ?: return@LaunchedEffect
        if (exportResult is ExportResult.Failure) {
            suppressNextPhotoExportAd = false
            return@LaunchedEffect
        }
        val result = exportResult as? ExportResult.Success ?: return@LaunchedEffect
        if (suppressNextPhotoExportAd) {
            suppressNextPhotoExportAd = false
            return@LaunchedEffect
        }
        if (lastShownAdUri == result.savedUri) return@LaunchedEffect
        lastShownAdUri = result.savedUri
        context.findActivity()?.let(adManager::showInterstitialAfterExport)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (screen) {
                    AppScreen.ModeSelection -> ModeSelectionScreen(
                        onOpenPhotoCollage = { screen = AppScreen.Templates },
                        onOpenVideoSplit = { screen = AppScreen.VideoEditor },
                    )
                    AppScreen.Templates -> {
                        BackHandler {
                            screen = AppScreen.ModeSelection
                        }
                        TemplatePickerScreen(
                            state = state,
                            paletteSeed = templatePaletteSeed,
                            gridState = templateGridState,
                            selectedFilter = templateFilter,
                            onFilterSelected = { templateFilter = it },
                            onOpenSingleImageTool = { screen = AppScreen.SingleImage },
                            onTemplateSelected = { templateId ->
                                viewModel.process(MergeIntent.SelectTemplate(templateId))
                                screen = AppScreen.Editor
                            },
                        )
                    }
                    AppScreen.Editor -> {
                        BackHandler {
                            screen = AppScreen.Templates
                        }
                        EditorScreen(
                            state = state,
                            onIntent = viewModel::process,
                            onBack = { screen = AppScreen.Templates },
                            onShowInterstitialAd = { action -> showInterstitialForAction(action) },
                            onShowInterstitialBeforeExport = { action -> showInterstitialBeforePhotoExport(action) },
                        )
                    }
                    AppScreen.SingleImage -> {
                        val singleImageViewModel = koinViewModel<SingleImageViewModel>()
                        val singleImageState by singleImageViewModel.state.collectAsStateWithLifecycle()
                        BackHandler {
                            screen = AppScreen.Templates
                        }
                        SingleImageScreen(
                            state = singleImageState,
                            onIntent = singleImageViewModel::process,
                            onBack = { screen = AppScreen.Templates },
                            onUseInCollage = { source ->
                                viewModel.process(MergeIntent.AssignImages(listOf(source)))
                                singleImageViewModel.process(SingleImageIntent.ClearResult)
                                screen = AppScreen.Templates
                            },
                            onShowInterstitialAd = { action -> showInterstitialForAction(action) },
                        )
                    }
                    AppScreen.VideoEditor -> {
                        val videoViewModel = koinViewModel<VideoMergeViewModel>()
                        val videoState by videoViewModel.state.collectAsStateWithLifecycle()
                        LaunchedEffect(videoState.exportResult) {
                            val result = videoState.exportResult as? ExportResult.Success ?: return@LaunchedEffect
                            if (lastShownVideoAdUri == result.savedUri) return@LaunchedEffect
                            lastShownVideoAdUri = result.savedUri
                            context.findActivity()?.let(adManager::showInterstitialAfterExport)
                        }
                        BackHandler {
                            screen = AppScreen.ModeSelection
                        }
                        VideoEditorScreen(
                            state = videoState,
                            onIntent = videoViewModel::process,
                            onBack = { screen = AppScreen.ModeSelection },
                        )
                    }
                }
            }
            AppBannerAd(modifier = Modifier.navigationBarsPadding())
        }
        if (appOpenAdLoadingVisible) {
            AppOpenAdLoadingOverlay()
        }
    }
}

@Composable
private fun AppBannerAd(
    modifier: Modifier = Modifier,
) {
    AdContainer(modifier = modifier) {
        BannerAd(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        )
    }
}

@Composable
private fun AppOpenAdLoadingOverlay() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.app_open_ad_loading),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
