package com.example.splitframe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.splitframe.ads.AdsConfigRepository
import com.example.splitframe.presentation.SplitFrameApp
import com.example.splitframe.ui.theme.SplitFrameTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val adsConfigRepository: AdsConfigRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null || !adsConfigRepository.hasRequestedConsentInfoThisProcess) {
            adsConfigRepository.gatherConsent(this)
        }
        setContent {
            SplitFrameTheme {
                SplitFrameApp()
            }
        }
    }
}
