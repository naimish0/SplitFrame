package com.example.splitframe.di

import androidx.room.Room
import androidx.work.WorkManager
import com.example.splitframe.ads.AdsConfigRepository
import com.example.splitframe.ads.SplitFrameAdManager
import com.example.splitframe.data.ProjectStore
import com.example.splitframe.data.VideoProjectStore
import com.example.splitframe.data.local.SplitFrameDatabase
import com.example.splitframe.domain.TemplateRepository
import com.example.splitframe.export.ImageExportRepository
import com.example.splitframe.export.ImageSourceReader
import com.example.splitframe.export.MixedMediaMetadataReader
import com.example.splitframe.export.SingleImageProcessingRepository
import com.example.splitframe.export.VideoExportRepository
import com.example.splitframe.export.VideoMetadataReader
import com.example.splitframe.presentation.merge.MergeViewModel
import com.example.splitframe.presentation.single.SingleImageViewModel
import com.example.splitframe.presentation.video.VideoMergeViewModel
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
            .build()
    }
    single { get<SplitFrameDatabase>().preferenceDao() }
    single { get<SplitFrameDatabase>().exportHistoryDao() }
    single { get<SplitFrameDatabase>().favoriteTemplateDao() }
    single { get<SplitFrameDatabase>().videoProjectDao() }
    single { get<SplitFrameDatabase>().videoExportWorkDao() }
    single { ProjectStore(get(), get(), get()) }
    single { VideoProjectStore(get(), get()) }
    single { TemplateRepository() }
    single { ImageSourceReader(androidApplication().contentResolver) }
    single { ImageExportRepository(androidApplication(), get()) }
    single { SingleImageProcessingRepository(androidApplication(), get()) }
    single { VideoMetadataReader(androidApplication().contentResolver) }
    single { MixedMediaMetadataReader(androidApplication().contentResolver, get(), get()) }
    single { VideoExportRepository(androidApplication()) }
    single { WorkManager.getInstance(androidApplication()) }
    single { AdsConfigRepository() }
    single { SplitFrameAdManager(androidApplication()) }
    viewModel { MergeViewModel(get(), get(), get(), get()) }
    viewModel { SingleImageViewModel(get()) }
    viewModel { VideoMergeViewModel(get(), get(), get()) }
}
