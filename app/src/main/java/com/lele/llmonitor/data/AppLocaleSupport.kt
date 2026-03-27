package com.lele.llmonitor.data

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

private const val SETTINGS_PREF_NAME = "llmonitor_settings"
const val APP_LANGUAGE_OPTION_KEY = "app_language_option"
private const val LANGUAGE_TAG_EN = "en"
private const val LANGUAGE_TAG_ZH_CN = "zh-CN"
private const val LANGUAGE_TAG_ZH_HK = "zh-HK"
private const val LANGUAGE_TAG_ZH_TW = "zh-TW"
@Volatile
private var currentAppLanguageOption: AppLanguageOption = AppLanguageOption.default

enum class AppLanguageOption(
    val preferenceValue: String,
    val explicitLanguageTag: String?
) {
    FOLLOW_SYSTEM(
        preferenceValue = "system",
        explicitLanguageTag = null
    ),
    ENGLISH(
        preferenceValue = "en",
        explicitLanguageTag = LANGUAGE_TAG_EN
    ),
    CHINESE_SIMPLIFIED_CHINA(
        preferenceValue = "zh-CN",
        explicitLanguageTag = LANGUAGE_TAG_ZH_CN
    ),
    CHINESE_TRADITIONAL_HONG_KONG(
        preferenceValue = "zh-HK",
        explicitLanguageTag = LANGUAGE_TAG_ZH_HK
    ),
    CHINESE_TRADITIONAL_TAIWAN(
        preferenceValue = "zh-TW",
        explicitLanguageTag = LANGUAGE_TAG_ZH_TW
    );

    companion object {
        val default: AppLanguageOption = FOLLOW_SYSTEM

        fun fromPreferenceValue(value: String?): AppLanguageOption {
            return entries.firstOrNull { it.preferenceValue == value } ?: default
        }
    }
}

fun applySupportedAppLocale(
    context: Context,
    optionOverride: AppLanguageOption? = null
) {
    val resolvedOption = optionOverride ?: resolveStoredAppLanguageOption(context)
    currentAppLanguageOption = resolvedOption
    val localeOptionForResources = if (resolvedOption == AppLanguageOption.FOLLOW_SYSTEM) {
        resolveSupportedSystemLanguageOption()
    } else {
        resolvedOption
    }
    val targetLocales = if (localeOptionForResources == AppLanguageOption.FOLLOW_SYSTEM) {
        LocaleListCompat.forLanguageTags(LANGUAGE_TAG_EN)
    } else {
        LocaleListCompat.forLanguageTags(
            localeOptionForResources.explicitLanguageTag ?: LANGUAGE_TAG_EN
        )
    }
    if (AppCompatDelegate.getApplicationLocales().toLanguageTags() == targetLocales.toLanguageTags()) {
        return
    }
    AppCompatDelegate.setApplicationLocales(targetLocales)
}

fun resolveCurrentAppLanguageOption(): AppLanguageOption = currentAppLanguageOption

fun resolveSupportedSystemLanguageOption(): AppLanguageOption {
    val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Resources.getSystem().configuration.locales[0]
    } else {
        @Suppress("DEPRECATION")
        Resources.getSystem().configuration.locale
    } ?: Locale.getDefault()
    val language = locale.language.lowercase(Locale.US)
    if (language == "en") return AppLanguageOption.ENGLISH
    if (language != "zh") return AppLanguageOption.ENGLISH

    return when (locale.country.uppercase(Locale.US)) {
        "CN" -> AppLanguageOption.CHINESE_SIMPLIFIED_CHINA
        "HK" -> AppLanguageOption.CHINESE_TRADITIONAL_HONG_KONG
        "TW" -> AppLanguageOption.CHINESE_TRADITIONAL_TAIWAN
        else -> AppLanguageOption.ENGLISH
    }
}

fun resolveStoredAppLanguageOption(context: Context): AppLanguageOption {
    val preferences = context.getSharedPreferences(SETTINGS_PREF_NAME, Context.MODE_PRIVATE)
    return resolveStoredAppLanguageOption(preferences)
}

fun resolveStoredAppLanguageOption(
    preferences: SharedPreferences
): AppLanguageOption {
    return AppLanguageOption.fromPreferenceValue(
        preferences.getString(APP_LANGUAGE_OPTION_KEY, AppLanguageOption.default.preferenceValue)
    )
}
