package com.example.splitframe.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import com.example.splitframe.presentation.merge.EditorScreen
import com.example.splitframe.presentation.merge.MergeIntent
import com.example.splitframe.presentation.merge.MergeViewModel
import com.example.splitframe.presentation.merge.TemplatePickerScreen
import kotlin.random.Random
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private enum class AppScreen {
    Templates,
    Editor,
}

@Composable
fun SplitFrameApp(
    viewModel: MergeViewModel = koinViewModel(),
    adManager: SplitFrameAdManager = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var screen by remember { mutableStateOf(AppScreen.Templates) }
    var lastShownAdUri by rememberSaveable { mutableStateOf<String?>(null) }
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
        AppScreen.Templates -> TemplatePickerScreen(
            state = state,
            paletteSeed = templatePaletteSeed,
            onTemplateSelected = { templateId ->
                viewModel.process(MergeIntent.SelectTemplate(templateId))
                screen = AppScreen.Editor
            },
        )
        AppScreen.Editor -> EditorScreen(
            state = state,
            onIntent = viewModel::process,
            onBack = { screen = AppScreen.Templates },
        )
    }
}

private fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
