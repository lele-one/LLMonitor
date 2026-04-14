package com.lele.llmonitor.ui

import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import com.lele.llmonitor.data.HomeWallpaperManager
import com.lele.llmonitor.data.HomeWallpaperStorage
import com.lele.llmonitor.data.SettingsManager
import com.lele.llmonitor.data.resolveHomeWallpaperStartupPreviewRenderSpec
import com.lele.llmonitor.service.BatteryMonitorService
import com.lele.llmonitor.ui.components.AppTopBar
import com.lele.llmonitor.ui.dashboard.BatteryViewModel
import com.lele.llmonitor.ui.dashboard.DashboardScreen
import com.lele.llmonitor.ui.navigation.MainNavHost
import com.lele.llmonitor.ui.soc.SocScreen
import com.lele.llmonitor.ui.theme.LLMonitorTheme
import com.lele.llmonitor.ui.theme.resolveAppColorScheme
import com.lele.llmonitor.ui.widget.forceRefreshBatteryWidgetsOnce
import com.lele.llmonitor.ui.wallpaper.HomeWallpaperImage
import com.lele.llmonitor.ui.wallpaper.homeWallpaperBlur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        @Volatile
        private var hasForcedWidgetRefreshInCurrentProcess: Boolean = false
    }

    private var isHdrModeEnabled: Boolean = false

    private fun resolveLaunchDarkTheme(themeMode: Int): Boolean {
        return when (themeMode) {
            1 -> false
            2 -> true
            else -> {
                val uiMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                uiMode == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    private fun applyLaunchWindowBackground() {
        val launchSnapshot = SettingsManager.resolveLaunchAppearanceSnapshot()
        val themeMode = launchSnapshot?.themeMode ?: SettingsManager.themeMode.value
        val themePalettePreset = launchSnapshot?.themePalettePreset ?: SettingsManager.themePalettePreset.value

        val backgroundDrawable = if (launchSnapshot != null) {
            ColorDrawable(launchSnapshot.backgroundArgb)
        } else {
            val colorScheme = resolveAppColorScheme(
                context = this,
                darkTheme = resolveLaunchDarkTheme(themeMode),
                themePalettePreset = themePalettePreset
            )
            ColorDrawable(colorScheme.background.toArgb())
        }
        if (launchSnapshot == null || !launchSnapshot.wallpaperEnabled) {
            window.setBackgroundDrawable(backgroundDrawable)
            return
        }
        val wallpaperFile = HomeWallpaperStorage.resolveWallpaperFile(this)
        val wallpaperVersionMatches = wallpaperFile.exists() &&
            wallpaperFile.length() > 0L &&
            wallpaperFile.lastModified() == launchSnapshot.wallpaperVersion
        val displayDensity = resources.displayMetrics.density
        val expectedRenderSpec = resolveHomeWallpaperStartupPreviewRenderSpec(
            backgroundArgb = launchSnapshot.backgroundArgb,
            wallpaperAlpha = launchSnapshot.wallpaperAlpha,
            wallpaperBlur = launchSnapshot.wallpaperBlur,
            displayDensity = displayDensity
        )
        val previewFile = HomeWallpaperStorage.resolveStartupPreviewFile(this).takeIf {
            it.exists() && it.length() > 0L
        }
        val previewMatches = wallpaperVersionMatches &&
            previewFile != null &&
            launchSnapshot.startupPreviewVersion == launchSnapshot.wallpaperVersion &&
            launchSnapshot.startupPreviewRenderSpec == expectedRenderSpec
        if (!previewMatches) {
            window.setBackgroundDrawable(backgroundDrawable)
            return
        }
        val wallpaperDrawable = BitmapDrawable(resources, requireNotNull(previewFile).absolutePath).apply {
            gravity = Gravity.FILL
            alpha = 255
        }
        window.setBackgroundDrawable(
            LayerDrawable(
                arrayOf(
                    backgroundDrawable,
                    wallpaperDrawable
                )
            )
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        SettingsManager.init(applicationContext)
        applyLaunchWindowBackground()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val themeMode by SettingsManager.themeMode
            val themePalettePreset by SettingsManager.themePalettePreset
            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemDark
            }

            LLMonitorTheme(
                darkTheme = darkTheme,
                themePalettePreset = themePalettePreset
            ) {
                val viewModel: BatteryViewModel = viewModel()
                val wallpaperEnabled by HomeWallpaperManager.isEnabled
                val wallpaperAlpha by HomeWallpaperManager.wallpaperAlpha
                val wallpaperBlur by HomeWallpaperManager.wallpaperBlur
                val wallpaperFile by HomeWallpaperManager.wallpaperFile

                MainNavHost(
                    viewModel = viewModel,
                    wallpaperEnabled = wallpaperEnabled,
                    wallpaperAlpha = wallpaperAlpha,
                    wallpaperBlur = wallpaperBlur,
                    wallpaperFile = wallpaperFile,
                    onSetHdrMode = { enabled ->
                        setHdrMode(enabled)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (!hasForcedWidgetRefreshInCurrentProcess) {
            hasForcedWidgetRefreshInCurrentProcess = true
            lifecycleScope.launch(Dispatchers.Default) {
                runCatching {
                    forceRefreshBatteryWidgetsOnce(applicationContext)
                }
            }
        }

        lifecycleScope.launch {
            // 冷启动优先保障首屏交互；服务启动稍后执行，不影响最终功能。
            delay(8000L)
            if (!BatteryMonitorService.isRunning) {
                ContextCompat.startForegroundService(
                    applicationContext,
                    Intent(applicationContext, BatteryMonitorService::class.java)
                )
            }
        }
    }

    private fun setHdrMode(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (enabled == isHdrModeEnabled) return
            isHdrModeEnabled = enabled

            val colorMode = if (enabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    android.content.pm.ActivityInfo.COLOR_MODE_HDR
                } else {
                    android.content.pm.ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT
                }
            } else {
                android.content.pm.ActivityInfo.COLOR_MODE_DEFAULT
            }
            window.colorMode = colorMode
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainSwipeScreen(
    viewModel: BatteryViewModel,
    wallpaperEnabled: Boolean,
    wallpaperAlpha: Float,
    wallpaperBlur: Float,
    wallpaperFile: File?,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onSetHdrMode: (Boolean) -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 2 }
    )
    val socPreloadReady = viewModel.socPreloadReady
    val homeStable = viewModel.isStable
    LaunchedEffect(homeStable) {
        if (homeStable) {
            // 仅在主页进入稳定态后触发一次预加载，避免返回主页时重复抢占主线程。
            viewModel.scheduleSocPreloadIfNeeded()
        }
    }
    val instant = viewModel.instantStatus
    val subtitle = if (instant.remainingTime != "--") {
        com.lele.llmonitor.i18n.l10n("状态：${instant.statusText} (余 ${instant.remainingTime})")
    } else {
        com.lele.llmonitor.i18n.l10n("状态：${instant.statusText}")
    }

    val wallpaperModeEnabled = wallpaperEnabled && wallpaperFile != null
    val mainContainerColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(mainContainerColor)
    ) {
        if (wallpaperModeEnabled) {
            HomeWallpaperImage(
                wallpaperFile = requireNotNull(wallpaperFile),
                alpha = wallpaperAlpha,
                modifier = Modifier
                    .fillMaxSize()
                    .homeWallpaperBlur(wallpaperBlur)
            )
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = if (wallpaperModeEnabled) Color.Transparent else mainContainerColor,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                AppTopBar(
                    scheduleName = "",
                    weekStatusText = subtitle,
                    wallpaperModeEnabled = wallpaperModeEnabled,
                    onAboutClick = onOpenAbout,
                    onSettingsClick = onOpenSettings
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                MainContentPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    viewModel = viewModel,
                    onSetHdrMode = onSetHdrMode,
                    userScrollEnabled = true,
                    socPreloadReady = socPreloadReady
                )
            }
        }
    }
}

@Composable
private fun MainContentPager(
    state: PagerState,
    modifier: Modifier,
    viewModel: BatteryViewModel,
    onSetHdrMode: (Boolean) -> Unit,
    userScrollEnabled: Boolean,
    socPreloadReady: Boolean
) {
    HorizontalPager(
        state = state,
        modifier = modifier,
        beyondViewportPageCount = 1,
        userScrollEnabled = userScrollEnabled
    ) { page ->
        val pageOffset = ((state.currentPage - page) + state.currentPageOffsetFraction).absoluteValue
        val transitionFraction = 1f - pageOffset.coerceIn(0f, 1f)
        val pageScale = lerp(0.96f, 1f, transitionFraction)
        val pageAlpha = lerp(0.90f, 1f, transitionFraction)

        val pageModifier = if (pageOffset < 0.001f) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = pageScale
                    scaleY = pageScale
                    alpha = pageAlpha
                }
        }
        Box(modifier = pageModifier) {
            when (page) {
                0 -> DashboardScreen(
                    viewModel = viewModel,
                    onSetHdrMode = onSetHdrMode
                )

                1 -> {
                    if (state.currentPage == 1 || socPreloadReady) {
                        SocScreen()
                    } else {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
