package com.lele.llmonitor.service

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.*
import android.os.SystemClock
import com.lele.llmonitor.data.BatteryEngine
import com.lele.llmonitor.data.BatteryRepository
import com.lele.llmonitor.data.SettingsManager
import com.lele.llmonitor.data.local.AppDatabase
import com.lele.llmonitor.data.local.BatteryEntity
import com.lele.llmonitor.ui.widget.BatteryWidget
import com.lele.llmonitor.ui.widget.BatteryWidgetKeys
import com.lele.llmonitor.utils.BatteryUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.core.content.ContextCompat
import com.lele.llmonitor.ui.MainActivity

class BatteryMonitorService : Service() {
    override fun onCreate() {
        super.onCreate()
        isRunning = true
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var db: AppDatabase
    private var designCapacity: Int = 0
    private val dependencyInitMutex = Mutex()
    private var isForceUpdateReceiverRegistered = false
    private var serviceStartElapsedMs: Long = 0L
    
    // 充电状态变化广播接收器，用于立即响应充电器插拔
    private var powerStateReceiver: BroadcastReceiver? = null
    // 用于触发立即更新的 Channel (CONFLATED: 丢弃旧消息，只保留最新一个)
    private val updateTrigger = Channel<Unit>(Channel.CONFLATED)
    
    // 短暂 WakeLock，仅在更新时持有，避免 CPU 休眠导致更新中断
    private var wakeLock: PowerManager.WakeLock? = null
    private val visualRefreshScheduler = VisualRefreshScheduler()
    
    // Live 通知资格最近一次成立时间（用于抗短时 plugged 抖动）
    private var lastLiveEligibleTime = 0L
    // “本次关闭”后仅在当前外接电源会话内禁用 Live，插拔后自动恢复
    private var isLiveSuppressedForCurrentSession = false
    // 仅用于 Live：首次切入 Live 时强制重建前台通知，避免系统保留旧会话导致无法再次提升
    private var isLiveForegroundArmed = false
    // 防止重复 startService 导致多条监控循环并发运行
    private var isMonitorLoopStarted = false
    // 跟踪当前服务是否处于前台，避免 startForegroundService 触发时遗漏前台升级。
    private var isForegroundActive = false
    // 电池温度显示精度（0~3 位小数），随读取结果自适应
    private var lastBatteryTempFractionDigits: Int = 1
    
    // 强制刷新广播接收器
    private val forceUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SettingsManager.ACTION_FORCE_UPDATE) {
                // 收到强制刷新请求，立即触发更新
                visualRefreshScheduler.onExternalTrigger()
                updateTrigger.trySend(Unit)
            }
        }
    }
    
    companion object {
        @Volatile
        var isRunning: Boolean = false

        private const val CHANNEL_ID = "battery_monitor"
        // v2: 新通道 ID 用于规避旧版本通道配置被系统锁定后只能卸载重装的问题
        private const val LIVE_CHANNEL_ID = "battery_live_update_v2" // 灵动岛专用通道
        private const val NOTIFICATION_ID = 1
        private const val SECONDARY_NOTIFICATION_ID = 2
        private const val LIVE_OFF_GRACE_MS = 5000L
        // Android 16 Live Updates 常量 (API 36)
        private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
        private const val ACTION_NOTIFICATION_DISMISSED = "com.lele.llmonitor.ACTION_NOTIFICATION_DISMISSED"
        private const val ACTION_LIVE_NOTIFICATION_CLOSE_ONCE =
            "com.lele.llmonitor.ACTION_LIVE_NOTIFICATION_CLOSE_ONCE"
        const val ACTION_KEEP_ALIVE_KICK = "com.lele.llmonitor.ACTION_KEEP_ALIVE_KICK"
        private const val KEEP_ALIVE_REQUEST_CODE = 20260306
        private const val KEEP_ALIVE_INTERVAL_MS = 2 * 60 * 1000L
        private const val VISUAL_UPDATE_TIMEOUT_MS = 2500L
        private const val STARTUP_WIDGET_DEFER_MS = 8000L
        // 数据库保留清理周期（写入改为每次采样都写）
        private const val DB_CLEANUP_INTERVAL_MS = 10_000L
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (serviceStartElapsedMs == 0L) {
            serviceStartElapsedMs = SystemClock.elapsedRealtime()
        }
        val action = intent?.action

        // 关键修复：首次拉起时先尽快进入前台，避免初始化耗时触发
        // ForegroundServiceDidNotStartInTimeException。
        if (!isForegroundActive) {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createBootstrapNotification())
            isForegroundActive = true
        }

        SettingsManager.init(applicationContext)
        BatteryRepository.init(applicationContext)
        if (
            action == Intent.ACTION_POWER_CONNECTED ||
            action == Intent.ACTION_POWER_DISCONNECTED ||
            action == SettingsManager.ACTION_FORCE_UPDATE ||
            action == ACTION_KEEP_ALIVE_KICK
        ) {
            visualRefreshScheduler.onExternalTrigger()
            if (isMonitorLoopStarted) {
                scheduleNextKeepAliveKick()
                updateTrigger.trySend(Unit)
                return START_STICKY
            }
        }

        when (intent?.action) {
            ACTION_NOTIFICATION_DISMISSED -> {
                handleNormalNotificationDismiss()
                visualRefreshScheduler.onExternalTrigger()
                if (isMonitorLoopStarted) {
                    updateTrigger.trySend(Unit)
                    return START_STICKY
                }
            }
            ACTION_LIVE_NOTIFICATION_CLOSE_ONCE -> {
                isLiveSuppressedForCurrentSession = true
                visualRefreshScheduler.onExternalTrigger()
                if (isMonitorLoopStarted) {
                    updateTrigger.trySend(Unit)
                    return START_STICKY
                }
            }
        }

        // 服务已在运行时：不重复初始化/重建前台，只触发一次立即刷新并续约自愈闹钟
        if (isMonitorLoopStarted) {
            scheduleNextKeepAliveKick()
            updateTrigger.trySend(Unit)
            return START_STICKY
        }

        scheduleNextKeepAliveKick()
        
        // 注册强制刷新广播
        if (!isForceUpdateReceiverRegistered) {
            val filter = IntentFilter(SettingsManager.ACTION_FORCE_UPDATE)
            ContextCompat.registerReceiver(
                this,
                forceUpdateReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isForceUpdateReceiverRegistered = true
        }
        
        // 注册充电状态变化广播接收器
        if (powerStateReceiver == null) {
            powerStateReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    // 拔线时先立即刷新一次通知，避免灵动岛残留到下一个周期
                    when (intent?.action) {
                        Intent.ACTION_POWER_CONNECTED -> {
                            // 新会话开始，清除“本次关闭”状态
                            isLiveSuppressedForCurrentSession = false
                        }
                        Intent.ACTION_POWER_DISCONNECTED -> {
                            // 会话结束，清除“本次关闭”状态并立即刷新
                            isLiveSuppressedForCurrentSession = false
                            serviceScope.launch {
                                forceImmediateNotificationRefresh(pluggedOverride = 0)
                            }
                        }
                    }
                    // 收到充电器插拔事件，触发立即更新循环
                    visualRefreshScheduler.onExternalTrigger()
                    updateTrigger.trySend(Unit)
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            registerReceiver(powerStateReceiver, filter)
        }

        if (!isMonitorLoopStarted) {
            isMonitorLoopStarted = true
            serviceScope.launch {
                ensureDependenciesInitialized()
                // 初始化短暂 WakeLock（不立即获取，仅在更新时短暂持有）
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "LLMonitor::BatteryUpdate"
                ).apply { setReferenceCounted(false) }
                
                var lastCleanupTime = 0L

                while (isActive) {
                    val loopStartTime = SystemClock.elapsedRealtime()
                    var targetDelay = 3000L // 默认延时
                    try {
                        // 获取短暂 WakeLock，确保更新期间 CPU 不休眠（最多 5 秒自动释放）
                        wakeLock?.acquire(5000L)
                        
                        val currentTime = System.currentTimeMillis()
                        
                        // 1. 先获取电池状态 (Level, Status, Temp)
                        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
                        val tempRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
                        val tempReading = BatteryEngine.parseBatteryTemperature(tempRaw)
                        val tempC = tempReading.celsius
                        lastBatteryTempFractionDigits = tempReading.fractionDigits
                        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
                        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                                         status == BatteryManager.BATTERY_STATUS_FULL
                        val isOnExternalPower = plugged != 0
                        if (!isOnExternalPower && isLiveSuppressedForCurrentSession) {
                            // 兜底：若漏掉插拔广播，离开外接电源时也自动清理会话态
                            isLiveSuppressedForCurrentSession = false
                        }

                        // 2. 获取电流和电压
                        val invert = SettingsManager.isInvertCurrent.value
                        val doubleCell = SettingsManager.isDoubleCell.value
                        val useVirtualVoltage = SettingsManager.isVirtualVoltageEnabled.value
                        
                        // 原始电流：入库时保持原始值，便于后续在显示层动态套用修正
                        val rawCurrentMa = BatteryEngine.getCurrentMa(applicationContext)
                        val sanitizedRawCurrentMa = BatteryEngine.sanitizeCurrentReading(
                            rawCurrentMa = rawCurrentMa,
                            level = level,
                            status = status,
                            plugged = plugged
                        )
                        
                        // 根据设置选择真实电压或虚拟电压
                        val voltageV = if (useVirtualVoltage) {
                            val totalCapacity = BatteryEngine.getBatteryDesignCapacity(applicationContext)
                            val currentCapacity = BatteryEngine.getBatteryCurrentCapacity(applicationContext)
                            BatteryEngine.getVirtualVoltage(currentCapacity, totalCapacity, isCharging)
                        } else {
                            BatteryEngine.getVoltageV(applicationContext)
                        }
                        
                        // 原始功率（基于原始电流）
                        val rawPowerW = voltageV * (sanitizedRawCurrentMa / 1000f)

                        // 构造实体对象
                        val entry = BatteryEntity(
                            timestamp = currentTime,
                            level = level,
                            voltage = voltageV,
                            current = sanitizedRawCurrentMa,
                            power = rawPowerW,
                            temperature = tempC
                        )

                        // 确定当前需要的刷新率
                        // 维度一：是否外接电源；维度二：当前是否熄屏。
                        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                        val isScreenInteractive = powerManager.isInteractive
                        val currentNotifyInterval = if (isOnExternalPower) {
                            if (isScreenInteractive) {
                                SettingsManager.refreshRateNotifyCharging.value
                            } else {
                                SettingsManager.refreshRateNotifyChargingScreenOff.value
                            }
                        } else {
                            if (isScreenInteractive) {
                                SettingsManager.refreshRateNotifyNotCharging.value
                            } else {
                                SettingsManager.refreshRateNotifyNotChargingScreenOff.value
                            }
                        }
                        // 熄屏时让主循环按用户配置间隔运行，避免被 10s 轮询上限限制。
                        val schedulerPollCeiling = if (isScreenInteractive) 10_000L else currentNotifyInterval
                        val now = SystemClock.elapsedRealtime()
                        val refreshDecision = visualRefreshScheduler.evaluate(
                            nowElapsedMs = now,
                            notifyIntervalMs = currentNotifyInterval,
                            pluggedState = plugged,
                            pollCeilingMs = schedulerPollCeiling
                        )
                        targetDelay = refreshDecision.targetDelayMs

                        if (refreshDecision.shouldPublish) {
                            publishVisuals(
                                entry = entry,
                                level = level,
                                plugged = plugged,
                                tempC = tempC,
                                nowElapsedMs = now
                            )
                        }

                        // 2.5 实时更新：推送给 UI 仓库用于绘图 (不再依赖 60s 记录)
                        // 实时推送给 UI 时增加超时保护，避免主线程拥塞反向阻塞服务更新循环
                        withTimeoutOrNull(300L) {
                            BatteryRepository.emitNewEntry(entry)
                        }

                        // 4. 每次采样都入库，保证曲线可完整恢复
                        db.batteryDao().insert(entry)

                        // 清理过期数据 (保留最近 48 小时)
                        if (currentTime - lastCleanupTime >= DB_CLEANUP_INTERVAL_MS) {
                            val retentionPeriod = 48 * 60 * 60 * 1000L
                            db.batteryDao().clearOldData(currentTime - retentionPeriod)
                            lastCleanupTime = currentTime
                        }

                    } catch (e: Exception) {
                        e.printStackTrace()
                        targetDelay = 3000L // 异常回退
                    } finally {
                        try {
                            if (wakeLock?.isHeld == true) wakeLock?.release()
                        } catch (_: Exception) {}
                    }

                    // 精准计时补偿：计算代码执行耗时，并从下一次等待时间中扣除
                    val elapsed = SystemClock.elapsedRealtime() - loopStartTime
                    val remainingWait = (targetDelay - elapsed).coerceAtLeast(0L)

                    // 等待下一个周期，或者等待立即更新信号
                    val triggered = withTimeoutOrNull(remainingWait) {
                        updateTrigger.receive()
                        true
                    }
                    // 如果是被广播唤醒（插拔电源、强制刷新），重置防抖计时器，确保下一轮立即更新
                    if (triggered == true) {
                        visualRefreshScheduler.onExternalTrigger()
                    }
                }
            }
        } else {
            // 已有监控循环在跑，只触发一次立即刷新
            updateTrigger.trySend(Unit)
        }
        return START_STICKY
    }

    private fun scheduleNextKeepAliveKick(delayMs: Long = KEEP_ALIVE_INTERVAL_MS) {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, BatteryMonitorService::class.java)
                .setAction(ACTION_KEEP_ALIVE_KICK)
            val pendingIntent = PendingIntent.getForegroundService(
                this,
                KEEP_ALIVE_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delayMs,
                pendingIntent
            )
        } catch (_: Exception) {
            // 自愈闹钟不是核心路径，失败时维持原有 START_STICKY 行为
        }
    }

    // 移除旧的 recordBatteryData 方法，逻辑已合并到主循环中
    // 保留辅助方法

    private fun handleNormalNotificationDismiss() {
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        if (plugged != 0) {
            SettingsManager.toggleNotificationEnabled(false)
        } else {
            SettingsManager.toggleShowNotificationWhenNotCharging(false)
        }
    }

    private suspend fun publishVisuals(
        entry: BatteryEntity,
        level: Int,
        plugged: Int,
        tempC: Float,
        nowElapsedMs: Long
    ) {
        val notificationUpdated = runCatching {
            updateNotification(entry, level, plugged)
        }.onFailure { it.printStackTrace() }.isSuccess

        val shouldDeferWidget = serviceStartElapsedMs > 0L &&
            (nowElapsedMs - serviceStartElapsedMs) < STARTUP_WIDGET_DEFER_MS
        val widgetUpdated = if (shouldDeferWidget) {
            true
        } else {
            withTimeoutOrNull(VISUAL_UPDATE_TIMEOUT_MS) {
                runCatching { updateWidget(entry, tempC) }
                    .onFailure { it.printStackTrace() }
                    .isSuccess
            } ?: false
        }

        visualRefreshScheduler.onPublishResult(
            nowElapsedMs = nowElapsedMs,
            notificationUpdated = notificationUpdated,
            widgetUpdated = widgetUpdated
        )
    }

    private suspend fun forceImmediateNotificationRefresh(pluggedOverride: Int? = null) {
        try {
            ensureDependenciesInitialized()
            val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
            val tempRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val tempReading = BatteryEngine.parseBatteryTemperature(tempRaw)
            val tempC = tempReading.celsius
            lastBatteryTempFractionDigits = tempReading.fractionDigits
            val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val plugged = pluggedOverride ?: (batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

            val rawCurrentMa = BatteryEngine.getCurrentMa(applicationContext)
            val sanitizedRawCurrentMa = BatteryEngine.sanitizeCurrentReading(
                rawCurrentMa = rawCurrentMa,
                level = level,
                status = status,
                plugged = plugged
            )

            val useVirtualVoltage = SettingsManager.isVirtualVoltageEnabled.value
            val voltageV = if (useVirtualVoltage) {
                val totalCapacity = BatteryEngine.getBatteryDesignCapacity(applicationContext)
                val currentCapacity = BatteryEngine.getBatteryCurrentCapacity(applicationContext)
                BatteryEngine.getVirtualVoltage(currentCapacity, totalCapacity, isCharging)
            } else {
                BatteryEngine.getVoltageV(applicationContext)
            }

            val entry = BatteryEntity(
                timestamp = System.currentTimeMillis(),
                level = level,
                voltage = voltageV,
                current = sanitizedRawCurrentMa,
                power = voltageV * (sanitizedRawCurrentMa / 1000f),
                temperature = tempC
            )

            // 这条“立即刷新”读取路径也需要落库，保持“每读取一次都写入曲线数据”。
            db.batteryDao().insert(entry)

            publishVisuals(
                entry = entry,
                level = level,
                plugged = plugged,
                tempC = tempC,
                nowElapsedMs = SystemClock.elapsedRealtime()
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 更新 Glance 小组件状态
     */
    private suspend fun updateWidget(entry: BatteryEntity, temp: Float) {
        ensureDependenciesInitialized()
        val invert = SettingsManager.isInvertCurrent.value
        val doubleCell = SettingsManager.isDoubleCell.value

        val displayCurrent = BatteryEngine.applyCurrentAdjustments(entry.current, invert, doubleCell)
        val displayPower = entry.voltage * (displayCurrent / 1000f)
        val displayTemp = BatteryEngine.formatTemperatureC(entry.temperature, lastBatteryTempFractionDigits)

        val currentCapacity = BatteryEngine.getBatteryCurrentCapacity(applicationContext)
        val updateTimeStr = BatteryUtils.formatTimestamp(System.currentTimeMillis())

        val context = applicationContext
        val widget = BatteryWidget()
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(BatteryWidget::class.java)

        glanceIds.forEach { glanceId ->
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[BatteryWidgetKeys.POWER] = displayPower
                prefs[BatteryWidgetKeys.CURRENT] = displayCurrent
                prefs[BatteryWidgetKeys.CAPACITY] = currentCapacity
                prefs[BatteryWidgetKeys.TOTAL_CAPACITY] = designCapacity
                prefs[BatteryWidgetKeys.TEMP] = temp
                prefs[BatteryWidgetKeys.TEMP_FRACTION_DIGITS] = lastBatteryTempFractionDigits
                prefs[BatteryWidgetKeys.UPDATE_TIME] = updateTimeStr
            }
            widget.update(context, glanceId)
        }
    }

    /**
     * 使用 Android 16 原生 Notification.ProgressStyle 更新通知
     * 支持 Live Updates (灵动岛风格)
     */

    @SuppressLint("NewApi")
    private fun updateNotification(entry: BatteryEntity, level: Int, plugged: Int) {
        val channelId = CHANNEL_ID
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 0. 判断显示策略
        // 三个开关完全独立：
        // - isNotificationEnabled: 控制com.lele.llmonitor.i18n.l10n("充电时普通通知")
        // - isShowNotificationWhenNotCharging: 控制com.lele.llmonitor.i18n.l10n("未充电时普通通知")
        // - isLiveNotificationEnabled: 控制com.lele.llmonitor.i18n.l10n("实时活动")（仅充电时生效）
        val isNormalChargingEnabled = SettingsManager.isNotificationEnabled.value
        val isNormalNotChargingEnabled = SettingsManager.isShowNotificationWhenNotCharging.value
        val isLiveEnabled = SettingsManager.isLiveNotificationEnabled.value
        val isLiveCloseActionEnabled = SettingsManager.isLiveCloseActionEnabled.value
        
        val status = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isChargingStatus =
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val isPluggedIn = plugged != 0 // AC, USB, Wireless 任一即可
        
        // 判定两种通知是否应该显示
        var shouldShowNormal = false
        var shouldShowLive = false
        
        // 普通通知条件（按是否外接电源划分场景）：
        // - 外接电源：isNormalChargingEnabled
        // - 纯电池供电：isNormalNotChargingEnabled
        if (isPluggedIn) {
            shouldShowNormal = isNormalChargingEnabled
        } else {
            shouldShowNormal = isNormalNotChargingEnabled
        }
        
        // 实时活动条件：有外部供电(非纯电池) + 实况开关开启 + Android 16+
        val supportsLive = Build.VERSION.SDK_INT >= 36
        val liveEligibleNow = supportsLive &&
            isLiveEnabled &&
            isPluggedIn &&
            !isLiveSuppressedForCurrentSession
        val nowRealtime = SystemClock.elapsedRealtime()
        if (liveEligibleNow) {
            lastLiveEligibleTime = nowRealtime
        }
        // 关闭灵动岛前增加短暂缓冲，避免电源状态瞬时抖动导致“闪断重启”
        shouldShowLive = liveEligibleNow || (
            supportsLive &&
                isLiveEnabled &&
                !isLiveSuppressedForCurrentSession &&
                isChargingStatus &&
                nowRealtime - lastLiveEligibleTime <= LIVE_OFF_GRACE_MS
            )
        

        
        // 准备 Intent
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // 准备滑动删除的 Intent (仅用于普通通知，灵动岛不绑定)
        val deleteIntentNormal = PendingIntent.getService(
            this,
            100,
            Intent(this, BatteryMonitorService::class.java).setAction(ACTION_NOTIFICATION_DISMISSED),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val closeLiveOnceIntent = PendingIntent.getService(
            this,
            101,
            Intent(this, BatteryMonitorService::class.java).setAction(ACTION_LIVE_NOTIFICATION_CLOSE_ONCE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val closeLiveOnceAction = Notification.Action.Builder(
            0,
            com.lele.llmonitor.i18n.l10n("本次关闭"),
            closeLiveOnceIntent
        ).build()
        
        // 1. 构建通知对象 (如果需要)
        
        // 供电方式文本 (两种通知共用)
        val supplyText = resolveSupplyTextForNotification(plugged)

        val invert = SettingsManager.isInvertCurrent.value
        val doubleCell = SettingsManager.isDoubleCell.value
        val displayCurrent = BatteryEngine.applyCurrentAdjustments(entry.current, invert, doubleCell)
        val displayPower = entry.voltage * (displayCurrent / 1000f)
        val displayTemp = BatteryEngine.formatTemperatureC(entry.temperature, lastBatteryTempFractionDigits)
        
        // --- Live Notification Builder ---
        var liveNotification: Notification? = null
        if (shouldShowLive) {
            // Android 16 Live Update Style
            // 动态获取颜色 (Monet 取色)
        val color = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val colorRes = if (isChargingStatus) {
                android.R.color.system_accent1_600
            } else {
                android.R.color.system_neutral1_600
            }
            getColor(colorRes)
        } else {
            if (isChargingStatus) Color.GREEN else Color.GRAY
        }

        val progressStyle = Notification.ProgressStyle()
            .setProgressTrackerIcon(Icon.createWithResource(this, com.lele.llmonitor.R.drawable.ic_dot))
            .addProgressSegment(
                Notification.ProgressStyle.Segment(100)
                .setColor(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val colorRes = if (isChargingStatus) {
                                android.R.color.system_accent1_600
                            } else {
                                android.R.color.system_neutral1_600
                            }
                            getColor(colorRes)
                        } else {
                            if (isChargingStatus) Color.GREEN else Color.LTGRAY
                        }
                    )
            )
             .setProgress(level)

            val supplyIcon = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> com.lele.llmonitor.R.drawable.ic_bolt
                BatteryManager.BATTERY_PLUGGED_USB -> com.lele.llmonitor.R.drawable.ic_usb
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> com.lele.llmonitor.R.drawable.ic_wireless
                else -> com.lele.llmonitor.R.drawable.ic_bolt
            }

            val builder = Notification.Builder(this, LIVE_CHANNEL_ID) // 使用灵动岛专用通道
                .setSmallIcon(supplyIcon)
                .setColor(color) // 适配 Monet 动态取色
                .setContentTitle("${String.format("%.1f", displayPower)}W")
                .setContentText("$level% • $supplyText • ${String.format("%.2f", entry.voltage)}V • ${displayCurrent.toInt()}mA • ${displayTemp}℃")
                .setStyle(progressStyle)
                .setOngoing(true) // 灵动岛必须为 ongoing
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
            if (isLiveCloseActionEnabled) {
                builder.addAction(closeLiveOnceAction)
            }
            
            // 请求提升为 Live Update
            val extraParams = Bundle()
            extraParams.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
            builder.addExtras(extraParams)
            
            liveNotification = builder.build()
        }
        
        // --- Normal Notification Builder ---
        var normalNotification: Notification? = null
        if (shouldShowNormal) {
             val builder = Notification.Builder(this, channelId)
                .setSmallIcon(com.lele.llmonitor.R.drawable.ic_dot)
                .setLargeIcon(null as android.graphics.Bitmap?)
                .setContentTitle("${String.format("%.1f", displayPower)}W")
                .setContentText("$level% • $supplyText • ${String.format("%.2f", entry.voltage)}V • ${displayCurrent.toInt()}mA • ${displayTemp}℃")
                .setProgress(100, level, false)
                .setOngoing(false)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .setDeleteIntent(deleteIntentNormal)
            
            normalNotification = builder.build()
        }
        
        // 2. 执行显示/隐藏逻辑
        
        if (shouldShowLive && shouldShowNormal) {
            // 情况 A: 两者都显示
            if (!isLiveForegroundArmed) {
                // 切入 Live 前先移除旧前台通知，确保本次请求能重新走 promoted ongoing 判定
                stopForeground(STOP_FOREGROUND_REMOVE)
                isForegroundActive = false
                isLiveForegroundArmed = true
            }
            // 前台通知 ID 固定，避免在不同 ID 间切换导致灵动岛“重启感”
            startForeground(NOTIFICATION_ID, liveNotification)
            isForegroundActive = true
            // Normal 作为次级通知
            manager.notify(SECONDARY_NOTIFICATION_ID, normalNotification)
            
        } else if (shouldShowLive) {
            // 情况 B: 仅 Live
            if (!isLiveForegroundArmed) {
                // 切入 Live 前先移除旧前台通知，确保本次请求能重新走 promoted ongoing 判定
                stopForeground(STOP_FOREGROUND_REMOVE)
                isForegroundActive = false
                isLiveForegroundArmed = true
            }
            startForeground(NOTIFICATION_ID, liveNotification)
            isForegroundActive = true
            manager.cancel(SECONDARY_NOTIFICATION_ID)
            
        } else if (shouldShowNormal) {
            // 情况 C: 仅 Normal
            isLiveForegroundArmed = false
            startForeground(NOTIFICATION_ID, normalNotification)
            isForegroundActive = true
            manager.cancel(SECONDARY_NOTIFICATION_ID)
            
        } else {
            // 情况 D: 都不显示
            isLiveForegroundArmed = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForegroundActive = false
            manager.cancel(SECONDARY_NOTIFICATION_ID)
        }
    }

    private fun createNotificationChannel() {
        // 普通通知通道 (低优先级)
        val channel = NotificationChannel(
            CHANNEL_ID,
            com.lele.llmonitor.i18n.l10n("电池监控后台服务"),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = com.lele.llmonitor.i18n.l10n("显示实时充电功率（静默通知，不会打扰）")
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        
        // 灵动岛专用通道 (DEFAULT 优先级，Android 16 要求)
        val liveChannel = NotificationChannel(
            LIVE_CHANNEL_ID,
            com.lele.llmonitor.i18n.l10n("实时活动 (灵动岛)"),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = com.lele.llmonitor.i18n.l10n("充电时显示灵动岛风格的实况通知")
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
        nm.createNotificationChannel(liveChannel)
    }

    private fun createPendingIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this, 0,
            Intent(this, com.lele.llmonitor.ui.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createBootstrapNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.lele.llmonitor.R.drawable.ic_dot)
            .setContentTitle("LLMonitor")
            .setContentText("Starting monitoring...")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(createPendingIntent())
            .build()
    }

    private fun resolveSupplyTextForNotification(plugged: Int): String {
        return when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> com.lele.llmonitor.i18n.l10n("电源适配器")
            BatteryManager.BATTERY_PLUGGED_USB -> com.lele.llmonitor.i18n.l10n("电脑 (USB)")
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> com.lele.llmonitor.i18n.l10n("无线充电")
            else -> com.lele.llmonitor.i18n.l10n("电池供电")
        }
    }

    /**
     * 创建初始通知 - 使用 Android 16 原生 ProgressStyle
     */
    @SuppressLint("NewApi")
    private fun createInitialNotification(): Notification {
        // 立即读取当前电池数据，直接显示真实内容
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val tempRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempReading = BatteryEngine.parseBatteryTemperature(tempRaw)
        val tempC = tempReading.celsius
        lastBatteryTempFractionDigits = tempReading.fractionDigits
        
        val supplyText = resolveSupplyTextForNotification(plugged)
        
        // 选择通知渠道：有外部供电且灵动岛开启时用 Live 渠道，否则用普通渠道
        val isLiveEnabled = SettingsManager.isLiveNotificationEnabled.value
        val useChannel = if (plugged != 0 && isLiveEnabled && Build.VERSION.SDK_INT >= 36) LIVE_CHANNEL_ID else CHANNEL_ID
        
        val builder = Notification.Builder(this, useChannel)
            .setSmallIcon(com.lele.llmonitor.R.drawable.ic_bolt)
            .setContentTitle("--W")
            .setContentText("$level% • $supplyText • ${BatteryEngine.formatTemperatureC(tempC, tempReading.fractionDigits)}℃")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(createPendingIntent())

        if (Build.VERSION.SDK_INT >= 36) {
            val progressStyle = Notification.ProgressStyle()
                .addProgressSegment(
                    Notification.ProgressStyle.Segment(100)
                        .setColor(getColor(android.R.color.system_neutral1_600))
                )
                .setProgress(level)
            
            builder.setStyle(progressStyle)
            val extras = Bundle()
            extras.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
            builder.addExtras(extras)
        }

        return builder.build()
    }

    private suspend fun ensureDependenciesInitialized() {
        if (::db.isInitialized && designCapacity > 0) return
        dependencyInitMutex.withLock {
            if (::db.isInitialized && designCapacity > 0) return
            withContext(Dispatchers.IO) {
                if (!::db.isInitialized) {
                    db = AppDatabase.getInstance(applicationContext)
                }
                if (designCapacity <= 0) {
                    designCapacity = BatteryEngine.getBatteryDesignCapacity(applicationContext)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onTaskRemoved(rootIntent: Intent?) {
        // 从最近任务划掉时，预置一次快速拉起，降低“后台掉线”窗口
        scheduleNextKeepAliveKick(delayMs = 5000L)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isRunning = false
        isMonitorLoopStarted = false
        isForegroundActive = false
        scheduleNextKeepAliveKick(delayMs = 5000L)
        // 注销广播接收器
        powerStateReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        powerStateReceiver = null
        serviceScope.cancel()
        if (isForceUpdateReceiverRegistered) {
            try { unregisterReceiver(forceUpdateReceiver) } catch (_: Exception) {}
            isForceUpdateReceiverRegistered = false
        }
        super.onDestroy()
    }
}
