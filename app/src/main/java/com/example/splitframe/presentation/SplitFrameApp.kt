package com.example.splitframe.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    var screen by remember { mutableStateOf(AppScreen.ModeSelection) }
    var lastShownAdUri by rememberSaveable { mutableStateOf<String?>(null) }
    var lastShownVideoAdUri by rememberSaveable { mutableStateOf<String?>(null) }
    val templateGridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    var templateFilter by rememberSaveable { mutableStateOf(TemplateFilter.ALL) }
    val templatePaletteSeed = rememberSaveable { Random.nextInt() }

    LaunchedEffect(Unit) {
        adManager.preloadInterstitial()
    }

    LaunchedEffect(state.exportResult) {
        val result = state.exportResult as? ExportResult.Success ?: return@LaunchedEffect
        if (lastShownAdUri == result.savedUri) return@LaunchedEffect
        lastShownAdUri = result.savedUri
        context.findActivity()?.let(adManager::showInterstitialAfterExport)
    }

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

private fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
