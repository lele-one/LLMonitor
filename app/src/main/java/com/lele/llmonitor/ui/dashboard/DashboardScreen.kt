package com.lele.llmonitor.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberTransition
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.lele.llmonitor.data.SettingsManager
import com.lele.llmonitor.ui.components.HomeCard
import com.lele.llmonitor.ui.components.InfoCard
import com.lele.llmonitor.ui.components.NavigationBarBottomInsetSpacer
import com.lele.llmonitor.ui.components.PowerCurveCard
import com.lele.llmonitor.ui.components.TemperatureCurveCard
import com.lele.llmonitor.ui.components.HdrGlowWrapper
import com.lele.llmonitor.ui.components.rememberActiveRingRotationState
import com.lele.llmonitor.ui.components.squishyClickable
import com.lele.llmonitor.ui.components.dialog.M3EAlertDialog
import com.lele.llmonitor.ui.theme.AppCorners
import com.lele.llmonitor.ui.theme.AppShapes
import com.lele.llmonitor.ui.theme.isAppInDarkTheme
import com.lele.llmonitor.ui.theme.llClassCardBorderColor
import com.lele.llmonitor.ui.theme.resolveThemePaletteActiveRingAccentColors
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lele.llmonitor.data.BatteryEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    viewModel: BatteryViewModel,
    onSetHdrMode: (Boolean) -> Unit
) {
    val history = viewModel.displayHistory
    val liveInstant = viewModel.instantStatus
    val shouldRunEntrance = viewModel.shouldRunEntrance
    val startupPhase = viewModel.homeStartupPhase
    val curveAnimationEnabled = viewModel.isCurveAnimationReady

    // 1. 从 SettingsManager 读取设置
    val invertCurrent by SettingsManager.isInvertCurrent
    val isDoubleCell by SettingsManager.isDoubleCell
    val isDebugMode by SettingsManager.isDebugModeEnabled
    val themePalettePreset by SettingsManager.themePalettePreset
    val isDarkTheme = isAppInDarkTheme()
    val activeRingRotationState = rememberActiveRingRotationState()
    val themeActiveRingColors = remember(themePalettePreset, isDarkTheme) {
        resolveThemePaletteActiveRingAccentColors(
            preset = themePalettePreset,
            isDarkTheme = isDarkTheme
        )
    }
    // 动画期间也保持实时数据刷新，不冻结瞬时卡片数值。
    val instant = liveInstant
    val isHighTemperature = instant.temperature > 40f
    val batteryHeatRingColors = remember {
        listOf(
            Color(0xFFFF6B6B),
            Color(0xFFFF3B30),
            Color(0xFFCC1F1A),
            Color(0xFFFF8A80)
        )
    }

    // 0. 权限与生命周期监听
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(viewModel.cachedIsIgnoringBatteryOptimizations)
    }
    val dashboardScope = rememberCoroutineScope()

    LaunchedEffect(context) {
        isIgnoringBatteryOptimizations = withContext(Dispatchers.Default) {
            SettingsManager.isIgnoringBatteryOptimizations(context)
        }
    }
    
    // Android 13+ 通知权限状态
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    var animatedCardKeys by remember { mutableStateOf(setOf<String>()) }
    var showCapacityInfoDialog by remember { mutableStateOf(false) }
    LaunchedEffect(isIgnoringBatteryOptimizations, hasNotificationPermission) {
        viewModel.updateDashboardPermissionCache(
            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
            hasNotificationPermission = hasNotificationPermission
        )
    }

    if (showCapacityInfoDialog) {
        CapacityNoticeDialog(onDismiss = { showCapacityInfoDialog = false })
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                dashboardScope.launch {
                    isIgnoringBatteryOptimizations = withContext(Dispatchers.Default) {
                        SettingsManager.isIgnoringBatteryOptimizations(context)
                    }
                }
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    hasNotificationPermission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> hasNotificationPermission = isGranted }
    )

    // 进入/离开 Dashboard 都兜底关闭 HDR，确保当前页只走普通色域与激活环动画
    DisposableEffect(Unit) {
        onDispose {
            onSetHdrMode(false)
        }
    }
    LaunchedEffect(Unit) {
        onSetHdrMode(false)
    }

    // 0.3 虚拟电压建议逻辑
    var showVirtualVoltageSuggestion by remember { mutableStateOf(false) }
    val isVirtualVoltageEnabled by SettingsManager.isVirtualVoltageEnabled
    val isVirtualVoltageSuggestionDismissed by SettingsManager.isVirtualVoltageSuggestionDismissed
    
    // 监听电压是否为 0V (持续 10 秒)
    LaunchedEffect(liveInstant.voltage, isVirtualVoltageEnabled, isVirtualVoltageSuggestionDismissed) {
        if (!isVirtualVoltageEnabled && !isVirtualVoltageSuggestionDismissed) {
            if (liveInstant.voltage == 0f) {
                // 如果电压为 0，启动 10 秒倒计时
                delay(10000L) // 10 seconds
                showVirtualVoltageSuggestion = true
            } else {
                // 如果电压不为 0，重置状态
                showVirtualVoltageSuggestion = false
            }
        }
    }

    val showPowerActiveRing = instant.isCharging
    val showBatteryHeatRing = isHighTemperature
    val isNotificationDismissed by SettingsManager.isNotificationPermissionDismissed
    val rawShowNotificationCard = !hasNotificationPermission &&
        !isNotificationDismissed &&
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
    val isBatteryOptDismissed by SettingsManager.isBatteryOptimizationDismissed
    val rawShowBatteryOptCard = !isIgnoringBatteryOptimizations && !isBatteryOptDismissed
    val rawShowSuggestionCard = showVirtualVoltageSuggestion
    val rawOptionalCards = DashboardOptionalCardsState(
        showNotificationCard = rawShowNotificationCard,
        showBatteryOptCard = rawShowBatteryOptCard,
        showSuggestionCard = rawShowSuggestionCard
    )
    LaunchedEffect(Unit) {
        viewModel.onDashboardEntered(rawOptionalCards)
    }
    LaunchedEffect(rawOptionalCards) {
        viewModel.updateOptionalCardsSnapshot(rawOptionalCards)
    }

    val renderedOptionalCards = when (startupPhase) {
        HomeStartupPhase.EnterAnimating -> viewModel.dashboardOptionalCardsSnapshot
        HomeStartupPhase.ColdPending,
        HomeStartupPhase.Stable -> rawOptionalCards
    }
    val showNotificationCard = renderedOptionalCards.showNotificationCard
    val showBatteryOptCard = renderedOptionalCards.showBatteryOptCard
    val showSuggestionCard = renderedOptionalCards.showSuggestionCard
    val totalAnimatedItems = (if (showNotificationCard) 1 else 0) +
        (if (showBatteryOptCard) 1 else 0) +
        (if (showSuggestionCard) 1 else 0) +
        6

    LaunchedEffect(shouldRunEntrance, totalAnimatedItems, animatedCardKeys.size) {
        if (!shouldRunEntrance) return@LaunchedEffect
        if (totalAnimatedItems <= 0 || animatedCardKeys.size >= totalAnimatedItems) {
            viewModel.onDashboardEntranceFinished()
        }
    }

    var optionalOrderCursor = 0
    fun nextOptionalOrder(): Int {
        val current = optionalOrderCursor
        optionalOrderCursor += 1
        return current
    }

    var coreOrderCursor = 0
    fun nextCoreOrder(): Int {
        val current = coreOrderCursor
        coreOrderCursor += 1
        return current
    }

    fun hasAnimated(entryKey: String): Boolean {
        return !shouldRunEntrance || animatedCardKeys.contains(entryKey)
    }

    fun markAnimated(entryKey: String) {
        if (shouldRunEntrance && !animatedCardKeys.contains(entryKey)) {
            animatedCardKeys = animatedCardKeys + entryKey
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            item(key = "spacer_top") { Spacer(Modifier.height(8.dp)) }

            // 0.1 权限引导：通知权限 (Android 13+)
            if (showNotificationCard) {
                val notificationOrder = nextOptionalOrder()
                item(key = "permission_notification") {
                    StaggeredEntry(
                        itemKey = "permission_notification",
                        order = notificationOrder,
                        alreadyAnimated = hasAnimated("permission_notification"),
                        onAnimationCompleted = { markAnimated("permission_notification") }
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppShapes.g2(AppCorners.lg),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            border = BorderStroke(1.dp, llClassCardBorderColor(accentColor = MaterialTheme.colorScheme.tertiary)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    com.lele.llmonitor.i18n.l10n("实时通知受限"),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    com.lele.llmonitor.i18n.l10n("为了在通知栏显示实时充电功率，需要授予通知权限。"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                try {
                                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                } catch (_: Exception) {
                                                    openAppNotificationSettings(context)
                                                }
                                            } else {
                                                openAppNotificationSettings(context)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(com.lele.llmonitor.i18n.l10n("允许通知"), color = MaterialTheme.colorScheme.onTertiary)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            SettingsManager.setNotificationPermissionDismissed(true)
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(com.lele.llmonitor.i18n.l10n("不再提醒"))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 0.2 权限引导：忽略电池优化
            if (showBatteryOptCard) {
                val batteryOptOrder = nextOptionalOrder()
                item(key = "permission_battery_opt") {
                    StaggeredEntry(
                        itemKey = "permission_battery_opt",
                        order = batteryOptOrder,
                        alreadyAnimated = hasAnimated("permission_battery_opt"),
                        onAnimationCompleted = { markAnimated("permission_battery_opt") }
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppShapes.g2(AppCorners.lg),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            border = BorderStroke(1.dp, llClassCardBorderColor(accentColor = MaterialTheme.colorScheme.error)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    com.lele.llmonitor.i18n.l10n("后台保活受限"),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    com.lele.llmonitor.i18n.l10n("为了保证桌面小组件实时刷新，请将本应用加入电池优化白名单。"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            SettingsManager.requestIgnoreBatteryOptimizations(context)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(com.lele.llmonitor.i18n.l10n("立即开启"), color = MaterialTheme.colorScheme.onError)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            SettingsManager.setBatteryOptimizationDismissed(true)
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(com.lele.llmonitor.i18n.l10n("不再提醒"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // 0.3 建议卡片：虚拟电压
            if (showSuggestionCard) {
                val suggestionOrder = nextOptionalOrder()
                item(key = "suggestion_virtual_voltage") {
                    StaggeredEntry(
                        itemKey = "suggestion_virtual_voltage",
                        order = suggestionOrder,
                        alreadyAnimated = hasAnimated("suggestion_virtual_voltage"),
                        onAnimationCompleted = { markAnimated("suggestion_virtual_voltage") }
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppShapes.g2(AppCorners.lg),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            border = BorderStroke(1.dp, llClassCardBorderColor(accentColor = MaterialTheme.colorScheme.primary)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        com.lele.llmonitor.i18n.l10n("检测到电压读数异常"),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    com.lele.llmonitor.i18n.l10n("设备似乎无法读取实时电压。建议开启“虚拟电压”功能以获得估算数据。"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            SettingsManager.toggleVirtualVoltageEnabled(true)
                                            SettingsManager.sendForceUpdateBroadcast(context)
                                            showVirtualVoltageSuggestion = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(com.lele.llmonitor.i18n.l10n("开启虚拟电压"), color = MaterialTheme.colorScheme.onPrimary)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            SettingsManager.setVirtualVoltageSuggestionDismissed(true)
                                            showVirtualVoltageSuggestion = false
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(com.lele.llmonitor.i18n.l10n("不再提醒"))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 1. 功率与电流
            val powerCurrentOrder = nextCoreOrder()
            item(key = "card_power_current") {
                StaggeredEntry(
                    itemKey = "card_power_current",
                    order = powerCurrentOrder,
                    alreadyAnimated = hasAnimated("card_power_current"),
                    onAnimationCompleted = { markAnimated("card_power_current") }
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        HdrGlowWrapper(
                            visible = showPowerActiveRing,
                            modifier = Modifier.weight(1f),
                            cardShape = AppShapes.g2(AppCorners.lg),
                            activeBorderColors = themeActiveRingColors,
                            rotationDegreesState = activeRingRotationState
                        ) {
                            InfoCard(
                                com.lele.llmonitor.i18n.l10n("瞬时功率"),
                                "${String.format("%.2f", instant.power)} W",
                                Modifier.fillMaxWidth().squishyClickable { /* Optional tap logic */ },
                                sourceLines = if (isDebugMode) instant.powerSourceLines else emptyList()
                            )
                        }
                        HdrGlowWrapper(
                            visible = showBatteryHeatRing,
                            modifier = Modifier.weight(1f),
                            cardShape = AppShapes.g2(AppCorners.lg),
                            activeBorderColors = batteryHeatRingColors,
                            rotationDegreesState = activeRingRotationState
                        ) {
                            InfoCard(
                                com.lele.llmonitor.i18n.l10n("电池温度"),
                                "${BatteryEngine.formatTemperatureC(instant.temperature, instant.temperatureFractionDigits)} °C",
                                Modifier.fillMaxWidth().squishyClickable { /* Optional tap logic */ },
                                sourceLines = if (isDebugMode) instant.temperatureSourceLines else emptyList()
                            )
                        }
                    }
                }
            }

            // 2. 电压与温度
            val voltageTempOrder = nextCoreOrder()
            item(key = "card_voltage_temp") {
                StaggeredEntry(
                    itemKey = "card_voltage_temp",
                    order = voltageTempOrder,
                    alreadyAnimated = hasAnimated("card_voltage_temp"),
                    onAnimationCompleted = { markAnimated("card_voltage_temp") }
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        val isVirtualVoltage by SettingsManager.isVirtualVoltageEnabled
                        val voltageTitle = if (isVirtualVoltage) com.lele.llmonitor.i18n.l10n("虚拟电压") else com.lele.llmonitor.i18n.l10n("电池电压")
                        InfoCard(
                            voltageTitle,
                            "${String.format("%.2f", instant.voltage)} V",
                            Modifier.weight(1f).squishyClickable { /* Optional tap logic */ },
                            sourceLines = if (isDebugMode) instant.voltageSourceLines else emptyList()
                        )
                        InfoCard(
                            com.lele.llmonitor.i18n.l10n("电池电流"),
                            "${instant.current.toInt()} mA",
                            Modifier.weight(1f).squishyClickable { /* Optional tap logic */ },
                            sourceLines = if (isDebugMode) instant.currentSourceLines else emptyList()
                        )
                    }
                }
            }

            // 3. 供电状态与健康
            val supplyHealthOrder = nextCoreOrder()
            item(key = "card_supply_health") {
                StaggeredEntry(
                    itemKey = "card_supply_health",
                    order = supplyHealthOrder,
                    alreadyAnimated = hasAnimated("card_supply_health"),
                    onAnimationCompleted = { markAnimated("card_supply_health") }
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        InfoCard(
                            com.lele.llmonitor.i18n.l10n("供电状态"),
                            instant.supplyStatus,
                            Modifier.weight(1f).squishyClickable { /* Optional tap logic */ },
                            singleLineAutoShrink = true,
                            sourceLines = if (isDebugMode) instant.supplySourceLines else emptyList()
                        )
                        InfoCard(
                            com.lele.llmonitor.i18n.l10n("电池状态"),
                            instant.healthStatus,
                            Modifier.weight(1f).squishyClickable { /* Optional tap logic */ },
                            singleLineAutoShrink = true,
                            sourceLines = if (isDebugMode) instant.healthSourceLines else emptyList()
                        )
                    }
                }
            }

            // 4. 电量百分比
            val capacityOrder = nextCoreOrder()
            item(key = "card_capacity") {
                StaggeredEntry(
                    itemKey = "card_capacity",
                    order = capacityOrder,
                    alreadyAnimated = hasAnimated("card_capacity"),
                    onAnimationCompleted = { markAnimated("card_capacity") }
                ) {
                    HomeCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .squishyClickable { /* Optional: show details */ }
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = com.lele.llmonitor.i18n.l10n("系统剩余容量 / 总容量"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                IconButton(
                                    onClick = { showCapacityInfoDialog = true },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = com.lele.llmonitor.i18n.l10n("容量计算说明"),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))

                            val percentage = if (instant.totalCapacity > 0) {
                                (instant.capacity.toFloat() / instant.totalCapacity * 100).toInt()
                            } else 0

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    text = "${instant.capacity} / ${instant.totalCapacity} mAh",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "$percentage%",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            if (instant.totalCapacity > 0) {
                                LinearProgressIndicator(
                                    progress = { (instant.capacity.toFloat() / instant.totalCapacity).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            }

                            if (isDebugMode && instant.capacitySourceLines.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                                Spacer(Modifier.height(6.dp))
                                instant.capacitySourceLines.forEach { line ->
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val powerCurveOrder = nextCoreOrder()
            item(key = "card_power_curve") {
                StaggeredEntry(
                    itemKey = "card_power_curve",
                    order = powerCurveOrder,
                    alreadyAnimated = hasAnimated("card_power_curve"),
                    onAnimationCompleted = { markAnimated("card_power_curve") }
                ) {
                    PowerCurveCard(
                        history = history,
                        recordIntervalMs = viewModel.recordIntervalMs,
                        invert = invertCurrent,
                        isDualCell = isDoubleCell,
                        animationEnabled = curveAnimationEnabled,
                        modifier = Modifier.squishyClickable { /* Zoom or details */ }
                    )
                }
            }

            // 6. 温度曲线
            val temperatureCurveOrder = nextCoreOrder()
            item(key = "card_temperature_curve") {
                StaggeredEntry(
                    itemKey = "card_temperature_curve",
                    order = temperatureCurveOrder,
                    alreadyAnimated = hasAnimated("card_temperature_curve"),
                    onAnimationCompleted = { markAnimated("card_temperature_curve") }
                ) {
                    TemperatureCurveCard(
                        history = history,
                        animationEnabled = curveAnimationEnabled,
                        modifier = Modifier.squishyClickable { /* Zoom or details */ }
                    )
                }
            }
                item(key = "spacer_bottom") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun CapacityNoticeDialog(
    onDismiss: () -> Unit
) {
    NoticeInfoDialog(
        title = com.lele.llmonitor.i18n.l10n("容量计算说明"),
        intro = com.lele.llmonitor.i18n.l10n("以下内容用于解释本卡片容量数据来源："),
        items = listOf(
            com.lele.llmonitor.i18n.l10n("此处容量与百分比基于系统提供的电池容量数据计算，并非系统状态栏百分比。"),
            com.lele.llmonitor.i18n.l10n("部分厂商存在锁容策略，可能出现系统显示已充满，但实际电池容量尚未达到满值的情况。")
        ),
        confirmText = com.lele.llmonitor.i18n.l10n("知道了"),
        onDismiss = onDismiss
    )
}

@Composable
private fun NoticeInfoDialog(
    title: String,
    intro: String,
    items: List<String>,
    confirmText: String,
    onDismiss: () -> Unit
) {
    M3EAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = intro,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
                items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                NavigationBarBottomInsetSpacer()
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(confirmText)
            }
        }
    )
}

@Composable
private fun StaggeredEntry(
    itemKey: String,
    order: Int,
    alreadyAnimated: Boolean,
    onAnimationCompleted: () -> Unit,
    content: @Composable () -> Unit
) {
    val resolvedDelayMillis = resolveEntryDelayMillis(order)
    var hasStarted by remember(itemKey, resolvedDelayMillis, alreadyAnimated) {
        mutableStateOf(alreadyAnimated || resolvedDelayMillis == 0)
    }
    val transitionState = remember(itemKey, alreadyAnimated) {
        MutableTransitionState(alreadyAnimated).apply { targetState = alreadyAnimated }
    }
    LaunchedEffect(itemKey, resolvedDelayMillis, alreadyAnimated) {
        if (alreadyAnimated) {
            hasStarted = true
            transitionState.targetState = true
            return@LaunchedEffect
        }
        if (hasStarted || resolvedDelayMillis <= 0) {
            hasStarted = true
            return@LaunchedEffect
        }
        delay(resolvedDelayMillis.toLong())
        hasStarted = true
    }
    LaunchedEffect(hasStarted, alreadyAnimated) {
        transitionState.targetState = alreadyAnimated || hasStarted
    }

    val transition = rememberTransition(
        transitionState = transitionState,
        label = "dashboard_stagger_transition_${itemKey}_$order"
    )
    var completed by remember(itemKey) { mutableStateOf(false) }

    val progress by transition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = ENTRY_ANIMATION_DURATION_MS,
                easing = ENTRY_MOTION_EASING
            )
        },
        label = "dashboard_stagger_progress_${itemKey}_$order"
    ) { isEntered: Boolean ->
        if (isEntered) 1f else 0f
    }

    LaunchedEffect(
        transition.currentState,
        transition.targetState,
        completed
    ) {
        if (!completed && transition.currentState && transition.targetState) {
            completed = true
            onAnimationCompleted()
        }
    }

    val density = LocalDensity.current
    val startOffsetPx = with(density) { ENTRY_TRANSLATE_Y_DP.dp.toPx() }
    val isVisible = alreadyAnimated || hasStarted
    val alpha = if (isVisible) {
        ENTRY_INITIAL_ALPHA + (1f - ENTRY_INITIAL_ALPHA) * progress
    } else {
        0f
    }
    val scale = if (isVisible) {
        ENTRY_INITIAL_SCALE + (1f - ENTRY_INITIAL_SCALE) * progress
    } else {
        ENTRY_INITIAL_SCALE
    }
    val translationY = startOffsetPx * (1f - progress)

    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha
            this.scaleX = scale
            this.scaleY = scale
            this.translationY = translationY
            clip = false
            compositingStrategy = CompositingStrategy.ModulateAlpha
        }
    )
    {
        content()
    }
}

private val ENTRY_MOTION_EASING = FastOutSlowInEasing
private const val ENTRY_FIRST_ROW_DELAY_MS = 0
private const val ENTRY_STAGGER_BASE_STEP_MS = 92.0
private const val ENTRY_STAGGER_EXPONENT = 1.2
private const val ENTRY_STAGGER_MAX_DELAY_MS = 880
private const val ENTRY_ANIMATION_DURATION_MS = 340
private const val ENTRY_TRANSLATE_Y_DP = 18f
private const val ENTRY_INITIAL_ALPHA = 0.18f
private const val ENTRY_INITIAL_SCALE = 0.968f

private fun resolveEntryDelayMillis(order: Int): Int {
    val curvedOffset = (ENTRY_STAGGER_BASE_STEP_MS * order.toDouble().pow(ENTRY_STAGGER_EXPONENT)).roundToInt()
    return (ENTRY_FIRST_ROW_DELAY_MS + curvedOffset).coerceAtMost(ENTRY_STAGGER_MAX_DELAY_MS)
}

private fun openAppNotificationSettings(context: android.content.Context) {
    try {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(fallbackIntent)
    }
}
