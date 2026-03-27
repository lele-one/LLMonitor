package com.lele.llmonitor.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object HomeWallpaperManager {
    private const val PREF_WALLPAPER_ENABLED = "home_wallpaper_enabled"
    private const val PREF_WALLPAPER_ALPHA = "home_wallpaper_alpha"
    private const val PREF_WALLPAPER_BLUR = "home_wallpaper_blur"
    private const val LEGACY_PREF_WALLPAPER_BLUR_RADIUS = "home_wallpaper_blur_radius"
    private const val LEGACY_MAX_WALLPAPER_BLUR_RADIUS = 32f

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var appContext: Context? = null
    private var homeWallpaperAlphaPersistJob: Job? = null
    private var homeWallpaperAlphaGeneration: Long = 0L
    private var homeWallpaperBlurPersistJob: Job? = null
    private var homeWallpaperBlurGeneration: Long = 0L
    private var historyRefreshJob: Job? = null
    private var historyRefreshGeneration: Long = 0L

    var isEnabled = mutableStateOf(false)
        private set
    var wallpaperAlpha = mutableStateOf(DEFAULT_HOME_WALLPAPER_ALPHA)
        private set
    var wallpaperBlur = mutableStateOf(DEFAULT_HOME_WALLPAPER_BLUR)
        private set
    var wallpaperFile = mutableStateOf<File?>(null)
        private set
    var historyFiles = mutableStateOf<List<File>>(emptyList())
        private set

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences("llmonitor_settings", Context.MODE_PRIVATE)
        wallpaperAlpha.value =
            (prefs.getFloat(PREF_WALLPAPER_ALPHA, DEFAULT_HOME_WALLPAPER_ALPHA)).coerceIn(0f, 1f)
        wallpaperBlur.value = when {
            prefs.contains(PREF_WALLPAPER_BLUR) -> {
                prefs.getFloat(PREF_WALLPAPER_BLUR, DEFAULT_HOME_WALLPAPER_BLUR).coerceIn(0f, 1f)
            }

            prefs.contains(LEGACY_PREF_WALLPAPER_BLUR_RADIUS) -> {
                (
                    prefs.getFloat(LEGACY_PREF_WALLPAPER_BLUR_RADIUS, 0f)
                        .coerceIn(0f, LEGACY_MAX_WALLPAPER_BLUR_RADIUS) / LEGACY_MAX_WALLPAPER_BLUR_RADIUS
                    ).coerceIn(0f, 1f)
            }

            else -> DEFAULT_HOME_WALLPAPER_BLUR
        }
        prefs.edit()
            .putFloat(PREF_WALLPAPER_BLUR, wallpaperBlur.value)
            .remove(LEGACY_PREF_WALLPAPER_BLUR_RADIUS)
            .apply()
        refreshState()
        val storedEnabled = prefs.getBoolean(PREF_WALLPAPER_ENABLED, wallpaperFile.value != null)
        isEnabled.value = storedEnabled && wallpaperFile.value != null
    }

    fun setEnabled(enabled: Boolean) {
        val context = appContext ?: return
        val next = enabled && wallpaperFile.value != null
        isEnabled.value = next
        context.getSharedPreferences("llmonitor_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_WALLPAPER_ENABLED, next)
            .apply()
        SettingsManager.refreshLaunchAppearanceSnapshot()
    }

    fun previewHomeWallpaperAlpha(alpha: Float) {
        homeWallpaperAlphaPersistJob?.cancel()
        homeWallpaperAlphaGeneration += 1L
        wallpaperAlpha.value = alpha.coerceIn(0f, 1f)
    }

    fun setHomeWallpaperAlpha(alpha: Float) {
        val context = appContext ?: return
        val clamped = alpha.coerceIn(0f, 1f)
        wallpaperAlpha.value = clamped
        val generation = ++homeWallpaperAlphaGeneration
        homeWallpaperAlphaPersistJob?.cancel()
        homeWallpaperAlphaPersistJob = scope.launch {
            delay(300)
            if (generation != homeWallpaperAlphaGeneration) return@launch
            context.getSharedPreferences("llmonitor_settings", Context.MODE_PRIVATE)
                .edit()
                .putFloat(PREF_WALLPAPER_ALPHA, clamped)
                .apply()
            SettingsManager.refreshLaunchAppearanceSnapshot()
        }
    }

    fun setAlpha(alpha: Float) {
        setHomeWallpaperAlpha(alpha)
    }

    fun previewHomeWallpaperBlur(blur: Float) {
        homeWallpaperBlurPersistJob?.cancel()
        homeWallpaperBlurGeneration += 1L
        wallpaperBlur.value = blur.coerceIn(0f, 1f)
    }

    fun setHomeWallpaperBlur(blur: Float) {
        val context = appContext ?: return
        val clamped = blur.coerceIn(0f, 1f)
        wallpaperBlur.value = clamped
        val generation = ++homeWallpaperBlurGeneration
        homeWallpaperBlurPersistJob?.cancel()
        homeWallpaperBlurPersistJob = scope.launch {
            delay(300)
            if (generation != homeWallpaperBlurGeneration) return@launch
            context.getSharedPreferences("llmonitor_settings", Context.MODE_PRIVATE)
                .edit()
                .putFloat(PREF_WALLPAPER_BLUR, clamped)
                .apply()
            SettingsManager.refreshLaunchAppearanceSnapshot()
        }
    }

    fun importWallpaper(
        uri: Uri,
        deleteSourceAfterImport: Boolean = false,
        onCompleted: (Boolean) -> Unit = {}
    ) {
        val context = appContext ?: return
        scope.launch {
            val success = HomeWallpaperStorage.importWallpaper(
                context = context,
                sourceUri = uri,
                deleteSourceAfterImport = deleteSourceAfterImport
            )
            if (success) {
                refreshState()
                setEnabled(true)
            }
            onCompleted(success)
        }
    }

    fun applyWallpaperFromHistory(fileName: String) {
        val context = appContext ?: return
        scope.launch {
            if (HomeWallpaperStorage.applyWallpaperFromHistory(context, fileName)) {
                refreshState()
                setEnabled(true)
            }
        }
    }

    fun deleteHistoryWallpaper(fileName: String) {
        val context = appContext ?: return
        scope.launch {
            HomeWallpaperStorage.deleteHistoryWallpaper(context, fileName)
            refreshState()
            SettingsManager.refreshLaunchAppearanceSnapshot()
        }
    }

    fun refreshState() {
        val context = appContext ?: return
        val currentFile = HomeWallpaperStorage.resolveWallpaperFile(context)
            .takeIf { it.exists() && it.length() > 0L }
        wallpaperFile.value = currentFile
        val generation = ++historyRefreshGeneration
        historyRefreshJob?.cancel()
        historyRefreshJob = scope.launch {
            val resolvedHistory = withContext(Dispatchers.IO) {
                HomeWallpaperStorage.resolveHistoryFiles(
                    context = context,
                    excludeSameAsFile = currentFile
                )
            }
            if (generation != historyRefreshGeneration) return@launch
            historyFiles.value = resolvedHistory
        }
        if (currentFile == null) {
            isEnabled.value = false
        }
        SettingsManager.refreshLaunchAppearanceSnapshot()
    }
}
