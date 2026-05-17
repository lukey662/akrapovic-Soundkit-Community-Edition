package com.akrapovic.soundkit.community

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.akrapovic.soundkit.community.ble.PermissionPolicy
import com.akrapovic.soundkit.community.service.BleConnectionService
import com.akrapovic.soundkit.community.ui.SoundKitApp
import com.akrapovic.soundkit.community.ui.SoundKitViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val blePermissions = remember { PermissionPolicy.requiredBlePermissions() }
            val notificationPermissions = remember {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    listOf(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    emptyList()
                }
            }
            var blePermissionsGranted by remember { mutableStateOf(hasAllPermissions(blePermissions)) }
            var notificationsGranted by remember { mutableStateOf(hasAllPermissions(notificationPermissions)) }

            val bleLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions(),
            ) { result ->
                blePermissionsGranted = blePermissions.all { permission ->
                    result[permission] == true || hasPermission(permission)
                }
            }
            val notificationLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions(),
            ) { result ->
                notificationsGranted = notificationPermissions.all { permission ->
                    result[permission] == true || hasPermission(permission)
                }
            }
            val viewModel: SoundKitViewModel = hiltViewModel()

            LaunchedEffect(Unit) {
                blePermissionsGranted = hasAllPermissions(blePermissions)
                notificationsGranted = hasAllPermissions(notificationPermissions)
            }
            LaunchedEffect(blePermissionsGranted) {
                if (blePermissionsGranted) {
                    BleConnectionService.start(this@MainActivity)
                }
            }

            SoundKitApp(
                viewModel = viewModel,
                blePermissions = blePermissions,
                blePermissionsGranted = blePermissionsGranted,
                notificationsGranted = notificationsGranted,
                onRequestBlePermissions = { bleLauncher.launch(blePermissions.toTypedArray()) },
                onRequestNotificationPermission = {
                    notificationLauncher.launch(notificationPermissions.toTypedArray())
                },
            )
        }
    }

    private fun Context.hasAllPermissions(permissions: List<String>): Boolean {
        return permissions.all { hasPermission(it) }
    }

    private fun Context.hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
}
