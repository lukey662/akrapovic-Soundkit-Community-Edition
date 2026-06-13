package com.akrapovic.soundkit.community.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.filters.SdkSuppress
import com.akrapovic.soundkit.community.ui.onboarding.OnboardingFlow
import com.akrapovic.soundkit.community.ui.theme.SoundKitTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@SdkSuppress(maxSdkVersion = 35)
class OnboardingFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun singleScreenShowsBreadcrumbsAndAllSections() {
        composeRule.setContent {
            SoundKitTheme {
                OnboardingFlow(
                    blePermissionsGranted = false,
                    notificationsGranted = false,
                    selectedVehicleId = null,
                    onAcceptRisk = {},
                    onSelectVehicle = {},
                    onRequestBlePermissions = {},
                    onRequestNotificationPermission = {},
                    onComplete = {},
                )
            }
        }

        composeRule.onNodeWithText("Set up Sound Kit").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Onboarding progress").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Risk notice").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Vehicle selection step").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Bluetooth onboarding step").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Notifications onboarding step").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Battery onboarding step").assertIsDisplayed()
        composeRule.onNodeWithText("Get started").assertIsNotEnabled()
    }

    @Test
    fun getStartedEnabledWhenRequirementsMet() {
        var completed = false
        composeRule.setContent {
            SoundKitTheme {
                OnboardingFlow(
                    blePermissionsGranted = true,
                    notificationsGranted = true,
                    selectedVehicleId = "audi-rs3",
                    onAcceptRisk = {},
                    onSelectVehicle = {},
                    onRequestBlePermissions = {},
                    onRequestNotificationPermission = {},
                    onComplete = { completed = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Accept risk disclaimer").performClick()
        composeRule.onNodeWithText("Get started").performClick()
        composeRule.runOnIdle {
            assertTrue(completed)
        }
    }

    @Test
    fun grantBluetoothVisibleOnSameScreen() {
        composeRule.setContent {
            SoundKitTheme {
                OnboardingFlow(
                    blePermissionsGranted = false,
                    notificationsGranted = true,
                    selectedVehicleId = null,
                    onAcceptRisk = {},
                    onSelectVehicle = {},
                    onRequestBlePermissions = {},
                    onRequestNotificationPermission = {},
                    onComplete = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Grant Bluetooth permissions").assertIsDisplayed()
    }
}
