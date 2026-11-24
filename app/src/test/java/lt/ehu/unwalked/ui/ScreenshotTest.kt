package lt.ehu.unwalked.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import lt.ehu.unwalked.core.ui.theme.Palette
import lt.ehu.unwalked.core.ui.theme.UnwalkedTheme
import lt.ehu.unwalked.domain.recommendation.CategoryGroup
import lt.ehu.unwalked.domain.recommendation.Hook
import lt.ehu.unwalked.domain.recommendation.PlaceCandidate
import lt.ehu.unwalked.domain.recommendation.Recommendation
import lt.ehu.unwalked.domain.route.RouteShape
import lt.ehu.unwalked.domain.route.WalkRoute
import lt.ehu.unwalked.domain.stats.Period
import lt.ehu.unwalked.domain.stats.PeriodRange
import lt.ehu.unwalked.domain.stats.PeriodStats
import lt.ehu.unwalked.domain.stats.Walk
import lt.ehu.unwalked.domain.validation.CredentialErrors
import lt.ehu.unwalked.feature.auth.AuthMode
import lt.ehu.unwalked.feature.auth.AuthScreen
import lt.ehu.unwalked.feature.auth.AuthUiState
import lt.ehu.unwalked.feature.discover.DiscoverActions
import lt.ehu.unwalked.feature.discover.DiscoverContent
import lt.ehu.unwalked.feature.discover.DiscoverScreen
import lt.ehu.unwalked.feature.discover.DiscoverUiState
import lt.ehu.unwalked.feature.map.MapActions
import lt.ehu.unwalked.feature.map.MapScreen
import lt.ehu.unwalked.feature.map.MapSummary
import lt.ehu.unwalked.feature.map.MapUiState
import lt.ehu.unwalked.feature.privacy.PrivacyActions
import lt.ehu.unwalked.feature.privacy.PrivacyScreen
import lt.ehu.unwalked.feature.privacy.PrivacyUiState
import lt.ehu.unwalked.feature.stats.StatsScreen
import lt.ehu.unwalked.feature.stats.StatsUiState
import lt.ehu.unwalked.feature.walk.RoutePreviewScreen
import lt.ehu.unwalked.feature.walk.RouteUiState
import lt.ehu.unwalked.feature.walk.WalkActions
import lt.ehu.unwalked.feature.walk.WalkFormState
import lt.ehu.unwalked.feature.walk.WalkScreen
import lt.ehu.unwalked.tracking.RecordingStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent { UnwalkedTheme { content() } }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private val fakeMap: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier.background(Brush.radialGradient(listOf(Palette.Heat[1], Palette.Background))))
    }

    private val sampleRoute = WalkRoute(emptyList(), 3540.0, 44, RouteShape.Loop, 20, 1, 29)

    private fun recommendation(id: Long, name: String, category: String, group: CategoryGroup, minutes: Int, hook: Hook, passed: Int = 0) =
        Recommendation(PlaceCandidate(id, name, category, 0.0, 0.0, id), group, minutes * 80.0, minutes, hook, passed)

    @Test
    fun signIn() = capture("01-sign-in") {
        AuthScreen(AuthUiState(AuthMode.SignIn, email = "ona@example", errors = CredentialErrors(email = "Enter an email like name@example.com."), submitted = true), {}, {}, {}, {}, {})
    }

    @Test
    fun register() = capture("02-register") {
        AuthScreen(AuthUiState(AuthMode.Register), {}, {}, {}, {}, {})
    }

    @Test
    fun mapWithHistory() = capture("03-map") {
        MapScreen(
            MapUiState(loading = false, period = Period.Year, summary = MapSummary("You explored 38% of your usual area", "Year 2026 · 412 streets · 26 new this month"), hasHistory = true, recording = RecordingStatus.Healthy),
            MapActions(),
            map = fakeMap,
        )
    }

    @Test
    fun mapRecordingStopped() = capture("04-map-stopped") {
        MapScreen(
            MapUiState(loading = false, summary = MapSummary("You explored 38% of your usual area", "Year 2026 · 412 streets · 26 new this month"), hasHistory = true, recording = RecordingStatus.Stopped(25, true)),
            MapActions(),
            map = fakeMap,
        )
    }

    @Test
    fun mapEmpty() = capture("05-map-empty") {
        MapScreen(MapUiState(loading = false), MapActions(), map = fakeMap)
    }

    @Test
    fun mapLoading() = capture("06-map-loading") {
        MapScreen(MapUiState(), MapActions(), map = fakeMap)
    }

    @Test
    fun discover() = capture("07-discover") {
        val items = listOf(
            recommendation(1, "Bernardine Garden", "park", CategoryGroup.Nature, 1, Hook.Overlooked, 23),
            recommendation(2, "Užupis riverside steps", "viewpoint", CategoryGroup.Nature, 1, Hook.OnYourWay),
            recommendation(3, "Tymo market", "farmers_market", CategoryGroup.Shops, 2, Hook.AroundTheCorner),
            recommendation(4, "Cat Cafe", "cafe", CategoryGroup.Food, 3, Hook.JustBeyond),
        )
        DiscoverScreen(DiscoverUiState(content = DiscoverContent.Ready(items, 4)), DiscoverActions())
    }

    @Test
    fun discoverLoading() = capture("08-discover-loading") {
        DiscoverScreen(DiscoverUiState(), DiscoverActions())
    }

    @Test
    fun discoverFailed() = capture("09-discover-failed") {
        DiscoverScreen(DiscoverUiState(content = DiscoverContent.Failed("No connection to the server. Check the internet and try again.")), DiscoverActions())
    }

    @Test
    fun stats() = capture("10-stats") {
        StatsScreen(
            StatsUiState(
                loading = false,
                period = Period.Month,
                range = PeriodRange(0, 1, 0, "November 2026", "What is new this month"),
                stats = PeriodStats(26, 140, 3, 41.2, 38, 4, Walk(0, 4.2), 412),
                longestWalkDay = "12 November",
            ),
            {},
            {},
        )
    }

    @Test
    fun walkForm() = capture("11-walk") {
        WalkScreen(WalkFormState(), WalkActions())
    }

    @Test
    fun routeReady() = capture("13-route") {
        RoutePreviewScreen(RouteUiState.Ready(sampleRoute), {}, {}, {}, {}, {}) { modifier, _, _, _ -> fakeMap(modifier) }
    }

    @Test
    fun routeBuilding() = capture("14-route-building") {
        RoutePreviewScreen(RouteUiState.Building, {}, {}, {}, {}, {}) { modifier, _, _, _ -> fakeMap(modifier) }
    }

    @Test
    fun privacy() = capture("15-privacy") {
        PrivacyScreen(PrivacyUiState(email = "ona@example.test", studyConsent = true), online = true, actions = PrivacyActions())
    }
}
