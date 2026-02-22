package com.lele.llpower

import android.app.Application
import com.lele.llpower.data.SettingsManager

class LLPowerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 必须在 App 启动时第一时间初始化
        SettingsManager.init(this)
    }
}