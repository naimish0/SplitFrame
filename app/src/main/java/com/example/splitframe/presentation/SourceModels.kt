package com.example.splitframe.presentation

import android.net.Uri
import com.example.splitframe.domain.ImageSource

fun ImageSource.coilModel(): Any =
    when (this) {
        is ImageSource.LocalUri -> Uri.parse(uri)
    }
