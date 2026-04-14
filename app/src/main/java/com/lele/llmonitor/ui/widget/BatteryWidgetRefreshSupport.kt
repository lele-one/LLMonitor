package com.lele.llmonitor.ui.widget

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.lele.llmonitor.data.BatteryEngine
import com.lele.llmonitor.data.SettingsManager
import com.lele.llmonitor.utils.BatteryUtils

suspend fun forceRefreshBatteryWidgetsOnce(context: Context) {
    val appContext = context.applicationContext
    val manager = GlanceAppWidgetManager(appContext)
    val glanceIds = manager.getGlanceIds(BatteryWidget::class.java)
    if (glanceIds.isEmpty()) return

    val batteryIntent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL

    val rawCurrentMa = BatteryEngine.getCurrentMa(appContext)
    val sanitizedRawCurrentMa = BatteryEngine.sanitizeCurrentReading(
        rawCurrentMa = rawCurrentMa,
        level = level,
        status = status,
        plugged = plugged
    )

    val invert = SettingsManager.isInvertCurrent.value
    val doubleCell = SettingsManager.isDoubleCell.value
    val displayCurrent = BatteryEngine.applyCurrentAdjustments(
        rawCurrentMa = sanitizedRawCurrentMa,
        invert = invert,
        isDoubleCell = doubleCell
    )

    val totalCapacity = BatteryEngine.getBatteryDesignCapacity(appContext)
    val currentCapacity = BatteryEngine.getBatteryCurrentCapacity(appContext)
    val voltageV = if (SettingsManager.isVirtualVoltageEnabled.value) {
        BatteryEngine.getVirtualVoltage(currentCapacity, totalCapacity, isCharging)
    } else {
        BatteryEngine.getVoltageV(appContext)
    }
    val displayPower = voltageV * (displayCurrent / 1000f)

    val tempRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
    val tempReading = BatteryEngine.parseBatteryTemperature(tempRaw)
    val tempC = tempReading.celsius
    val tempFractionDigits = tempReading.fractionDigits
    val updateTimeStr = BatteryUtils.formatTimestamp(System.currentTimeMillis())

    val widget = BatteryWidget()
    glanceIds.forEach { glanceId ->
        updateAppWidgetState(appContext, glanceId) { prefs ->
            prefs[BatteryWidgetKeys.POWER] = displayPower
            prefs[BatteryWidgetKeys.CURRENT] = displayCurrent
            prefs[BatteryWidgetKeys.CAPACITY] = currentCapacity
            prefs[BatteryWidgetKeys.TOTAL_CAPACITY] = totalCapacity
            prefs[BatteryWidgetKeys.TEMP] = tempC
            prefs[BatteryWidgetKeys.TEMP_FRACTION_DIGITS] = tempFractionDigits
            prefs[BatteryWidgetKeys.UPDATE_TIME] = updateTimeStr
        }
        widget.update(appContext, glanceId)
    }
}
