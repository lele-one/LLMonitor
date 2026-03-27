package com.lele.llmonitor.ui.navigation

import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.lele.llmonitor.ui.MainSwipeScreen
import com.lele.llmonitor.ui.dashboard.BatteryViewModel
import com.lele.llmonitor.ui.settings.SettingsScreen
import com.lele.llmonitor.ui.settings.SettingsRoutes
import java.io.File

internal object RootRoutes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_SCENE = "settings/scene"
    const val SETTINGS_HARDWARE = "settings/hardware"
    const val SETTINGS_SYSTEM = "settings/system"
    const val SETTINGS_DATA = "settings/data"
    const val SETTINGS_ABOUT = "settings_about"
}

private fun mapSettingsInternalRouteToRootRoute(route: String): String {
    return when (route) {
        SettingsRoutes.APPEARANCE -> RootRoutes.SETTINGS_APPEARANCE
        SettingsRoutes.SCENE -> RootRoutes.SETTINGS_SCENE
        SettingsRoutes.HARDWARE -> RootRoutes.SETTINGS_HARDWARE
        SettingsRoutes.SYSTEM -> RootRoutes.SETTINGS_SYSTEM
        SettingsRoutes.DATA -> RootRoutes.SETTINGS_DATA
        SettingsRoutes.ABOUT -> RootRoutes.SETTINGS_ABOUT
        else -> RootRoutes.SETTINGS
    }
}

internal fun androidx.navigation.NavGraphBuilder.registerMainRoutes(
    navController: NavHostController,
    viewModel: BatteryViewModel,
    wallpaperEnabled: Boolean,
    wallpaperAlpha: Float,
    wallpaperBlur: Float,
    wallpaperFile: File?,
    onSetHdrMode: (Boolean) -> Unit
) {
    val navigateToSettingsRoot: (String) -> Unit = { settingsRoute ->
        navController.navigate(mapSettingsInternalRouteToRootRoute(settingsRoute)) {
            launchSingleTop = true
        }
    }

    composable(RootRoutes.HOME) {
        MainSwipeScreen(
            viewModel = viewModel,
            wallpaperEnabled = wallpaperEnabled,
            wallpaperAlpha = wallpaperAlpha,
            wallpaperBlur = wallpaperBlur,
            wallpaperFile = wallpaperFile,
            onOpenSettings = {
                navController.navigate(RootRoutes.SETTINGS) {
                    launchSingleTop = true
                }
            },
            onOpenAbout = {
                navController.navigate(RootRoutes.SETTINGS_ABOUT) {
                    launchSingleTop = true
                }
            },
            onSetHdrMode = onSetHdrMode
        )
    }
    composable(RootRoutes.SETTINGS) {
        SettingsScreen(
            viewModel = viewModel,
            onExit = { navController.popBackStack() },
            openAboutDirectly = false,
            initialSettingsRoute = SettingsRoutes.HOME,
            onNavigateFromHome = navigateToSettingsRoot
        )
    }
    composable(RootRoutes.SETTINGS_APPEARANCE) {
        SettingsScreen(
            viewModel = viewModel,
            onExit = { navController.popBackStack() },
            initialSettingsRoute = SettingsRoutes.APPEARANCE,
            onNavigateFromHome = navigateToSettingsRoot
        )
    }
    composable(RootRoutes.SETTINGS_SCENE) {
        SettingsScreen(
            viewModel = viewModel,
            onExit = { navController.popBackStack() },
            initialSettingsRoute = SettingsRoutes.SCENE,
            onNavigateFromHome = navigateToSettingsRoot
        )
    }
    composable(RootRoutes.SETTINGS_HARDWARE) {
        SettingsScreen(
            viewModel = viewModel,
            onExit = { navController.popBackStack() },
            initialSettingsRoute = SettingsRoutes.HARDWARE,
            onNavigateFromHome = navigateToSettingsRoot
        )
    }
    composable(RootRoutes.SETTINGS_SYSTEM) {
        SettingsScreen(
            viewModel = viewModel,
            onExit = { navController.popBackStack() },
            initialSettingsRoute = SettingsRoutes.SYSTEM,
            onNavigateFromHome = navigateToSettingsRoot
        )
    }
    composable(RootRoutes.SETTINGS_DATA) {
        SettingsScreen(
            viewModel = viewModel,
            onExit = { navController.popBackStack() },
            initialSettingsRoute = SettingsRoutes.DATA,
            onNavigateFromHome = navigateToSettingsRoot
        )
    }
    composable(RootRoutes.SETTINGS_ABOUT) {
        SettingsScreen(
            viewModel = viewModel,
            onExit = { navController.popBackStack() },
            openAboutDirectly = true,
            initialSettingsRoute = SettingsRoutes.ABOUT,
            onNavigateFromHome = navigateToSettingsRoot
        )
    }
}
