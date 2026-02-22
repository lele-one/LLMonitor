package com.lele.llpower.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lele.llpower.data.SettingsManager
import com.lele.llpower.ui.components.InfoCard
import com.lele.llpower.ui.components.PowerCurveCard
import com.lele.llpower.ui.components.TemperatureCurveCard
import com.lele.llpower.ui.components.HdrGlowWrapper
import com.lele.llpower.ui.components.squishyClickable
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun StaggeredEntry(index: Int, content: @Composable () -> Unit) {
    val visible = rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 30L) // Subtle staggered delay
        visible.value = true
    }
    AnimatedVisibility(
        visible = visible.value,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                slideInVertically(
                    initialOffsetY = { 20 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: BatteryViewModel,
    onNavigateToSettings: () -> Unit,
    onSetHdrMode: (Boolean) -> Unit
) {
    val history = viewModel.displayHistory
    val instant = viewModel.instantStatus

    // 1. 从 SettingsManager 读取设置
    val invertCurrent by SettingsManager.isInvertCurrent
    val isDoubleCell by SettingsManager.isDoubleCell
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // 0. 权限与生命周期监听
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isIgnoringBatteryOptimizations by remember { mutableStateOf(true) }
    
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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoringBatteryOptimizations = SettingsManager.isIgnoringBatteryOptimizations(context)
                
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

    // 0.3 虚拟电压建议逻辑
    var showVirtualVoltageSuggestion by remember { mutableStateOf(false) }
    val isVirtualVoltageEnabled by SettingsManager.isVirtualVoltageEnabled
    val isVirtualVoltageSuggestionDismissed by SettingsManager.isVirtualVoltageSuggestionDismissed
    
    // 监听电压是否为 0V (持续 10 秒)
    LaunchedEffect(instant.voltage, isVirtualVoltageEnabled, isVirtualVoltageSuggestionDismissed) {
        if (!isVirtualVoltageEnabled && !isVirtualVoltageSuggestionDismissed) {
            if (instant.voltage == 0f) {
                // 如果电压为 0，启动 10 秒倒计时
                delay(10000L) // 10 seconds
                showVirtualVoltageSuggestion = true
            } else {
                // 如果电压不为 0，重置状态
                showVirtualVoltageSuggestion = false
            }
        }
    }

    // HDR Glow logic
    var showHdrGlow by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel.chargingStartedEvent) {
        viewModel.chargingStartedEvent.collect {
            // 双重保险：只有当当前确实处于充电或充满状态时，才触发呼吸灯特效
            // 这可以过滤掉在后台插拔电源时留在缓冲区的过期事件
            if (instant.statusText == "充电中" || instant.statusText == "已充满") {
                onSetHdrMode(true)
                showHdrGlow = true
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("LLPower", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                            Text("beta", modifier = Modifier.padding(start = 4.dp, top = 4.dp), style = MaterialTheme.typography.bodySmall)
                        }
                        val subtitle = if (instant.remainingTime != "--") {
                            "状态：${instant.statusText} (余 ${instant.remainingTime})"
                        } else "状态：${instant.statusText}"
                        Text(subtitle, style = MaterialTheme.typography.bodySmall)
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .squishyClickable { onNavigateToSettings() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Settings, "设置")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "spacer_top") { Spacer(Modifier.height(8.dp)) }

            // 0.1 权限引导：通知权限 (Android 13+)
            val isNotificationDismissed by SettingsManager.isNotificationPermissionDismissed
            if (!hasNotificationPermission && !isNotificationDismissed && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                item(key = "permission_notification") {
                    StaggeredEntry(index = 0) {
                        ElevatedCard(
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "实时通知受限",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "为了在通知栏显示实时充电功率，需要授予通知权限。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { /* Handled by modifier */ },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                        modifier = Modifier.weight(1f).squishyClickable { 
                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) 
                                        }
                                    ) {
                                        Text("允许通知", color = MaterialTheme.colorScheme.onTertiary)
                                    }
                                    OutlinedButton(
                                        onClick = { /* Handled by modifier */ },
                                        modifier = Modifier.weight(1f).squishyClickable { 
                                            SettingsManager.setNotificationPermissionDismissed(true) 
                                        }
                                    ) {
                                        Text("不再提醒")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 0.2 权限引导：忽略电池优化
            val isBatteryOptDismissed by SettingsManager.isBatteryOptimizationDismissed
            if (!isIgnoringBatteryOptimizations && !isBatteryOptDismissed) {
                item(key = "permission_battery_opt") {
                    StaggeredEntry(index = 1) {
                        ElevatedCard(
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "后台保活受限",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "为了保证桌面小组件实时刷新，请将本应用加入电池优化白名单。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { /* Handled by modifier */ },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier.weight(1f).squishyClickable {
                                            SettingsManager.requestIgnoreBatteryOptimizations(context)
                                        }
                                    ) {
                                        Text("立即开启", color = MaterialTheme.colorScheme.onError)
                                    }
                                    OutlinedButton(
                                        onClick = { /* Handled by modifier */ },
                                        modifier = Modifier.weight(1f).squishyClickable {
                                            SettingsManager.setBatteryOptimizationDismissed(true)
                                        }
                                    ) {
                                        Text("不再提醒")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // 0.3 建议卡片：虚拟电压
            if (showVirtualVoltageSuggestion) {
                item(key = "suggestion_virtual_voltage") {
                    StaggeredEntry(index = 2) {
                        ElevatedCard(
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "检测到电压读数异常",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    "设备似乎无法读取实时电压。建议开启“虚拟电压”功能以获得估算数据。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { /* Handled by modifier */ },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.weight(1f).squishyClickable {
                                            SettingsManager.toggleVirtualVoltageEnabled(true)
                                            context.sendBroadcast(android.content.Intent("com.lele.llpower.ACTION_FORCE_UPDATE"))
                                            showVirtualVoltageSuggestion = false
                                        }
                                    ) {
                                        Text("开启虚拟电压", color = MaterialTheme.colorScheme.onPrimary)
                                    }
                                    OutlinedButton(
                                        onClick = { /* Handled by modifier */ },
                                        modifier = Modifier.weight(1f).squishyClickable {
                                            SettingsManager.setVirtualVoltageSuggestionDismissed(true)
                                            showVirtualVoltageSuggestion = false
                                        }
                                    ) {
                                        Text("不再提醒")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 1. 功率与电流
            item(key = "card_power_current") {
                StaggeredEntry(index = 3) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        InfoCard("瞬时功率", "${String.format("%.2f", instant.power)} W", Modifier.weight(1f).squishyClickable { /* Optional tap logic */ })
                        InfoCard("电池电流", "${instant.current.toInt()} mA", Modifier.weight(1f).squishyClickable { /* Optional tap logic */ })
                    }
                }
            }

            // 2. 电压与温度
            item(key = "card_voltage_temp") {
                StaggeredEntry(index = 4) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        val isVirtualVoltage by SettingsManager.isVirtualVoltageEnabled
                        val voltageTitle = if (isVirtualVoltage) "虚拟电压" else "电池电压"
                        InfoCard(voltageTitle, "${String.format("%.2f", instant.voltage)} V", Modifier.weight(1f).squishyClickable { /* Optional tap logic */ })
                        InfoCard("电池温度", "${String.format("%.1f", instant.temperature)} °C", Modifier.weight(1f).squishyClickable { /* Optional tap logic */ })
                    }
                }
            }

            // 3. 供电状态与健康
            item(key = "card_supply_health") {
                StaggeredEntry(index = 5) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        HdrGlowWrapper(
                            visible = showHdrGlow,
                            onAnimationEnd = {
                                showHdrGlow = false
                                onSetHdrMode(false)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            AnimatedContent(
                                targetState = instant.supplyStatus,
                                transitionSpec = {
                                    fadeIn() togetherWith fadeOut()
                                },
                                label = "SupplyStatusAnimation"
                            ) { status ->
                                InfoCard("供电状态", status, Modifier.fillMaxWidth().squishyClickable { /* Optional tap logic */ })
                            }
                        }
                        InfoCard("电池状态", instant.healthStatus, Modifier.weight(1f).squishyClickable { /* Optional tap logic */ })
                    }
                }
            }

            // 4. 电量百分比
            item(key = "card_capacity") {
                StaggeredEntry(index = 6) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().squishyClickable { /* Optional: show details */ },
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("当前剩余电量 / 总容量", style = MaterialTheme.typography.labelMedium)
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
                        }
                    }
                }
            }

            item(key = "card_power_curve") {
                StaggeredEntry(index = 7) {
                    PowerCurveCard(
                        history = history,
                        recordIntervalMs = viewModel.recordIntervalMs,
                        invert = invertCurrent,
                        isDualCell = isDoubleCell,
                        modifier = Modifier.squishyClickable { /* Zoom or details */ }
                    )
                }
            }

            // 6. 温度曲线
            item(key = "card_temperature_curve") {
                StaggeredEntry(index = 8) {
                    TemperatureCurveCard(
                        history = history,
                        modifier = Modifier.squishyClickable { /* Zoom or details */ }
                    )
                }
            }
            item(key = "spacer_bottom") { Spacer(Modifier.height(24.dp)) }
        }
    }
}