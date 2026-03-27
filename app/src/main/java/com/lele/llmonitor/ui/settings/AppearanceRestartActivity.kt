package com.lele.llmonitor.ui.settings

import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.lele.llmonitor.data.AppIconPaletteManager
import com.lele.llmonitor.ui.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val RESTART_POLL_INTERVAL_MS = 32L
private const val INVALID_TASK_ID = -1
private const val INVALID_PID = -1

internal const val EXTRA_SOURCE_TASK_ID = "appearance_restart_source_task_id"
internal const val EXTRA_SOURCE_PID = "appearance_restart_source_pid"

class AppearanceRestartActivity : ComponentActivity() {
    private var restartJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            restartJob = lifecycleScope.launch {
                awaitSourceShutdownAndRelaunch()
            }
        }
    }

    override fun onDestroy() {
        restartJob?.cancel()
        restartJob = null
        super.onDestroy()
    }

    private suspend fun awaitSourceShutdownAndRelaunch() {
        val sourceTaskId = intent.getIntExtra(EXTRA_SOURCE_TASK_ID, INVALID_TASK_ID)
        val sourcePid = intent.getIntExtra(EXTRA_SOURCE_PID, INVALID_PID)
        while (!isSourceGone(sourceTaskId, sourcePid)) {
            delay(RESTART_POLL_INTERVAL_MS)
        }

        if (!hasLaunchTask()) {
            packageManager.getLaunchIntentForPackage(packageName)
                ?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                ?.let(::startActivity)
        }
        finish()
    }

    private fun isSourceGone(sourceTaskId: Int, sourcePid: Int): Boolean {
        return !hasTask(sourceTaskId) && !isProcessAlive(sourcePid)
    }

    private fun hasTask(taskId: Int): Boolean {
        if (taskId == INVALID_TASK_ID) return false
        val activityManager = getSystemService(ActivityManager::class.java) ?: return false
        return activityManager.appTasks.any { task ->
            task.taskInfo.taskId == taskId
        }
    }

    private fun isProcessAlive(pid: Int): Boolean {
        if (pid == INVALID_PID) return false
        val activityManager = getSystemService(ActivityManager::class.java) ?: return false
        return activityManager.runningAppProcesses?.any { processInfo ->
            processInfo.pid == pid
        } == true
    }

    private fun hasLaunchTask(): Boolean {
        val mainActivityName = MainActivity::class.java.name
        val activityManager = getSystemService(ActivityManager::class.java) ?: return false
        return activityManager.appTasks.any { task ->
            val taskInfo = task.taskInfo
            val baseActivityClassName = taskInfo.baseActivity?.className
            val topActivityClassName = taskInfo.topActivity?.className
            val baseIntentClassName = taskInfo.baseIntent?.component?.className
            baseActivityClassName == mainActivityName ||
                topActivityClassName == mainActivityName ||
                baseIntentClassName == mainActivityName ||
                AppIconPaletteManager.isLauncherAliasClassName(baseActivityClassName) ||
                AppIconPaletteManager.isLauncherAliasClassName(topActivityClassName) ||
                AppIconPaletteManager.isLauncherAliasClassName(baseIntentClassName)
        }
    }
}
