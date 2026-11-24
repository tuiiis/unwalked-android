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
import lt.ehu.unwalked.domain.recommendation.PlaceCandidate
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

    @Test
    fun routeListsNamedPlacesNearTheLineInOrder() = runTest {
        val h3 = H3Core.newInstance()
        val start = LatLon(54.6872, 25.2797)
        val end = LatLon(54.6962, 25.2797)
        fun place(id: Long, name: String, category: String, north: Double, east: Double): PlaceCandidate {
            val lat = start.lat + north / 111_320.0
            val lon = start.lon + east / (111_320.0 * Math.cos(Math.toRadians(start.lat)))
            return PlaceCandidate(id, name, category, lat, lon, h3.latLngToCell(lat, lon, 11))
        }
        val far = place(1, "Far Cafe", "cafe", 500.0, 600.0)
        val late = place(2, "Late Bakery", "bakery", 900.0, 40.0)
        val early = place(3, "Early Park", "park", 100.0, -60.0)
        val unnamed = place(4, "", "cafe", 300.0, 10.0)
        val salon = place(5, "Salon", "beauty_salon", 400.0, 10.0)
        val vm = RouteViewModel(
            RouteRequest(1.0, RouteShape.OneWay, RoutePreference.Places, destination = end),
            FakeHabitualSource(emptyMap(), emptyList()),
            { HabitualPoint(start.lat, start.lon) },
            object : RouteRepository {
                override suspend fun walking(waypoints: List<LatLon>): Outcome<RoutedPath> = Outcome.Success(RoutedPath((0..10).map { LatLon(start.lat + (end.lat - start.lat) * it / 10, start.lon) }, 1000.0))
            },
            FakePlacesRepository(Outcome.Success(listOf(far, late, early, unnamed, salon))),
            RoutePlanner(),
            h3,
            ErrorReporter(),
        )
        val ready = vm.state.first { it is RouteUiState.Ready } as RouteUiState.Ready
        assertEquals(listOf("Early Park", "Late Bakery"), ready.route.places.map { it.name })
        assertEquals(2, ready.route.newPlaces)
    }
}
