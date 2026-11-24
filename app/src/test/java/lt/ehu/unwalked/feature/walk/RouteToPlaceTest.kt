package lt.ehu.unwalked.feature.walk

import androidx.lifecycle.viewModelScope
import com.uber.h3core.H3Core
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
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
        val done = vm.state.first { it !is RouteUiState.Building && it !is RouteUiState.Slow }
        assertEquals(listOf(listOf(LatLon(start.lat, start.lon), place)), asked)
        assertTrue(done.toString(), done is RouteUiState.Ready)
    }

    private fun planned(metres: Double) = RouteViewModel(
        RouteRequest(4.0, RouteShape.OneWay, RoutePreference.NewCells),
        FakeHabitualSource(emptyMap(), emptyList()),
        { HabitualPoint(54.6872, 25.2797) },
        object : RouteRepository {
            override suspend fun walking(waypoints: List<LatLon>): Outcome<RoutedPath> = Outcome.Success(RoutedPath(waypoints, metres))
        },
        FakePlacesRepository(Outcome.Success(emptyList())),
        RoutePlanner(),
        H3Core.newInstance(),
        ErrorReporter(),
    )

    @Test
    fun routeWithinTenPercentIsReady() = runTest {
        val done = planned(4_300.0).state.first { it !is RouteUiState.Building && it !is RouteUiState.Slow }
        assertTrue(done.toString(), done is RouteUiState.Ready)
    }

    @Test
    fun routeOutsideTenPercentIsOfferedAsClosest() = runTest {
        val vm = planned(3_300.0)
        val closest = vm.state.first { it !is RouteUiState.Building && it !is RouteUiState.Slow }
        assertTrue(closest.toString(), closest is RouteUiState.Closest)
        assertEquals(3_300.0, (closest as RouteUiState.Closest).route.metres, 1.0)
        vm.accept()
        assertEquals(RouteUiState.Ready(closest.route), vm.state.value)
    }

    @Test
    fun slowRouteOffersAShorterWalk() = runTest {
        val vm = RouteViewModel(
            RouteRequest(4.0, RouteShape.OneWay, RoutePreference.NewCells),
            FakeHabitualSource(emptyMap(), emptyList()),
            { HabitualPoint(54.6872, 25.2797) },
            object : RouteRepository {
                override suspend fun walking(waypoints: List<LatLon>): Outcome<RoutedPath> {
                    delay(60_000)
                    return Outcome.Success(RoutedPath(waypoints, 4_000.0))
                }
            },
            FakePlacesRepository(Outcome.Success(emptyList())),
            RoutePlanner(),
            H3Core.newInstance(),
            ErrorReporter(),
        )
        advanceTimeBy(5_001)
        assertEquals(RouteUiState.Slow, vm.state.value)
        vm.viewModelScope.cancel()
    }
}
