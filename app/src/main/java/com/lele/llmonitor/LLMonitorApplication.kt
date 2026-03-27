package com.lele.llmonitor

import android.app.Application
import android.content.res.Configuration
import com.lele.llmonitor.data.BatteryRepository
import com.lele.llmonitor.data.HomeWallpaperManager
import com.lele.llmonitor.data.SettingsManager
import com.lele.llmonitor.data.applySupportedAppLocale

class LLMonitorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        applySupportedAppLocale(this)
        // 必须在 App 启动时第一时间初始化
        SettingsManager.init(this)
        HomeWallpaperManager.init(this)
        BatteryRepository.preloadInitialHistoryAsync(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySupportedAppLocale(this)
        SettingsManager.refreshLaunchAppearanceSnapshot()
        SettingsManager.syncAppIcon()
    }
}
