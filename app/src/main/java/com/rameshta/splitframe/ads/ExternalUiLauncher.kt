package com.rameshta.splitframe.ads

import androidx.compose.runtime.staticCompositionLocalOf

internal fun interface ExternalUiLauncher {
    fun launch(reason: ExternalUiReason, action: () -> Unit)
}

internal val LocalExternalUiLauncher = staticCompositionLocalOf<ExternalUiLauncher> {
    ExternalUiLauncher { _, action -> action() }
}
