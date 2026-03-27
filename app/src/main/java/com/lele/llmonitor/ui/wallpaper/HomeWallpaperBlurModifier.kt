package com.lele.llmonitor.ui.wallpaper

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lele.llmonitor.data.resolveHomeWallpaperBlurRadiusPx
import com.lele.llmonitor.data.resolveHomeWallpaperBlurRadiusDp

@Composable
internal fun Modifier.homeWallpaperBlur(
    wallpaperBlur: Float,
    radiusScale: Float = 1f
): Modifier {
    val resolvedRadiusScale = radiusScale.coerceAtLeast(0f)
    val blurRadiusDp = resolveHomeWallpaperBlurRadiusDp(wallpaperBlur) * resolvedRadiusScale
    if (blurRadiusDp <= 0.01f) return this

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val density = LocalDensity.current.density
        val blurRadiusPx = remember(wallpaperBlur, density, resolvedRadiusScale) {
            resolveHomeWallpaperBlurRadiusPx(
                wallpaperBlur = wallpaperBlur,
                displayDensity = density
            ) * resolvedRadiusScale
        }
        this.graphicsLayer {
            renderEffect = android.graphics.RenderEffect.createBlurEffect(
                blurRadiusPx,
                blurRadiusPx,
                android.graphics.Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        }
    } else {
        this.blur(blurRadiusDp.dp)
    }
}
