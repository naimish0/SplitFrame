package com.rameshta.splitframe.presentation

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rameshta.splitframe.R
import com.rameshta.splitframe.ads.AdsConfigRepository
import com.rameshta.splitframe.ads.BannerAd
import com.rameshta.splitframe.ads.BannerAdLoadState
import com.rameshta.splitframe.ads.EmbeddedAdPolicy
import com.rameshta.splitframe.ads.EmbeddedAdSurface
import com.rameshta.splitframe.ads.ExternalUiReason
import com.rameshta.splitframe.ads.FullScreenAdState
import com.rameshta.splitframe.ads.LocalExternalUiLauncher
import com.rameshta.splitframe.ads.SplitFrameAdManager
import com.rameshta.splitframe.ads.WorkflowCompletionEvents
import com.rameshta.splitframe.data.RecentVideoProjectStore
import com.rameshta.splitframe.data.VideoProjectStore
import com.rameshta.splitframe.domain.ExportResult
import com.rameshta.splitframe.presentation.home.HomeDashboardViewModel
import com.rameshta.splitframe.presentation.merge.EditorScreen
import com.rameshta.splitframe.presentation.merge.MergeIntent
import com.rameshta.splitframe.presentation.merge.MergeViewModel
import com.rameshta.splitframe.presentation.merge.TemplatePickerScreen
import com.rameshta.splitframe.presentation.single.SingleImageIntent
import com.rameshta.splitframe.presentation.single.SingleImageScreen
import com.rameshta.splitframe.presentation.single.SingleImageViewModel
import com.rameshta.splitframe.presentation.video.VideoEditorScreen
import com.rameshta.splitframe.presentation.video.VideoEditorStatus
import com.rameshta.splitframe.presentation.video.VideoMergeIntent
import com.rameshta.splitframe.presentation.video.VideoMergeViewModel
import com.rameshta.splitframe.presentation.video.VideoProjectSessionArgs
import com.rameshta.splitframe.presentation.video.VideoProjectsScreen
import com.rameshta.splitframe.ui.components.AdContainer
import java.util.UUID
import kotlin.random.Random
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

internal enum class AppScreen {
    ModeSelection,
    Templates,
    Editor,
    SingleImage,
    VideoProjects,
    VideoEditor,
    PrivacyPolicy,
}

internal data class AppRoute(
    val screen: AppScreen = AppScreen.ModeSelection,
    val activeVideoProjectId: String? = null,
    val createVideoProjectIfMissing: Boolean = false,
    val photoBackDestination: AppScreen = AppScreen.Templates,
)

internal fun AppRoute.savedValues(): List<String> =
    listOf(
        screen.name,
        activeVideoProjectId.orEmpty(),
        createVideoProjectIfMissing.toString(),
        validPhotoBackDestination().name,
    )

internal fun restoreAppRoute(savedValues: List<String>): AppRoute {
    val projectId = canonicalProjectIdOrNull(savedValues.getOrNull(1))
    val savedScreen = savedValues.getOrNull(0)
        ?.let { name -> AppScreen.entries.firstOrNull { it.name == name } }
        ?: AppScreen.ModeSelection
    val photoBackDestination = savedValues.getOrNull(3)
        ?.let { name -> AppScreen.entries.firstOrNull { it.name == name } }
        ?.takeIf { it == AppScreen.ModeSelection || it == AppScreen.Templates }
        ?: AppScreen.Templates
    return AppRoute(
        screen = when {
            savedScreen == AppScreen.VideoEditor && projectId != null -> AppScreen.VideoEditor
            savedScreen == AppScreen.VideoProjects -> AppScreen.VideoProjects
            savedScreen == AppScreen.Templates -> AppScreen.Templates
            savedScreen == AppScreen.Editor -> AppScreen.Editor
            savedScreen == AppScreen.SingleImage -> AppScreen.SingleImage
            savedScreen == AppScreen.PrivacyPolicy -> AppScreen.PrivacyPolicy
            else -> AppScreen.ModeSelection
        },
        activeVideoProjectId = projectId,
        createVideoProjectIfMissing = savedScreen == AppScreen.VideoEditor &&
            projectId != null && savedValues.getOrNull(2) == "true",
        photoBackDestination = photoBackDestination,
    )
}

private fun canonicalProjectIdOrNull(rawProjectId: String?): String? {
    val raw = rawProjectId ?: return null
    if (raw.isBlank() || raw != raw.trim()) return null
    val parsed = runCatching { UUID.fromString(raw) }.getOrNull() ?: return null
    return parsed.toString().takeIf { it == raw }
}

private fun AppRoute.validPhotoBackDestination(): AppScreen =
    photoBackDestination.takeIf { destination ->
        destination == AppScreen.ModeSelection || destination == AppScreen.Templates
    } ?: AppScreen.Templates

private val AppRouteSaver = listSaver<AppRoute, String>(
    save = { route -> route.savedValues() },
    restore = ::restoreAppRoute,
)

@Composable
fun SplitFrameApp(
    viewModel: MergeViewModel = koinViewModel(),
    videoProjectStore: VideoProjectStore = koinInject(),
    recentVideoProjectStore: RecentVideoProjectStore = koinInject(),
    adManager: SplitFrameAdManager = koinInject(),
    adsConfigRepository: AdsConfigRepository = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val singleImageViewModel = koinViewModel<SingleImageViewModel>()
    val singleImageState by singleImageViewModel.state.collectAsStateWithLifecycle()
    val adsEnabled by adsConfigRepository.isAdsEnabled.collectAsStateWithLifecycle()
    val privacyOptionsRequired by adsConfigRepository.privacyOptionsRequired.collectAsStateWithLifecycle()
    val fullScreenAdState by adManager.fullScreenAdState.collectAsStateWithLifecycle()
    val appOpenWindowActive by adManager.appOpenLoadingSurfaceVisible.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val embeddedAdsEligible = EmbeddedAdPolicy.isEligible(
        adsEnabled = adsEnabled,
        fullScreenAdState = fullScreenAdState,
        appOpenWindowActive = appOpenWindowActive,
        imeVisible = imeVisible,
    )
    val externalUiLauncher = LocalExternalUiLauncher.current
    var route by rememberSaveable(stateSaver = AppRouteSaver) { mutableStateOf(AppRoute()) }
    val screen = route.screen
    var skipNextPhotoExportWorkflowCount by rememberSaveable { mutableStateOf(false) }
    var foregroundPhotoExportActive by remember { mutableStateOf(false) }
    var foregroundResizeExportActive by remember { mutableStateOf(false) }
    val templateGridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    val templatePaletteSeed = rememberSaveable { Random.nextInt() }

    val storagePermissionDeniedMessage = stringResource(R.string.storage_permission_required)
    val videoProjectUnavailableMessage = stringResource(R.string.video_project_unavailable)
    val recentExportUnavailableMessage = stringResource(R.string.home_recent_export_unavailable)
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

    fun runWithLegacyStoragePermission(action: () -> Unit) {
        val needsPermission =
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingLegacyStorageAction = action
            externalUiLauncher.launch(ExternalUiReason.Permission) {
                legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } else {
            action()
        }
    }

    fun runVideoExportWithStoragePermission(action: () -> Unit) {
        runWithLegacyStoragePermission(action)
    }

    LaunchedEffect(state.isExporting, singleImageState.isProcessing) {
        adManager.updateForegroundExportInProgress(
            state.isExporting || singleImageState.isProcessing,
        )
    }
    DisposableEffect(adManager) {
        onDispose { adManager.updateForegroundExportInProgress(false) }
    }

    LaunchedEffect(state.isExporting, state.exportResult, screen) {
        if (state.isExporting && screen == AppScreen.Editor) {
            foregroundPhotoExportActive = true
        } else if (!state.isExporting && state.exportResult is ExportResult.Failure) {
            foregroundPhotoExportActive = false
        }
    }

    LaunchedEffect(
        singleImageState.isProcessing,
        singleImageState.result,
        singleImageState.error,
        screen,
    ) {
        if (singleImageState.isProcessing && screen == AppScreen.SingleImage) {
            foregroundResizeExportActive = true
        } else if (
            !singleImageState.isProcessing &&
            singleImageState.result == null &&
            singleImageState.error != null
        ) {
            foregroundResizeExportActive = false
        }
    }

    LaunchedEffect(state.exportResult) {
        val exportResult = state.exportResult ?: return@LaunchedEffect
        if (exportResult is ExportResult.Failure) {
            skipNextPhotoExportWorkflowCount = false
            foregroundPhotoExportActive = false
            return@LaunchedEffect
        }
        val result = exportResult as? ExportResult.Success ?: return@LaunchedEffect
        val shouldSkipWorkflowCount = skipNextPhotoExportWorkflowCount
        skipNextPhotoExportWorkflowCount = false
        val isNaturalBreak = foregroundPhotoExportActive && screen == AppScreen.Editor
        foregroundPhotoExportActive = false
        if (shouldSkipWorkflowCount) return@LaunchedEffect
        val completion = WorkflowCompletionEvents.photoCollage(result.savedUri)
            ?: return@LaunchedEffect
        adManager.recordWorkflowCompletion(
            activity = context.findActivity(),
            completion = completion,
            workflowInProgress = state.isExporting,
            naturalBreak = isNaturalBreak,
        )
    }

    LaunchedEffect(singleImageState.result?.savedUri) {
        val result = singleImageState.result ?: return@LaunchedEffect
        val completion = WorkflowCompletionEvents.imageResize(result.savedUri)
            ?: return@LaunchedEffect
        val isNaturalBreak = foregroundResizeExportActive && screen == AppScreen.SingleImage
        foregroundResizeExportActive = false
        adManager.recordWorkflowCompletion(
            activity = context.findActivity(),
            completion = completion,
            workflowInProgress = singleImageState.isProcessing,
            naturalBreak = isNaturalBreak,
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (screen) {
                AppScreen.ModeSelection -> {
                    val homeViewModel = koinViewModel<HomeDashboardViewModel>()
                    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
                    ModeSelectionScreen(
                        state = homeState,
                        onOpenPhotoCollage = {
                            route = route.copy(screen = AppScreen.Templates)
                        },
                        onOpenResizeImage = {
                            route = route.copy(
                                screen = AppScreen.SingleImage,
                                photoBackDestination = AppScreen.ModeSelection,
                            )
                        },
                        onOpenVideoProjects = {
                            route = route.copy(screen = AppScreen.VideoProjects)
                        },
                        onOpenVideoProject = { projectId ->
                            route = AppRoute(
                                screen = AppScreen.VideoEditor,
                                activeVideoProjectId = projectId,
                                createVideoProjectIfMissing = false,
                            )
                        },
                        onOpenLayout = { templateId ->
                            val accepted = viewModel.selectTemplateForEditing(templateId)
                            route = route.copy(
                                screen = if (accepted) AppScreen.Editor else AppScreen.Templates,
                                photoBackDestination = AppScreen.ModeSelection,
                            )
                        },
                        onOpenRecentPhotoExport = { savedUri ->
                            val viewerIntent = context.imageViewerIntent(savedUri)
                            if (viewerIntent == null) {
                                Toast.makeText(
                                    context,
                                    recentExportUnavailableMessage,
                                    Toast.LENGTH_LONG,
                                ).show()
                            } else {
                                externalUiLauncher.launch(ExternalUiReason.ExternalViewer) {
                                    context.startActivity(viewerIntent)
                                }
                            }
                        },
                        onOpenPrivacyPolicy = {
                            route = route.copy(screen = AppScreen.PrivacyPolicy)
                        },
                    )
                }
                AppScreen.Templates -> {
                    BackHandler { route = route.copy(screen = AppScreen.ModeSelection) }
                    TemplatePickerScreen(
                        state = state,
                        paletteSeed = templatePaletteSeed,
                        gridState = templateGridState,
                        onIntent = viewModel::process,
                        onOpenSingleImageTool = {
                            route = route.copy(
                                screen = AppScreen.SingleImage,
                                photoBackDestination = AppScreen.Templates,
                            )
                        },
                        onTemplateSelected = { templateId ->
                            if (viewModel.selectTemplateForEditing(templateId)) {
                                route = route.copy(
                                    screen = AppScreen.Editor,
                                    photoBackDestination = AppScreen.Templates,
                                )
                            }
                        },
                        showNativeAds = embeddedAdsEligible,
                    )
                }
                AppScreen.Editor -> {
                    val backDestination = route.validPhotoBackDestination()
                    BackHandler { route = route.copy(screen = backDestination) }
                    EditorScreen(
                        state = state,
                        onIntent = viewModel::process,
                        onBack = { route = route.copy(screen = backDestination) },
                        onRequestExport = ::runWithLegacyStoragePermission,
                        onExportForShare = { skipNextPhotoExportWorkflowCount = true },
                    )
                }
                AppScreen.SingleImage -> {
                    val backDestination = route.validPhotoBackDestination()
                    BackHandler { route = route.copy(screen = backDestination) }
                    SingleImageScreen(
                        state = singleImageState,
                        onIntent = { intent ->
                            if (intent == SingleImageIntent.Process) {
                                runWithLegacyStoragePermission { singleImageViewModel.process(intent) }
                            } else {
                                singleImageViewModel.process(intent)
                            }
                        },
                        onBack = { route = route.copy(screen = backDestination) },
                        onUseInCollage = { source ->
                            viewModel.process(MergeIntent.AssignImages(listOf(source)))
                            singleImageViewModel.process(SingleImageIntent.ClearResult)
                            route = route.copy(
                                screen = AppScreen.Templates,
                                photoBackDestination = AppScreen.Templates,
                            )
                        },
                    )
                }
                AppScreen.VideoProjects -> {
                    val recentProjectsFlow = remember(recentVideoProjectStore) {
                        recentVideoProjectStore.observeProjects()
                    }
                    val recentProjects by recentProjectsFlow.collectAsStateWithLifecycle(initialValue = null)
                    BackHandler { route = route.copy(screen = AppScreen.ModeSelection) }
                    VideoProjectsScreen(
                        projects = recentProjects,
                        onBack = { route = route.copy(screen = AppScreen.ModeSelection) },
                        onNewProject = {
                            route = AppRoute(
                                screen = AppScreen.VideoEditor,
                                activeVideoProjectId = UUID.randomUUID().toString(),
                                createVideoProjectIfMissing = true,
                            )
                        },
                        onOpenProject = { projectId ->
                            route = AppRoute(
                                screen = AppScreen.VideoEditor,
                                activeVideoProjectId = projectId,
                                createVideoProjectIfMissing = false,
                            )
                        },
                        onRenameProject = recentVideoProjectStore::rename,
                        onDuplicateProject = recentVideoProjectStore::duplicate,
                        onDeleteProject = recentVideoProjectStore::delete,
                        onUndoDelete = recentVideoProjectStore::undoDelete,
                        onFinalizeDelete = recentVideoProjectStore::finalizeDelete,
                        showNativeAds = embeddedAdsEligible,
                    )
                }
                AppScreen.VideoEditor -> {
                    val projectId = route.activeVideoProjectId
                    if (projectId == null) {
                        LaunchedEffect(Unit) { route = AppRoute() }
                    } else {
                        val sessionArgs = remember(projectId, route.createVideoProjectIfMissing) {
                            VideoProjectSessionArgs(
                                projectId = projectId,
                                createIfMissing = route.createVideoProjectIfMissing,
                            )
                        }
                        val videoViewModel = koinViewModel<VideoMergeViewModel>(
                            key = "video-project:$projectId",
                            parameters = { parametersOf(sessionArgs) },
                        )
                        val videoState by videoViewModel.state.collectAsStateWithLifecycle()
                        var foregroundVideoExportRequested by remember(projectId) {
                            mutableStateOf(false)
                        }
                        var foregroundVideoWorkId by remember(projectId) {
                            mutableStateOf<String?>(null)
                        }
                        LaunchedEffect(videoState.project?.id, videoState.isProjectPersisted) {
                            if (
                                videoState.project?.id == projectId &&
                                videoState.isProjectPersisted &&
                                route.createVideoProjectIfMissing
                            ) {
                                route = route.copy(createVideoProjectIfMissing = false)
                            }
                        }
                        LaunchedEffect(videoState.status) {
                            if (videoState.status == VideoEditorStatus.ProjectUnavailable) {
                                route = route.copy(
                                    screen = AppScreen.VideoProjects,
                                    createVideoProjectIfMissing = false,
                                )
                                Toast.makeText(context, videoProjectUnavailableMessage, Toast.LENGTH_LONG).show()
                            } else if (
                                videoState.status == VideoEditorStatus.ExportFailed ||
                                videoState.status == VideoEditorStatus.ExportCancelled ||
                                videoState.status == VideoEditorStatus.RecoverableMediaError
                            ) {
                                foregroundVideoExportRequested = false
                                foregroundVideoWorkId = null
                            }
                        }
                        LaunchedEffect(videoState.exportWorkId, videoState.isExporting) {
                            if (foregroundVideoExportRequested && videoState.isExporting) {
                                foregroundVideoWorkId = WorkflowCompletionEvents
                                    .videoComposition(videoState.exportWorkId)
                                    ?.stableWorkflowId
                                if (foregroundVideoWorkId != null) {
                                    foregroundVideoExportRequested = false
                                }
                            }
                        }
                        LaunchedEffect(videoState.exportWorkId, videoState.exportResult) {
                            if (videoState.exportResult !is ExportResult.Success) return@LaunchedEffect
                            val completion = WorkflowCompletionEvents.videoComposition(videoState.exportWorkId)
                                ?: return@LaunchedEffect
                            val workId = completion.stableWorkflowId
                            val isNaturalBreak = foregroundVideoWorkId == workId
                            adManager.recordWorkflowCompletion(
                                activity = context.findActivity(),
                                completion = completion,
                                workflowInProgress = videoState.isExporting,
                                naturalBreak = isNaturalBreak,
                            )
                            if (isNaturalBreak) foregroundVideoWorkId = null
                        }
                        BackHandler { route = route.copy(screen = AppScreen.VideoProjects) }
                        VideoEditorScreen(
                            state = videoState,
                            onIntent = { intent ->
                                if (
                                    intent == VideoMergeIntent.StartExport ||
                                    intent == VideoMergeIntent.RetryExport
                                ) {
                                    runVideoExportWithStoragePermission {
                                        foregroundVideoExportRequested = true
                                        videoViewModel.process(intent)
                                    }
                                } else {
                                    videoViewModel.process(intent)
                                }
                            },
                            onBack = { route = route.copy(screen = AppScreen.VideoProjects) },
                        )
                    }
                }
                AppScreen.PrivacyPolicy -> {
                    BackHandler { route = route.copy(screen = AppScreen.ModeSelection) }
                    PrivacyScreen(
                        onBack = { route = route.copy(screen = AppScreen.ModeSelection) },
                        showAdPrivacyOptions = privacyOptionsRequired,
                        onManageAdPrivacy = {
                            val activity = context.findActivity() ?: return@PrivacyScreen
                            adManager.clearCachedAds()
                            externalUiLauncher.launch(ExternalUiReason.Privacy) {
                                adsConfigRepository.showPrivacyOptions(activity)
                            }
                        },
                    )
                }
            }
        }
        val embeddedAdSurface = when (screen) {
            AppScreen.ModeSelection -> EmbeddedAdSurface.Home
            AppScreen.Templates -> EmbeddedAdSurface.TemplateDiscovery
            AppScreen.VideoProjects -> EmbeddedAdSurface.RecentProjects
            AppScreen.Editor -> EmbeddedAdSurface.Editor
            AppScreen.SingleImage -> EmbeddedAdSurface.Resize
            AppScreen.VideoEditor -> EmbeddedAdSurface.VideoEditor
            AppScreen.PrivacyPolicy -> EmbeddedAdSurface.Privacy
        }
        if (EmbeddedAdPolicy.shouldShowBanner(embeddedAdSurface, embeddedAdsEligible)) {
            AppBannerAd(modifier = Modifier.navigationBarsPadding())
        }
    }

}

@Composable
private fun AppBannerAd(
    modifier: Modifier = Modifier,
) {
    var loadState by remember { mutableStateOf(BannerAdLoadState.Loading) }
    if (loadState == BannerAdLoadState.Failed) return
    val loaded = loadState == BannerAdLoadState.Loaded
    AdContainer(
        modifier = if (loaded) {
            modifier
        } else {
            modifier
                .alpha(0f)
                .clearAndSetSemantics { }
        },
    ) {
        BannerAd(
            modifier = Modifier.fillMaxWidth(),
            onLoadStateChanged = { loadState = it },
        )
    }
}

private fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun Context.imageViewerIntent(savedUri: String): Intent? {
    val uri = runCatching { Uri.parse(savedUri) }
        .getOrNull()
        ?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }
        ?: return null
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "image/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return intent.takeIf { it.resolveActivity(packageManager) != null }
}
