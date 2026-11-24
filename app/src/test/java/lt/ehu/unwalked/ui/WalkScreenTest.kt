package lt.ehu.unwalked.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
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
    fun sliderShowsDistanceAndTimeAndBuildsTheRoute() {
        val vm = WalkViewModel()
        val requests = mutableListOf<RouteRequest>()
        compose.setContent {
            UnwalkedTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                WalkScreen(state, WalkActions(onLength = vm::onLength, onShape = vm::onShape, onPreference = vm::onPreference, onBuild = { requests += vm.request() }))
            }
        }
        compose.onNodeWithTag("lengthValue").assertTextEquals("3.5 km")
        compose.onNodeWithText("about 45 min on foot").assertIsDisplayed()

        compose.onNodeWithTag("lengthSlider").performSemanticsAction(SemanticsActions.SetProgress) { it(6.2f) }
        compose.onNodeWithTag("lengthValue").assertTextEquals("6.0 km")
        compose.onNodeWithText("about 1 h 15 min on foot").assertIsDisplayed()

        compose.onNodeWithTag("buildRoute").performClick()
        assertEquals(6.0, requests.single().kilometres, 0.0)
    }
}
