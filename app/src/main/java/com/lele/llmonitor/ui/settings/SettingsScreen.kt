package com.lele.llmonitor.ui.settings

import android.app.Activity
import android.content.Intent
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.graphics.BlurMaskFilter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.InvertColors
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.lele.llmonitor.data.HomeWallpaperManager
import com.lele.llmonitor.R
import com.lele.llmonitor.data.AppLanguageOption
import com.lele.llmonitor.data.FollowSystemAppIconMode
import com.lele.llmonitor.data.SettingsManager
import com.lele.llmonitor.data.shouldCloseTaskAfterThemeModeChange
import com.lele.llmonitor.ui.dashboard.BatteryViewModel
import com.lele.llmonitor.ui.motion.AppMotion
import com.lele.llmonitor.ui.components.NavigationBarBottomInsetSpacer
import com.lele.llmonitor.ui.components.rememberActiveRingRotationState
import com.lele.llmonitor.ui.theme.AppCorners
import com.lele.llmonitor.ui.theme.AppShapes
import com.lele.llmonitor.ui.theme.ThemePalettePreset
import com.lele.llmonitor.ui.theme.isAppInDarkTheme
import com.lele.llmonitor.ui.theme.llClassCardBorderColor
import com.lele.llmonitor.ui.theme.llClassCardContainerColor
import com.lele.llmonitor.ui.theme.pageSurfaceColor
import com.lele.llmonitor.ui.theme.pageSurfaceTopAppBarColors
import com.lele.llmonitor.ui.wallpaper.HomeWallpaperImage
import com.lele.llmonitor.ui.wallpaper.homeWallpaperBlur
import com.lele.llmonitor.ui.wallpaper.rememberHomeWallpaperViewportSize
import kotlin.math.roundToInt

internal object SettingsRoutes {
    const val HOME = "settings/home"
    const val APPEARANCE = "settings/appearance"
    const val SCENE = "settings/scene"
    const val HARDWARE = "settings/hardware"
    const val SYSTEM = "settings/system"
    const val DATA = "settings/data"
    const val ABOUT = "settings/about"
    const val OPEN_SOURCE = "open_source_licenses"
}

private fun settingsTitleForRoute(route: String?): String {
    if (route?.startsWith("open_source_license_detail/") == true) {
        return com.lele.llmonitor.i18n.l10n("许可详情")
    }
    if (route?.startsWith("home_wallpaper_crop") == true) {
        return com.lele.llmonitor.i18n.l10n("裁切壁纸")
    }
    return when (route) {
        SettingsRoutes.APPEARANCE -> com.lele.llmonitor.i18n.l10n("外观设置")
        SettingsRoutes.SCENE -> com.lele.llmonitor.i18n.l10n("场景设置")
        SettingsRoutes.HARDWARE -> com.lele.llmonitor.i18n.l10n("硬件修正")
        SettingsRoutes.SYSTEM -> com.lele.llmonitor.i18n.l10n("系统与诊断")
        SettingsRoutes.DATA -> com.lele.llmonitor.i18n.l10n("数据管理")
        SettingsRoutes.ABOUT -> com.lele.llmonitor.i18n.l10n("关于")
        SettingsRoutes.OPEN_SOURCE -> com.lele.llmonitor.i18n.l10n("开源许可")
        else -> com.lele.llmonitor.i18n.l10n("设置")
    }
}

private fun restartAppAfterAppearanceChange(context: Context) {
    val activity = context as? Activity ?: return
    activity.startActivity(
        Intent(activity, AppearanceRestartActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_SOURCE_TASK_ID, activity.taskId)
            putExtra(EXTRA_SOURCE_PID, Process.myPid())
        }
    )
    activity.finishAndRemoveTask()
    Handler(Looper.getMainLooper()).post {
        Process.killProcess(Process.myPid())
        kotlin.system.exitProcess(0)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: BatteryViewModel,
    onExit: (() -> Unit)? = null,
    openAboutDirectly: Boolean = false,
    initialSettingsRoute: String? = null,
    onNavigateFromHome: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showClearDialog by remember { mutableStateOf(false) }
    // 0: 充电时, 1: 未充电时
    var selectedTab by remember { mutableIntStateOf(0) }
    var isBatteryOptimized by remember {
        mutableStateOf(!SettingsManager.isIgnoringBatteryOptimizations(context))
    }
    val navController = rememberNavController()
    val startRoute = initialSettingsRoute ?: if (openAboutDirectly) {
        SettingsRoutes.ABOUT
    } else {
        SettingsRoutes.HOME
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val canNavigateBack = currentRoute != null && currentRoute != startRoute
    val themeMode by SettingsManager.themeMode
    val themePalettePreset by SettingsManager.themePalettePreset
    val followSystemAppIconMode by SettingsManager.followSystemAppIconMode
    val appLanguageOption by SettingsManager.appLanguageOption
    var pendingThemeMode by remember { mutableStateOf<Int?>(null) }
    var pendingFollowSystemAppIconMode by remember { mutableStateOf<FollowSystemAppIconMode?>(null) }
    var pendingThemePalettePreset by remember { mutableStateOf<ThemePalettePreset?>(null) }
    var pendingAppLanguageOption by remember { mutableStateOf<AppLanguageOption?>(null) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isBatteryOptimized = !SettingsManager.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text(text = com.lele.llmonitor.i18n.l10n("清除记录")) },
            text = { Text(text = com.lele.llmonitor.i18n.l10n("确定要清空所有充电历史数据吗？\n此操作不可撤销，图表将被重置。")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearDialog = false
                    }
                ) {
                    Text(com.lele.llmonitor.i18n.l10n("删除"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(com.lele.llmonitor.i18n.l10n("取消"))
                }
            }
        )
    }

    pendingThemeMode?.let { targetThemeMode ->
        AlertDialog(
            onDismissRequest = { pendingThemeMode = null },
            title = { Text(com.lele.llmonitor.i18n.l10n("确认切换显示模式")) },
            text = { Text(com.lele.llmonitor.i18n.l10n("切换显示模式后，应用将尝试自动重启以刷新启动图标；若重启失败，请手动重新打开应用。是否继续？")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingThemeMode = null
                        SettingsManager.setThemeMode(
                            mode = targetThemeMode,
                            onCompleted = {
                                restartAppAfterAppearanceChange(context)
                            }
                        )
                    }
                ) {
                    Text(com.lele.llmonitor.i18n.l10n("确认并重启"))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingThemeMode = null }) {
                    Text(com.lele.llmonitor.i18n.l10n("取消"))
                }
            }
        )
    }

    pendingFollowSystemAppIconMode?.let { targetMode ->
        AlertDialog(
            onDismissRequest = { pendingFollowSystemAppIconMode = null },
            title = { Text(com.lele.llmonitor.i18n.l10n("确认切换启动图标")) },
            text = { Text(com.lele.llmonitor.i18n.l10n("切换启动图标样式需要刷新桌面入口，应用将尝试自动重启；若重启失败，请手动重新打开应用。是否继续？")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingFollowSystemAppIconMode = null
                        SettingsManager.setFollowSystemAppIconMode(
                            mode = targetMode,
                            onCompleted = {
                                restartAppAfterAppearanceChange(context)
                            }
                        )
                    }
                ) {
                    Text(com.lele.llmonitor.i18n.l10n("确认并重启"))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingFollowSystemAppIconMode = null }) {
                    Text(com.lele.llmonitor.i18n.l10n("取消"))
                }
            }
        )
    }

    pendingThemePalettePreset?.let { targetPreset ->
        AlertDialog(
            onDismissRequest = { pendingThemePalettePreset = null },
            title = { Text(com.lele.llmonitor.i18n.l10n("确认切换主题配色")) },
            text = { Text(com.lele.llmonitor.i18n.l10n("切换主题配色会同步更新应用图标，应用将尝试自动重启；若重启失败，请手动重新打开应用。是否继续？")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingThemePalettePreset = null
                        SettingsManager.setThemePalettePreset(
                            preset = targetPreset,
                            onCompleted = {
                                restartAppAfterAppearanceChange(context)
                            }
                        )
                    }
                ) {
                    Text(com.lele.llmonitor.i18n.l10n("确认并重启"))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingThemePalettePreset = null }) {
                    Text(com.lele.llmonitor.i18n.l10n("取消"))
                }
            }
        )
    }

    pendingAppLanguageOption?.let { targetLanguageOption ->
        val targetL10n: (String) -> String = { raw ->
            com.lele.llmonitor.i18n.l10n(raw, targetLanguageOption)
        }
        AlertDialog(
            onDismissRequest = { pendingAppLanguageOption = null },
            title = { Text(targetL10n("确认切换语言")) },
            text = { Text(targetL10n("切换语言后，应用将尝试自动重启以应用新的界面语言；若重启失败，请手动重新打开应用。是否继续？")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingAppLanguageOption = null
                        SettingsManager.setAppLanguageOption(
                            option = targetLanguageOption,
                            onCompleted = {
                                restartAppAfterAppearanceChange(context)
                            }
                        )
                    }
                ) {
                    Text(targetL10n("确认并重启"))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAppLanguageOption = null }) {
                    Text(targetL10n("取消"))
                }
            }
        )
    }

    Scaffold(
        containerColor = pageSurfaceColor(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = pageSurfaceTopAppBarColors(),
                title = { Text(settingsTitleForRoute(currentRoute)) },
                navigationIcon = {
                    if (canNavigateBack || onExit != null) {
                        IconButton(
                            onClick = {
                                if (canNavigateBack) {
                                    navController.navigateUp()
                                } else {
                                    onExit?.invoke()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = com.lele.llmonitor.i18n.l10n("返回")
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            enterTransition = { AppMotion.routeEnterTransition() },
            exitTransition = { AppMotion.routeExitTransition() },
            popEnterTransition = { AppMotion.routePopEnterTransition() },
            popExitTransition = { AppMotion.routePopExitTransition() },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            composable(SettingsRoutes.HOME) {
                SettingsRouteContainer {
                    SettingsHomePage(
                        onNavigate = { route ->
                            if (onNavigateFromHome != null) {
                                onNavigateFromHome(route)
                            } else {
                                navController.navigate(route) { launchSingleTop = true }
                            }
                        }
                    )
                }
            }
            composable(SettingsRoutes.APPEARANCE) {
                SettingsRouteContainer {
                    item {
                        LLClassStyleAppearanceCard(
                            themeMode = themeMode,
                            followSystemAppIconMode = followSystemAppIconMode,
                            currentLanguageOption = appLanguageOption,
                            selectedPreset = themePalettePreset,
                            onThemeModeChange = { nextThemeMode ->
                                if (nextThemeMode == themeMode) return@LLClassStyleAppearanceCard
                                if (
                                    shouldCloseTaskAfterThemeModeChange(
                                        currentThemeMode = themeMode,
                                        currentFollowSystemAppIconMode = followSystemAppIconMode,
                                        targetThemeMode = nextThemeMode
                                    )
                                ) {
                                    pendingThemeMode = nextThemeMode
                                } else {
                                    SettingsManager.setThemeMode(mode = nextThemeMode)
                                }
                            },
                            onFollowSystemAppIconModeChange = { nextMode ->
                                if (nextMode != followSystemAppIconMode) {
                                    pendingFollowSystemAppIconMode = nextMode
                                }
                            },
                            onPresetSelected = { nextPreset ->
                                if (nextPreset != themePalettePreset) {
                                    pendingThemePalettePreset = nextPreset
                                }
                            },
                            onAppLanguageOptionChange = { nextOption ->
                                if (nextOption != appLanguageOption) {
                                    pendingAppLanguageOption = nextOption
                                }
                            },
                            onOpenWallpaperCrop = { sourceUri ->
                                navController.navigate(createHomeWallpaperCropRoute(sourceUri))
                            }
                        )
                    }
                }
            }
            composable(SettingsRoutes.SCENE) {
                SettingsRouteContainer {
                    item {
                        SceneSettingsPage(
                            selectedTab = selectedTab,
                            onSelectedTabChange = { selectedTab = it },
                            onForceRefresh = { SettingsManager.sendForceUpdateBroadcast(context) }
                        )
                    }
                }
            }
            composable(SettingsRoutes.HARDWARE) {
                SettingsRouteContainer {
                    item {
                        HardwareSettingsPage(
                            onForceRefresh = { SettingsManager.sendForceUpdateBroadcast(context) }
                        )
                    }
                }
            }
            composable(SettingsRoutes.SYSTEM) {
                SettingsRouteContainer {
                    item {
                        SystemSettingsPage(
                            isBatteryOptimized = isBatteryOptimized,
                            onOpenBatteryOptimizationSetting = {
                                if (isBatteryOptimized) {
                                    val intent = Intent(
                                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                }
                            }
                        )
                    }
                }
            }
            composable(SettingsRoutes.DATA) {
                SettingsRouteContainer {
                    item {
                        DataManagementPage(
                            onClearClick = { showClearDialog = true }
                        )
                    }
                }
            }
            composable(SettingsRoutes.ABOUT) {
                AboutScreen(
                    onOpenOpenSource = { navController.navigate(SettingsRoutes.OPEN_SOURCE) }
                )
            }
            composable(SettingsRoutes.OPEN_SOURCE) {
                OpenSourceLicensesScreen(navController = navController)
            }
            composable(
                route = OPEN_SOURCE_LICENSE_DETAIL_ROUTE,
                arguments = listOf(
                    navArgument(OPEN_SOURCE_LICENSE_DETAIL_ARG) {
                        type = NavType.StringType
                    }
                )
            ) { entry ->
                val groupId = entry.arguments?.getString(OPEN_SOURCE_LICENSE_DETAIL_ARG).orEmpty()
                OpenSourceLicenseDetailScreen(
                    navController = navController,
                    groupId = groupId
                )
            }
            composable(
                route = HOME_WALLPAPER_CROP_ROUTE,
                arguments = listOf(
                    navArgument(HOME_WALLPAPER_CROP_SOURCE_URI_ARG) {
                        type = NavType.StringType
                    }
                )
            ) { entry ->
                val encodedSourceUri =
                    entry.arguments?.getString(HOME_WALLPAPER_CROP_SOURCE_URI_ARG).orEmpty()
                HomeWallpaperCropScreen(
                    navController = navController,
                    encodedSourceUri = encodedSourceUri
                )
            }
        }
    }
}

@Composable
private fun SettingsRouteContainer(
    content: LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        content()
        item { NavigationBarBottomInsetSpacer() }
    }
}

private fun LazyListScope.SettingsHomePage(
    onNavigate: (String) -> Unit
) {
    item {
        SettingsEntryCard(
            title = com.lele.llmonitor.i18n.l10n("外观设置"),
            icon = Icons.Rounded.BrightnessAuto,
            onClick = { onNavigate(SettingsRoutes.APPEARANCE) }
        )
    }
    item {
        SettingsEntryCard(
            title = com.lele.llmonitor.i18n.l10n("场景设置"),
            icon = Icons.Rounded.Tune,
            onClick = { onNavigate(SettingsRoutes.SCENE) }
        )
    }
    item {
        SettingsEntryCard(
            title = com.lele.llmonitor.i18n.l10n("硬件修正"),
            icon = Icons.Default.Settings,
            onClick = { onNavigate(SettingsRoutes.HARDWARE) }
        )
    }
    item {
        SettingsEntryCard(
            title = com.lele.llmonitor.i18n.l10n("系统与诊断"),
            icon = Icons.Default.Warning,
            onClick = { onNavigate(SettingsRoutes.SYSTEM) }
        )
    }
    item {
        SettingsEntryCard(
            title = com.lele.llmonitor.i18n.l10n("数据管理"),
            icon = Icons.Default.Delete,
            onClick = { onNavigate(SettingsRoutes.DATA) }
        )
    }
    item {
        SettingsEntryCard(
            title = com.lele.llmonitor.i18n.l10n("关于 LLMonitor"),
            icon = Icons.Rounded.Info,
            onClick = { onNavigate(SettingsRoutes.ABOUT) }
        )
    }
}

@Composable
private fun SceneSettingsPage(
    selectedTab: Int,
    onSelectedTabChange: (Int) -> Unit,
    onForceRefresh: () -> Unit
) {
    SettingsSectionCard(
        title = com.lele.llmonitor.i18n.l10n("场景设置"),
        subtitle = com.lele.llmonitor.i18n.l10n("充电/未充电通知与刷新频率"),
        leadingIcon = Icons.Rounded.Tune,
        modifier = Modifier.padding(settingsCardOuterPadding())
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {},
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { onSelectedTabChange(0) },
                text = { Text(com.lele.llmonitor.i18n.l10n("充电时")) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { onSelectedTabChange(1) },
                text = { Text(com.lele.llmonitor.i18n.l10n("未充电时")) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (selectedTab == 0) {
            ListItem(
                headlineContent = { Text(com.lele.llmonitor.i18n.l10n("通知")) },
                supportingContent = { Text(com.lele.llmonitor.i18n.l10n("在通知栏显示实时功率信息")) },
                trailingContent = {
                    Switch(
                        checked = SettingsManager.isNotificationEnabled.value,
                        onCheckedChange = {
                            SettingsManager.toggleNotificationEnabled(it)
                            onForceRefresh()
                        }
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            if (android.os.Build.VERSION.SDK_INT >= 36) {
                ListItem(
                    headlineContent = { Text(com.lele.llmonitor.i18n.l10n("实时活动")) },
                    supportingContent = { Text(com.lele.llmonitor.i18n.l10n("显示灵动岛风格实况通知")) },
                    trailingContent = {
                        Switch(
                            checked = SettingsManager.isLiveNotificationEnabled.value,
                            onCheckedChange = {
                                SettingsManager.toggleLiveNotificationEnabled(it)
                                onForceRefresh()
                            }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                ListItem(
                    headlineContent = { Text(com.lele.llmonitor.i18n.l10n("显示本次关闭按钮")) },
                    supportingContent = { Text(com.lele.llmonitor.i18n.l10n("在实时活动中显示“本次关闭”按钮")) },
                    trailingContent = {
                        Switch(
                            checked = SettingsManager.isLiveCloseActionEnabled.value,
                            onCheckedChange = {
                                SettingsManager.toggleLiveCloseActionEnabled(it)
                                onForceRefresh()
                            }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            FrequencySelectorItem(
                title = com.lele.llmonitor.i18n.l10n("应用界面刷新率"),
                currentValueMs = SettingsManager.refreshRateUiCharging.value,
                options = listOf(500L, 1000L, 2000L, 3000L, 5000L),
                onValueChange = {
                    SettingsManager.setRefreshRateUiCharging(it)
                    onForceRefresh()
                }
            )

            FrequencySelectorItem(
                title = com.lele.llmonitor.i18n.l10n("通知/组件更新率"),
                currentValueMs = SettingsManager.refreshRateNotifyCharging.value,
                options = listOf(1000L, 2000L, 3000L, 5000L, 10000L, 30000L),
                onValueChange = {
                    SettingsManager.setRefreshRateNotifyCharging(it)
                    onForceRefresh()
                }
            )

            FrequencySelectorItem(
                title = com.lele.llmonitor.i18n.l10n("熄屏下通知/组件更新率"),
                currentValueMs = SettingsManager.refreshRateNotifyChargingScreenOff.value,
                options = listOf(3000L, 5000L, 10000L, 30000L, 60000L, 180000L, 300000L),
                onValueChange = {
                    SettingsManager.setRefreshRateNotifyChargingScreenOff(it)
                    onForceRefresh()
                }
            )
        } else {
            ListItem(
                headlineContent = { Text(com.lele.llmonitor.i18n.l10n("通知")) },
                supportingContent = { Text(com.lele.llmonitor.i18n.l10n("保留通知栏常驻显示")) },
                trailingContent = {
                    Switch(
                        checked = SettingsManager.isShowNotificationWhenNotCharging.value,
                        onCheckedChange = {
                            SettingsManager.toggleShowNotificationWhenNotCharging(it)
                            onForceRefresh()
                        }
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            FrequencySelectorItem(
                title = com.lele.llmonitor.i18n.l10n("应用界面刷新率"),
                currentValueMs = SettingsManager.refreshRateUiNotCharging.value,
                options = listOf(1000L, 3000L, 5000L, 10000L, 15000L, 30000L, 60000L),
                onValueChange = {
                    SettingsManager.setRefreshRateUiNotCharging(it)
                    onForceRefresh()
                }
            )

            FrequencySelectorItem(
                title = com.lele.llmonitor.i18n.l10n("通知/组件更新率"),
                currentValueMs = SettingsManager.refreshRateNotifyNotCharging.value,
                options = listOf(3000L, 5000L, 10000L, 30000L, 60000L, 180000L, 300000L, 600000L),
                onValueChange = {
                    SettingsManager.setRefreshRateNotifyNotCharging(it)
                    onForceRefresh()
                }
            )

            FrequencySelectorItem(
                title = com.lele.llmonitor.i18n.l10n("熄屏下通知/组件更新率"),
                currentValueMs = SettingsManager.refreshRateNotifyNotChargingScreenOff.value,
                options = listOf(10000L, 30000L, 60000L, 180000L, 300000L, 600000L, 900000L),
                onValueChange = {
                    SettingsManager.setRefreshRateNotifyNotChargingScreenOff(it)
                    onForceRefresh()
                }
            )
        }
    }
}

@Composable
private fun HardwareSettingsPage(
    onForceRefresh: () -> Unit
) {
    SettingsSectionCard(
        title = com.lele.llmonitor.i18n.l10n("硬件修正"),
        subtitle = com.lele.llmonitor.i18n.l10n("兼容不同设备的电池读数"),
        leadingIcon = Icons.Default.Settings,
        modifier = Modifier.padding(settingsCardOuterPadding())
    ) {
        ListItem(
            headlineContent = { Text(com.lele.llmonitor.i18n.l10n("反转电流正负")) },
            supportingContent = { Text(com.lele.llmonitor.i18n.l10n("如果充电时电流显示为负，请开启此项")) },
            trailingContent = {
                Switch(
                    checked = SettingsManager.isInvertCurrent.value,
                    onCheckedChange = {
                        SettingsManager.toggleInvertCurrent(it)
                        onForceRefresh()
                    }
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        ListItem(
            headlineContent = { Text(com.lele.llmonitor.i18n.l10n("双芯电池修正")) },
            supportingContent = { Text(com.lele.llmonitor.i18n.l10n("如果使用双芯电池，请开启此项")) },
            trailingContent = {
                Switch(
                    checked = SettingsManager.isDoubleCell.value,
                    onCheckedChange = {
                        SettingsManager.toggleDoubleCell(it)
                        onForceRefresh()
                    }
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        ListItem(
            headlineContent = { Text(com.lele.llmonitor.i18n.l10n("虚拟电压")) },
            supportingContent = { Text(com.lele.llmonitor.i18n.l10n("若设备无法读取电压，尝试使用估算电压")) },
            trailingContent = {
                Switch(
                    checked = SettingsManager.isVirtualVoltageEnabled.value,
                    onCheckedChange = {
                        SettingsManager.toggleVirtualVoltageEnabled(it)
                        onForceRefresh()
                    }
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
private fun SystemSettingsPage(
    isBatteryOptimized: Boolean,
    onOpenBatteryOptimizationSetting: () -> Unit
) {
    SettingsSectionCard(
        title = com.lele.llmonitor.i18n.l10n("系统与诊断"),
        subtitle = com.lele.llmonitor.i18n.l10n("后台保活与调试选项"),
        leadingIcon = Icons.Default.Warning,
        modifier = Modifier.padding(settingsCardOuterPadding())
    ) {
        ListItem(
            modifier = Modifier.clickable { onOpenBatteryOptimizationSetting() },
            headlineContent = {
                Text(if (isBatteryOptimized) com.lele.llmonitor.i18n.l10n("禁用电池优化") else com.lele.llmonitor.i18n.l10n("电池优化已禁用"))
            },
            supportingContent = {
                Text(
                    if (isBatteryOptimized) {
                        com.lele.llmonitor.i18n.l10n("点击禁用，确保后台实时更新不中断")
                    } else {
                        com.lele.llmonitor.i18n.l10n("后台更新不受限制")
                    }
                )
            },
            leadingContent = {
                Icon(
                    if (isBatteryOptimized) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = com.lele.llmonitor.i18n.l10n("电池优化"),
                    tint = if (isBatteryOptimized) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            },
            trailingContent = {
                if (isBatteryOptimized) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = com.lele.llmonitor.i18n.l10n("前往设置"))
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        ListItem(
            headlineContent = { Text(com.lele.llmonitor.i18n.l10n("调试模式")) },
            supportingContent = { Text(com.lele.llmonitor.i18n.l10n("显示各指标可用数据来源（仅用于诊断）")) },
            trailingContent = {
                Switch(
                    checked = SettingsManager.isDebugModeEnabled.value,
                    onCheckedChange = { SettingsManager.toggleDebugModeEnabled(it) }
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
private fun DataManagementPage(
    onClearClick: () -> Unit
) {
    SettingsSectionCard(
        title = com.lele.llmonitor.i18n.l10n("数据管理"),
        subtitle = com.lele.llmonitor.i18n.l10n("历史记录维护"),
        leadingIcon = Icons.Default.Delete,
        modifier = Modifier.padding(settingsCardOuterPadding())
    ) {
        ListItem(
            modifier = Modifier.clickable { onClearClick() },
            headlineContent = {
                Text(com.lele.llmonitor.i18n.l10n("清除历史数据"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            },
            supportingContent = { Text(com.lele.llmonitor.i18n.l10n("删除所有已存储的充电功率记录")) },
            leadingContent = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = com.lele.llmonitor.i18n.l10n("清除"),
                    tint = MaterialTheme.colorScheme.error
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LLClassStyleAppearanceCard(
    themeMode: Int,
    followSystemAppIconMode: FollowSystemAppIconMode,
    currentLanguageOption: AppLanguageOption,
    selectedPreset: ThemePalettePreset,
    onThemeModeChange: (Int) -> Unit,
    onFollowSystemAppIconModeChange: (FollowSystemAppIconMode) -> Unit,
    onPresetSelected: (ThemePalettePreset) -> Unit,
    onAppLanguageOptionChange: (AppLanguageOption) -> Unit,
    onOpenWallpaperCrop: (Uri) -> Unit
) {
    data class ThemeOption(
        val mode: Int,
        val label: String,
        val icon: ImageVector
    )

    val options = listOf(
        ThemeOption(0, com.lele.llmonitor.i18n.l10n("跟随系统"), Icons.Rounded.BrightnessAuto),
        ThemeOption(1, com.lele.llmonitor.i18n.l10n("浅色模式"), Icons.Rounded.LightMode),
        ThemeOption(2, com.lele.llmonitor.i18n.l10n("深色模式"), Icons.Rounded.DarkMode)
    )
    val languageOptions = listOf(
        AppLanguageOption.FOLLOW_SYSTEM to "Follow System",
        AppLanguageOption.ENGLISH to "English",
        AppLanguageOption.CHINESE_SIMPLIFIED_CHINA to "简体中文（中国）",
        AppLanguageOption.CHINESE_TRADITIONAL_HONG_KONG to "繁體中文（中國香港）",
        AppLanguageOption.CHINESE_TRADITIONAL_TAIWAN to "繁體中文（中國台灣）"
    )
    val currentLanguageLabel = languageOptions.firstOrNull {
        it.first == currentLanguageOption
    }?.second ?: "English"
    val previewDarkTheme = isAppInDarkTheme()
    val wallpaperViewport = rememberHomeWallpaperViewportSize()
    val wallpaperAspectRatio = remember(wallpaperViewport) {
        (wallpaperViewport.width / wallpaperViewport.height).coerceIn(0.3f, 0.7f)
    }
    val wallpaperEnabled by HomeWallpaperManager.isEnabled
    val wallpaperAlpha by HomeWallpaperManager.wallpaperAlpha
    val wallpaperBlur by HomeWallpaperManager.wallpaperBlur
    val wallpaperFile by HomeWallpaperManager.wallpaperFile
    val wallpaperHistoryFiles by HomeWallpaperManager.historyFiles
    val homeCardOpacity by SettingsManager.homeCardOpacity
    var sliderCardTransparency by remember { mutableFloatStateOf((1f - homeCardOpacity).coerceIn(0f, 1f)) }
    var isCardTransparencySliding by remember { mutableStateOf(false) }
    var sliderAlpha by remember { mutableFloatStateOf(wallpaperAlpha) }
    var isAlphaSliding by remember { mutableStateOf(false) }
    var sliderBlur by remember { mutableFloatStateOf(wallpaperBlur) }
    var isBlurSliding by remember { mutableStateOf(false) }
    var pendingDeleteHistoryFileName by remember { mutableStateOf<String?>(null) }
    val wallpaperPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onOpenWallpaperCrop(uri)
        }
    }

    LaunchedEffect(wallpaperAlpha, isAlphaSliding) {
        if (!isAlphaSliding) {
            sliderAlpha = wallpaperAlpha
        }
    }

    LaunchedEffect(homeCardOpacity, isCardTransparencySliding) {
        if (!isCardTransparencySliding) {
            sliderCardTransparency = (1f - homeCardOpacity).coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(wallpaperBlur, isBlurSliding) {
        if (!isBlurSliding) {
            sliderBlur = wallpaperBlur
        }
    }

    pendingDeleteHistoryFileName?.let { historyFileName ->
        AlertDialog(
            onDismissRequest = { pendingDeleteHistoryFileName = null },
            title = { Text(com.lele.llmonitor.i18n.l10n("确认删除历史壁纸")) },
            text = { Text(com.lele.llmonitor.i18n.l10n("删除后无法恢复，这张历史壁纸将从列表中移除。")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteHistoryFileName = null
                        HomeWallpaperManager.deleteHistoryWallpaper(historyFileName)
                    }
                ) {
                    Text(com.lele.llmonitor.i18n.l10n("确认删除"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteHistoryFileName = null }) {
                    Text(com.lele.llmonitor.i18n.l10n("取消"))
                }
            }
        )
    }

    SettingsSectionCard(
        title = com.lele.llmonitor.i18n.l10n("显示模式"),
        leadingIcon = Icons.Rounded.InvertColors,
        modifier = Modifier.padding(settingsCardOuterPadding())
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = themeMode == option.mode,
                    onClick = { onThemeModeChange(option.mode) },
                    modifier = Modifier.weight(1f),
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = options.size
                    ),
                    icon = {},
                    label = {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = option.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                )
            }
        }

        if (themeMode == 0) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f),
                modifier = Modifier.padding(top = 16.dp, bottom = 14.dp)
            )
            Text(
                text = com.lele.llmonitor.i18n.l10n("启动图标样式"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                listOf(
                    FollowSystemAppIconMode.LIGHT to ThemeOption(
                        mode = 1,
                        label = com.lele.llmonitor.i18n.l10n("浅色图标"),
                        icon = Icons.Rounded.LightMode
                    ),
                    FollowSystemAppIconMode.DARK to ThemeOption(
                        mode = 2,
                        label = com.lele.llmonitor.i18n.l10n("深色图标"),
                        icon = Icons.Rounded.DarkMode
                    )
                ).forEachIndexed { index, entry ->
                    val (mode, option) = entry
                    SegmentedButton(
                        selected = followSystemAppIconMode == mode,
                        onClick = { onFollowSystemAppIconModeChange(mode) },
                        modifier = Modifier.weight(1f),
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = 2
                        ),
                        icon = {},
                        label = {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = option.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = option.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    val activeRingRotationDegreesState = rememberActiveRingRotationState()
    SettingsSectionCard(
        title = com.lele.llmonitor.i18n.l10n("主题配色"),
        leadingIcon = Icons.Rounded.Palette,
        modifier = Modifier.padding(settingsCardOuterPadding())
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ThemePalettePreset.visibleEntries.chunked(2).forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowOptions.forEach { option ->
                        ThemePaletteOptionCard(
                            preset = option,
                            darkTheme = previewDarkTheme,
                            activeRingRotationDegreesState = activeRingRotationDegreesState,
                            selected = option == selectedPreset,
                            onClick = { onPresetSelected(option) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowOptions.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f),
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = com.lele.llmonitor.i18n.l10n("主页卡片透明度"),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(sliderCardTransparency * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Slider(
            value = sliderCardTransparency,
            onValueChange = { nextTransparency ->
                isCardTransparencySliding = true
                sliderCardTransparency = nextTransparency
                SettingsManager.setHomeCardOpacity((1f - nextTransparency).coerceIn(0f, 1f))
            },
            onValueChangeFinished = {
                isCardTransparencySliding = false
                SettingsManager.setHomeCardOpacity((1f - sliderCardTransparency).coerceIn(0f, 1f))
            },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            modifier = Modifier.fillMaxWidth()
        )

    }

    SettingsSectionCard(
        title = com.lele.llmonitor.i18n.l10n("主界面壁纸"),
        leadingIcon = Icons.Rounded.Image,
        titleTrailing = {
            Switch(
                checked = wallpaperEnabled,
                onCheckedChange = HomeWallpaperManager::setEnabled,
                enabled = wallpaperFile != null
            )
        },
        modifier = Modifier.padding(settingsCardOuterPadding())
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            item {
                WallpaperThumbnailCard(
                    wallpaperAspectRatio = wallpaperAspectRatio,
                    onClick = null
                ) {
                    if (wallpaperFile != null) {
                        HomeWallpaperImage(
                            wallpaperFile = requireNotNull(wallpaperFile),
                            alpha = wallpaperAlpha,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .homeWallpaperBlur(sliderBlur)
                        )
                    } else {
                        EmptyWallpaperThumbnail()
                    }
                }
            }

            item {
                WallpaperThumbnailCard(
                    wallpaperAspectRatio = wallpaperAspectRatio,
                    onClick = {
                        wallpaperPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = com.lele.llmonitor.i18n.l10n("添加壁纸"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.size(58.dp)
                        )
                    }
                }
            }

            items(
                items = wallpaperHistoryFiles,
                key = { historyFile -> "${historyFile.absolutePath}:${historyFile.lastModified()}" }
            ) { historyFile ->
                WallpaperThumbnailCard(
                    wallpaperAspectRatio = wallpaperAspectRatio,
                    onClick = {
                        HomeWallpaperManager.applyWallpaperFromHistory(historyFile.name)
                    }
                ) {
                    HomeWallpaperImage(
                        wallpaperFile = historyFile,
                        alpha = 1f,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    HistoryWallpaperDeleteButton(
                        onClick = { pendingDeleteHistoryFileName = historyFile.name }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = com.lele.llmonitor.i18n.l10n("透明度"),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(sliderAlpha * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Slider(
            value = sliderAlpha,
            onValueChange = { nextAlpha ->
                isAlphaSliding = true
                sliderAlpha = nextAlpha
                HomeWallpaperManager.previewHomeWallpaperAlpha(nextAlpha)
            },
            onValueChangeFinished = {
                isAlphaSliding = false
                HomeWallpaperManager.setHomeWallpaperAlpha(sliderAlpha)
            },
            valueRange = 0f..1f,
            enabled = wallpaperEnabled,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = com.lele.llmonitor.i18n.l10n("模糊度"),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(sliderBlur * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Slider(
            value = sliderBlur,
            onValueChange = { nextBlur ->
                isBlurSliding = true
                sliderBlur = nextBlur
                HomeWallpaperManager.previewHomeWallpaperBlur(nextBlur)
            },
            onValueChangeFinished = {
                isBlurSliding = false
                HomeWallpaperManager.setHomeWallpaperBlur(sliderBlur)
            },
            valueRange = 0f..1f,
            enabled = wallpaperEnabled,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }

    SettingsSectionCard(
        title = com.lele.llmonitor.i18n.l10n("语言"),
        leadingIcon = Icons.Rounded.Language,
        modifier = Modifier.padding(settingsCardOuterPadding())
    ) {
        LanguageSelectorItem(
            title = com.lele.llmonitor.i18n.l10n("应用语言"),
            currentOption = currentLanguageOption,
            currentLabel = currentLanguageLabel,
            options = languageOptions,
            onValueChange = onAppLanguageOptionChange
        )
    }
}

@Composable
private fun WallpaperThumbnailCard(
    wallpaperAspectRatio: Float,
    onClick: (() -> Unit)?,
    content: @Composable BoxScope.() -> Unit
) {
    val thumbnailShape = AppShapes.g2(AppCorners.lg)
    Column(
        modifier = Modifier.width(132.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(wallpaperAspectRatio)
                .clip(thumbnailShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                    shape = thumbnailShape
                )
                .let { baseModifier ->
                    if (onClick != null) {
                        baseModifier.clickable(onClick = onClick)
                    } else {
                        baseModifier
                    }
                }
        ) {
            content()
        }
    }
}

@Composable
private fun EmptyWallpaperThumbnail() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun BoxScope.HistoryWallpaperDeleteButton(
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(6.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = com.lele.llmonitor.i18n.l10n("删除历史壁纸"),
                tint = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.size(25.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemePaletteOptionCard(
    preset: ThemePalettePreset,
    darkTheme: Boolean,
    activeRingRotationDegreesState: State<Float>,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val previewColorScheme = remember(preset, darkTheme, context) {
        com.lele.llmonitor.ui.theme.resolveAppColorScheme(
            context = context,
            darkTheme = darkTheme,
            themePalettePreset = preset
        )
    }
    val previewSpec = remember(preset, previewColorScheme, darkTheme) {
        resolveThemePalettePreviewSpec(
            preset = preset,
            colorScheme = previewColorScheme,
            isDarkTheme = darkTheme
        )
    }
    val previewAccentColors = remember(previewSpec.accentBorderColors) {
        previewSpec.accentBorderColors.orderForSpectrumPreview()
    }
    val tileShape = AppShapes.g2(AppCorners.md)
    val outlineColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    }

    Card(
        onClick = onClick,
        modifier = modifier,
        shape = tileShape,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.28f)
                    .clip(tileShape)
                    .background(previewColorScheme.surface)
                    .border(
                        BorderStroke(if (selected) 1.5.dp else 1.dp, outlineColor),
                        tileShape
                    )
                    .padding(10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        previewAccentColors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(AppShapes.g2(AppCorners.xs))
                                    .background(color)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        previewSpec.cardColors.take(3).forEachIndexed { index, sample ->
                            ThemePalettePreviewBlock(
                                containerColor = sample.container,
                                contentColor = sample.content,
                                activeBorderColors = previewSpec.accentBorderColors,
                                activeRingRotationDegreesState = activeRingRotationDegreesState,
                                showActiveRing = index == 1,
                                modifier = Modifier.weight(if (index == 1) 1.18f else 1f)
                            )
                        }
                    }
                }
            }

            Text(
                text = resolveThemePaletteLabel(preset),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ThemePalettePreviewBlock(
    containerColor: Color,
    contentColor: Color,
    activeBorderColors: List<Color>,
    activeRingRotationDegreesState: State<Float>,
    showActiveRing: Boolean,
    modifier: Modifier = Modifier
) {
    val cardShape = AppShapes.g2(AppCorners.sm)
    Box(
        modifier = modifier
            .height(54.dp)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(1.5.dp)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(cardShape)
                    .background(containerColor)
                    .padding(horizontal = 8.dp, vertical = 7.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .height(8.dp)
                            .clip(AppShapes.g2(AppCorners.xs))
                            .background(contentColor.copy(alpha = 0.92f))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.56f)
                            .height(5.dp)
                            .clip(AppShapes.g2(AppCorners.xs))
                            .background(contentColor.copy(alpha = 0.52f))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.42f)
                            .height(5.dp)
                            .clip(AppShapes.g2(AppCorners.xs))
                            .background(contentColor.copy(alpha = 0.34f))
                    )
                }
            }
            if (showActiveRing) {
                AnimatedThemePaletteActiveRing(
                    modifier = Modifier.matchParentSize(),
                    cardShape = cardShape,
                    activeBorderColors = activeBorderColors,
                    outerStrokeWidth = ThemePaletteActiveRingOuterStrokeWidth,
                    innerStrokeWidth = ThemePaletteActiveRingInnerStrokeWidth,
                    outerAlpha = ThemePaletteActiveRingOuterAlpha,
                    rotationDegreesState = activeRingRotationDegreesState
                )
            }
        }
    }
}

private fun List<Color>.orderForSpectrumPreview(): List<Color> {
    val previewColors = take(4)
    if (previewColors.size <= 2) return previewColors

    val hueEntries = previewColors.map { color ->
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        color to hsv[0]
    }.sortedBy { it.second }

    val largestGapIndex = hueEntries.indices.maxByOrNull { index ->
        val currentHue = hueEntries[index].second
        val nextHue = hueEntries[(index + 1) % hueEntries.size].second
        (nextHue - currentHue + 360f) % 360f
    } ?: return previewColors

    val startIndex = (largestGapIndex + 1) % hueEntries.size
    return List(hueEntries.size) { offset ->
        hueEntries[(startIndex + offset) % hueEntries.size].first
    }
}

private data class ThemePalettePreviewSpec(
    val cardColors: List<ThemePalettePreviewColor>,
    val accentBorderColors: List<Color>
)

private data class ThemePalettePreviewColor(
    val container: Color,
    val content: Color
)

private fun resolveThemePaletteLabel(preset: ThemePalettePreset): String {
    return when (preset) {
        ThemePalettePreset.DYNAMIC -> com.lele.llmonitor.i18n.l10n("动态多彩")
        ThemePalettePreset.OCEAN -> com.lele.llmonitor.i18n.l10n("沧蓝")
        ThemePalettePreset.FOREST -> com.lele.llmonitor.i18n.l10n("松青")
        ThemePalettePreset.SUNSET -> com.lele.llmonitor.i18n.l10n("曛橙")
        ThemePalettePreset.BLOSSOM -> com.lele.llmonitor.i18n.l10n("樱霭")
        ThemePalettePreset.JIZI -> com.lele.llmonitor.i18n.l10n("霁紫")
    }
}

private fun resolveThemePalettePreviewSpec(
    preset: ThemePalettePreset,
    colorScheme: ColorScheme,
    isDarkTheme: Boolean
): ThemePalettePreviewSpec {
    val pageReferenceColor = if (isDarkTheme) {
        lerp(colorScheme.surface, colorScheme.background, 0.35f)
    } else {
        lerp(colorScheme.surface, colorScheme.background, 0.45f)
    }
    val rawSpec = when (preset) {
        ThemePalettePreset.DYNAMIC -> ThemePalettePreviewSpec(
            cardColors = buildDynamicPreviewCardPalette(
                primaryColor = colorScheme.primary,
                secondaryColor = colorScheme.secondary,
                tertiaryColor = colorScheme.tertiary,
                isDarkTheme = isDarkTheme
            ),
            accentBorderColors = listOf(
                Color(0xFF4285F4),
                Color(0xFFEA4335),
                Color(0xFFFBBC04),
                Color(0xFF34A853)
            )
        )
        ThemePalettePreset.OCEAN -> ThemePalettePreviewSpec(
            cardColors = oceanPreviewCardPalette(isDarkTheme),
            accentBorderColors = if (isDarkTheme) {
                listOf(
                    Color(0xFF95F1FF),
                    Color(0xFF47D7FF),
                    Color(0xFF149EFF),
                    Color(0xFF5B75F0)
                )
            } else {
                listOf(
                    Color(0xFF7DEAFF),
                    Color(0xFF18CCFF),
                    Color(0xFF0077E3),
                    Color(0xFF325BC8)
                )
            }
        )
        ThemePalettePreset.FOREST -> ThemePalettePreviewSpec(
            cardColors = forestPreviewCardPalette(isDarkTheme),
            accentBorderColors = if (isDarkTheme) {
                listOf(
                    Color(0xFFA8E5B3),
                    Color(0xFF63D57D),
                    Color(0xFF45B25A),
                    Color(0xFFB8C55C)
                )
            } else {
                listOf(
                    Color(0xFF8BDEA0),
                    Color(0xFF2FB36A),
                    Color(0xFF2F7A3E),
                    Color(0xFF9AAA39)
                )
            }
        )
        ThemePalettePreset.SUNSET -> ThemePalettePreviewSpec(
            cardColors = sunsetPreviewCardPalette(isDarkTheme),
            accentBorderColors = if (isDarkTheme) {
                listOf(
                    Color(0xFFFFE0A1),
                    Color(0xFFFFBA67),
                    Color(0xFFFF7D50),
                    Color(0xFFD08A30)
                )
            } else {
                listOf(
                    Color(0xFFFFD68A),
                    Color(0xFFFFA749),
                    Color(0xFFF1693B),
                    Color(0xFFC37923)
                )
            }
        )
        ThemePalettePreset.BLOSSOM -> ThemePalettePreviewSpec(
            cardColors = blossomPreviewCardPalette(isDarkTheme),
            accentBorderColors = if (isDarkTheme) {
                listOf(
                    Color(0xFFFFD4E2),
                    Color(0xFFFFA5CA),
                    Color(0xFFEF679A),
                    Color(0xFFB77FC5)
                )
            } else {
                listOf(
                    Color(0xFFFFC4D8),
                    Color(0xFFFF87B7),
                    Color(0xFFDA4D86),
                    Color(0xFFA96DB7)
                )
            }
        )
        ThemePalettePreset.JIZI -> ThemePalettePreviewSpec(
            cardColors = jiziPreviewCardPalette(isDarkTheme),
            accentBorderColors = if (isDarkTheme) {
                listOf(
                    Color(0xFFE5DBFF),
                    Color(0xFFCDB7FF),
                    Color(0xFFA07DFF),
                    Color(0xFF7582FF)
                )
            } else {
                listOf(
                    Color(0xFFD9CBFF),
                    Color(0xFFBBA3FF),
                    Color(0xFF8B72FF),
                    Color(0xFF5D69D9)
                )
            }
        )
    }
    return rawSpec.normalizeForBackground(
        backgroundColor = pageReferenceColor,
        isDarkTheme = isDarkTheme
    )
}

private fun buildDynamicPreviewCardPalette(
    primaryColor: Color,
    secondaryColor: Color,
    tertiaryColor: Color,
    isDarkTheme: Boolean,
    paletteSize: Int = 12
): List<ThemePalettePreviewColor> {
    if (isDarkTheme) {
        return buildLegacyDarkDynamicPreviewCardPalette(
            seedColor = primaryColor,
            paletteSize = paletteSize
        )
    }

    val boundedPaletteSize = paletteSize.coerceAtLeast(1)
    val anchors = listOf(
        primaryColor,
        lerp(primaryColor, secondaryColor, 0.32f),
        secondaryColor,
        lerp(secondaryColor, tertiaryColor, 0.34f),
        tertiaryColor,
        lerp(primaryColor, tertiaryColor, 0.42f)
    )
    return anchors.flatMapIndexed { index, anchor ->
        listOf(
            buildDynamicPreviewColorFromAnchor(
                anchorColor = anchor,
                isDarkTheme = isDarkTheme,
                toneShift = if (index % 2 == 0) -0.015f else 0.022f,
                saturationShift = if (index % 3 == 0) -0.012f else 0f
            ),
            buildDynamicPreviewColorFromAnchor(
                anchorColor = anchor,
                isDarkTheme = isDarkTheme,
                toneShift = if (index % 2 == 0) 0.032f else -0.018f,
                saturationShift = -0.024f
            )
        )
    }.take(boundedPaletteSize)
}

private fun buildLegacyDarkDynamicPreviewCardPalette(
    seedColor: Color,
    paletteSize: Int
): List<ThemePalettePreviewColor> {
    val boundedPaletteSize = paletteSize.coerceAtLeast(1)
    val seedHsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seedColor.toArgb(), seedHsv)
    val baseHue = seedHsv[0]
    val hueStep = 360f / boundedPaletteSize.toFloat()

    val containerSaturation = 0.28f
    val containerValue = 0.24f
    val contentSaturation = 0.62f
    val contentValue = 0.92f

    return List(boundedPaletteSize) { index ->
        val hue = (baseHue + (hueStep * index.toFloat())) % 360f
        buildPreviewColor(
            containerHue = hue,
            containerSaturation = containerSaturation,
            containerValue = containerValue,
            contentHue = hue,
            contentSaturation = contentSaturation,
            contentValue = contentValue
        )
    }
}

private fun oceanPreviewCardPalette(isDarkTheme: Boolean): List<ThemePalettePreviewColor> {
    return if (isDarkTheme) {
        listOf(
            fixedPreviewColor(0xFF163949, 0xFF9EDBFF),
            fixedPreviewColor(0xFF144048, 0xFF86E7FF),
            fixedPreviewColor(0xFF153E4A, 0xFF86D8FF),
            fixedPreviewColor(0xFF1A4147, 0xFFA7E4ED),
            fixedPreviewColor(0xFF173845, 0xFF9BE7F2),
            fixedPreviewColor(0xFF184149, 0xFF89D8E8),
            fixedPreviewColor(0xFF11424C, 0xFF7FE3FF),
            fixedPreviewColor(0xFF1D4048, 0xFFA4DCE8),
            fixedPreviewColor(0xFF133E4B, 0xFF8ADAF8),
            fixedPreviewColor(0xFF18404D, 0xFF9AD8F0),
            fixedPreviewColor(0xFF154245, 0xFF92E9E6),
            fixedPreviewColor(0xFF1D434A, 0xFFB6E1E9)
        )
    } else {
        listOf(
            fixedPreviewColor(0xFFE6F4FF, 0xFF0D4D7A),
            fixedPreviewColor(0xFFE7FBFF, 0xFF006D88),
            fixedPreviewColor(0xFFE9F7FF, 0xFF116B8D),
            fixedPreviewColor(0xFFEDF8FB, 0xFF2E6B7B),
            fixedPreviewColor(0xFFE8F9FA, 0xFF15606F),
            fixedPreviewColor(0xFFEAF6F7, 0xFF2F7284),
            fixedPreviewColor(0xFFE3F7FF, 0xFF006C8E),
            fixedPreviewColor(0xFFECF9FA, 0xFF3C7180),
            fixedPreviewColor(0xFFE8F6FF, 0xFF2A5F8E),
            fixedPreviewColor(0xFFEAF7FB, 0xFF34708A),
            fixedPreviewColor(0xFFE7FBF7, 0xFF006B75),
            fixedPreviewColor(0xFFEEF8FB, 0xFF4A7881)
        )
    }
}

private fun forestPreviewCardPalette(isDarkTheme: Boolean): List<ThemePalettePreviewColor> {
    return if (isDarkTheme) {
        listOf(
            fixedPreviewColor(0xFF183C31, 0xFF9FE7C8),
            fixedPreviewColor(0xFF1D4130, 0xFFB0E28D),
            fixedPreviewColor(0xFF173A3A, 0xFF8DE5D2),
            fixedPreviewColor(0xFF223D2A, 0xFFC1E69A),
            fixedPreviewColor(0xFF1C352B, 0xFF84E0B0),
            fixedPreviewColor(0xFF243E36, 0xFFB5E1C2),
            fixedPreviewColor(0xFF1B4037, 0xFF8EE6C2),
            fixedPreviewColor(0xFF254230, 0xFFD0E59B),
            fixedPreviewColor(0xFF173B33, 0xFF7FE2B8),
            fixedPreviewColor(0xFF223B34, 0xFFA8E0BC),
            fixedPreviewColor(0xFF1F433A, 0xFF95E8D0),
            fixedPreviewColor(0xFF28452F, 0xFFC9EBAB)
        )
    } else {
        listOf(
            fixedPreviewColor(0xFFE8F8F1, 0xFF136A4A),
            fixedPreviewColor(0xFFF0F8E8, 0xFF4B6B15),
            fixedPreviewColor(0xFFE7FAF8, 0xFF0D6B67),
            fixedPreviewColor(0xFFF4F8E9, 0xFF5E641C),
            fixedPreviewColor(0xFFEAF7EE, 0xFF246B4F),
            fixedPreviewColor(0xFFF0F6EE, 0xFF386452),
            fixedPreviewColor(0xFFE9FAF2, 0xFF0E6C53),
            fixedPreviewColor(0xFFF4F8EA, 0xFF5E6718),
            fixedPreviewColor(0xFFE5F7EF, 0xFF1A6B54),
            fixedPreviewColor(0xFFEAF6F1, 0xFF33604D),
            fixedPreviewColor(0xFFEAFBF7, 0xFF156C63),
            fixedPreviewColor(0xFFF5FAED, 0xFF617117)
        )
    }
}

private fun sunsetPreviewCardPalette(isDarkTheme: Boolean): List<ThemePalettePreviewColor> {
    return if (isDarkTheme) {
        listOf(
            fixedPreviewColor(0xFF4A271C, 0xFFFFC7A4),
            fixedPreviewColor(0xFF4B2A1E, 0xFFFFBA95),
            fixedPreviewColor(0xFF4D2C17, 0xFFFFCF8D),
            fixedPreviewColor(0xFF4D2F24, 0xFFFFC09E),
            fixedPreviewColor(0xFF55311B, 0xFFFFC491),
            fixedPreviewColor(0xFF4B291F, 0xFFFFB48F),
            fixedPreviewColor(0xFF4A2B20, 0xFFFFC0A9),
            fixedPreviewColor(0xFF53331D, 0xFFFFCF85),
            fixedPreviewColor(0xFF4E3026, 0xFFFFBB9A),
            fixedPreviewColor(0xFF4A2A1A, 0xFFFFC3A0),
            fixedPreviewColor(0xFF502F22, 0xFFFFB68D),
            fixedPreviewColor(0xFF543127, 0xFFFFC5A6)
        )
    } else {
        listOf(
            fixedPreviewColor(0xFFFFF0E7, 0xFF8A411C),
            fixedPreviewColor(0xFFFFEFE7, 0xFF9C562A),
            fixedPreviewColor(0xFFFFF4E0, 0xFF9B5A00),
            fixedPreviewColor(0xFFFFF1E8, 0xFF9B6642),
            fixedPreviewColor(0xFFFFF2E4, 0xFF955112),
            fixedPreviewColor(0xFFFFEEE5, 0xFFA55B31),
            fixedPreviewColor(0xFFFFF0EA, 0xFF8E4B2F),
            fixedPreviewColor(0xFFFFF5DE, 0xFF946006),
            fixedPreviewColor(0xFFFFF1E9, 0xFF986345),
            fixedPreviewColor(0xFFFFF2E8, 0xFF8C4824),
            fixedPreviewColor(0xFFFFEFE7, 0xFF9A4824),
            fixedPreviewColor(0xFFFFF3E9, 0xFFA46B44)
        )
    }
}

private fun blossomPreviewCardPalette(isDarkTheme: Boolean): List<ThemePalettePreviewColor> {
    return if (isDarkTheme) {
        listOf(
            fixedPreviewColor(0xFF472633, 0xFFFFBED6),
            fixedPreviewColor(0xFF4A2D39, 0xFFF1BDD4),
            fixedPreviewColor(0xFF43303A, 0xFFDCC4D2),
            fixedPreviewColor(0xFF4C2630, 0xFFFFB7C5),
            fixedPreviewColor(0xFF46313A, 0xFFE5C5D3),
            fixedPreviewColor(0xFF532838, 0xFFFFC0D1),
            fixedPreviewColor(0xFF482C38, 0xFFE9BED1),
            fixedPreviewColor(0xFF40303B, 0xFFD7C6D0),
            fixedPreviewColor(0xFF512B34, 0xFFFFB7BF),
            fixedPreviewColor(0xFF493039, 0xFFDEC2CE),
            fixedPreviewColor(0xFF4A2A39, 0xFFFFBDD7),
            fixedPreviewColor(0xFF433039, 0xFFD3C9D0)
        )
    } else {
        listOf(
            fixedPreviewColor(0xFFFFF0F5, 0xFFA34374),
            fixedPreviewColor(0xFFFBEEF4, 0xFF8B5D78),
            fixedPreviewColor(0xFFF8F0F5, 0xFF7C657F),
            fixedPreviewColor(0xFFFFEFF1, 0xFFAF4F67),
            fixedPreviewColor(0xFFF9F1F4, 0xFF8F617A),
            fixedPreviewColor(0xFFFFF0F4, 0xFF9F526F),
            fixedPreviewColor(0xFFFDF0F6, 0xFFA05D85),
            fixedPreviewColor(0xFFF9F2F6, 0xFF7D677D),
            fixedPreviewColor(0xFFFFEFF0, 0xFFB15E72),
            fixedPreviewColor(0xFFFAF1F4, 0xFF936676),
            fixedPreviewColor(0xFFFFF1F5, 0xFF9D5C7C),
            fixedPreviewColor(0xFFF7F2F5, 0xFF756A7A)
        )
    }
}

private fun jiziPreviewCardPalette(isDarkTheme: Boolean): List<ThemePalettePreviewColor> {
    return if (isDarkTheme) {
        listOf(
            fixedPreviewColor(0xFF2A2438, 0xFFD7CBFF),
            fixedPreviewColor(0xFF30274A, 0xFFE0C7FF),
            fixedPreviewColor(0xFF232B46, 0xFFC9D2FF),
            fixedPreviewColor(0xFF34283E, 0xFFDEC3F2),
            fixedPreviewColor(0xFF26304A, 0xFFC2D7FF),
            fixedPreviewColor(0xFF2F2544, 0xFFD7C9FF),
            fixedPreviewColor(0xFF28304B, 0xFFC8D2FF),
            fixedPreviewColor(0xFF342844, 0xFFE1C6F7),
            fixedPreviewColor(0xFF2B2847, 0xFFD0CBFF),
            fixedPreviewColor(0xFF25324D, 0xFFC0D8FF),
            fixedPreviewColor(0xFF312640, 0xFFDBC8F3),
            fixedPreviewColor(0xFF292D48, 0xFFCAD0FF)
        )
    } else {
        listOf(
            fixedPreviewColor(0xFFF7F2FF, 0xFF6B51A8),
            fixedPreviewColor(0xFFF6F0FF, 0xFF7B5BBB),
            fixedPreviewColor(0xFFF2F2FF, 0xFF5665BB),
            fixedPreviewColor(0xFFF8F1FE, 0xFF85539E),
            fixedPreviewColor(0xFFF1F4FF, 0xFF4D6AAF),
            fixedPreviewColor(0xFFF6F0FF, 0xFF7158B0),
            fixedPreviewColor(0xFFF4F3FF, 0xFF5E63AA),
            fixedPreviewColor(0xFFF8F2FF, 0xFF8859A9),
            fixedPreviewColor(0xFFF2F1FF, 0xFF6B5FB2),
            fixedPreviewColor(0xFFF0F4FF, 0xFF4F70BA),
            fixedPreviewColor(0xFFF7F1FF, 0xFF7B57AA),
            fixedPreviewColor(0xFFF3F2FF, 0xFF5E67AC)
        )
    }
}

private fun fixedPreviewColor(
    container: Long,
    content: Long
): ThemePalettePreviewColor {
    return ThemePalettePreviewColor(
        container = Color(container),
        content = Color(content)
    )
}

private fun buildPreviewColor(
    containerHue: Float,
    containerSaturation: Float,
    containerValue: Float,
    contentHue: Float,
    contentSaturation: Float,
    contentValue: Float
): ThemePalettePreviewColor {
    val containerInt = android.graphics.Color.HSVToColor(
        floatArrayOf(containerHue, containerSaturation, containerValue)
    )
    val contentInt = android.graphics.Color.HSVToColor(
        floatArrayOf(contentHue, contentSaturation, contentValue)
    )
    return ThemePalettePreviewColor(
        container = Color(containerInt),
        content = Color(contentInt)
    )
}

private fun buildDynamicPreviewColorFromAnchor(
    anchorColor: Color,
    isDarkTheme: Boolean,
    toneShift: Float,
    saturationShift: Float
): ThemePalettePreviewColor {
    val anchorHsv = FloatArray(3)
    android.graphics.Color.colorToHSV(anchorColor.toArgb(), anchorHsv)
    val hue = anchorHsv[0]
    val anchorSaturation = anchorHsv[1]

    val containerSaturation = (
        anchorSaturation * if (isDarkTheme) 0.34f else 0.26f
            + saturationShift
        ).coerceIn(
        if (isDarkTheme) 0.07f else 0.05f,
        if (isDarkTheme) 0.18f else 0.14f
    )
    val containerValue = (
        if (isDarkTheme) 0.28f else 0.965f
            + toneShift
        ).coerceIn(
        if (isDarkTheme) 0.22f else 0.91f,
        if (isDarkTheme) 0.34f else 0.992f
    )
    val contentSaturation = (
        anchorSaturation * if (isDarkTheme) 0.62f else 0.56f
            + (saturationShift * 0.65f)
        ).coerceIn(
        if (isDarkTheme) 0.20f else 0.24f,
        if (isDarkTheme) 0.42f else 0.46f
    )
    val contentValue = (
        if (isDarkTheme) 0.94f else 0.30f
            - (toneShift * 0.55f)
        ).coerceIn(
        if (isDarkTheme) 0.89f else 0.21f,
        if (isDarkTheme) 0.99f else 0.35f
    )

    return buildPreviewColor(
        containerHue = hue,
        containerSaturation = containerSaturation,
        containerValue = containerValue,
        contentHue = hue,
        contentSaturation = contentSaturation,
        contentValue = contentValue
    )
}

private fun ThemePalettePreviewSpec.normalizeForBackground(
    backgroundColor: Color,
    isDarkTheme: Boolean
): ThemePalettePreviewSpec {
    return copy(
        cardColors = cardColors.map { previewColor ->
            normalizePreviewCardColorForBackground(
                previewColor = previewColor,
                backgroundColor = backgroundColor,
                isDarkTheme = isDarkTheme
            )
        }
    )
}

private fun normalizePreviewCardColorForBackground(
    previewColor: ThemePalettePreviewColor,
    backgroundColor: Color,
    isDarkTheme: Boolean
): ThemePalettePreviewColor {
    val normalizedContainer = ensureContainerBackgroundContrast(
        containerColor = previewColor.container,
        backgroundColor = backgroundColor,
        isDarkTheme = isDarkTheme
    )
    val normalizedContent = ensureContentContrast(
        preferredContentColor = previewColor.content,
        containerColor = normalizedContainer
    )
    return ThemePalettePreviewColor(
        container = normalizedContainer,
        content = normalizedContent
    )
}

private fun ensureContainerBackgroundContrast(
    containerColor: Color,
    backgroundColor: Color,
    isDarkTheme: Boolean
): Color {
    val minimumContrast = if (isDarkTheme) 1.32f else 1.18f
    if (contrastRatio(containerColor, backgroundColor) >= minimumContrast) {
        return containerColor
    }

    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(containerColor.toArgb(), hsv)
    val darkenAgainstBackground = backgroundColor.luminance() > 0.5f
    val originalSaturation = hsv[1]
    val originalValue = hsv[2]

    var bestCandidate = containerColor
    var bestContrast = contrastRatio(containerColor, backgroundColor)
    val maxSteps = 12
    for (step in 1..maxSteps) {
        val progress = step / maxSteps.toFloat()
        val candidateSaturation = (originalSaturation + (0.10f * progress)).coerceIn(
            0f,
            if (isDarkTheme) 0.45f else 0.38f
        )
        val candidateValue = if (darkenAgainstBackground) {
            (originalValue - (if (isDarkTheme) 0.10f else 0.24f) * progress).coerceAtLeast(0f)
        } else {
            (originalValue + (if (isDarkTheme) 0.20f else 0.12f) * progress).coerceAtMost(1f)
        }
        val candidate = Color(
            android.graphics.Color.HSVToColor(
                floatArrayOf(hsv[0], candidateSaturation, candidateValue)
            )
        )
        val candidateContrast = contrastRatio(candidate, backgroundColor)
        if (candidateContrast > bestContrast) {
            bestCandidate = candidate
            bestContrast = candidateContrast
        }
        if (candidateContrast >= minimumContrast) {
            return candidate
        }
    }
    return bestCandidate
}

private fun ensureContentContrast(
    preferredContentColor: Color,
    containerColor: Color
): Color {
    val minimumContrast = 4.5f
    if (contrastRatio(preferredContentColor, containerColor) >= minimumContrast) {
        return preferredContentColor
    }

    val useDarkContent = containerColor.luminance() > 0.52f
    val fallbackBase = if (useDarkContent) {
        Color(0xFF102030)
    } else {
        Color(0xFFF6F8FF)
    }
    val fallbackTarget = if (useDarkContent) Color.Black else Color.White
    var bestCandidate = preferredContentColor
    var bestContrast = contrastRatio(preferredContentColor, containerColor)

    for (step in 0..8) {
        val progress = step / 8f
        val candidate = lerp(fallbackBase, fallbackTarget, progress)
        val candidateContrast = contrastRatio(candidate, containerColor)
        if (candidateContrast > bestContrast) {
            bestCandidate = candidate
            bestContrast = candidateContrast
        }
        if (candidateContrast >= minimumContrast) {
            return candidate
        }
    }
    return bestCandidate
}

private fun contrastRatio(first: Color, second: Color): Float {
    val firstLum = first.luminance() + 0.05f
    val secondLum = second.luminance() + 0.05f
    return maxOf(firstLum, secondLum) / minOf(firstLum, secondLum)
}

private val ThemePaletteActiveRingOuterStrokeWidth: Dp = 2.4.dp
private val ThemePaletteActiveRingInnerStrokeWidth: Dp = 1.2.dp
private const val ThemePaletteActiveRingOuterAlpha: Float = 0.42f

private data class HaloLayerSpec(
    val strokeWidthScale: Float,
    val blurRadiusScale: Float,
    val alphaScale: Float
)

private val ThemePaletteActiveRingHaloSpecs = listOf(
    HaloLayerSpec(
        strokeWidthScale = 1.58f,
        blurRadiusScale = 3.8f,
        alphaScale = 0.12f
    ),
    HaloLayerSpec(
        strokeWidthScale = 1.34f,
        blurRadiusScale = 2.6f,
        alphaScale = 0.22f
    ),
    HaloLayerSpec(
        strokeWidthScale = 1.16f,
        blurRadiusScale = 1.5f,
        alphaScale = 0.36f
    ),
    HaloLayerSpec(
        strokeWidthScale = 1.04f,
        blurRadiusScale = 0.7f,
        alphaScale = 0.54f
    )
)

@Composable
private fun AnimatedThemePaletteActiveRing(
    modifier: Modifier = Modifier,
    cardShape: androidx.compose.ui.graphics.Shape,
    activeBorderColors: List<Color>,
    outerStrokeWidth: Dp = ThemePaletteActiveRingOuterStrokeWidth,
    innerStrokeWidth: Dp = ThemePaletteActiveRingInnerStrokeWidth,
    outerAlpha: Float = ThemePaletteActiveRingOuterAlpha,
    rotationDegreesState: State<Float>
) {
    val resolvedRotationDegrees by rotationDegreesState
    Box(
        modifier = modifier
            .drawWithCache {
                val safeBorderColors = if (activeBorderColors.isEmpty()) {
                    listOf(Color.White, Color.White)
                } else {
                    activeBorderColors
                }
                val gradientColors = if (safeBorderColors.first() == safeBorderColors.last()) {
                    safeBorderColors
                } else {
                    safeBorderColors + safeBorderColors.first()
                }
                val centerX = size.width / 2f
                val centerY = size.height / 2f

                val shader = android.graphics.SweepGradient(
                    centerX,
                    centerY,
                    gradientColors.map { it.toArgb() }.toIntArray(),
                    null
                )
                val outline = cardShape.createOutline(size, layoutDirection, this)
                val outlinePath = Path().apply {
                    when (outline) {
                        is Outline.Generic -> addPath(outline.path)
                        is Outline.Rounded -> addRoundRect(outline.roundRect)
                        is Outline.Rectangle -> addRect(outline.rect)
                    }
                }
                val outerStrokeWidthPx = outerStrokeWidth.toPx()
                val innerStrokeWidthPx = innerStrokeWidth.toPx()
                val shaderBrush = ShaderBrush(shader)
                val androidOutlinePath = outlinePath.asAndroidPath()
                val shaderMatrix = android.graphics.Matrix()
                val haloPaints = ThemePaletteActiveRingHaloSpecs.map { spec ->
                    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeJoin = android.graphics.Paint.Join.ROUND
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        strokeWidth = outerStrokeWidthPx * spec.strokeWidthScale
                        alpha = (255f * outerAlpha * spec.alphaScale)
                            .toInt()
                            .coerceIn(0, 255)
                        setShader(shader)
                        maskFilter = BlurMaskFilter(
                            outerStrokeWidthPx * spec.blurRadiusScale,
                            BlurMaskFilter.Blur.NORMAL
                        )
                    }
                }

                onDrawWithContent {
                    shaderMatrix.reset()
                    shaderMatrix.setRotate(resolvedRotationDegrees, centerX, centerY)
                    shader.setLocalMatrix(shaderMatrix)

                    drawIntoCanvas { canvas ->
                        haloPaints.forEach { paint ->
                            canvas.nativeCanvas.drawPath(androidOutlinePath, paint)
                        }
                    }
                    drawPath(
                        path = outlinePath,
                        style = Stroke(width = innerStrokeWidthPx),
                        brush = shaderBrush,
                        alpha = 1f
                    )
                }
            }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSelectorItem(
    title: String,
    currentOption: AppLanguageOption,
    currentLabel: String,
    options: List<Pair<AppLanguageOption, String>>,
    onValueChange: (AppLanguageOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(title) },
            textStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = AppShapes.g2(AppCorners.md),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedLabelColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = true
                )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = AppShapes.g2(AppCorners.md),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            options.forEach { entry ->
                val option = entry.first
                val label = entry.second
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                    trailingIcon = if (option == currentOption) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrequencySelectorItem(
    title: String,
    currentValueMs: Long,
    options: List<Long>,
    onValueChange: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    fun formatDuration(ms: Long): String {
        return when {
            ms < 1000 -> "${ms}ms"
            ms < 60000 -> com.lele.llmonitor.i18n.l10n("${ms / 1000}秒")
            else -> com.lele.llmonitor.i18n.l10n("${ms / 60000}分钟")
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = formatDuration(currentValueMs),
            onValueChange = {},
            readOnly = true,
            label = { Text(title) },
            textStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = AppShapes.g2(AppCorners.md),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedLabelColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = true
                )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = AppShapes.g2(AppCorners.md),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            options.forEach { ms ->
                DropdownMenuItem(
                    text = { Text(formatDuration(ms)) },
                    onClick = {
                        onValueChange(ms)
                        expanded = false
                    },
                    trailingIcon = if (ms == currentValueMs) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null
                )
            }
        }
    }
}
