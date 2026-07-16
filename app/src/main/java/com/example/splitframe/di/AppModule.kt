package com.example.splitframe.di

import androidx.room.Room
import com.example.splitframe.ads.AdsConfigRepository
import com.example.splitframe.ads.SplitFrameAdManager
import com.example.splitframe.data.ProjectStore
import com.example.splitframe.data.local.SplitFrameDatabase
import com.example.splitframe.domain.TemplateRepository
import com.example.splitframe.export.ImageExportRepository
import com.example.splitframe.export.ImageSourceReader
import com.example.splitframe.export.SingleImageEnhancementRepository
import com.example.splitframe.ml.SuperResolutionProcessor
import com.example.splitframe.presentation.merge.MergeViewModel
import com.example.splitframe.presentation.single.SingleImageViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single {
        Room.databaseBuilder(
            androidApplication(),
            SplitFrameDatabase::class.java,
            "splitframe.db",
        ).build()
    }
    single { get<SplitFrameDatabase>().preferenceDao() }
    single { get<SplitFrameDatabase>().exportHistoryDao() }
    single { get<SplitFrameDatabase>().favoriteTemplateDao() }
    single { ProjectStore(get(), get(), get()) }
    single { TemplateRepository() }
    single { ImageSourceReader(androidApplication().contentResolver) }
    single { ImageExportRepository(androidApplication(), get()) }
    single { SingleImageEnhancementRepository(androidApplication(), get()) }
    single { SuperResolutionProcessor(androidApplication()) }
    single { AdsConfigRepository() }
    single { SplitFrameAdManager(androidApplication()) }
    viewModel { MergeViewModel(get(), get(), get(), get(), get()) }
    viewModel { SingleImageViewModel(get()) }
}
