package com.example.splitframe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.splitframe.presentation.SplitFrameApp
import com.example.splitframe.ui.theme.SplitFrameTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SplitFrameTheme {
                SplitFrameApp()
            }
        }
    }
}
