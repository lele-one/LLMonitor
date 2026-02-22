package com.lele.llpower.service

import android.annotation.SuppressLint
import androidx.core.content.ContextCompat
import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.*
import android.os.SystemClock
import com.lele.llpower.data.BatteryEngine
import com.lele.llpower.data.BatteryRepository
import com.lele.llpower.data.SettingsManager
import com.lele.llpower.data.local.AppDatabase
import com.lele.llpower.data.local.BatteryEntity
import com.lele.llpower.ui.widget.BatteryWidget
import com.lele.llpower.ui.widget.BatteryWidgetKeys
import com.lele.llpower.utils.BatteryUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.lele.llpower.ui.MainActivity

class BatteryMonitorService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var db: AppDatabase
    private var designCapacity: Int = 0
    private var isForceUpdateReceiverRegistered = false
    private var isNotificationDismissReceiverRegistered = false
    
    // 充电状态变化广播接收器，用于立即响应充电器插拔
    private var powerStateReceiver: BroadcastReceiver? = null
    // 用于触发立即更新的 Channel (CONFLATED: 丢弃旧消息，只保留最新一个)
    private val updateTrigger = Channel<Unit>(Channel.CONFLATED)
    
    // 短暂 WakeLock，仅在更新时持有，避免 CPU 休眠导致更新中断
    private var wakeLock: PowerManager.WakeLock? = null
    
    // 记录上次通知更新的时间戳，用于防抖
    private var lastNotificationTime = 0L
    
    // 强制刷新广播接收器
    private val forceUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.lele.llpower.ACTION_FORCE_UPDATE") {
                // 收到强制刷新请求，立即触发更新
                updateTrigger.trySend(Unit)
            }
        }
    }
    
    // 通知被用户手动划掉的监听器 (仅影响普通通知，绝不触碰灵动岛)
    private val notificationDismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_NOTIFICATION_DISMISSED) {
                // 判断当前是处于充电还是非充电状态，关闭对应的普通通知开关
                val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                                 status == BatteryManager.BATTERY_STATUS_FULL
                
                if (isCharging) {
                    SettingsManager.toggleNotificationEnabled(false)
                } else {
                    SettingsManager.toggleShowNotificationWhenNotCharging(false)
                }
                // 触发刷新，让 UI 和通知状态同步
                context?.sendBroadcast(Intent("com.lele.llpower.ACTION_FORCE_UPDATE"))
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "battery_monitor"
        private const val LIVE_CHANNEL_ID = "battery_live_update" // 灵动岛专用通道
        private const val NOTIFICATION_ID = 1
        // Android 16 Live Updates 常量 (API 36)
        private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
        private const val ACTION_NOTIFICATION_DISMISSED = "com.lele.llpower.ACTION_NOTIFICATION_DISMISSED"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        SettingsManager.init(applicationContext)
        designCapacity = BatteryEngine.getBatteryDesignCapacity(applicationContext)
        db = AppDatabase.getInstance(applicationContext)
        
        // 注册强制刷新广播
        if (!isForceUpdateReceiverRegistered) {
            val filter = IntentFilter("com.lele.llpower.ACTION_FORCE_UPDATE")
            registerReceiver(forceUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            isForceUpdateReceiverRegistered = true
        }
        
        // 注册通知划掉广播（仅允许系统/本应用触发，避免第三方伪造广播）
        if (!isNotificationDismissReceiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                notificationDismissReceiver,
                IntentFilter(ACTION_NOTIFICATION_DISMISSED),
                ContextCompat.RECEIVER_EXPORTED
            )
            isNotificationDismissReceiverRegistered = true
        }
        
        // 启动前台服务 (显示初始通知)
        startForeground(NOTIFICATION_ID, createInitialNotification())
        
        // 注册充电状态变化广播接收器
        if (powerStateReceiver == null) {
            powerStateReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    // 拔线时先立即刷新一次通知，避免灵动岛残留到下一个周期
                    if (intent?.action == Intent.ACTION_POWER_DISCONNECTED) {
                        serviceScope.launch {
                            forceImmediateNotificationRefresh(pluggedOverride = 0)
                        }
                    }
                    // 收到充电器插拔事件，触发立即更新循环
                    updateTrigger.trySend(Unit)
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            registerReceiver(powerStateReceiver, filter)
        }

        serviceScope.launch {
            // 初始化短暂 WakeLock（不立即获取，仅在更新时短暂持有）
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "LLPower::BatteryUpdate"
            ).apply { setReferenceCounted(false) }
            
            // 延迟 0.5 秒再启动，平衡响应速度与竞态条件
            delay(500L)
            
            var lastRecordTime = 0L

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
                    val tempC = tempRaw / 10f
                    val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                    val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                                     status == BatteryManager.BATTERY_STATUS_FULL

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
                    val currentNotifyInterval = if (isCharging) {
                        SettingsManager.refreshRateNotifyCharging.value
                    } else {
                        SettingsManager.refreshRateNotifyNotCharging.value
                    }
                    targetDelay = currentNotifyInterval

                    // 2. 通知 + 组件更新：以设置里的通知刷新率为准，严格防抖
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastNotificationTime >= currentNotifyInterval) {
                        updateNotification(entry, level, plugged)
                        updateWidget(entry, tempC)
                        lastNotificationTime = now
                    }

                    // 2.5 实时更新：推送给 UI 仓库用于绘图 (不再依赖 60s 记录)
                    BatteryRepository.emitNewEntry(entry)

                    // 4. 低频记录：数据库记录 (每 60 秒)
                    if (currentTime - lastRecordTime >= 60000L) {
                        db.batteryDao().insert(entry)
                        
                        // 清理过期数据 (保留最近 48 小时)
                        val retentionPeriod = 48 * 60 * 60 * 1000L
                        db.batteryDao().clearOldData(currentTime - retentionPeriod)
                        
                        lastRecordTime = currentTime
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
                    lastNotificationTime = 0L
                }
            }
        }
        return START_STICKY
    }

    // 移除旧的 recordBatteryData 方法，逻辑已合并到主循环中
    // 保留辅助方法

    private suspend fun forceImmediateNotificationRefresh(pluggedOverride: Int? = null) {
        try {
            val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
            val tempRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val tempC = tempRaw / 10f
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

            updateNotification(entry, level, plugged)
            updateWidget(entry, tempC)
            lastNotificationTime = SystemClock.elapsedRealtime()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 更新 Glance 小组件状态
     */
    private suspend fun updateWidget(entry: BatteryEntity, temp: Float) {
        val invert = SettingsManager.isInvertCurrent.value
        val doubleCell = SettingsManager.isDoubleCell.value

        val displayCurrent = BatteryEngine.applyCurrentAdjustments(entry.current, invert, doubleCell)
        val displayPower = entry.voltage * (displayCurrent / 1000f)

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
        // - isNotificationEnabled: 控制"充电时普通通知"
        // - isShowNotificationWhenNotCharging: 控制"未充电时普通通知"
        // - isLiveNotificationEnabled: 控制"实时活动"（仅充电时生效）
        val isNormalChargingEnabled = SettingsManager.isNotificationEnabled.value
        val isNormalNotChargingEnabled = SettingsManager.isShowNotificationWhenNotCharging.value
        val isLiveEnabled = SettingsManager.isLiveNotificationEnabled.value
        
        val status = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        
        // 判定两种通知是否应该显示
        var shouldShowNormal = false
        var shouldShowLive = false
        
        // 普通通知条件：
        // - 充电时：isNormalChargingEnabled
        // - 未充电时：isNormalNotChargingEnabled
        if (isCharging) {
            shouldShowNormal = isNormalChargingEnabled
        } else {
            shouldShowNormal = isNormalNotChargingEnabled
        }
        
        // 实时活动条件：有外部供电(非纯电池) + 实况开关开启 + Android 16+
        val isPluggedIn = plugged != 0 // AC, USB, Wireless 任一即可
        if (isPluggedIn && isLiveEnabled && Build.VERSION.SDK_INT >= 36) {
            shouldShowLive = true
        }
        

        
        // ID 定义
        val ID_NORMAL = 1
        val ID_LIVE = 2
        
        // 准备 Intent
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // 准备滑动删除的 Intent (仅用于普通通知，灵动岛不绑定)
        val deleteIntentNormal = PendingIntent.getBroadcast(
            this, 100, Intent(ACTION_NOTIFICATION_DISMISSED).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // 1. 构建通知对象 (如果需要)
        
        // 供电方式文本 (两种通知共用)
        val supplyText = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Battery"
        }

        val invert = SettingsManager.isInvertCurrent.value
        val doubleCell = SettingsManager.isDoubleCell.value
        val displayCurrent = BatteryEngine.applyCurrentAdjustments(entry.current, invert, doubleCell)
        val displayPower = entry.voltage * (displayCurrent / 1000f)
        
        // --- Live Notification Builder ---
        var liveNotification: Notification? = null
        if (shouldShowLive) {
            // Android 16 Live Update Style
            // 动态获取颜色 (Monet 取色)
        val color = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val colorRes = if (isCharging) {
                android.R.color.system_accent1_600
            } else {
                android.R.color.system_neutral1_600
            }
            getColor(colorRes)
        } else {
            if (isCharging) Color.GREEN else Color.GRAY
        }

        val progressStyle = Notification.ProgressStyle()
            .setProgressTrackerIcon(Icon.createWithResource(this, com.lele.llpower.R.drawable.ic_dot))
            .addProgressSegment(
                Notification.ProgressStyle.Segment(100)
                .setColor(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val colorRes = if (isCharging) {
                                android.R.color.system_accent1_600
                            } else {
                                android.R.color.system_neutral1_600
                            }
                            getColor(colorRes)
                        } else {
                            if (isCharging) Color.GREEN else Color.LTGRAY
                        }
                    )
            )
             .setProgress(level)

            val supplyIcon = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> com.lele.llpower.R.drawable.ic_bolt
                BatteryManager.BATTERY_PLUGGED_USB -> com.lele.llpower.R.drawable.ic_usb
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> com.lele.llpower.R.drawable.ic_wireless
                else -> com.lele.llpower.R.drawable.ic_bolt
            }

            val builder = Notification.Builder(this, LIVE_CHANNEL_ID) // 使用灵动岛专用通道
                .setSmallIcon(supplyIcon)
                .setColor(color) // 适配 Monet 动态取色
                .setContentTitle("${String.format("%.1f", displayPower)}W")
                .setContentText("$level% • $supplyText • ${String.format("%.2f", entry.voltage)}V • ${displayCurrent.toInt()}mA • ${String.format("%.1f", entry.temperature)}℃")
                .setStyle(progressStyle)
                .setOngoing(true) // 灵动岛必须为 ongoing
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
            
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
                .setSmallIcon(com.lele.llpower.R.drawable.ic_dot)
                .setLargeIcon(null as android.graphics.Bitmap?)
                .setContentTitle("${String.format("%.1f", displayPower)}W")
                .setContentText("$level% • $supplyText • ${String.format("%.2f", entry.voltage)}V • ${displayCurrent.toInt()}mA • ${String.format("%.1f", entry.temperature)}℃")
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
            // Live 作为前台服务的主通知 (优先级更高)
            startForeground(ID_LIVE, liveNotification)
            // Normal 作为即使通知
            manager.notify(ID_NORMAL, normalNotification)
            
        } else if (shouldShowLive) {
            // 情况 B: 仅 Live
            startForeground(ID_LIVE, liveNotification)
            manager.cancel(ID_NORMAL)
            
        } else if (shouldShowNormal) {
            // 情况 C: 仅 Normal
            startForeground(ID_NORMAL, normalNotification)
            manager.cancel(ID_LIVE)
            
        } else {
            // 情况 D: 都不显示
            stopForeground(STOP_FOREGROUND_REMOVE)
            manager.cancel(ID_NORMAL)
            manager.cancel(ID_LIVE)
        }
    }

    private fun createNotificationChannel() {
        // 普通通知通道 (低优先级)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "电池监控后台服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示实时充电功率（静默通知，不会打扰）"
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        
        // 灵动岛专用通道 (DEFAULT 优先级，Android 16 要求)
        val liveChannel = NotificationChannel(
            LIVE_CHANNEL_ID,
            "实时活动 (灵动岛)",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "充电时显示灵动岛风格的实况通知"
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
            Intent(this, com.lele.llpower.ui.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
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
        val tempC = tempRaw / 10f
        
        val supplyText = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Battery"
        }
        
        // 选择通知渠道：有外部供电且灵动岛开启时用 Live 渠道，否则用普通渠道
        val isLiveEnabled = SettingsManager.isLiveNotificationEnabled.value
        val useChannel = if (plugged != 0 && isLiveEnabled && Build.VERSION.SDK_INT >= 36) LIVE_CHANNEL_ID else CHANNEL_ID
        
        val builder = Notification.Builder(this, useChannel)
            .setSmallIcon(com.lele.llpower.R.drawable.ic_bolt)
            .setContentTitle("--W")
            .setContentText("$level% • $supplyText • ${String.format("%.1f", tempC)}℃")
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

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
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
        if (isNotificationDismissReceiverRegistered) {
            try { unregisterReceiver(notificationDismissReceiver) } catch (_: Exception) {}
            isNotificationDismissReceiverRegistered = false
        }
        super.onDestroy()
    }
}
