package com.lele.llmonitor.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.toArgb
import com.lele.llmonitor.ui.theme.ThemePalettePreset
import com.lele.llmonitor.ui.theme.resolveAppColorScheme

private const val KEY_LAUNCH_BACKGROUND_ARGB = "launch_background_argb"
private const val KEY_LAUNCH_THEME_MODE = "launch_theme_mode"
private const val KEY_LAUNCH_THEME_PALETTE = "launch_theme_palette"
private const val KEY_LAUNCH_WALLPAPER_ENABLED = "launch_wallpaper_enabled"
private const val KEY_LAUNCH_WALLPAPER_ALPHA = "launch_wallpaper_alpha"
private const val KEY_LAUNCH_WALLPAPER_BLUR = "launch_wallpaper_blur"
private const val KEY_LAUNCH_WALLPAPER_VERSION = "launch_wallpaper_version"
private const val KEY_LAUNCH_STARTUP_PREVIEW_VERSION = "launch_startup_preview_version"
private const val KEY_LAUNCH_STARTUP_PREVIEW_RENDER_SPEC = "launch_startup_preview_render_spec"

data class LaunchAppearanceSnapshot(
    val backgroundArgb: Int,
    val themeMode: Int,
    val themePalettePreset: ThemePalettePreset,
    val wallpaperEnabled: Boolean,
    val wallpaperAlpha: Float,
    val wallpaperBlur: Float,
    val wallpaperVersion: Long,
    val startupPreviewVersion: Long,
    val startupPreviewRenderSpec: String
)

internal fun buildLaunchAppearanceSnapshot(
    context: Context,
    themeMode: Int,
    themePalettePreset: ThemePalettePreset,
    wallpaperEnabled: Boolean,
    wallpaperAlpha: Float,
    wallpaperBlur: Float,
    wallpaperVersion: Long = 0L,
    startupPreviewVersion: Long = 0L,
    startupPreviewRenderSpec: String = ""
): LaunchAppearanceSnapshot {
    val darkTheme = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemDarkModeEnabled(context)
    }
    val backgroundArgb = resolveAppColorScheme(
        context = context,
        darkTheme = darkTheme,
        themePalettePreset = themePalettePreset
    ).background.toArgb()
    return LaunchAppearanceSnapshot(
        backgroundArgb = backgroundArgb,
        themeMode = themeMode,
        themePalettePreset = themePalettePreset,
        wallpaperEnabled = wallpaperEnabled,
        wallpaperAlpha = wallpaperAlpha.coerceIn(0f, 1f),
        wallpaperBlur = wallpaperBlur.coerceIn(0f, 1f),
        wallpaperVersion = wallpaperVersion.coerceAtLeast(0L),
        startupPreviewVersion = startupPreviewVersion.coerceAtLeast(0L),
        startupPreviewRenderSpec = startupPreviewRenderSpec
    )
}

internal fun readLaunchAppearanceSnapshot(
    preferences: SharedPreferences
): LaunchAppearanceSnapshot? {
    if (!preferences.contains(KEY_LAUNCH_BACKGROUND_ARGB)) {
        return null
    }
    return LaunchAppearanceSnapshot(
        backgroundArgb = preferences.getInt(KEY_LAUNCH_BACKGROUND_ARGB, 0),
        themeMode = preferences.getInt(KEY_LAUNCH_THEME_MODE, DEFAULT_THEME_MODE).coerceIn(0, 2),
        themePalettePreset = ThemePalettePreset.fromPreferenceValue(
            preferences.getString(KEY_LAUNCH_THEME_PALETTE, ThemePalettePreset.default.preferenceValue)
        ),
        wallpaperEnabled = preferences.getBoolean(KEY_LAUNCH_WALLPAPER_ENABLED, false),
        wallpaperAlpha = preferences.getFloat(
            KEY_LAUNCH_WALLPAPER_ALPHA,
            DEFAULT_HOME_WALLPAPER_ALPHA
        ).coerceIn(0f, 1f),
        wallpaperBlur = preferences.getFloat(
            KEY_LAUNCH_WALLPAPER_BLUR,
            DEFAULT_HOME_WALLPAPER_BLUR
        ).coerceIn(0f, 1f),
        wallpaperVersion = preferences.getLong(KEY_LAUNCH_WALLPAPER_VERSION, 0L).coerceAtLeast(0L),
        startupPreviewVersion = preferences.getLong(
            KEY_LAUNCH_STARTUP_PREVIEW_VERSION,
            0L
        ).coerceAtLeast(0L),
        startupPreviewRenderSpec = preferences.getString(
            KEY_LAUNCH_STARTUP_PREVIEW_RENDER_SPEC,
            ""
        ).orEmpty()
    )
}

internal fun persistLaunchAppearanceSnapshot(
    preferences: SharedPreferences,
    snapshot: LaunchAppearanceSnapshot
) {
    preferences.edit()
        .putInt(KEY_LAUNCH_BACKGROUND_ARGB, snapshot.backgroundArgb)
        .putInt(KEY_LAUNCH_THEME_MODE, snapshot.themeMode)
        .putString(KEY_LAUNCH_THEME_PALETTE, snapshot.themePalettePreset.preferenceValue)
        .putBoolean(KEY_LAUNCH_WALLPAPER_ENABLED, snapshot.wallpaperEnabled)
        .putFloat(KEY_LAUNCH_WALLPAPER_ALPHA, snapshot.wallpaperAlpha)
        .putFloat(KEY_LAUNCH_WALLPAPER_BLUR, snapshot.wallpaperBlur)
        .putLong(KEY_LAUNCH_WALLPAPER_VERSION, snapshot.wallpaperVersion)
        .putLong(KEY_LAUNCH_STARTUP_PREVIEW_VERSION, snapshot.startupPreviewVersion)
        .putString(KEY_LAUNCH_STARTUP_PREVIEW_RENDER_SPEC, snapshot.startupPreviewRenderSpec)
        .commit()
}
