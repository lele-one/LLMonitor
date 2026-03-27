package com.lele.llmonitor.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.AnyRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.lele.llmonitor.R
import com.lele.llmonitor.ui.theme.ThemePalettePreset

private enum class AppIconPalette(
    val preferenceValue: String,
    @StringRes val accentNameRes: Int,
    val previewBackgroundColor: Color,
    val previewDarkBackgroundColor: Color,
    val previewRingColor: Color,
    val previewGlyphColor: Color,
    @DrawableRes val previewForegroundRes: Int,
    @DrawableRes val previewDarkForegroundRes: Int,
    @AnyRes val previewAdaptiveIconRes: Int,
    val aliasClassName: String,
    val darkAliasClassName: String? = null
) {
    OCEAN(
        preferenceValue = "a",
        accentNameRes = R.string.app_icon_palette_ocean,
        previewBackgroundColor = Color(0xFFBFE9FF),
        previewDarkBackgroundColor = Color(0xFF0F4256),
        previewRingColor = Color(0xFF176B87),
        previewGlyphColor = Color(0xFF001F2A),
        previewForegroundRes = R.drawable.ic_launcher_palette_a_foreground,
        previewDarkForegroundRes = R.drawable.ic_launcher_palette_a_dark_foreground,
        previewAdaptiveIconRes = R.mipmap.ic_launcher_a,
        aliasClassName = "com.lele.llmonitor.LauncherAliasA",
        darkAliasClassName = "com.lele.llmonitor.LauncherAliasADark"
    ),
    FOREST(
        preferenceValue = "b",
        accentNameRes = R.string.app_icon_palette_forest,
        previewBackgroundColor = Color(0xFFB9F2D1),
        previewDarkBackgroundColor = Color(0xFF184D36),
        previewRingColor = Color(0xFF2D7A55),
        previewGlyphColor = Color(0xFF002113),
        previewForegroundRes = R.drawable.ic_launcher_palette_b_foreground,
        previewDarkForegroundRes = R.drawable.ic_launcher_palette_b_dark_foreground,
        previewAdaptiveIconRes = R.mipmap.ic_launcher_b,
        aliasClassName = "com.lele.llmonitor.LauncherAliasB",
        darkAliasClassName = "com.lele.llmonitor.LauncherAliasBDark"
    ),
    SUNSET(
        preferenceValue = "c",
        accentNameRes = R.string.app_icon_palette_sunset,
        previewBackgroundColor = Color(0xFFFFDCC4),
        previewDarkBackgroundColor = Color(0xFF703500),
        previewRingColor = Color(0xFF9D4C16),
        previewGlyphColor = Color(0xFF2F1400),
        previewForegroundRes = R.drawable.ic_launcher_palette_c_foreground,
        previewDarkForegroundRes = R.drawable.ic_launcher_palette_c_dark_foreground,
        previewAdaptiveIconRes = R.mipmap.ic_launcher_c,
        aliasClassName = "com.lele.llmonitor.LauncherAliasC",
        darkAliasClassName = "com.lele.llmonitor.LauncherAliasCDark"
    ),
    BLOSSOM(
        preferenceValue = "d",
        accentNameRes = R.string.app_icon_palette_blossom,
        previewBackgroundColor = Color(0xFFFFD9E7),
        previewDarkBackgroundColor = Color(0xFF7A2851),
        previewRingColor = Color(0xFFA04572),
        previewGlyphColor = Color(0xFF35001E),
        previewForegroundRes = R.drawable.ic_launcher_palette_d_foreground,
        previewDarkForegroundRes = R.drawable.ic_launcher_palette_d_dark_foreground,
        previewAdaptiveIconRes = R.mipmap.ic_launcher_d,
        aliasClassName = "com.lele.llmonitor.LauncherAliasD",
        darkAliasClassName = "com.lele.llmonitor.LauncherAliasDDark"
    ),
    JIZI(
        preferenceValue = "e",
        accentNameRes = R.string.app_icon_palette_jizi,
        previewBackgroundColor = Color(0xFFEBDDFF),
        previewDarkBackgroundColor = Color(0xFF4C3978),
        previewRingColor = Color(0xFF7252B8),
        previewGlyphColor = Color(0xFF28104D),
        previewForegroundRes = R.drawable.ic_launcher_palette_e_foreground,
        previewDarkForegroundRes = R.drawable.ic_launcher_palette_e_dark_foreground,
        previewAdaptiveIconRes = R.mipmap.ic_launcher_e,
        aliasClassName = "com.lele.llmonitor.LauncherAliasE",
        darkAliasClassName = "com.lele.llmonitor.LauncherAliasEDark"
    ),
    DYNAMIC(
        preferenceValue = "f",
        accentNameRes = R.string.app_icon_palette_dynamic_multicolor,
        previewBackgroundColor = Color(0xFFF3F6FB),
        previewDarkBackgroundColor = Color(0xFF172033),
        previewRingColor = Color(0xFF4285F4),
        previewGlyphColor = Color(0xFF4285F4),
        previewForegroundRes = R.drawable.ic_launcher_palette_f_foreground,
        previewDarkForegroundRes = R.drawable.ic_launcher_palette_f_dark_foreground,
        previewAdaptiveIconRes = R.mipmap.ic_launcher_f,
        aliasClassName = "com.lele.llmonitor.LauncherAliasF",
        darkAliasClassName = "com.lele.llmonitor.LauncherAliasFDark"
    );

    fun resolveAliasClassName(darkModeEnabled: Boolean): String {
        return if (darkModeEnabled) darkAliasClassName ?: aliasClassName else aliasClassName
    }
}

private fun ThemePalettePreset.toAppIconPalette(): AppIconPalette {
    return when (this) {
        ThemePalettePreset.DYNAMIC -> AppIconPalette.DYNAMIC
        ThemePalettePreset.OCEAN -> AppIconPalette.OCEAN
        ThemePalettePreset.FOREST -> AppIconPalette.FOREST
        ThemePalettePreset.SUNSET -> AppIconPalette.SUNSET
        ThemePalettePreset.BLOSSOM -> AppIconPalette.BLOSSOM
        ThemePalettePreset.JIZI -> AppIconPalette.JIZI
    }
}

private fun applyAppIconPalette(
    context: Context,
    palette: AppIconPalette,
    darkModeEnabled: Boolean,
    preservedAliasClassNames: Set<String> = emptySet(),
    disableInactiveAliases: Boolean = true
) {
    val packageManager = context.packageManager
    val enabledAliasClassName = palette.resolveAliasClassName(darkModeEnabled)
    AppIconPalette.entries.forEach { option ->
        listOfNotNull(option.aliasClassName, option.darkAliasClassName).forEach { aliasClassName ->
            if (!disableInactiveAliases && aliasClassName != enabledAliasClassName) {
                return@forEach
            }
            val targetState = if (aliasClassName == enabledAliasClassName) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            val componentName = ComponentName(context, aliasClassName)
            if (packageManager.getComponentEnabledSetting(componentName) == targetState) {
                return@forEach
            }
            if (
                targetState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                aliasClassName in preservedAliasClassNames
            ) {
                return@forEach
            }
            packageManager.setComponentEnabledSetting(
                componentName,
                targetState,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}

object AppIconPaletteManager {
    fun sync(
        context: Context,
        themeMode: Int,
        themePalettePreset: ThemePalettePreset,
        followSystemAppIconMode: FollowSystemAppIconMode
    ) {
        val useDarkVariant = resolveAppIconDarkModeEnabled(
            themeMode = themeMode,
            followSystemAppIconMode = followSystemAppIconMode
        )
        sync(
            context = context,
            useDarkVariant = useDarkVariant,
            themePalettePreset = themePalettePreset
        )
    }

    fun sync(
        context: Context,
        useDarkVariant: Boolean,
        themePalettePreset: ThemePalettePreset,
        disableInactiveAliases: Boolean = true
    ) {
        applyAppIconPalette(
            context = context,
            palette = themePalettePreset.toAppIconPalette(),
            darkModeEnabled = useDarkVariant,
            disableInactiveAliases = disableInactiveAliases
        )
    }

    fun syncOnAppStart(
        context: Context,
        themeMode: Int,
        themePalettePreset: ThemePalettePreset,
        followSystemAppIconMode: FollowSystemAppIconMode
    ) {
        applyAppIconPalette(
            context = context,
            palette = themePalettePreset.toAppIconPalette(),
            darkModeEnabled = resolveAppIconDarkModeEnabled(themeMode, followSystemAppIconMode),
            disableInactiveAliases = false
        )
    }

    fun isLauncherAliasClassName(className: String?): Boolean {
        if (className == null) return false
        return AppIconPalette.entries.any { option ->
            option.aliasClassName == className || option.darkAliasClassName == className
        }
    }

    fun resolveCurrentEnabledAlias(context: Context): String? {
        val packageManager = context.packageManager
        return AppIconPalette.entries
            .asSequence()
            .flatMap { option ->
                sequenceOf(option.aliasClassName, option.darkAliasClassName).filterNotNull()
            }
            .firstOrNull { aliasClassName ->
                packageManager.getComponentEnabledSetting(ComponentName(context, aliasClassName)) ==
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }
    }

    fun resolveExpectedAlias(
        themeMode: Int,
        themePalettePreset: ThemePalettePreset,
        followSystemAppIconMode: FollowSystemAppIconMode
    ): String {
        val darkModeEnabled = resolveAppIconDarkModeEnabled(
            themeMode = themeMode,
            followSystemAppIconMode = followSystemAppIconMode
        )
        return themePalettePreset.toAppIconPalette().resolveAliasClassName(darkModeEnabled)
    }

    fun resolveExpectedAlias(
        useDarkVariant: Boolean,
        themePalettePreset: ThemePalettePreset
    ): String {
        return themePalettePreset.toAppIconPalette().resolveAliasClassName(useDarkVariant)
    }
}
