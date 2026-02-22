package com.lele.llpower.ui.dashboard

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.lele.llpower.data.BatteryEngine
import com.lele.llpower.data.BatteryRepository
import com.lele.llpower.data.SettingsManager
import com.lele.llpower.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

// 保持 InstantBatteryState 数据类不变
data class InstantBatteryState(
    val voltage: Float = 0f,
    val current: Float = 0f,
    val capacity: Long = 0,
    val totalCapacity: Int = 0,
    val power: Float = 0f,
    val statusText: String = "未知",
    val temperature: Float = 0f,
    val supplyStatus: String = "检测中",
    val healthStatus: String = "未知",
    val remainingTime: String = "--"
)

class BatteryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val dao = db.batteryDao()

    val displayHistory = BatteryRepository.latestHistory

    var instantStatus by mutableStateOf(InstantBatteryState())
        private set

    private val _chargingStartedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val chargingStartedEvent = _chargingStartedEvent.asSharedFlow()

    val recordIntervalMs = 60000L  // 每分钟记录一次
    private var designCapacity: Int = 0

    init {
        designCapacity = BatteryEngine.getBatteryDesignCapacity(application)

        viewModelScope.launch(Dispatchers.IO) {
            if (BatteryRepository.latestHistory.isEmpty()) {
                val pastData = dao.getStaticHistory(System.currentTimeMillis() - 172800000L) // 加载最近 48 小时数据
                BatteryRepository.loadInitialData(pastData)
            }
        }

        viewModelScope.launch {
            var lastIsCharging: Boolean? = null  // 上一次的充电状态
            var lastPlugged: Int? = null         // 上一次的电源类型
            var lastFullUpdateTime = 0L          // 上一次完整数据更新时间
            
            while (true) {
                val context = getApplication<Application>().applicationContext
                val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                 status == BatteryManager.BATTERY_STATUS_FULL
                
                val currentTime = System.currentTimeMillis()
                val fullRefreshInterval = if (isCharging) {
                    SettingsManager.refreshRateUiCharging.value
                } else {
                    SettingsManager.refreshRateUiNotCharging.value
                }
                
                // 判断是否需要完整数据刷新
                // 同时监测充电状态和电源类型变化（USB/AC/无线/无）
                val stateChanged = (lastIsCharging != null && lastIsCharging != isCharging) ||
                                   (lastPlugged != null && lastPlugged != plugged)
                val intervalPassed = currentTime - lastFullUpdateTime >= fullRefreshInterval
                
                if (stateChanged || intervalPassed) {
                    // Detect if charging just started
                    if (lastIsCharging == false && isCharging) {
                        _chargingStartedEvent.tryEmit(Unit)
                    }
                    
                    // 状态变化 或 达到刷新间隔：执行完整数据更新
                    updateInstantStatus()
                    lastFullUpdateTime = currentTime
                }
                
                // 记录当前状态
                lastIsCharging = isCharging
                lastPlugged = plugged
                
                // 固定每秒检测一次供电状态
                delay(1000L)
            }
        }
    }

    private fun updateInstantStatus() {
        val context = getApplication<Application>().applicationContext
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val invert = SettingsManager.isInvertCurrent.value
        val doubleCell = SettingsManager.isDoubleCell.value

        val finalCurrent = BatteryEngine.getAdjustedCurrentMa(context, invert, doubleCell)
        
        // 虚拟电压逻辑
        val useVirtualVoltage = SettingsManager.isVirtualVoltageEnabled.value
        val voltageV = if (useVirtualVoltage) {
             val totalCapacity = BatteryEngine.getBatteryDesignCapacity(context)
             val currentCapacity = BatteryEngine.getBatteryCurrentCapacity(context)
             val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
             val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                              status == BatteryManager.BATTERY_STATUS_FULL
             BatteryEngine.getVirtualVoltage(currentCapacity, totalCapacity, isCharging)
        } else {
            BatteryEngine.getVoltageV(context)
        }

        val rawPower = voltageV * (finalCurrent / 1000f)

        val finalPower = if (kotlin.math.abs(rawPower) < 0.005f) 0.0f else rawPower

        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val supply = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "电源适配器"
            BatteryManager.BATTERY_PLUGGED_USB -> "电脑 (USB)"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "无线充电"
            else -> "电池供电"
        }

        val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f

        val health = when (intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, 1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "过热"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "过压"
            else -> "正常"
        }

        var timeText = "--"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val remainingMs = bm.computeChargeTimeRemaining()
            if (remainingMs > 0) {
                val hours = remainingMs / 3600000
                val minutes = (remainingMs % 3600000) / 60000
                timeText = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
            }
        }

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val statusText = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "充电中"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "放电中"
            BatteryManager.BATTERY_STATUS_FULL -> "已充满"
            else -> "状态正常"
        }

        instantStatus = instantStatus.copy(
            current = finalCurrent,
            voltage = voltageV,
            power = finalPower,
            capacity = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) / 1000,
            totalCapacity = designCapacity,
            statusText = statusText,
            supplyStatus = supply,
            temperature = temp,
            healthStatus = health,
            remainingTime = timeText
        )
    }

    fun clearHistory() {
        viewModelScope.launch {
            dao.deleteAll()
            BatteryRepository.latestHistory.clear()
        }
    }
}