package com.akrapovic.soundkit.community.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.diagnostics.DiagnosticsShare
import com.akrapovic.soundkit.community.domain.DiagnosticsEntry
import com.akrapovic.soundkit.community.domain.DiagnosticsLevel
import com.akrapovic.soundkit.community.ui.components.AkraCardShape
import com.akrapovic.soundkit.community.ui.components.AkraHeroHeader
import com.akrapovic.soundkit.community.ui.theme.AkraColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    modifier: Modifier = Modifier,
    entries: List<DiagnosticsEntry>,
    hasPendingCrash: Boolean = false,
    onBuildReport: () -> String = { entries.toExportText() },
    onCreateReportFile: () -> File? = { null },
    onBuildCrashReport: () -> String = { "" },
    onCreateCrashReportFile: () -> File? = { null },
    onCrashHandled: () -> Unit = {},
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            AkraHeroHeader(
                eyebrow = "Troubleshooting",
                title = "Diagnostics",
                subtitle = "Create a local report when pairing or connection support needs more detail.",
            )
            val hasReport = entries.isNotEmpty() || hasPendingCrash
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DiagnosticsButton(
                    modifier = Modifier.weight(1f),
                    label = "Copy report",
                    enabled = hasReport,
                    filled = true,
                    contentDescription = "Copy diagnostics report to clipboard",
                    onClick = {
                        clipboardManager.setText(AnnotatedString(onBuildReport()))
                    },
                )
                DiagnosticsButton(
                    modifier = Modifier.weight(1f),
                    label = "Share report",
                    enabled = hasReport,
                    filled = false,
                    contentDescription = "Share diagnostics report",
                    onClick = {
                        onCreateReportFile()?.let { file ->
                            DiagnosticsShare.shareReport(
                                context = context,
                                file = file,
                                subject = "Sound Kit diagnostics",
                                previewText = onBuildReport(),
                            )
                        }
                    },
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        if (hasPendingCrash) {
            item {
                CrashPanel(
                    onCopyCrash = {
                        clipboardManager.setText(AnnotatedString(onBuildCrashReport()))
                    },
                    onShareCrash = {
                        onCreateCrashReportFile()?.let { file ->
                            DiagnosticsShare.shareReport(
                                context = context,
                                file = file,
                                subject = "Sound Kit crash log",
                                previewText = onBuildCrashReport(),
                            )
                            onCrashHandled()
                        }
                    },
                    onDismiss = onCrashHandled,
                )
            }
        }

        if (entries.isEmpty()) {
            item {
                EmptyStatePanel()
            }
        } else {
            items(entries.asReversed(), key = { it.id }) { entry ->
                DiagnosticRow(entry)
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun DiagnosticsButton(
    modifier: Modifier = Modifier,
    label: String,
    enabled: Boolean,
    filled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                when {
                    !enabled -> AkraColors.Titanium
                    filled -> accent
                    else -> Color.Transparent
                },
            )
            .border(1.dp, if (enabled) accent else AkraColors.Titanium, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                !enabled -> AkraColors.Mist
                filled -> MaterialTheme.colorScheme.onPrimary
                else -> accent
            },
        )
    }
}

@Composable
private fun CrashPanel(
    onCopyCrash: () -> Unit,
    onShareCrash: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .clip(AkraCardShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, AkraColors.Danger.copy(alpha = 0.45f), AkraCardShape),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(AkraColors.Danger),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Eyebrow("Crash report")
            Text(
                text = "Crash detected on last session",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "The crash log is stored only on this phone. Share it only if you want help debugging it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DiagnosticsButton(
                    modifier = Modifier.weight(1f),
                    label = "Copy crash",
                    enabled = true,
                    filled = false,
                    contentDescription = "Copy crash log",
                    onClick = onCopyCrash,
                )
                DiagnosticsButton(
                    modifier = Modifier.weight(1f),
                    label = "Share crash",
                    enabled = true,
                    filled = true,
                    contentDescription = "Share crash log",
                    onClick = onShareCrash,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .border(1.dp, AkraColors.Titanium)
                    .clickable(onClick = onDismiss)
                    .semantics { contentDescription = "Dismiss crash log" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Dismiss crash log",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyStatePanel() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(AkraCardShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, AkraColors.Titanium, AkraCardShape),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(AkraColors.Mist),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Eyebrow("Ready")
            Text(
                text = "No diagnostics yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Start a scan or connect to a receiver to see BLE events here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiagnosticRow(entry: DiagnosticsEntry) {
    val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    val accent = entry.level.accentColor()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, AkraColors.Titanium, RoundedCornerShape(18.dp)),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accent),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${formatter.format(Date(entry.timestampMillis))}  ${entry.level.name.uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = accent.copy(alpha = 0.85f),
            )
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Eyebrow(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

private fun DiagnosticsLevel.accentColor(): Color = when (this) {
    DiagnosticsLevel.Debug -> AkraColors.Mist
    DiagnosticsLevel.Info -> AkraColors.Silver
    DiagnosticsLevel.Warning -> AkraColors.Amber
    DiagnosticsLevel.Error -> AkraColors.Danger
}

private fun List<DiagnosticsEntry>.toExportText(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    return joinToString(separator = "\n") { entry ->
        "${formatter.format(Date(entry.timestampMillis))} ${entry.level} ${entry.message}"
    }
}
