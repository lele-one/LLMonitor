package com.lele.llmonitor.data

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

enum class FollowSystemAppIconMode(
    val preferenceValue: Int
) {
    LIGHT(1),
    DARK(2);

    companion object {
        val default: FollowSystemAppIconMode = DARK

        fun fromPreferenceValue(value: Int?): FollowSystemAppIconMode {
            return when (value) {
                LIGHT.preferenceValue -> LIGHT
                DARK.preferenceValue -> DARK
                else -> default
            }
        }
    }
}

internal fun isSystemDarkModeEnabled(context: Context): Boolean {
    val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return mode == Configuration.UI_MODE_NIGHT_YES
}

internal fun resolveStoredFollowSystemAppIconMode(
    preferences: SharedPreferences,
    fallbackSystemDarkModeEnabled: Boolean
): FollowSystemAppIconMode {
    if (preferences.contains(FOLLOW_SYSTEM_APP_ICON_MODE_KEY)) {
        return FollowSystemAppIconMode.fromPreferenceValue(
            preferences.getInt(FOLLOW_SYSTEM_APP_ICON_MODE_KEY, FollowSystemAppIconMode.default.preferenceValue)
        )
    }
    return if (fallbackSystemDarkModeEnabled) {
        FollowSystemAppIconMode.DARK
    } else {
        FollowSystemAppIconMode.LIGHT
    }
}

internal fun resolveAppIconDarkModeEnabled(
    themeMode: Int,
    followSystemAppIconMode: FollowSystemAppIconMode
): Boolean {
    return when (themeMode) {
        1 -> false
        2 -> true
        else -> followSystemAppIconMode == FollowSystemAppIconMode.DARK
    }
}

internal fun resolveFollowSystemAppIconModeForThemeModeChange(
    currentThemeMode: Int,
    currentFollowSystemAppIconMode: FollowSystemAppIconMode,
    targetThemeMode: Int
): FollowSystemAppIconMode {
    return when {
        targetThemeMode != 0 -> currentFollowSystemAppIconMode
        currentThemeMode == 2 -> FollowSystemAppIconMode.DARK
        currentThemeMode == 1 -> FollowSystemAppIconMode.LIGHT
        else -> currentFollowSystemAppIconMode
    }
}

internal fun shouldCloseTaskAfterThemeModeChange(
    currentThemeMode: Int,
    currentFollowSystemAppIconMode: FollowSystemAppIconMode,
    targetThemeMode: Int
): Boolean {
    val currentIconUsesDarkVariant = resolveAppIconDarkModeEnabled(
        themeMode = currentThemeMode,
        followSystemAppIconMode = currentFollowSystemAppIconMode
    )
    val targetFollowSystemAppIconMode = resolveFollowSystemAppIconModeForThemeModeChange(
        currentThemeMode = currentThemeMode,
        currentFollowSystemAppIconMode = currentFollowSystemAppIconMode,
        targetThemeMode = targetThemeMode
    )
    val targetIconUsesDarkVariant = resolveAppIconDarkModeEnabled(
        themeMode = targetThemeMode,
        followSystemAppIconMode = targetFollowSystemAppIconMode
    )
    return currentIconUsesDarkVariant != targetIconUsesDarkVariant
}

internal fun resolveAppCompatNightMode(themeMode: Int): Int {
    return when (themeMode) {
        1 -> AppCompatDelegate.MODE_NIGHT_NO
        2 -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
}

internal fun applyAppNightMode(themeMode: Int) {
    AppCompatDelegate.setDefaultNightMode(resolveAppCompatNightMode(themeMode))
}

const val DEFAULT_THEME_MODE: Int = 2
const val FOLLOW_SYSTEM_APP_ICON_MODE_KEY: String = "follow_system_app_icon_mode"
