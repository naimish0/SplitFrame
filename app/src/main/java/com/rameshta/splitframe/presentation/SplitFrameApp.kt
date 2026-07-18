package com.rameshta.splitframe.presentation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rameshta.splitframe.R
import com.rameshta.splitframe.ads.AdsConfigRepository
import com.rameshta.splitframe.ads.BannerAd
import com.rameshta.splitframe.ads.SplitFrameAdManager
import com.rameshta.splitframe.domain.ExportResult
import com.rameshta.splitframe.domain.TemplateFilter
import com.rameshta.splitframe.presentation.merge.EditorScreen
import com.rameshta.splitframe.presentation.merge.MergeIntent
import com.rameshta.splitframe.presentation.merge.MergeViewModel
import com.rameshta.splitframe.presentation.merge.TemplatePickerScreen
import com.rameshta.splitframe.presentation.single.SingleImageIntent
import com.rameshta.splitframe.presentation.single.SingleImageScreen
import com.rameshta.splitframe.presentation.single.SingleImageViewModel
import com.rameshta.splitframe.presentation.video.VideoEditorScreen
import com.rameshta.splitframe.presentation.video.VideoMergeIntent
import com.rameshta.splitframe.presentation.video.VideoMergeViewModel
import com.rameshta.splitframe.ui.components.AdContainer
import kotlin.random.Random
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private enum class AppScreen {
    ModeSelection,
    Templates,
    Editor,
    SingleImage,
    VideoEditor,
    PrivacyPolicy,
}

@Composable
fun SplitFrameApp(
    viewModel: MergeViewModel = koinViewModel(),
    adManager: SplitFrameAdManager = koinInject(),
    adsConfigRepository: AdsConfigRepository = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val adsEnabled by adsConfigRepository.isAdsEnabled.collectAsStateWithLifecycle()
    val privacyOptionsRequired by adsConfigRepository.privacyOptionsRequired.collectAsStateWithLifecycle()
    val appOpenAdVisible by adManager.isAppOpenAdVisible.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var screen by remember { mutableStateOf(AppScreen.ModeSelection) }
    var lastShownAdUri by rememberSaveable { mutableStateOf<String?>(null) }
    var lastShownVideoAdUri by rememberSaveable { mutableStateOf<String?>(null) }
    var suppressNextPhotoExportAd by rememberSaveable { mutableStateOf(false) }
    var backgroundedAtElapsedMillis by remember { mutableStateOf<Long?>(null) }
    val templateGridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    var templateFilter by rememberSaveable { mutableStateOf(TemplateFilter.ALL) }
    val templatePaletteSeed = rememberSaveable { Random.nextInt() }

    val storagePermissionDeniedMessage = stringResource(R.string.storage_permission_required)
    var pendingLegacyStorageAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingLegacyStorageAction
        pendingLegacyStorageAction = null
        if (granted) {
            action?.invoke()
        } else {
            Toast.makeText(context, storagePermissionDeniedMessage, Toast.LENGTH_LONG).show()
        }
    }

    var pendingNotificationAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showNotificationPermissionDialog by remember { mutableStateOf(false) }
    var notificationPermissionHandled by rememberSaveable { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        val action = pendingNotificationAction
        pendingNotificationAction = null
        action?.invoke()
    }

    fun runWithLegacyStoragePermission(action: () -> Unit) {
        val needsPermission =
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingLegacyStorageAction = action
            legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            action()
        }
    }

    fun runVideoExportWithPermissions(action: () -> Unit) {
        runWithLegacyStoragePermission {
            val needsNotificationChoice =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED &&
                    !notificationPermissionHandled
            if (needsNotificationChoice) {
                pendingNotificationAction = action
                showNotificationPermissionDialog = true
            } else {
                action()
            }
        }
    }

    LaunchedEffect(adsEnabled) {
        if (adsEnabled) {
            adManager.preloadInterstitial()
            adManager.preloadAppOpenAd()
        }
    }

    DisposableEffect(lifecycleOwner, context, adsEnabled, screen) {
        val observer = LifecycleEventObserver { _, event ->
            val activity = context.findActivity()
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (activity?.isChangingConfigurations != true) {
                        backgroundedAtElapsedMillis = SystemClock.elapsedRealtime()
                    }
                }
                Lifecycle.Event.ON_START -> {
                    val backgroundedAt = backgroundedAtElapsedMillis
                    backgroundedAtElapsedMillis = null
                    if (
                        adsEnabled &&
                        screen == AppScreen.ModeSelection &&
                        backgroundedAt != null &&
                        SystemClock.elapsedRealtime() - backgroundedAt >= AppOpenMinimumBackgroundMs
                    ) {
                        activity?.let(adManager::showAppOpenAdIfAvailable)
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.exportResult) {
        val exportResult = state.exportResult ?: return@LaunchedEffect
        if (exportResult is ExportResult.Failure) {
            suppressNextPhotoExportAd = false
            return@LaunchedEffect
        }
        val result = exportResult as? ExportResult.Success ?: return@LaunchedEffect
        if (lastShownAdUri == result.savedUri) return@LaunchedEffect
        lastShownAdUri = result.savedUri
        if (suppressNextPhotoExportAd) {
            suppressNextPhotoExportAd = false
            return@LaunchedEffect
        }
        context.findActivity()?.let(adManager::showInterstitialAfterExport)
    }

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
                    onOpenPrivacyPolicy = { screen = AppScreen.PrivacyPolicy },
                    showNativeAd = adsEnabled && !appOpenAdVisible,
                )
                AppScreen.Templates -> {
                    BackHandler { screen = AppScreen.ModeSelection }
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
                    BackHandler { screen = AppScreen.Templates }
                    EditorScreen(
                        state = state,
                        onIntent = viewModel::process,
                        onBack = { screen = AppScreen.Templates },
                        onRequestExport = ::runWithLegacyStoragePermission,
                        onExportForShare = { suppressNextPhotoExportAd = true },
                    )
                }
                AppScreen.SingleImage -> {
                    val singleImageViewModel = koinViewModel<SingleImageViewModel>()
                    val singleImageState by singleImageViewModel.state.collectAsStateWithLifecycle()
                    BackHandler { screen = AppScreen.Templates }
                    SingleImageScreen(
                        state = singleImageState,
                        onIntent = { intent ->
                            if (intent == SingleImageIntent.Process) {
                                runWithLegacyStoragePermission { singleImageViewModel.process(intent) }
                            } else {
                                singleImageViewModel.process(intent)
                            }
                        },
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
                    BackHandler { screen = AppScreen.ModeSelection }
                    VideoEditorScreen(
                        state = videoState,
                        onIntent = { intent ->
                            if (
                                intent == VideoMergeIntent.StartExport ||
                                intent == VideoMergeIntent.RetryExport
                            ) {
                                runVideoExportWithPermissions { videoViewModel.process(intent) }
                            } else {
                                videoViewModel.process(intent)
                            }
                        },
                        onBack = { screen = AppScreen.ModeSelection },
                    )
                }
                AppScreen.PrivacyPolicy -> {
                    BackHandler { screen = AppScreen.ModeSelection }
                    PrivacyScreen(
                        onBack = { screen = AppScreen.ModeSelection },
                        showAdPrivacyOptions = privacyOptionsRequired,
                        onManageAdPrivacy = {
                            val activity = context.findActivity() ?: return@PrivacyScreen
                            adManager.clearCachedAds()
                            adsConfigRepository.showPrivacyOptions(activity)
                        },
                    )
                }
            }
        }
        if (
            adsEnabled &&
            !appOpenAdVisible &&
            screen != AppScreen.ModeSelection &&
            screen != AppScreen.PrivacyPolicy
        ) {
            AppBannerAd(modifier = Modifier.navigationBarsPadding())
        }
    }

    if (showNotificationPermissionDialog) {
        AlertDialog(
            onDismissRequest = {
                notificationPermissionHandled = true
                showNotificationPermissionDialog = false
                val action = pendingNotificationAction
                pendingNotificationAction = null
                action?.invoke()
            },
            title = { Text(stringResource(R.string.video_notification_permission_title)) },
            text = { Text(stringResource(R.string.video_notification_permission_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        notificationPermissionHandled = true
                        showNotificationPermissionDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            val action = pendingNotificationAction
                            pendingNotificationAction = null
                            action?.invoke()
                        }
                    },
                ) {
                    Text(stringResource(R.string.continue_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        notificationPermissionHandled = true
                        showNotificationPermissionDialog = false
                        val action = pendingNotificationAction
                        pendingNotificationAction = null
                        action?.invoke()
                    },
                ) {
                    Text(stringResource(R.string.not_now))
                }
            },
        )
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

private fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private const val AppOpenMinimumBackgroundMs = 30_000L
