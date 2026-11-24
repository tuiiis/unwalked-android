package lt.ehu.unwalked.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import lt.ehu.unwalked.core.ui.theme.UnwalkedTheme
import lt.ehu.unwalked.feature.privacy.LongTask
import lt.ehu.unwalked.feature.privacy.PrivacyActions
import lt.ehu.unwalked.feature.privacy.PrivacyScreen
import lt.ehu.unwalked.feature.privacy.PrivacyUiState
import lt.ehu.unwalked.feature.privacy.TimelineExportSteps
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
    fun developerOptionsShowTheSampleWeek() {
        var loaded = false
        compose.setContent { UnwalkedTheme { PrivacyScreen(PrivacyUiState(), online = true, PrivacyActions(onSample = { loaded = true })) } }

        compose.onNodeWithText("Developer options").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Generate sample walks").performScrollTo().performClick()
        assertEquals(true, loaded)
    }

    @Test
    fun importBlocksTheScreenUntilCancelled() {
        var cancelled = 0
        var back = 0
        compose.setContent { UnwalkedTheme { PrivacyScreen(PrivacyUiState(task = LongTask.Timeline, busy = true), online = true, PrivacyActions(onCancelTask = { cancelled++ }, onBack = { back++ })) } }

        compose.onNodeWithText("Importing Google Timeline").assertIsDisplayed()
        Espresso.pressBackUnconditionally()
        compose.onNodeWithText("Importing Google Timeline").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, cancelled)
        assertEquals(0, back)
    }

    @Test
    fun importShowsTheExportStepsFirst() {
        var settings = 0
        var picker = 0
        compose.setContent {
            UnwalkedTheme { PrivacyScreen(PrivacyUiState(), online = true, PrivacyActions(onOpenLocationSettings = { settings++ }, onImportTimeline = { picker++ })) }
        }

        compose.onNodeWithText("Import Google Timeline").performScrollTo().performClick()
        TimelineExportSteps.forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithText("Open settings").performClick()
        assertEquals(1, settings)
        assertEquals(0, picker)
        compose.onNodeWithText("Choose file").performClick()
        assertEquals(1, picker)
        assertEquals(0, compose.onAllNodesWithText("Export your Timeline first").fetchSemanticsNodes().size)
    }

    @Test
    fun sampleGenerationBlocksTheScreenUntilCancelled() {
        var cancelled = 0
        compose.setContent { UnwalkedTheme { PrivacyScreen(PrivacyUiState(task = LongTask.Sample, busy = true), online = true, PrivacyActions(onCancelTask = { cancelled++ })) } }

        compose.onNodeWithText("Generating sample walks").assertIsDisplayed()
        Espresso.pressBackUnconditionally()
        compose.onNodeWithText("Generating sample walks").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, cancelled)
    }
}
