package com.rameshta.splitframe.presentation

import android.net.Uri
import com.rameshta.splitframe.domain.ImageSource

fun ImageSource.coilModel(): Any =
    when (this) {
        is ImageSource.LocalUri -> Uri.parse(uri)
    }
