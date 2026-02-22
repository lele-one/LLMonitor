package com.lele.llpower.ui.settings

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.Context
import android.os.PowerManager
import com.lele.llpower.data.SettingsManager
import com.lele.llpower.ui.dashboard.BatteryViewModel
import com.lele.llpower.ui.components.squishyClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: BatteryViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }
    // 0: 充电时, 1: 未充电时
    var selectedTab by remember { mutableIntStateOf(0) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text(text = "清除记录") },
            text = { Text(text = "确定要清空所有充电历史数据吗？\n此操作不可撤销，图表将被重置。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearDialog = false
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()) // 允许滚动
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // 1. 场景设置卡片 (合并了充电/未充电)
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth().squishyClickable { }
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    
                    // 顶部选项卡
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.primary,
                        divider = {},
                        indicator = { tabPositions ->
                            // 使用 SecondaryIndicator 并配合 tabIndicatorOffset
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("充电时") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("未充电时") }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 内容区域
                    if (selectedTab == 0) {
                        // --- 充电时配置 ---
                        
                        // 普通通知开关
                        ListItem(
                            headlineContent = { Text("通知") },
                            supportingContent = { Text("在通知栏显示实时功率信息") },
                            trailingContent = {
                                    Switch(
                                        checked = SettingsManager.isNotificationEnabled.value,
                                        onCheckedChange = { 
                                            SettingsManager.toggleNotificationEnabled(it)
                                            context.sendBroadcast(Intent("com.lele.llpower.ACTION_FORCE_UPDATE"))
                                        }
                                    )
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        )

                        // 实时活动开关 (Android 16+)
                        if (android.os.Build.VERSION.SDK_INT >= 36) {
                            ListItem(
                                headlineContent = { Text("实时活动") },
                                supportingContent = { Text("显示灵动岛风格实况通知") },
                                trailingContent = {
                                    Switch(
                                        checked = SettingsManager.isLiveNotificationEnabled.value,
                                        onCheckedChange = { 
                                            SettingsManager.toggleLiveNotificationEnabled(it)
                                            context.sendBroadcast(Intent("com.lele.llpower.ACTION_FORCE_UPDATE"))
                                        }
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                            )
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        
                        // 频率设置
                        FrequencySelectorItem(
                            title = "应用界面刷新率",
                            currentValueMs = SettingsManager.refreshRateUiCharging.value,
                            options = listOf(500L, 1000L, 2000L, 3000L, 5000L),
                            onValueChange = { 
                                SettingsManager.setRefreshRateUiCharging(it)
                                context.sendBroadcast(Intent("com.lele.llpower.ACTION_FORCE_UPDATE"))
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                        
                        FrequencySelectorItem(
                            title = "通知/组件更新率",
                            currentValueMs = SettingsManager.refreshRateNotifyCharging.value,
                            options = listOf(1000L, 2000L, 3000L, 5000L, 10000L, 30000L),
                            onValueChange = { 
                                SettingsManager.setRefreshRateNotifyCharging(it)
                                context.sendBroadcast(Intent("com.lele.llpower.ACTION_FORCE_UPDATE"))
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )

                    } else {
                        // --- 未充电时配置 ---
                        
                        ListItem(
                            headlineContent = { Text("通知") },
                            supportingContent = { Text("保留通知栏常驻显示") },
                            trailingContent = {
                                    Switch(
                                        checked = SettingsManager.isShowNotificationWhenNotCharging.value,
                                        onCheckedChange = { 
                                            SettingsManager.toggleShowNotificationWhenNotCharging(it)
                                            context.sendBroadcast(Intent("com.lele.llpower.ACTION_FORCE_UPDATE"))
                                        }
                                    )
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        )
                        
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        
                        FrequencySelectorItem(
                            title = "应用界面刷新率",
                            currentValueMs = SettingsManager.refreshRateUiNotCharging.value,
                            options = listOf(1000L, 3000L, 5000L, 10000L, 15000L, 30000L, 60000L),
                            onValueChange = { 
                                SettingsManager.setRefreshRateUiNotCharging(it)
                                context.sendBroadcast(Intent("com.lele.llpower.ACTION_FORCE_UPDATE"))
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                        
                        FrequencySelectorItem(
                            title = "通知/组件更新率",
                            currentValueMs = SettingsManager.refreshRateNotifyNotCharging.value,
                            options = listOf(3000L, 5000L, 10000L, 30000L, 60000L, 180000L, 300000L, 600000L),
                            onValueChange = { 
                                SettingsManager.setRefreshRateNotifyNotCharging(it)
                                context.sendBroadcast(Intent("com.lele.llpower.ACTION_FORCE_UPDATE"))
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    }
                }
            }
            
            // 2. 硬件修正设置 (独立卡片)
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth().squishyClickable { }
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = "硬件修正",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                    )

                    ListItem(
                        headlineContent = { Text("反转电流正负") },
                        supportingContent = { Text("如果充电时电流显示为负，请开启此项") },
                        trailingContent = {
                            Switch(
                                checked = SettingsManager.isInvertCurrent.value,
                                onCheckedChange = { 
                                    SettingsManager.toggleInvertCurrent(it)
                                    context.sendBroadcast(Intent("com.lele.llpower.ACTION_FORCE_UPDATE"))
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    ListItem(
                        headlineContent = { Text("双芯电池修正") },
                        supportingContent = { Text("如果使用双芯电池，请开启此项") },
                        trailingContent = {
                            Switch(
                                checked = SettingsManager.isDoubleCell.value,
                                onCheckedChange = { 
                                    SettingsManager.toggleDoubleCell(it)
                                    context.sendBroadcast(Intent("com.lele.llpower.ACTION_FORCE_UPDATE"))
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    ListItem(
                        headlineContent = { Text("虚拟电压") },
                        supportingContent = { Text("若设备无法读取电压，尝试使用估算电压") },
                        trailingContent = {
                            Switch(
                                checked = SettingsManager.isVirtualVoltageEnabled.value,
                                onCheckedChange = { 
                                    SettingsManager.toggleVirtualVoltageEnabled(it)
                                    // 发送广播强制刷新 Service
                                    context.sendBroadcast(Intent("com.lele.llpower.ACTION_FORCE_UPDATE"))
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    )
                }
            }

            HorizontalDivider()

            // 3. 电池优化设置（后台保活）
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            var isBatteryOptimized by remember { 
                mutableStateOf(!pm.isIgnoringBatteryOptimizations(context.packageName)) 
            }
            
            ListItem(
                modifier = Modifier.squishyClickable {
                    if (isBatteryOptimized) {
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            android.net.Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                },
                headlineContent = { 
                    Text(if (isBatteryOptimized) "禁用电池优化" else "电池优化已禁用") 
                },
                supportingContent = { 
                    Text(if (isBatteryOptimized) 
                        "点击禁用，确保后台实时更新不中断" 
                        else "后台更新不受限制"
                    ) 
                },
                leadingContent = {
                    Icon(
                        if (isBatteryOptimized) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = "电池优化",
                        tint = if (isBatteryOptimized) MaterialTheme.colorScheme.error 
                               else MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = {
                    if (isBatteryOptimized) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "前往设置")
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            )

            HorizontalDivider()

            // 4. 清除数据
            ListItem(
                modifier = Modifier.squishyClickable { showClearDialog = true },
                headlineContent = {
                    Text("清除历史数据", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                },
                supportingContent = { Text("删除所有已存储的充电功率记录") },
                leadingContent = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "清除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
    }
}

@Composable
fun FrequencySelectorItem(
    title: String,
    currentValueMs: Long,
    options: List<Long>,
    onValueChange: (Long) -> Unit,
    containerColor: androidx.compose.ui.graphics.Color
) {
    var expanded by remember { mutableStateOf(false) }

    fun formatDuration(ms: Long): String {
        return when {
            ms < 1000 -> "${ms}ms"
            ms < 60000 -> "${ms / 1000}秒"
            else -> "${ms / 60000}分钟"
        }
    }

    Box {
        ListItem(
            modifier = Modifier.clickable { expanded = true },
            headlineContent = { Text(title) },
            trailingContent = {
                Text(
                    text = formatDuration(currentValueMs),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = ListItemDefaults.colors(containerColor = containerColor)
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { ms ->
                DropdownMenuItem(
                    text = { Text(formatDuration(ms)) },
                    onClick = {
                        onValueChange(ms)
                        expanded = false
                    },
                    trailingIcon = if (ms == currentValueMs) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null
                )
            }
        }
    }
}