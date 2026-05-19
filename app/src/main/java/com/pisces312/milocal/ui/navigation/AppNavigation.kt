package com.pisces312.milocal.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pisces312.milocal.ui.adddevice.AddDeviceScreen
import com.pisces312.milocal.ui.device.DeviceControlScreen
import com.pisces312.milocal.ui.home.HomeScreen

object Routes {
    const val HOME = "home"
    const val ADD_DEVICE = "add_device"
    const val DEVICE_CONTROL = "device/{deviceId}"

    fun deviceControl(deviceId: Long) = "device/$deviceId"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onAddDevice = { navController.navigate(Routes.ADD_DEVICE) },
                onDeviceClick = { id -> navController.navigate(Routes.deviceControl(id)) }
            )
        }
        composable(Routes.ADD_DEVICE) {
            AddDeviceScreen(
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.DEVICE_CONTROL,
            arguments = listOf(navArgument("deviceId") { type = NavType.LongType })
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getLong("deviceId") ?: 0L
            DeviceControlScreen(
                deviceId = deviceId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
