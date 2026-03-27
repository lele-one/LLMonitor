package com.lele.llmonitor.data

import kotlin.math.roundToInt

internal const val DEFAULT_HOME_WALLPAPER_ALPHA = 0.35f
internal const val DEFAULT_HOME_WALLPAPER_BLUR = 0f
internal const val HOME_WALLPAPER_MAX_BLUR_DP = 20f
private const val HOME_WALLPAPER_STARTUP_RENDER_SPEC_VERSION = 2

internal fun resolveHomeWallpaperStartupPreviewRenderSpec(
    backgroundArgb: Int,
    wallpaperAlpha: Float,
    wallpaperBlur: Float,
    displayDensity: Float
): String {
    return buildString {
        append('v')
        append(HOME_WALLPAPER_STARTUP_RENDER_SPEC_VERSION)
        append(':')
        append(backgroundArgb)
        append(':')
        append((wallpaperAlpha.coerceIn(0f, 1f) * 1000f).roundToInt())
        append(':')
        append((resolveHomeWallpaperBlurRadiusPx(wallpaperBlur, displayDensity) * 100f).roundToInt())
    }
}

internal fun resolveHomeWallpaperBlurRadiusDp(
    wallpaperBlur: Float
): Float = wallpaperBlur.coerceIn(0f, 1f) * HOME_WALLPAPER_MAX_BLUR_DP

internal fun resolveHomeWallpaperBlurRadiusPx(
    wallpaperBlur: Float,
    displayDensity: Float
): Float = resolveHomeWallpaperBlurRadiusDp(wallpaperBlur) * displayDensity.coerceAtLeast(0f)
