package com.akrapovic.soundkit.community.ui.onboarding

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.ui.components.AkraActionButton
import com.akrapovic.soundkit.community.ui.components.AkraCard
import com.akrapovic.soundkit.community.ui.components.AkraHeroHeader
import com.akrapovic.soundkit.community.ui.components.AkraScreen
import com.akrapovic.soundkit.community.ui.components.AkraStatusPill
import com.akrapovic.soundkit.community.ui.theme.LocalAkraTheme

private data class OnboardingStep(
    val id: String,
    val label: String,
    val contentDescription: String,
)

private val onboardingSteps = listOf(
    OnboardingStep("risk", "Risk", "Risk notice"),
    OnboardingStep("vehicle", "Vehicle", "Vehicle selection step"),
    OnboardingStep("bluetooth", "Bluetooth", "Bluetooth onboarding step"),
    OnboardingStep("notifications", "Alerts", "Notifications onboarding step"),
    OnboardingStep("battery", "Battery", "Battery onboarding step"),
)

@Composable
fun OnboardingFlow(
    blePermissionsGranted: Boolean,
    notificationsGranted: Boolean,
    selectedVehicleId: String?,
    onAcceptRisk: () -> Unit,
    onSelectVehicle: (String) -> Unit,
    onRequestBlePermissions: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    val accent = LocalAkraTheme.current.accent
    val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    var riskAccepted by rememberSaveable { mutableStateOf(false) }
    var riskExpanded by rememberSaveable { mutableStateOf(false) }

    val stepComplete = listOf(
        riskAccepted,
        isVehicleSelectionComplete(selectedVehicleId),
        blePermissionsGranted,
        notificationsGranted || !needsNotificationPermission,
        true, // battery is optional
    )
    val readyToFinish = stepComplete[0] && stepComplete[1] && stepComplete[2] && stepComplete[3]
    val activeStepIndex = stepComplete.indexOfFirst { !it }.let { if (it < 0) onboardingSteps.lastIndex else it }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!readyToFinish) {
                    Text(
                        text = when {
                            !riskAccepted -> "Accept the disclaimer to continue."
                            !isVehicleSelectionComplete(selectedVehicleId) ->
                                "Select your car (or Other car with Sound Kit) to continue."
                            !blePermissionsGranted -> "Bluetooth access is required to find your receiver."
                            needsNotificationPermission && !notificationsGranted ->
                                "Allow notifications so the connection can stay active."
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AkraActionButton(
                    label = "Get started",
                    enabled = readyToFinish,
                    contentDescription = "Finish setup and open Sound Kit",
                    onClick = {
                        onAcceptRisk()
                        onComplete()
                    },
                )
                TextButton(
                    onClick = { (context as? Activity)?.finish() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Exit app")
                }
            }
        },
    ) { paddingValues ->
        AkraScreen(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(paddingValues),
            scroll = true,
        ) {
            AkraHeroHeader(
                eyebrow = "First launch",
                title = "Set up Sound Kit",
                subtitle = "Everything on one screen — grant what you need, then tap Get started.",
                titleModifier = Modifier.semantics {
                    heading()
                    contentDescription = "Onboarding"
                },
            )

            OnboardingBreadcrumbs(
                steps = onboardingSteps,
                stepComplete = stepComplete,
                activeIndex = activeStepIndex,
            )

            OnboardingSection(
                stepNumber = 1,
                title = "Use at your own risk",
                titleSemantics = "Risk notice",
                complete = riskAccepted,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Independent open-source app — not affiliated with Akrapovič. " +
                            "Reverse-engineered protocol; use only when parked. You accept all risk.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(
                        modifier = Modifier.animateContentSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (riskExpanded) {
                            RiskNoticeBullets()
                        }
                        TextButton(onClick = { riskExpanded = !riskExpanded }) {
                            Text(if (riskExpanded) "Hide full disclaimer" else "Read full disclaimer")
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .clickable { riskAccepted = !riskAccepted }
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                            .semantics { contentDescription = "Accept risk disclaimer" },
                    ) {
                        Checkbox(
                            checked = riskAccepted,
                            onCheckedChange = {
                                riskAccepted = it
                                if (it) onAcceptRisk()
                            },
                            colors = CheckboxDefaults.colors(checkedColor = accent),
                        )
                        Text(
                            text = "I understand and accept",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            OnboardingSection(
                stepNumber = 2,
                title = "Your car",
                titleSemantics = "Vehicle selection step",
                complete = stepComplete[1],
            ) {
                VehicleSelectionContent(
                    selectedVehicleId = selectedVehicleId,
                    onSelectVehicle = onSelectVehicle,
                )
            }

            OnboardingSection(
                stepNumber = 3,
                title = "Bluetooth",
                titleSemantics = "Bluetooth onboarding step",
                complete = blePermissionsGranted,
            ) {
                Text(
                    text = "Find and control your receiver nearby. Nothing leaves this phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PermissionStatusPill(granted = blePermissionsGranted)
                if (!blePermissionsGranted) {
                    AkraActionButton(
                        label = "Grant Bluetooth access",
                        contentDescription = "Grant Bluetooth permissions",
                        onClick = onRequestBlePermissions,
                    )
                }
            }

            OnboardingSection(
                stepNumber = 4,
                title = "Notifications",
                titleSemantics = "Notifications onboarding step",
                complete = stepComplete[3],
            ) {
                Text(
                    text = "A small foreground alert keeps the BLE session alive while connected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (needsNotificationPermission) {
                    PermissionStatusPill(granted = notificationsGranted)
                    if (!notificationsGranted) {
                        AkraActionButton(
                            label = "Allow notifications",
                            contentDescription = "Grant notification permission",
                            onClick = onRequestNotificationPermission,
                        )
                    }
                } else {
                    AkraStatusPill(text = "NOT REQUIRED", color = MaterialTheme.colorScheme.primary)
                }
            }

            OnboardingSection(
                stepNumber = 5,
                title = "Background connection",
                titleSemantics = "Battery onboarding step",
                complete = true,
                optional = true,
            ) {
                Text(
                    text = "Optional — helps Android keep BLE alive when the screen is off. You can change this anytime in Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AkraActionButton(
                    label = "Open battery settings",
                    filled = false,
                    contentDescription = "Open battery optimization settings",
                    onClick = {
                        val intent = Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    },
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun OnboardingBreadcrumbs(
    steps: List<OnboardingStep>,
    stepComplete: List<Boolean>,
    activeIndex: Int,
) {
    val accent = LocalAkraTheme.current.accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .semantics { contentDescription = "Onboarding progress" },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        steps.forEachIndexed { index, step ->
            if (index > 0) {
                BreadcrumbConnector(
                    filled = stepComplete.getOrElse(index - 1) { false },
                    accent = accent,
                )
            }
            BreadcrumbChip(
                label = step.label,
                complete = stepComplete.getOrElse(index) { false },
                active = index == activeIndex,
                contentDescription = step.contentDescription,
            )
        }
    }
}

@Composable
private fun BreadcrumbConnector(filled: Boolean, accent: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .width(12.dp)
            .height(2.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (filled) accent.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            ),
    )
}

@Composable
private fun BreadcrumbChip(
    label: String,
    complete: Boolean,
    active: Boolean,
    contentDescription: String,
) {
    val accent = LocalAkraTheme.current.accent
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.semantics { this.contentDescription = contentDescription },
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    when {
                        complete -> accent
                        active -> accent.copy(alpha = 0.22f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                )
                .then(
                    if (active && !complete) {
                        Modifier.border(1.5.dp, accent, CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (complete) "✓" else "·",
                style = MaterialTheme.typography.labelSmall,
                color = if (complete) MaterialTheme.colorScheme.onPrimary else accent,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = when {
                complete -> accent
                active -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun OnboardingSection(
    stepNumber: Int,
    title: String,
    titleSemantics: String,
    complete: Boolean,
    optional: Boolean = false,
    content: @Composable () -> Unit,
) {
    val accent = LocalAkraTheme.current.accent
    AkraCard(
        accent = if (complete) accent else MaterialTheme.colorScheme.outline,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (complete) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (complete) "✓" else stepNumber.toString(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (complete) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics {
                            heading()
                            contentDescription = titleSemantics
                        },
                    )
                    if (optional) {
                        AkraStatusPill(text = "OPTIONAL", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusPill(granted: Boolean) {
    AkraStatusPill(
        text = if (granted) "GRANTED" else "NEEDED",
        color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RiskNoticeBullets() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Bullet("Not affiliated with or endorsed by Akrapovič d.d.")
        Bullet("Protocol reverse-engineered from a public APK; behavior may differ from the official app.")
        Bullet("May void exhaust or vehicle warranty; follow local noise and emissions rules.")
        Bullet("Never operate valves while driving — parked use only with safe ventilation.")
        Bullet("Provided as-is with no warranty; you accept all risk of damage, legal exposure, or injury.")
    }
}

@Composable
private fun Bullet(text: String) {
    Text(
        text = "•  $text",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
