package com.example.splitframe

import android.app.Application
import com.example.splitframe.di.appModule
import com.google.android.gms.ads.MobileAds
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SplitFrameApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SplitFrameApplication)
            modules(appModule)
        }
        MobileAds.initialize(this)
    }
}
