package lt.ehu.unwalked.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lt.ehu.unwalked.core.ui.theme.UnwalkedTheme
import lt.ehu.unwalked.feature.walk.RouteRequest
import lt.ehu.unwalked.feature.walk.WalkActions
import lt.ehu.unwalked.feature.walk.WalkScreen
import lt.ehu.unwalked.feature.walk.WalkViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class WalkScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun invalidLengthBlocksBuildingUntilFixed() {
        val vm = WalkViewModel()
        val requests = mutableListOf<RouteRequest>()
        compose.setContent {
            UnwalkedTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                WalkScreen(
                    state,
                    WalkActions(onMeasure = vm::onMeasure, onSlider = vm::onSlider, onText = vm::onText, onShape = vm::onShape, onPreference = vm::onPreference, onBuild = { vm.request()?.let(requests::add) }),
                )
            }
        }
        compose.onNodeWithTag("lengthInput").performTextReplacement("14")
        compose.onNodeWithText("Enter 1 to 10 km. By time: 15 min to 2 h.").assertIsDisplayed()
        compose.onNodeWithTag("buildRoute").assertIsNotEnabled()

        compose.onNodeWithTag("lengthInput").performTextReplacement("6")
        compose.onNodeWithTag("buildRoute").assertIsEnabled().performClick()
        assertEquals(6.0, requests.single().kilometres, 0.0)
    }
}
