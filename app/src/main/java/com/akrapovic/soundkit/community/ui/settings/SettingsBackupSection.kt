package com.akrapovic.soundkit.community.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.ui.components.AkraActionButton
import com.akrapovic.soundkit.community.ui.components.AkraListGroup
import com.akrapovic.soundkit.community.ui.components.AkraListRow
import com.akrapovic.soundkit.community.ui.components.AkraSectionTitle

@Composable
fun SettingsBackupSection(
    onExportBackup: () -> String,
    onImportBackup: (String) -> Unit,
) {
    val context = LocalContext.current
    val pendingImport = remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(onExportBackup().toByteArray())
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null && pendingImport.value) {
            val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            if (!json.isNullOrBlank()) {
                onImportBackup(json)
            }
        }
        pendingImport.value = false
    }

    AkraSectionTitle("Backup")
    Text(
        text = "Export or restore receivers, drive mode, theme, and vehicle selection. Local only — no cloud.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    AkraListGroup {
        AkraListRow(
            title = "Export settings",
            subtitle = "Save a JSON backup to Files",
            showChevron = true,
            onClick = { exportLauncher.launch("soundkit-settings-backup.json") },
        )
        AkraListRow(
            title = "Import settings",
            subtitle = "Restore from a previous backup",
            showChevron = true,
            onClick = {
                pendingImport.value = true
                importLauncher.launch(arrayOf("application/json", "text/plain"))
            },
        )
    }
    Spacer(Modifier.height(8.dp))
}
