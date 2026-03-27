package com.lele.llmonitor.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.lele.llmonitor.ui.dashboard.BatteryViewModel
import com.lele.llmonitor.ui.motion.AppMotion
import java.io.File

@Composable
internal fun MainNavHost(
    viewModel: BatteryViewModel,
    wallpaperEnabled: Boolean,
    wallpaperAlpha: Float,
    wallpaperBlur: Float,
    wallpaperFile: File?,
    onSetHdrMode: (Boolean) -> Unit,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = RootRoutes.HOME,
        enterTransition = { AppMotion.routeEnterTransition() },
        exitTransition = { AppMotion.routeExitTransition() },
        popEnterTransition = { AppMotion.routePopEnterTransition() },
        popExitTransition = { AppMotion.routePopExitTransition() },
        modifier = modifier
    ) {
        registerMainRoutes(
            navController = navController,
            viewModel = viewModel,
            wallpaperEnabled = wallpaperEnabled,
            wallpaperAlpha = wallpaperAlpha,
            wallpaperBlur = wallpaperBlur,
            wallpaperFile = wallpaperFile,
            onSetHdrMode = onSetHdrMode
        )
    }
}
