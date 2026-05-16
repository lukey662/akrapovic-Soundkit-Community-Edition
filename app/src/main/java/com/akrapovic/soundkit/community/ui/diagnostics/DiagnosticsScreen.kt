package com.akrapovic.soundkit.community.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.domain.DiagnosticsEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    modifier: Modifier = Modifier,
    entries: List<DiagnosticsEntry>,
) {
    val clipboardManager = LocalClipboardManager.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Diagnostics",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text("Local-only BLE events. No logs are uploaded or shared automatically.")
        Button(
            enabled = entries.isNotEmpty(),
            onClick = {
                clipboardManager.setText(AnnotatedString(entries.toExportText()))
            },
        ) {
            Text("Copy diagnostics report")
        }
        if (entries.isEmpty()) {
            Card {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = "No diagnostics yet. Start a scan or connect to a receiver.",
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries.asReversed()) { entry ->
                    DiagnosticRow(entry)
                }
            }
        }
    }
}

private fun List<DiagnosticsEntry>.toExportText(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    return joinToString(separator = "\n") { entry ->
        "${formatter.format(Date(entry.timestampMillis))} ${entry.level} ${entry.message}"
    }
}

@Composable
private fun DiagnosticRow(entry: DiagnosticsEntry) {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)
    Card {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${formatter.format(Date(entry.timestampMillis))} ${entry.level}",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(entry.message)
        }
    }
}

