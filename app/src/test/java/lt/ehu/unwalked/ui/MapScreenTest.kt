package lt.ehu.unwalked.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import lt.ehu.unwalked.core.ui.theme.UnwalkedTheme
import lt.ehu.unwalked.feature.map.MapActions
import lt.ehu.unwalked.feature.map.MapScreen
import lt.ehu.unwalked.feature.map.MapSummary
import lt.ehu.unwalked.feature.map.MapUiState
import lt.ehu.unwalked.tracking.RecordingStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class MapScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val summary = MapSummary("You explored 38% of your usual area", "Year 2026 · 412 streets · 26 new this month")

    private fun show(state: MapUiState) = compose.setContent { UnwalkedTheme { MapScreen(state, MapActions()) { modifier -> Box(modifier) } } }

    @Test
    fun startRecordingAndFindSomethingNewBeforeFirstSearch() {
        show(MapUiState(loading = false, summary = summary, hasHistory = true, recording = RecordingStatus.Off))

        compose.onNodeWithTag("startRecording").assertIsDisplayed()
        compose.onNodeWithTag("findNew").assertIsDisplayed()
    }

    @Test
    fun findSomethingNewIsGoneAfterFirstUse() {
        show(MapUiState(loading = false, summary = summary, hasHistory = true, recording = RecordingStatus.Off, showFindNew = false))

        compose.onNodeWithTag("startRecording").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTag("findNew").fetchSemanticsNodes().size)
    }
}
