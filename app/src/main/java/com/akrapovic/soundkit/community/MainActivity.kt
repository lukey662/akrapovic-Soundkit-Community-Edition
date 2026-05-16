package com.akrapovic.soundkit.community

import android.content.Context
import android.content.pm.PackageManager
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
            val permissions = remember { PermissionPolicy.requiredRuntimePermissions() }
            var permissionsGranted by remember { mutableStateOf(hasAllPermissions(permissions)) }
            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions(),
            ) { result ->
                permissionsGranted = permissions.all { permission ->
                    result[permission] == true || ContextCompat.checkSelfPermission(
                        this,
                        permission,
                    ) == PackageManager.PERMISSION_GRANTED
                }
            }
            val viewModel: SoundKitViewModel = hiltViewModel()

            LaunchedEffect(Unit) {
                permissionsGranted = hasAllPermissions(permissions)
            }
            LaunchedEffect(permissionsGranted) {
                if (permissionsGranted) {
                    BleConnectionService.start(this@MainActivity)
                }
            }

            SoundKitApp(
                viewModel = viewModel,
                permissions = permissions,
                permissionsGranted = permissionsGranted,
                onRequestPermissions = { launcher.launch(permissions.toTypedArray()) },
            )
        }
    }

    private fun Context.hasAllPermissions(permissions: List<String>): Boolean {
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}

