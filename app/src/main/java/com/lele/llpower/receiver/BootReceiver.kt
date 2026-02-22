package com.lele.llpower.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lele.llpower.service.BatteryMonitorService

/**
 * 广播接收器：响应开机和充电事件
 * - 开机时自动启动服务
 * - 插入充电器时确保服务运行（如果被杀）
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_POWER_CONNECTED -> {
                // 启动前台服务
                val serviceIntent = Intent(context, BatteryMonitorService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
