package com.lele.llpower.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge // 1. 导入此包
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

import com.lele.llpower.service.BatteryMonitorService
import com.lele.llpower.ui.dashboard.BatteryViewModel
import com.lele.llpower.ui.dashboard.DashboardScreen
import com.lele.llpower.ui.settings.SettingsScreen
import com.lele.llpower.ui.theme.LLPowerTheme

class MainActivity : ComponentActivity() {
    private var isHdrModeEnabled: Boolean = false

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        // 2. 开启沉浸式边缘到边缘设计
        // 它会自动处理状态栏和导航栏的透明度，并根据主题调整图标颜色（深色/浅色模式适配）
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        // 启动后台监测服务
        startForegroundService(Intent(this, BatteryMonitorService::class.java))

        setContent {
            LLPowerTheme {
                val navController = rememberNavController()
                val viewModel: BatteryViewModel = viewModel()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = "dashboard"
                    ) {
                        composable("dashboard") {
                            DashboardScreen(
                                viewModel = viewModel,
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                },
                                onSetHdrMode = { enabled ->
                                    setHdrMode(enabled)
                                }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                viewModel = viewModel, // 【新增】传入 ViewModel 实例
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun setHdrMode(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 避免重复设置同一色域模式，部分机型会因此出现整屏亮闪
            if (enabled == isHdrModeEnabled) return
            isHdrModeEnabled = enabled

            val colorMode = if (enabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    android.content.pm.ActivityInfo.COLOR_MODE_HDR
                } else {
                    android.content.pm.ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT
                }
            } else {
                android.content.pm.ActivityInfo.COLOR_MODE_DEFAULT
            }
            window.colorMode = colorMode
        }
    }
}
