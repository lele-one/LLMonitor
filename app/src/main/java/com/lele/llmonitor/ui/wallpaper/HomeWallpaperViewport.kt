package com.lele.llmonitor.ui.wallpaper

import android.app.Activity
import android.content.Context
import android.graphics.Point
import android.os.Build
import android.util.DisplayMetrics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun rememberHomeWallpaperViewportSize(): Size {
    val context = LocalContext.current
    return remember(context) {
        context.resolveHomeWallpaperViewportSize()
    }
}

internal fun Context.resolveHomeWallpaperViewportSize(): Size {
    val activity = this as? Activity
    if (activity != null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = activity.windowManager.currentWindowMetrics.bounds
            if (bounds.width() > 0 && bounds.height() > 0) {
                return Size(bounds.width().toFloat(), bounds.height().toFloat())
            }
        } else {
            @Suppress("DEPRECATION")
            val display = activity.windowManager.defaultDisplay
            val point = Point()
            @Suppress("DEPRECATION")
            display.getRealSize(point)
            if (point.x > 0 && point.y > 0) {
                return Size(point.x.toFloat(), point.y.toFloat())
            }
        }
    }

    val metrics: DisplayMetrics = resources.displayMetrics
    return Size(metrics.widthPixels.toFloat(), metrics.heightPixels.toFloat())
}
