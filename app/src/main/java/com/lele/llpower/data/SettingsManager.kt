package com.lele.llpower.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

object SettingsManager {
    private const val PREF_NAME = "llpower_settings"
    private var prefs: SharedPreferences? = null

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

    // 新增：虚拟电压开关 (针对电压读取异常设备)
    var isVirtualVoltageEnabled = mutableStateOf(false)
        private set


    // 新增：频率设置 (单位 ms)
    var refreshRateUiCharging = mutableStateOf(1000L)
        private set
    var refreshRateNotifyCharging = mutableStateOf(3000L)
        private set
        
    var refreshRateUiNotCharging = mutableStateOf(10000L) // 不充电时 UI 10秒（系统限制）
        private set
    var refreshRateNotifyNotCharging = mutableStateOf(180000L) // 不充电时 后台 3分钟
        private set

    // 忽略设置
    var isVirtualVoltageSuggestionDismissed = mutableStateOf(false)
        private set
    var isBatteryOptimizationDismissed = mutableStateOf(false)
        private set
    var isNotificationPermissionDismissed = mutableStateOf(false)
        private set

    /**
     * 核心初始化方法
     */
    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            // 从磁盘加载保存的状态
            isInvertCurrent.value = prefs?.getBoolean("invert_current", false) ?: false
            isDoubleCell.value = prefs?.getBoolean("double_cell", false) ?: false
            
            // 加载通知设置
            isNotificationEnabled.value = prefs?.getBoolean("notification_enabled", true) ?: true
            isShowNotificationWhenNotCharging.value = prefs?.getBoolean("show_notification_not_charging", true) ?: true
            isLiveNotificationEnabled.value = prefs?.getBoolean("live_notification_enabled", true) ?: true
            isVirtualVoltageEnabled.value = prefs?.getBoolean("virtual_voltage_enabled", false) ?: false
            
            // 加载忽略设置
            isVirtualVoltageSuggestionDismissed.value = prefs?.getBoolean("dismiss_virtual_voltage", false) ?: false
            isBatteryOptimizationDismissed.value = prefs?.getBoolean("dismiss_battery_opt", false) ?: false
            isNotificationPermissionDismissed.value = prefs?.getBoolean("dismiss_notification_perm", false) ?: false
            
            // 加载频率设置
            refreshRateUiCharging.value = prefs?.getLong("rate_ui_charging", 1000L) ?: 1000L
            refreshRateNotifyCharging.value = prefs?.getLong("rate_notify_charging", 3000L) ?: 3000L
            refreshRateUiNotCharging.value = prefs?.getLong("rate_ui_not_charging", 10000L) ?: 10000L
            refreshRateNotifyNotCharging.value = prefs?.getLong("rate_notify_not_charging", 180000L) ?: 180000L
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

    /**
     * 切换虚拟电压功能
     */
    fun toggleVirtualVoltageEnabled(value: Boolean) {
        isVirtualVoltageEnabled.value = value
        prefs?.edit()?.putBoolean("virtual_voltage_enabled", value)?.apply()
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
    
    fun setRefreshRateUiNotCharging(ms: Long) {
        refreshRateUiNotCharging.value = ms
        prefs?.edit()?.putLong("rate_ui_not_charging", ms)?.apply()
    }
    
    fun setRefreshRateNotifyNotCharging(ms: Long) {
        refreshRateNotifyNotCharging.value = ms
        prefs?.edit()?.putLong("rate_notify_not_charging", ms)?.apply()
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
}