package com.akrapovic.soundkit.community.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.SoundKitDevice
import com.akrapovic.soundkit.community.ui.SoundKitUiState
import com.akrapovic.soundkit.community.ui.control.ConnectedDeviceScreen
import com.akrapovic.soundkit.community.ui.scan.ScanScreen

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    state: SoundKitUiState,
    permissions: List<String>,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (SoundKitDevice) -> Unit,
    onToggleValve: () -> Unit,
    onDisconnect: () -> Unit,
    onRetryConnection: () -> Unit = {},
) {
    if (state.showsControlSection()) {
        ConnectedDeviceScreen(
            modifier = modifier,
            state = state,
            onToggleValve = onToggleValve,
            onDisconnect = onDisconnect,
            onRetryConnection = onRetryConnection,
        )
    } else {
        ScanScreen(
            modifier = modifier,
            state = state,
            permissions = permissions,
            permissionsGranted = permissionsGranted,
            onRequestPermissions = onRequestPermissions,
            onStartScan = onStartScan,
            onStopScan = onStopScan,
            onConnect = onConnect,
            onRetryConnection = onRetryConnection,
        )
    }
}

private fun SoundKitUiState.showsControlSection(): Boolean {
    return when (connectionState) {
        is ConnectionState.Connected,
        is ConnectionState.Connecting,
        is ConnectionState.Reconnecting,
        -> true
        ConnectionState.Disconnected,
        ConnectionState.Scanning,
        is ConnectionState.Error,
        -> false
    }
}
