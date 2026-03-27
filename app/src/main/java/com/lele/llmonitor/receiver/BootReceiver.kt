package com.lele.llmonitor.receiver

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.lele.llmonitor.service.BatteryMonitorService

/**
 * 广播接收器：仅在开机阶段自动拉起后台服务。
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        // 超过该开机窗口的 BOOT_COMPLETED 广播，视作迟到重放。
        private const val BOOT_WINDOW_MS = 3 * 60 * 1000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                val isLateBootReplay = SystemClock.elapsedRealtime() > BOOT_WINDOW_MS
                if (isLateBootReplay && isAppForeground(context)) {
                    // 应用前台冷启动时，忽略迟到开机广播，避免抢占首屏动画和主线程。
                    return
                }
                // 冷启动链路中只保留“开机拉起”这一条，避免额外广播在首屏阶段抢占资源。
                val serviceIntent = Intent(context, BatteryMonitorService::class.java).apply {
                    action = intent.action
                }
                try {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } catch (_: Exception) {
                    // 开机阶段允许静默失败，后续由系统/用户启动与服务自愈策略兜底。
                }
            }
        }
    }

    private fun isAppForeground(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val myPid = Process.myPid()
        val myPackage = context.packageName
        val running = am.runningAppProcesses ?: return false
        val mine = running.firstOrNull { it.pid == myPid || it.processName == myPackage } ?: return false
        return mine.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }
}
