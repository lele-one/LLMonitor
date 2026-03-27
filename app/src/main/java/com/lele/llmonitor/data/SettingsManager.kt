package com.lele.llmonitor.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import com.lele.llmonitor.ui.theme.ThemePalettePreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object SettingsManager {
    private const val PREF_NAME = "llmonitor_settings"
    const val ACTION_FORCE_UPDATE = "com.lele.llmonitor.ACTION_FORCE_UPDATE"
    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var launchSnapshotRefreshJob: Job? = null
    private var launchSnapshotRefreshGeneration: Long = 0L

    // 使用 Compose 状态，确保 UI 实时刷新
    var isInvertCurrent = mutableStateOf(false)
        private set

    var isDoubleCell = mutableStateOf(false)
        private set

    // 新增：通知设置
    var isNotificationEnabled = mutableStateOf(true)
        private set
        
    var isShowNotificationWhenNotCharging = mutableStateOf(true)
        private set
        
    var isLiveNotificationEnabled = mutableStateOf(true)
        private set

    var isLiveCloseActionEnabled = mutableStateOf(true)
        private set

    // 新增：虚拟电压开关 (针对电压读取异常设备)
    var isVirtualVoltageEnabled = mutableStateOf(false)
        private set

    // 主题设置：0 跟随系统，1 浅色，2 深色
    var themeMode = mutableStateOf(DEFAULT_THEME_MODE)
        private set
    var themePalettePreset = mutableStateOf(ThemePalettePreset.DYNAMIC)
        private set
    var followSystemAppIconMode = mutableStateOf(FollowSystemAppIconMode.default)
        private set
    var launchAppearanceSnapshot = mutableStateOf<LaunchAppearanceSnapshot?>(null)
        private set
    var homeCardOpacity = mutableStateOf(1f)
        private set
    var appLanguageOption = mutableStateOf(AppLanguageOption.default)
        private set


    // 新增：频率设置 (单位 ms)
    var refreshRateUiCharging = mutableStateOf(1000L)
        private set
    var refreshRateNotifyCharging = mutableStateOf(3000L)
        private set
    var refreshRateNotifyChargingScreenOff = mutableStateOf(10000L)
        private set
        
    var refreshRateUiNotCharging = mutableStateOf(10000L) // 不充电时 UI 10秒（系统限制）
        private set
    var refreshRateNotifyNotCharging = mutableStateOf(180000L) // 不充电时 后台 3分钟
        private set
    var refreshRateNotifyNotChargingScreenOff = mutableStateOf(180000L)
        private set

    // 忽略设置
    var isVirtualVoltageSuggestionDismissed = mutableStateOf(false)
        private set
    var isBatteryOptimizationDismissed = mutableStateOf(false)
        private set
    var isNotificationPermissionDismissed = mutableStateOf(false)
        private set
    var isDebugModeEnabled = mutableStateOf(false)
        private set

    /**
     * 核心初始化方法
     */
    fun init(context: Context) {
        if (prefs == null) {
            appContext = context.applicationContext
            prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            // 从磁盘加载保存的状态
            isInvertCurrent.value = prefs?.getBoolean("invert_current", false) ?: false
            isDoubleCell.value = prefs?.getBoolean("double_cell", false) ?: false
            
            // 加载通知设置
            isNotificationEnabled.value = prefs?.getBoolean("notification_enabled", true) ?: true
            isShowNotificationWhenNotCharging.value = prefs?.getBoolean("show_notification_not_charging", true) ?: true
            isLiveNotificationEnabled.value = prefs?.getBoolean("live_notification_enabled", true) ?: true
            isLiveCloseActionEnabled.value = prefs?.getBoolean("live_close_action_enabled", true) ?: true
            isVirtualVoltageEnabled.value = prefs?.getBoolean("virtual_voltage_enabled", false) ?: false

            // 加载主题设置
            themeMode.value = (prefs?.getInt("theme_mode", DEFAULT_THEME_MODE) ?: DEFAULT_THEME_MODE).coerceIn(0, 2)
            themePalettePreset.value = ThemePalettePreset.fromPreferenceValue(
                prefs?.getString("theme_palette_preset", ThemePalettePreset.DYNAMIC.preferenceValue)
            )
            followSystemAppIconMode.value = resolveStoredFollowSystemAppIconMode(
                preferences = requireNotNull(prefs),
                fallbackSystemDarkModeEnabled = isSystemDarkModeEnabled(context)
            )
            homeCardOpacity.value = normalizeHomeCardOpacity(prefs?.getFloat("home_card_opacity", 1f) ?: 1f)
            appLanguageOption.value = resolveStoredAppLanguageOption(requireNotNull(prefs))
            prefs?.edit()?.remove("home_card_blur")?.apply()

            // 加载忽略设置
            isVirtualVoltageSuggestionDismissed.value = prefs?.getBoolean("dismiss_virtual_voltage", false) ?: false
            isBatteryOptimizationDismissed.value = prefs?.getBoolean("dismiss_battery_opt", false) ?: false
            isNotificationPermissionDismissed.value = prefs?.getBoolean("dismiss_notification_perm", false) ?: false
            isDebugModeEnabled.value = prefs?.getBoolean("debug_mode_enabled", false) ?: false
            
            // 加载频率设置
            refreshRateUiCharging.value = prefs?.getLong("rate_ui_charging", 1000L) ?: 1000L
            refreshRateNotifyCharging.value = prefs?.getLong("rate_notify_charging", 3000L) ?: 3000L
            refreshRateNotifyChargingScreenOff.value =
                prefs?.getLong("rate_notify_charging_screen_off", 10000L) ?: 10000L
            refreshRateUiNotCharging.value = prefs?.getLong("rate_ui_not_charging", 10000L) ?: 10000L
            refreshRateNotifyNotCharging.value = prefs?.getLong("rate_notify_not_charging", 180000L) ?: 180000L
            refreshRateNotifyNotChargingScreenOff.value =
                prefs?.getLong("rate_notify_not_charging_screen_off", 180000L) ?: 180000L

            if (!requireNotNull(prefs).contains(FOLLOW_SYSTEM_APP_ICON_MODE_KEY) && themeMode.value == 0) {
                val migratedMode = if (isSystemDarkModeEnabled(context)) {
                    FollowSystemAppIconMode.DARK
                } else {
                    FollowSystemAppIconMode.LIGHT
                }
                followSystemAppIconMode.value = migratedMode
                prefs?.edit()?.putInt(FOLLOW_SYSTEM_APP_ICON_MODE_KEY, migratedMode.preferenceValue)?.apply()
            }

            launchAppearanceSnapshot.value = readLaunchAppearanceSnapshot(requireNotNull(prefs))
            applySupportedAppLocale(
                context = context,
                optionOverride = appLanguageOption.value
            )
            applyAppNightMode(themeMode.value)
            backgroundScope.launch {
                AppIconPaletteManager.syncOnAppStart(
                    context = context,
                    themeMode = themeMode.value,
                    themePalettePreset = themePalettePreset.value,
                    followSystemAppIconMode = followSystemAppIconMode.value
                )
            }
        }
    }

    /**
     * 切换反转电流并持久化
     */
    fun toggleInvertCurrent(value: Boolean) {
        isInvertCurrent.value = value
        prefs?.edit()?.putBoolean("invert_current", value)?.apply()
    }

    /**
     * 切换双电芯并持久化
     */
    fun toggleDoubleCell(value: Boolean) {
        isDoubleCell.value = value
        prefs?.edit()?.putBoolean("double_cell", value)?.apply()
    }
    
    /**
     * 切换通知总开关
     */
    fun toggleNotificationEnabled(value: Boolean) {
        isNotificationEnabled.value = value
        prefs?.edit()?.putBoolean("notification_enabled", value)?.apply()
    }

    /**
     * 切换未充电时显示通知
     */
    fun toggleShowNotificationWhenNotCharging(value: Boolean) {
        isShowNotificationWhenNotCharging.value = value
        prefs?.edit()?.putBoolean("show_notification_not_charging", value)?.apply()
    }

    /**
     * 切换实况通知
     */
    fun toggleLiveNotificationEnabled(value: Boolean) {
        isLiveNotificationEnabled.value = value
        prefs?.edit()?.putBoolean("live_notification_enabled", value)?.apply()
    }

    fun toggleLiveCloseActionEnabled(value: Boolean) {
        isLiveCloseActionEnabled.value = value
        prefs?.edit()?.putBoolean("live_close_action_enabled", value)?.apply()
    }

    /**
     * 切换虚拟电压功能
     */
    fun toggleVirtualVoltageEnabled(value: Boolean) {
        isVirtualVoltageEnabled.value = value
        prefs?.edit()?.putBoolean("virtual_voltage_enabled", value)?.apply()
    }

    fun setThemeMode(
        mode: Int,
        onCompleted: (() -> Unit)? = null
    ) {
        val normalized = mode.coerceIn(0, 2)
        val resolvedFollowSystemAppIconMode = resolveFollowSystemAppIconModeForThemeModeChange(
            currentThemeMode = themeMode.value,
            currentFollowSystemAppIconMode = followSystemAppIconMode.value,
            targetThemeMode = normalized
        )
        themeMode.value = normalized
        if (normalized == 0) {
            followSystemAppIconMode.value = resolvedFollowSystemAppIconMode
        }
        prefs?.edit()?.apply {
            putInt("theme_mode", normalized)
            if (normalized == 0) {
                putInt(FOLLOW_SYSTEM_APP_ICON_MODE_KEY, resolvedFollowSystemAppIconMode.preferenceValue)
            }
        }?.commit()
        applyAppNightMode(normalized)
        syncAppIcon(
            useDarkVariantOverride = resolveAppIconDarkModeEnabled(
                themeMode = normalized,
                followSystemAppIconMode = resolvedFollowSystemAppIconMode
            )
        )
        refreshLaunchAppearanceSnapshot()
        onCompleted?.invoke()
    }

    fun setThemePalettePreset(
        preset: ThemePalettePreset,
        onCompleted: (() -> Unit)? = null
    ) {
        themePalettePreset.value = preset
        prefs?.edit()?.putString("theme_palette_preset", preset.preferenceValue)?.commit()
        syncAppIcon()
        refreshLaunchAppearanceSnapshot()
        onCompleted?.invoke()
    }

    fun setFollowSystemAppIconMode(
        mode: FollowSystemAppIconMode,
        onCompleted: (() -> Unit)? = null
    ) {
        followSystemAppIconMode.value = mode
        prefs?.edit()?.putInt(FOLLOW_SYSTEM_APP_ICON_MODE_KEY, mode.preferenceValue)?.commit()
        syncAppIcon(
            useDarkVariantOverride = resolveAppIconDarkModeEnabled(
                themeMode = themeMode.value,
                followSystemAppIconMode = mode
            )
        )
        onCompleted?.invoke()
    }

    fun setHomeCardOpacity(value: Float) {
        val normalized = normalizeHomeCardOpacity(value)
        homeCardOpacity.value = normalized
        prefs?.edit()?.putFloat("home_card_opacity", normalized)?.apply()
    }

    fun setAppLanguageOption(
        option: AppLanguageOption,
        onCompleted: (() -> Unit)? = null
    ) {
        appLanguageOption.value = option
        prefs?.edit()?.putString(APP_LANGUAGE_OPTION_KEY, option.preferenceValue)?.commit()
        appContext?.let { context ->
            applySupportedAppLocale(
                context = context,
                optionOverride = option
            )
        }
        onCompleted?.invoke()
    }

    private fun normalizeHomeCardOpacity(rawValue: Float): Float {
        if (!rawValue.isFinite()) return 1f
        return if (rawValue > 1f) {
            (rawValue / 100f).coerceIn(0f, 1f)
        } else {
            rawValue.coerceIn(0f, 1f)
        }
    }

    fun refreshLaunchAppearanceSnapshot() {
        val context = appContext ?: return
        val sharedPrefs = prefs ?: return
        val wallpaperFile = HomeWallpaperStorage.resolveWallpaperFile(context)
            .takeIf { it.exists() && it.length() > 0L }
        val wallpaperVersion = wallpaperFile?.lastModified()?.coerceAtLeast(0L) ?: 0L
        val wallpaperEnabled = sharedPrefs.getBoolean("home_wallpaper_enabled", false) &&
            wallpaperVersion > 0L
        val wallpaperAlpha = sharedPrefs.getFloat(
            "home_wallpaper_alpha",
            DEFAULT_HOME_WALLPAPER_ALPHA
        ).coerceIn(0f, 1f)
        val wallpaperBlur = sharedPrefs.getFloat(
            "home_wallpaper_blur",
            DEFAULT_HOME_WALLPAPER_BLUR
        ).coerceIn(0f, 1f)
        val displayDensity = context.resources.displayMetrics.density
        val baseSnapshot = buildLaunchAppearanceSnapshot(
            context = context,
            themeMode = themeMode.value,
            themePalettePreset = themePalettePreset.value,
            wallpaperEnabled = wallpaperEnabled,
            wallpaperAlpha = wallpaperAlpha,
            wallpaperBlur = wallpaperBlur,
            wallpaperVersion = wallpaperVersion
        )
        val startupPreviewRenderSpec = if (wallpaperEnabled) {
            resolveHomeWallpaperStartupPreviewRenderSpec(
                backgroundArgb = baseSnapshot.backgroundArgb,
                wallpaperAlpha = wallpaperAlpha,
                wallpaperBlur = wallpaperBlur,
                displayDensity = displayDensity
            )
        } else {
            ""
        }
        val generation = ++launchSnapshotRefreshGeneration
        val immediateSnapshot = baseSnapshot.copy(
            startupPreviewVersion = 0L,
            startupPreviewRenderSpec = startupPreviewRenderSpec
        )
        launchAppearanceSnapshot.value = immediateSnapshot
        persistLaunchAppearanceSnapshot(sharedPrefs, immediateSnapshot)

        if (!wallpaperEnabled || wallpaperVersion <= 0L) return

        launchSnapshotRefreshJob?.cancel()
        launchSnapshotRefreshJob = backgroundScope.launch(Dispatchers.IO) {
            val refreshed = HomeWallpaperStorage.refreshStartupPreview(
                context = context,
                backgroundArgb = baseSnapshot.backgroundArgb,
                wallpaperAlpha = wallpaperAlpha,
                wallpaperBlur = wallpaperBlur
            )
            if (generation != launchSnapshotRefreshGeneration) return@launch

            val resolvedSnapshot = immediateSnapshot.copy(
                startupPreviewVersion = if (refreshed) wallpaperVersion else 0L
            )
            withContext(Dispatchers.Main.immediate) {
                if (generation != launchSnapshotRefreshGeneration) return@withContext
                launchAppearanceSnapshot.value = resolvedSnapshot
            }
            persistLaunchAppearanceSnapshot(sharedPrefs, resolvedSnapshot)
        }
    }

    fun resolveLaunchAppearanceSnapshot(): LaunchAppearanceSnapshot? {
        val sharedPrefs = prefs ?: return null
        return launchAppearanceSnapshot.value ?: readLaunchAppearanceSnapshot(sharedPrefs)?.also {
            launchAppearanceSnapshot.value = it
        }
    }

    fun syncAppIcon(useDarkVariantOverride: Boolean? = null) {
        val context = appContext ?: return
        if (useDarkVariantOverride != null) {
            AppIconPaletteManager.sync(
                context = context,
                useDarkVariant = useDarkVariantOverride,
                themePalettePreset = themePalettePreset.value
            )
        } else {
            AppIconPaletteManager.sync(
                context = context,
                themeMode = themeMode.value,
                themePalettePreset = themePalettePreset.value,
                followSystemAppIconMode = followSystemAppIconMode.value
            )
        }
    }
    
    // 频率设置方法
    fun setRefreshRateUiCharging(ms: Long) {
        refreshRateUiCharging.value = ms
        prefs?.edit()?.putLong("rate_ui_charging", ms)?.apply()
    }
    
    fun setRefreshRateNotifyCharging(ms: Long) {
        refreshRateNotifyCharging.value = ms
        prefs?.edit()?.putLong("rate_notify_charging", ms)?.apply()
    }

    fun setRefreshRateNotifyChargingScreenOff(ms: Long) {
        refreshRateNotifyChargingScreenOff.value = ms
        prefs?.edit()?.putLong("rate_notify_charging_screen_off", ms)?.apply()
    }
    
    fun setRefreshRateUiNotCharging(ms: Long) {
        refreshRateUiNotCharging.value = ms
        prefs?.edit()?.putLong("rate_ui_not_charging", ms)?.apply()
    }
    
    fun setRefreshRateNotifyNotCharging(ms: Long) {
        refreshRateNotifyNotCharging.value = ms
        prefs?.edit()?.putLong("rate_notify_not_charging", ms)?.apply()
    }

    fun setRefreshRateNotifyNotChargingScreenOff(ms: Long) {
        refreshRateNotifyNotChargingScreenOff.value = ms
        prefs?.edit()?.putLong("rate_notify_not_charging_screen_off", ms)?.apply()
    }

    // 忽略设置 helper methods
    fun setVirtualVoltageSuggestionDismissed(value: Boolean) {
        isVirtualVoltageSuggestionDismissed.value = value
        prefs?.edit()?.putBoolean("dismiss_virtual_voltage", value)?.apply()
    }
    
    fun setBatteryOptimizationDismissed(value: Boolean) {
        isBatteryOptimizationDismissed.value = value
        prefs?.edit()?.putBoolean("dismiss_battery_opt", value)?.apply()
    }
    
    fun setNotificationPermissionDismissed(value: Boolean) {
        isNotificationPermissionDismissed.value = value
        prefs?.edit()?.putBoolean("dismiss_notification_perm", value)?.apply()
    }

    fun toggleDebugModeEnabled(value: Boolean) {
        isDebugModeEnabled.value = value
        prefs?.edit()?.putBoolean("debug_mode_enabled", value)?.apply()
    }

    /**
     * 检查是否在电池优化白名单中
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 请求忽略电池优化
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        try {
            val intent = android.content.Intent().apply {
                action = android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendForceUpdateBroadcast(context: Context) {
        val intent = Intent(ACTION_FORCE_UPDATE).setPackage(context.packageName)
        context.sendBroadcast(intent)
    }
}
