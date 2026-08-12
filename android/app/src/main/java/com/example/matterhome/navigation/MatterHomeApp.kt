package com.example.matterhome.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesomeMotion
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.matterhome.AppViewModel
import com.example.matterhome.screens.AddDeviceScreen
import com.example.matterhome.screens.AutomationsScreen
import com.example.matterhome.screens.CommissioningProgressScreen
import com.example.matterhome.screens.DeviceDetailScreen
import com.example.matterhome.screens.HomeScreen
import com.example.matterhome.screens.SettingsScreen
import com.example.matterhome.screens.WifiSetupScreen
import com.example.matter.api.WifiCredentials

private object Routes {
    const val Home = "home"
    const val Automations = "automations"
    const val Settings = "settings"
    const val AddDevice = "add-device"
    const val Wifi = "wifi"
    const val Progress = "commissioning"
    const val Device = "device/{deviceId}"
    fun device(deviceId: String) = "device/$deviceId"
}

@Composable
fun MatterHomeApp(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBottomBar = route in setOf(Routes.Home, Routes.Automations, Routes.Settings)
    var pendingCredentials by remember { mutableStateOf<WifiCredentials?>(null) }
    val bluetoothPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val credentials = pendingCredentials
            pendingCredentials = null
            if (credentials != null && bluetoothPermissions.all { grants[it] == true }) {
                viewModel.commission(credentials)
                navController.navigate(Routes.Progress)
            } else {
                viewModel.showError("Bluetooth permission is required to add a Matter device")
            }
        }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(navController, route)
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Home,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.Home) {
                HomeScreen(
                    viewModel = viewModel,
                    onAddDevice = { navController.navigate(Routes.AddDevice) },
                    onDevice = { navController.navigate(Routes.device(it)) },
                )
            }
            composable(Routes.Automations) { AutomationsScreen(padding) }
            composable(Routes.Settings) { SettingsScreen(padding) }
            composable(Routes.AddDevice) {
                AddDeviceScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onCodeReady = { navController.navigate(Routes.Wifi) },
                )
            }
            composable(Routes.Wifi) {
                WifiSetupScreen(
                    onBack = { navController.popBackStack() },
                    onContinue = { credentials ->
                        val missingPermissions =
                            bluetoothPermissions.filter {
                                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                            }
                        if (missingPermissions.isEmpty()) {
                            viewModel.commission(credentials)
                            navController.navigate(Routes.Progress)
                        } else {
                            pendingCredentials = credentials
                            permissionLauncher.launch(missingPermissions.toTypedArray())
                        }
                    },
                )
            }
            composable(Routes.Progress) {
                CommissioningProgressScreen(
                    viewModel = viewModel,
                    onClose = {
                        viewModel.cancelCommissioning()
                        navController.popBackStack(Routes.Home, false)
                    },
                    onRetry = {
                        viewModel.cancelCommissioning()
                        navController.popBackStack()
                    },
                    onCompleted = { deviceId ->
                        navController.navigate(Routes.device(deviceId)) {
                            popUpTo(Routes.Home)
                        }
                    },
                )
            }
            composable(Routes.Device) { stackEntry ->
                DeviceDetailScreen(
                    viewModel = viewModel,
                    deviceId = stackEntry.arguments?.getString("deviceId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onRemoved = { navController.popBackStack(Routes.Home, false) },
                )
            }
        }
    }
}

@Composable
private fun AppBottomBar(navController: NavHostController, currentRoute: String?) {
    val items = listOf(
        Triple(Routes.Home, "Home", Icons.Filled.Home),
        Triple(Routes.Automations, "Automations", Icons.Outlined.AutoAwesomeMotion),
        Triple(Routes.Settings, "Settings", Icons.Filled.Settings),
    )
    NavigationBar {
        items.forEach { (route, label, icon) ->
            NavigationBarItem(
                selected = currentRoute == route,
                onClick = {
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
            )
        }
    }
}
