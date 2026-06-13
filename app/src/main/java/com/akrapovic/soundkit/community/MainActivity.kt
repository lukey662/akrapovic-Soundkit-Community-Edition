package com.akrapovic.soundkit.community

import android.Manifest
import android.content.Context
import android.content.Intent
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
    companion object {
        const val EXTRA_NAV_HOME = "nav_home"
        const val ACTION_SHORTCUT_OPEN = "com.akrapovic.soundkit.community.action.SHORTCUT_OPEN"
        const val ACTION_SHORTCUT_CLOSE = "com.akrapovic.soundkit.community.action.SHORTCUT_CLOSE"
        const val ACTION_SHORTCUT_CONNECT = "com.akrapovic.soundkit.community.action.SHORTCUT_CONNECT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShortcutIntent(intent)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcutIntent(intent)
    }

    private fun handleShortcutIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_SHORTCUT_OPEN -> dispatchValveAction(BleConnectionService.ACTION_OPEN)
            ACTION_SHORTCUT_CLOSE -> dispatchValveAction(BleConnectionService.ACTION_CLOSE)
            ACTION_SHORTCUT_CONNECT -> BleConnectionService.start(this)
        }
    }

    private fun dispatchValveAction(action: String) {
        BleConnectionService.start(this)
        startService(Intent(this, BleConnectionService::class.java).setAction(action))
    }

    private fun Context.hasAllPermissions(permissions: List<String>): Boolean {
        return permissions.all { hasPermission(it) }
    }

    private fun Context.hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
}
