package com.lele.llmonitor.ui.dashboard

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lele.llmonitor.data.BatteryEngine
import com.lele.llmonitor.data.BatteryRepository
import com.lele.llmonitor.data.BatterySourceProbe
import com.lele.llmonitor.data.SettingsManager
import com.lele.llmonitor.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 保持 InstantBatteryState 数据类不变
data class InstantBatteryState(
    val voltage: Float = 0f,
    val current: Float = 0f,
    val capacity: Long = 0,
    val totalCapacity: Int = 0,
    val power: Float = 0f,
    val statusText: String = com.lele.llmonitor.i18n.l10n("未知"),
    val isCharging: Boolean = false,
    val temperature: Float = 0f,
    val temperatureFractionDigits: Int = 1,
    val supplyStatus: String = com.lele.llmonitor.i18n.l10n("检测中"),
    val healthStatus: String = com.lele.llmonitor.i18n.l10n("未知"),
    val remainingTime: String = "--",
    val powerSourceLines: List<String> = emptyList(),
    val currentSourceLines: List<String> = emptyList(),
    val voltageSourceLines: List<String> = emptyList(),
    val temperatureSourceLines: List<String> = emptyList(),
    val supplySourceLines: List<String> = emptyList(),
    val healthSourceLines: List<String> = emptyList(),
    val capacitySourceLines: List<String> = emptyList()
)

enum class HomeStartupPhase {
    ColdPending,
    EnterAnimating,
    Stable
}

data class DashboardOptionalCardsState(
    val showNotificationCard: Boolean = false,
    val showBatteryOptCard: Boolean = false,
    val showSuggestionCard: Boolean = false
)

class BatteryViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        @Volatile
        private var hasPlayedEntranceInCurrentProcess: Boolean = false

        @Volatile
        private var hasSocPreloadedInCurrentProcess: Boolean = false
    }

    private val appContext = application.applicationContext

    val displayHistory = BatteryRepository.latestHistory

    var instantStatus by mutableStateOf(InstantBatteryState())
        private set

    var homeStartupPhase by mutableStateOf(
        if (hasPlayedEntranceInCurrentProcess) HomeStartupPhase.Stable else HomeStartupPhase.ColdPending
    )
        private set

    val shouldRunEntrance: Boolean
        get() = homeStartupPhase != HomeStartupPhase.Stable

    val isStable: Boolean
        get() = homeStartupPhase == HomeStartupPhase.Stable

    var dashboardOptionalCardsSnapshot by mutableStateOf(DashboardOptionalCardsState())
        private set

    var pendingOptionalCardsUpdate by mutableStateOf<DashboardOptionalCardsState?>(null)
        private set

    var isCurveAnimationReady by mutableStateOf(BatteryRepository.isInitialHistoryLoaded())
        private set

    var cachedIsIgnoringBatteryOptimizations by mutableStateOf(true)
        private set

    var cachedHasNotificationPermission by mutableStateOf(true)
        private set

    var socPreloadReady by mutableStateOf(hasSocPreloadedInCurrentProcess)
        private set

    // 携带单调时钟时间戳：前台可稳定触发，UI 侧可过滤后台积压事件
    private val _chargingStartedEvent = MutableSharedFlow<Long>(replay = 0, extraBufferCapacity = 1)
    val chargingStartedEvent = _chargingStartedEvent.asSharedFlow()

    val recordIntervalMs = 60000L  // 每分钟记录一次
    private var designCapacity: Int = 5000
    private var designCapacitySourceLines: List<String> = emptyList()
    private var hasFrozenOptionalCardsForCurrentEntrance: Boolean = false
    private var socPreloadScheduled: Boolean = false

    fun updateDashboardPermissionCache(
        isIgnoringBatteryOptimizations: Boolean,
        hasNotificationPermission: Boolean
    ) {
        cachedIsIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations
        cachedHasNotificationPermission = hasNotificationPermission
    }

    fun onDashboardEntered(initialOptionalCards: DashboardOptionalCardsState) {
        when (homeStartupPhase) {
            HomeStartupPhase.ColdPending -> {
                dashboardOptionalCardsSnapshot = initialOptionalCards
                pendingOptionalCardsUpdate = null
                hasFrozenOptionalCardsForCurrentEntrance = true
                homeStartupPhase = HomeStartupPhase.EnterAnimating
            }
            HomeStartupPhase.EnterAnimating -> {
                if (!hasFrozenOptionalCardsForCurrentEntrance) {
                    dashboardOptionalCardsSnapshot = initialOptionalCards
                    hasFrozenOptionalCardsForCurrentEntrance = true
                }
            }
            HomeStartupPhase.Stable -> {
                dashboardOptionalCardsSnapshot = initialOptionalCards
                pendingOptionalCardsUpdate = null
            }
        }
    }

    fun updateOptionalCardsSnapshot(cardsState: DashboardOptionalCardsState) {
        when (homeStartupPhase) {
            HomeStartupPhase.EnterAnimating -> {
                if (!hasFrozenOptionalCardsForCurrentEntrance) {
                    dashboardOptionalCardsSnapshot = cardsState
                    hasFrozenOptionalCardsForCurrentEntrance = true
                } else if (dashboardOptionalCardsSnapshot != cardsState) {
                    pendingOptionalCardsUpdate = cardsState
                }
            }
            HomeStartupPhase.ColdPending -> {
                dashboardOptionalCardsSnapshot = cardsState
                pendingOptionalCardsUpdate = null
            }
            HomeStartupPhase.Stable -> {
                dashboardOptionalCardsSnapshot = cardsState
                pendingOptionalCardsUpdate = null
            }
        }
    }

    fun onDashboardEntranceFinished() {
        if (homeStartupPhase == HomeStartupPhase.Stable) return

        homeStartupPhase = HomeStartupPhase.Stable
        hasPlayedEntranceInCurrentProcess = true
        hasFrozenOptionalCardsForCurrentEntrance = false
        pendingOptionalCardsUpdate?.let {
            dashboardOptionalCardsSnapshot = it
            pendingOptionalCardsUpdate = null
        }
    }

    fun scheduleSocPreloadIfNeeded() {
        if (hasSocPreloadedInCurrentProcess || socPreloadReady) {
            socPreloadReady = true
            return
        }
        if (socPreloadScheduled) return

        socPreloadScheduled = true
        viewModelScope.launch {
            delay(1500L)
            socPreloadReady = true
            hasSocPreloadedInCurrentProcess = true
        }
    }

    init {
        BatteryRepository.init(appContext)

        // 冷启动关键路径：把设计容量探测移出主线程，避免 ViewModel 初始化阻塞首页首帧。
        viewModelScope.launch(Dispatchers.Default) {
            val designProbe = BatteryEngine.getBatteryDesignCapacityWithSources(appContext)
            designCapacity = designProbe.value
            designCapacitySourceLines = toSourceLines(designProbe.probes)
        }

        // 冷启动预热：先读取一次系统电池状态，避免首帧从默认文案跳变导致闪烁。
        viewModelScope.launch(Dispatchers.Default) {
            val context = getApplication<Application>().applicationContext
            val initialIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val initialStatus = buildInstantStatus(context, initialIntent)
            withContext(Dispatchers.Main) {
                instantStatus = initialStatus
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            BatteryRepository.awaitInitialHistoryLoaded(appContext)
            withContext(Dispatchers.Main) {
                isCurveAnimationReady = true
            }
        }

        viewModelScope.launch(Dispatchers.Default) {
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
                        _chargingStartedEvent.tryEmit(SystemClock.elapsedRealtime())
                    }
                    
                    // 状态变化 或 达到刷新间隔：执行完整数据更新
                    val newStatus = buildInstantStatus(context, intent)
                    withContext(Dispatchers.Main) {
                        instantStatus = newStatus
                    }
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

    private fun buildInstantStatus(context: Context, intent: Intent?): InstantBatteryState {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val invert = SettingsManager.isInvertCurrent.value
        val doubleCell = SettingsManager.isDoubleCell.value
        val isDebugMode = SettingsManager.isDebugModeEnabled.value
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val currentProbe = BatteryEngine.getCurrentMaWithSources(context)
        val rawCurrent = currentProbe.value
        val sanitizedRawCurrent = BatteryEngine.sanitizeCurrentReading(
            rawCurrentMa = rawCurrent,
            level = level,
            status = status,
            plugged = plugged
        )
        val finalCurrent = BatteryEngine.applyCurrentAdjustments(sanitizedRawCurrent, invert, doubleCell)
        
        // 虚拟电压逻辑
        val useVirtualVoltage = SettingsManager.isVirtualVoltageEnabled.value
        val capacityProbe = BatteryEngine.getBatteryCurrentCapacityWithSources(context)
        val voltageProbe = if (useVirtualVoltage) {
            val value = BatteryEngine.getVirtualVoltage(capacityProbe.value, designCapacity, isCharging)
            val probes = mutableListOf<BatterySourceProbe>()
            probes += BatterySourceProbe("virtual-voltage(formula)", String.format("%.3fV", value), true)
            probes += BatterySourceProbe("capacity-input", "${capacityProbe.value}mAh", capacityProbe.value > 0)
            probes += BatterySourceProbe("design-capacity-input", "${designCapacity}mAh", designCapacity > 0)
            com.lele.llmonitor.data.BatteryProbeResult(value, probes)
        } else {
            BatteryEngine.getVoltageVWithSources(context)
        }
        val voltageV = voltageProbe.value

        val rawPower = voltageV * (finalCurrent / 1000f)

        val finalPower = if (kotlin.math.abs(rawPower) < 0.005f) 0.0f else rawPower

        val supply = when {
            (plugged and BatteryManager.BATTERY_PLUGGED_AC) != 0 -> com.lele.llmonitor.i18n.l10n("电源适配器")
            (plugged and BatteryManager.BATTERY_PLUGGED_USB) != 0 -> com.lele.llmonitor.i18n.l10n("电脑 (USB)")
            (plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0 -> com.lele.llmonitor.i18n.l10n("无线充电")
            (plugged and BatteryManager.BATTERY_PLUGGED_DOCK) != 0 -> com.lele.llmonitor.i18n.l10n("底座供电")
            else -> com.lele.llmonitor.i18n.l10n("电池供电")
        }

        val tempRaw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempReading = BatteryEngine.parseBatteryTemperature(tempRaw)
        val temp = tempReading.celsius

        val health = when (intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
            BatteryManager.BATTERY_HEALTH_UNKNOWN -> com.lele.llmonitor.i18n.l10n("未知")
            BatteryManager.BATTERY_HEALTH_GOOD -> com.lele.llmonitor.i18n.l10n("良好")
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> com.lele.llmonitor.i18n.l10n("过热")
            BatteryManager.BATTERY_HEALTH_DEAD -> com.lele.llmonitor.i18n.l10n("损坏")
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> com.lele.llmonitor.i18n.l10n("过压")
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> com.lele.llmonitor.i18n.l10n("故障")
            BatteryManager.BATTERY_HEALTH_COLD -> com.lele.llmonitor.i18n.l10n("过冷")
            else -> com.lele.llmonitor.i18n.l10n("未知")
        }

        var timeText = "--"
        val remainingTimeSources = mutableListOf<BatterySourceProbe>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val remainingMs = bm.computeChargeTimeRemaining()
            remainingTimeSources += BatterySourceProbe(
                "BatteryManager.computeChargeTimeRemaining()",
                "${remainingMs}ms",
                remainingMs > 0
            )
            if (remainingMs > 0) {
                val hours = remainingMs / 3600000
                val minutes = (remainingMs % 3600000) / 60000
                timeText = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
            }
        } else {
            remainingTimeSources += BatterySourceProbe("BatteryManager.computeChargeTimeRemaining()", "API<28", false)
        }

        val statusText = when (status) {
            BatteryManager.BATTERY_STATUS_UNKNOWN -> com.lele.llmonitor.i18n.l10n("状态未知")
            BatteryManager.BATTERY_STATUS_CHARGING -> com.lele.llmonitor.i18n.l10n("充电中")
            BatteryManager.BATTERY_STATUS_DISCHARGING -> com.lele.llmonitor.i18n.l10n("放电中")
            BatteryManager.BATTERY_STATUS_NOT_CHARGING ->
                if (plugged != 0) com.lele.llmonitor.i18n.l10n("已接电源(未充电)") else com.lele.llmonitor.i18n.l10n("未充电")
            BatteryManager.BATTERY_STATUS_FULL -> com.lele.llmonitor.i18n.l10n("已充满")
            else -> com.lele.llmonitor.i18n.l10n("状态异常")
        }

        val currentSourceLines = if (isDebugMode) {
            val extra = buildList {
                add(BatterySourceProbe("sanitizeCurrentReading", "${String.format("%.1f", sanitizedRawCurrent)}mA", true))
                add(BatterySourceProbe("invert-adjust", invert.toString(), true))
                add(BatterySourceProbe("double-cell-adjust", doubleCell.toString(), true))
            }
            toSourceLines(currentProbe.probes + extra)
        } else emptyList()

        val voltageSourceLines = if (isDebugMode) toSourceLines(voltageProbe.probes) else emptyList()
        val powerSourceLines = if (isDebugMode) {
            toSourceLines(
                listOf(
                    BatterySourceProbe(
                        "derived-power",
                        String.format("V(%.3f) * I(%.1f/1000) = %.3fW", voltageV, finalCurrent, rawPower),
                        true
                    )
                )
            )
        } else emptyList()

        val tempSourceLines = if (isDebugMode) {
            toSourceLines(
                listOf(
                    BatterySourceProbe(
                        "Intent.EXTRA_TEMPERATURE",
                        "${tempReading.raw}/${tempReading.divisor} => ${BatteryEngine.formatTemperatureC(temp, tempReading.fractionDigits)}℃",
                        true
                    )
                )
            )
        } else emptyList()

        val supplySourceLines = if (isDebugMode) {
            toSourceLines(
                listOf(
                    BatterySourceProbe("Intent.EXTRA_PLUGGED", plugged.toString(), true)
                )
            )
        } else emptyList()

        val healthSourceLines = if (isDebugMode) {
            toSourceLines(
                listOf(
                    BatterySourceProbe(
                        "Intent.EXTRA_HEALTH",
                        "${intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN}",
                        true
                    )
                )
            )
        } else emptyList()

        val capacitySourceLines = if (isDebugMode) {
            toSourceLines(
                capacityProbe.probes + listOf(
                    BatterySourceProbe("design-capacity", "${designCapacity}mAh", designCapacity > 0),
                    BatterySourceProbe("remaining-time", timeText, timeText != "--")
                ) + remainingTimeSources
            ) + designCapacitySourceLines.map { "DESIGN> $it" }
        } else emptyList()

        return instantStatus.copy(
            current = finalCurrent,
            voltage = voltageV,
            power = finalPower,
            capacity = capacityProbe.value.toLong(),
            totalCapacity = designCapacity,
            statusText = statusText,
            isCharging = isCharging,
            supplyStatus = supply,
            temperature = temp,
            temperatureFractionDigits = tempReading.fractionDigits,
            healthStatus = health,
            remainingTime = timeText,
            powerSourceLines = powerSourceLines,
            currentSourceLines = currentSourceLines,
            voltageSourceLines = voltageSourceLines,
            temperatureSourceLines = tempSourceLines,
            supplySourceLines = supplySourceLines,
            healthSourceLines = healthSourceLines,
            capacitySourceLines = capacitySourceLines
        )
    }

    private fun toSourceLines(probes: List<BatterySourceProbe>): List<String> {
        return probes.map { probe ->
            val status = if (probe.success) "OK" else "FAIL"
            "[$status] ${probe.source}: ${probe.value}"
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            AppDatabase.getInstance(appContext).batteryDao().deleteAll()
            BatteryRepository.clearAll()
        }
    }
}
