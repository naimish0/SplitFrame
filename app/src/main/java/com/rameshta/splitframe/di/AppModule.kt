package com.rameshta.splitframe.di

import androidx.room.Room
import androidx.work.WorkManager
import com.rameshta.splitframe.R
import com.rameshta.splitframe.ads.AdsConfigRepository
import com.rameshta.splitframe.ads.SharedPreferencesWorkflowInterstitialStateStore
import com.rameshta.splitframe.ads.SplitFrameAdManager
import com.rameshta.splitframe.ads.WorkflowInterstitialCoordinator
import com.rameshta.splitframe.ads.WorkflowInterstitialStateStore
import com.rameshta.splitframe.ads.WorkflowInterstitialTracker
import com.rameshta.splitframe.data.ProjectStore
import com.rameshta.splitframe.data.ContentResolverMediaUriAccess
import com.rameshta.splitframe.data.AndroidDeviceWallpaperDimensionsProvider
import com.rameshta.splitframe.data.DeviceWallpaperDimensionsProvider
import com.rameshta.splitframe.data.RecentVideoProjectStore
import com.rameshta.splitframe.data.VideoProjectStore
import com.rameshta.splitframe.data.local.SplitFrameDatabase
import com.rameshta.splitframe.domain.TemplateRepository
import com.rameshta.splitframe.export.ImageExportRepository
import com.rameshta.splitframe.export.ContentResolverOwnedPublicationAccess
import com.rameshta.splitframe.export.ExportPublicationReconciler
import com.rameshta.splitframe.export.ImageSourceReader
import com.rameshta.splitframe.export.MixedMediaMetadataReader
import com.rameshta.splitframe.export.SingleImageProcessingRepository
import com.rameshta.splitframe.export.SharedPreferencesExportPublicationJournal
import com.rameshta.splitframe.export.VideoExportRepository
import com.rameshta.splitframe.export.VideoExportRecoveryCoordinator
import com.rameshta.splitframe.export.VideoMetadataReader
import com.rameshta.splitframe.presentation.home.HomeDashboardViewModel
import com.rameshta.splitframe.presentation.merge.MergeViewModel
import com.rameshta.splitframe.presentation.single.SingleImageViewModel
import com.rameshta.splitframe.presentation.video.VideoMergeViewModel
import com.rameshta.splitframe.presentation.video.VideoProjectSessionArgs
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single {
        Room.databaseBuilder(
            androidApplication(),
            SplitFrameDatabase::class.java,
            "splitframe.db",
        )
            .addMigrations(SplitFrameDatabase.Migration1To2)
            .addMigrations(SplitFrameDatabase.Migration2To3)
            .addMigrations(SplitFrameDatabase.Migration3To4)
            .addMigrations(SplitFrameDatabase.Migration4To5)
            .addMigrations(SplitFrameDatabase.Migration5To6)
            .build()
    }
    single { get<SplitFrameDatabase>().preferenceDao() }
    single { get<SplitFrameDatabase>().exportHistoryDao() }
    single { get<SplitFrameDatabase>().favoriteTemplateDao() }
    single { get<SplitFrameDatabase>().recentLayoutDao() }
    single { get<SplitFrameDatabase>().videoProjectDao() }
    single { get<SplitFrameDatabase>().videoExportWorkDao() }
    single { get<SplitFrameDatabase>().recentProjectDao() }
    single { ProjectStore(get(), get(), get(), get()) }
    single<DeviceWallpaperDimensionsProvider> { AndroidDeviceWallpaperDimensionsProvider(androidApplication()) }
    single {
        VideoProjectStore(
            projectDao = get(),
            exportWorkDao = get(),
            recentProjectDao = get(),
            defaultProjectName = { androidApplication().getString(R.string.video_project_default_name) },
        )
    }
    single { ContentResolverMediaUriAccess(androidApplication().contentResolver) }
    single { RecentVideoProjectStore(get(), get(), get<ContentResolverMediaUriAccess>()) }
    single { TemplateRepository() }
    single { ImageSourceReader(androidApplication().contentResolver) }
    single { SharedPreferencesExportPublicationJournal(androidApplication()) }
    single { ContentResolverOwnedPublicationAccess(androidApplication().contentResolver) }
    single {
        ExportPublicationReconciler(
            journal = get<SharedPreferencesExportPublicationJournal>(),
            publicationAccess = get<ContentResolverOwnedPublicationAccess>(),
            videoExportDirectory = java.io.File(androidApplication().cacheDir, "video_exports"),
        )
    }
    single { ImageExportRepository(androidApplication(), get(), get<SharedPreferencesExportPublicationJournal>()) }
    single {
        SingleImageProcessingRepository(
            androidApplication(),
            get(),
            get<SharedPreferencesExportPublicationJournal>(),
        )
    }
    single { VideoMetadataReader(androidApplication().contentResolver) }
    single { MixedMediaMetadataReader(androidApplication().contentResolver, get(), get()) }
    single { VideoExportRepository(androidApplication(), get<SharedPreferencesExportPublicationJournal>()) }
    single { WorkManager.getInstance(androidApplication()) }
    single { VideoExportRecoveryCoordinator(get(), get()) }
    single { AdsConfigRepository(androidApplication()) }
    single<WorkflowInterstitialStateStore> {
        SharedPreferencesWorkflowInterstitialStateStore(androidApplication())
    }
    single { WorkflowInterstitialTracker(get()) }
    single { WorkflowInterstitialCoordinator(get()) }
    single { SplitFrameAdManager(androidApplication(), get(), get()) }
    viewModel { HomeDashboardViewModel(get(), get(), get(), get()) }
    viewModel { MergeViewModel(get(), get(), get(), get(), get()) }
    viewModel { SingleImageViewModel(get(), get(), get(), get()) }
    viewModel { (sessionArgs: VideoProjectSessionArgs) ->
        VideoMergeViewModel(get(), get(), get(), sessionArgs)
    }
}
