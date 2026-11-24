package lt.ehu.unwalked.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import lt.ehu.unwalked.core.ui.theme.UnwalkedTheme
import lt.ehu.unwalked.feature.privacy.PrivacyActions
import lt.ehu.unwalked.feature.privacy.PrivacyScreen
import lt.ehu.unwalked.feature.privacy.PrivacyUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class PrivacyScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun sampleWeekIsHiddenUntilDeveloperOptionsAreOn() {
        var taps = 0
        compose.setContent { UnwalkedTheme { PrivacyScreen(PrivacyUiState(), online = true, PrivacyActions(onVersion = { taps++ })) } }

        assertEquals(0, compose.onAllNodesWithText("Load a sample week in Vilnius").fetchSemanticsNodes().size)
        compose.onNodeWithText("Version").performScrollTo().performClick()
        assertEquals(1, taps)
    }

    @Test
    fun developerOptionsShowTheSampleWeek() {
        var loaded = false
        compose.setContent { UnwalkedTheme { PrivacyScreen(PrivacyUiState(developerOptions = true), online = true, PrivacyActions(onSample = { loaded = true })) } }

        compose.onNodeWithText("Developer options").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Load a sample week in Vilnius").performScrollTo().performClick()
        assertEquals(true, loaded)
    }
}
