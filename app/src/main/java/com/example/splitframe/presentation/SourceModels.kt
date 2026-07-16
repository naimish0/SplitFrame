package com.example.splitframe.presentation

import android.net.Uri
import com.example.splitframe.domain.ImageSource
import java.io.File

fun ImageSource.coilModel(): Any =
    when (this) {
        is ImageSource.Enhanced -> File(cachedEnhancedPath)
        is ImageSource.LocalUri -> Uri.parse(uri)
    }

fun ImageSource.originalCoilModel(): Any =
    when (this) {
        is ImageSource.Enhanced -> Uri.parse(originalUri)
        is ImageSource.LocalUri -> Uri.parse(uri)
    }
