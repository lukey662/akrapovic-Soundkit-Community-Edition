package com.akrapovic.soundkit.community.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.filters.SdkSuppress
import com.akrapovic.soundkit.community.ui.components.AkraStatePanel
import com.akrapovic.soundkit.community.ui.theme.SoundKitTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@SdkSuppress(maxSdkVersion = 35)
class AkraStatePanelTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsTitleBodyAndPrimaryAction() {
        var clicked = false
        composeRule.setContent {
            SoundKitTheme {
                AkraStatePanel(
                    title = "No receiver selected",
                    body = "Turn the car on, then scan while parked.",
                    primaryLabel = "Scan for receiver",
                    primaryContentDescription = "Scan for Sound Kit receiver",
                    onPrimary = { clicked = true },
                )
            }
        }

        composeRule.onNodeWithText("No receiver selected").assertIsDisplayed()
        composeRule.onNodeWithText("Turn the car on, then scan while parked.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Scan for Sound Kit receiver").performClick()
        composeRule.runOnIdle {
            assertTrue(clicked)
        }
    }
}
