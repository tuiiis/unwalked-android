package lt.ehu.unwalked.feature.walk

import com.uber.h3core.H3Core
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import lt.ehu.unwalked.core.error.ErrorReporter
import lt.ehu.unwalked.core.error.Outcome
import lt.ehu.unwalked.data.route.RouteRepository
import lt.ehu.unwalked.data.route.RoutedPath
import lt.ehu.unwalked.domain.recommendation.HabitualPoint
import lt.ehu.unwalked.domain.route.LatLon
import lt.ehu.unwalked.domain.route.RoutePlanner
import lt.ehu.unwalked.domain.route.RoutePreference
import lt.ehu.unwalked.domain.route.RouteShape
import lt.ehu.unwalked.testing.FakeHabitualSource
import lt.ehu.unwalked.testing.FakePlacesRepository
import lt.ehu.unwalked.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RouteToPlaceTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun routeGoesFromWhereYouAreToThePlace() = runTest {
        val start = HabitualPoint(54.6872, 25.2797)
        val place = LatLon(54.6900, 25.2830)
        val asked = mutableListOf<List<LatLon>>()
        val routes = object : RouteRepository {
            override suspend fun walking(waypoints: List<LatLon>): Outcome<RoutedPath> {
                asked += waypoints
                return Outcome.Success(RoutedPath(waypoints, 420.0))
            }
        }
        val vm = RouteViewModel(
            RouteRequest(0.0, RouteShape.OneWay, RoutePreference.Places, destination = place),
            FakeHabitualSource(emptyMap(), emptyList()),
            { start },
            routes,
            FakePlacesRepository(Outcome.Success(emptyList())),
            RoutePlanner(),
            H3Core.newInstance(),
            ErrorReporter(),
        )
        val done = vm.state.first { it !is RouteUiState.Building }
        assertEquals(listOf(listOf(LatLon(start.lat, start.lon), place)), asked)
        assertTrue(done.toString(), done is RouteUiState.Ready)
    }
}
