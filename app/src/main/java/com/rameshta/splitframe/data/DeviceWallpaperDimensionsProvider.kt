package com.rameshta.splitframe.data

import android.app.WallpaperManager
import android.content.Context
import com.rameshta.splitframe.domain.ImageDimensions

fun interface DeviceWallpaperDimensionsProvider {
    fun dimensions(): ImageDimensions?
}

class AndroidDeviceWallpaperDimensionsProvider(
    private val context: Context,
) : DeviceWallpaperDimensionsProvider {
    override fun dimensions(): ImageDimensions? = runCatching {
        val wallpaperManager = WallpaperManager.getInstance(context)
        val displayMetrics = context.resources.displayMetrics
        val width = wallpaperManager.desiredMinimumWidth.takeIf { it > 0 }
            ?: displayMetrics.widthPixels
        val height = wallpaperManager.desiredMinimumHeight.takeIf { it > 0 }
            ?: displayMetrics.heightPixels
        if (width > 0 && height > 0) ImageDimensions(width, height) else null
    }.getOrNull()
}
