package lt.ehu.unwalked.feature.walk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uber.h3core.H3Core
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lt.ehu.unwalked.core.error.ErrorReporter
import lt.ehu.unwalked.core.error.Outcome
import lt.ehu.unwalked.data.history.Cells
import lt.ehu.unwalked.data.places.PlacesRepository
import lt.ehu.unwalked.data.route.RouteRepository
import lt.ehu.unwalked.data.route.RoutedPath
import lt.ehu.unwalked.domain.recommendation.HabitualPoint
import lt.ehu.unwalked.domain.recommendation.HabitualSource
import lt.ehu.unwalked.domain.recommendation.PlaceCandidate
import lt.ehu.unwalked.domain.route.LatLon
import lt.ehu.unwalked.domain.route.RoutePlan
import lt.ehu.unwalked.domain.route.RoutePlanner
import lt.ehu.unwalked.domain.route.RoutePreference
import lt.ehu.unwalked.domain.route.RouteShape
import lt.ehu.unwalked.domain.route.Spurs
import lt.ehu.unwalked.domain.route.WalkRoute
import lt.ehu.unwalked.domain.tracking.GeoMath
import lt.ehu.unwalked.domain.validation.WalkLength

sealed interface RouteUiState {
    data object Building : RouteUiState
    data object Slow : RouteUiState
    data class Ready(val route: WalkRoute) : RouteUiState
    data class Closest(val route: WalkRoute) : RouteUiState
    data class Failed(val message: String) : RouteUiState
}

class RouteViewModel(
    private val request: RouteRequest,
    private val habitual: HabitualSource,
    private val start: suspend () -> HabitualPoint?,
    private val routes: RouteRepository,
    private val places: PlacesRepository,
    private val planner: RoutePlanner,
    private val h3: H3Core,
    private val errors: ErrorReporter,
) : ViewModel() {
    private val mutableState = MutableStateFlow<RouteUiState>(RouteUiState.Building)
    val state: StateFlow<RouteUiState> = mutableState.asStateFlow()

    init {
        build()
    }

    fun build() {
        mutableState.value = RouteUiState.Building
        val slow = viewModelScope.launch {
            delay(SLOW_MILLIS)
            if (mutableState.value == RouteUiState.Building) mutableState.value = RouteUiState.Slow
        }
        viewModelScope.launch {
            val origin = start()?.let { LatLon(it.lat, it.lon) } ?: FALLBACK_START
            val visited = habitual.habitual().visited
            val nearby = nearbyPlaces(origin)
            val target = request.destination
            val routed = if (target != null) routes.walking(listOf(origin, target)) else planned(origin, visited, nearby)
            mutableState.value = when (routed) {
                is Outcome.Success -> {
                    val route = withContext(Dispatchers.Default) {
                        val path = Spurs.trim(routed.value.path)
                        describe(path, routed.value.metres - (Spurs.length(routed.value.path) - Spurs.length(path)), visited, nearby)
                    }
                    if (target == null && !fits(route.metres)) RouteUiState.Closest(route) else RouteUiState.Ready(route)
                }

                is Outcome.Failure -> {
                    errors.log(routed.error)
                    RouteUiState.Failed(ErrorReporter.userText(routed.error))
                }
            }
            slow.cancel()
        }
    }

    fun accept() {
        val closest = mutableState.value as? RouteUiState.Closest ?: return
        mutableState.value = RouteUiState.Ready(closest.route)
    }

    private fun fits(metres: Double): Boolean = abs(metres / 1000 - request.kilometres) / request.kilometres <= TOLERANCE

    private suspend fun planned(origin: LatLon, visited: Set<Long>, nearby: List<PlaceCandidate>): Outcome<RoutedPath> {
        val best = withContext(Dispatchers.Default) { choosePlan(origin, request.kilometres, visited, nearby) }
        val routed = routes.walking(best.waypoints)
        if (routed !is Outcome.Success || fits(routed.value.metres)) return routed
        val corrected = request.kilometres * request.kilometres / (routed.value.metres / 1000)
        val retry = routes.walking(planner.plan(origin, corrected, request.shape, best.bearingDegrees).waypoints)
        return if (retry is Outcome.Success && abs(retry.value.metres / 1000 - request.kilometres) < abs(routed.value.metres / 1000 - request.kilometres)) retry else routed
    }

    private suspend fun nearbyPlaces(origin: LatLon): List<PlaceCandidate> {
        val centre = h3.latLngToCell(origin.lat, origin.lon, Cells.QUERY_RESOLUTION)
        val cells = h3.gridDisk(centre, NEARBY_RINGS).map { h3.h3ToString(it) }
        return (places.inCells(cells, PLACE_LIMIT) as? Outcome.Success)?.value.orEmpty()
    }

    private fun choosePlan(origin: LatLon, kilometres: Double, visited: Set<Long>, nearby: List<PlaceCandidate>): RoutePlan {
        val newPlaces = nearby.filter { it.cell !in visited }
        val isNew: (LatLon) -> Boolean = when (request.preference) {
            RoutePreference.NewCells -> { point -> h3.latLngToCell(point.lat, point.lon, Cells.RESOLUTION) !in visited }
            RoutePreference.Places -> { point -> newPlaces.any { GeoMath.distanceMeters(it.lat, it.lon, point.lat, point.lon) < PLACE_RADIUS } }
        }
        return planner.candidates(origin, kilometres, request.shape).maxBy { planner.score(it, isNew) }
    }

    private fun describe(path: List<LatLon>, metres: Double, visited: Set<Long>, nearby: List<PlaceCandidate>): WalkRoute {
        val cells = LinkedHashSet<Long>()
        var previous: Long? = null
        path.forEach { point ->
            val cell = h3.latLngToCell(point.lat, point.lon, Cells.RESOLUTION)
            val last = previous
            if (last != null && last != cell) runCatching { h3.gridPathCells(last, cell) }.getOrNull()?.let { cells += it }
            cells += cell
            previous = cell
        }
        val newCells = cells.count { it !in visited }
        val newPlaces = nearby.count { it.cell !in visited && it.cell in cells }
        return WalkRoute(
            path = path,
            metres = metres,
            minutes = (metres / 1000 / WalkLength.WALKING_KMH * 60).roundToInt(),
            shape = request.shape,
            newCells = newCells,
            newPlaces = newPlaces,
            unknownPercent = if (cells.isEmpty()) 0 else (100.0 * newCells / cells.size).roundToInt(),
        )
    }

    private companion object {
        val FALLBACK_START = LatLon(54.6872, 25.2797)
        const val TOLERANCE = 0.10
        const val SLOW_MILLIS = 5_000L
        const val NEARBY_RINGS = 3
        const val PLACE_LIMIT = 200
        const val PLACE_RADIUS = 120.0
    }
}
