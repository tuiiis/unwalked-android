package lt.ehu.unwalked.data.places

import com.uber.h3core.H3Core
import kotlinx.coroutines.delay
import lt.ehu.unwalked.core.error.Outcome
import lt.ehu.unwalked.core.error.map
import lt.ehu.unwalked.core.network.PlacesByCellsRequest
import lt.ehu.unwalked.core.network.SafeApiCall
import lt.ehu.unwalked.core.network.UnwalkedApi
import lt.ehu.unwalked.domain.recommendation.PlaceCandidate
import timber.log.Timber

interface PlacesRepository {
    suspend fun inCells(cells: List<String>, limit: Int): Outcome<List<PlaceCandidate>>

    suspend fun prepareAreas(cells: List<String>, wait: Boolean = true, onLoading: () -> Unit = {}) = Unit
}

class RemotePlacesRepository(
    private val api: UnwalkedApi,
    private val call: SafeApiCall,
    private val h3: H3Core,
    private val pollMillis: Long = POLL_MILLIS,
) : PlacesRepository {
    override suspend fun prepareAreas(cells: List<String>, wait: Boolean, onLoading: () -> Unit) {
        val areas = cells.map { h3.h3ToString(h3.cellToParent(h3.stringToH3(it), AREA_RESOLUTION)) }.distinct().take(MAX_AREAS)
        val states = areas.associateWith { area ->
            when (val result = call { api.ensureArea(area) }) {
                is Outcome.Success -> result.value.status
                is Outcome.Failure -> "unavailable: ${result.error}"
            }
        }
        Timber.i("Areas for %d cells: %s", cells.size, states)
        var loading = states.filterValues { it == LOADING }.keys.toList()
        if (loading.isEmpty() || !wait) return
        onLoading()
        repeat(MAX_POLLS) { poll ->
            delay(pollMillis)
            loading = loading.filter { (call { api.area(it) } as? Outcome.Success)?.value?.status == LOADING }
            if (loading.isEmpty()) {
                Timber.i("Areas ready after %d polls", poll + 1)
                return
            }
        }
        Timber.w("Areas still loading after %d polls: %s", MAX_POLLS, loading)
    }

    override suspend fun inCells(cells: List<String>, limit: Int): Outcome<List<PlaceCandidate>> {
        if (cells.isEmpty()) return Outcome.Success(emptyList())
        return call { api.placesByCells(PlacesByCellsRequest(cells, limit = limit)) }.map { places ->
            places.map { PlaceCandidate(it.id, it.name, it.category, it.lat, it.lon, h3.stringToH3(it.h3Cell)) }
        }
    }

    private companion object {
        const val AREA_RESOLUTION = 5
        const val MAX_AREAS = 7
        const val MAX_POLLS = 36
        const val POLL_MILLIS = 5_000L
        const val LOADING = "Loading"
    }
}
