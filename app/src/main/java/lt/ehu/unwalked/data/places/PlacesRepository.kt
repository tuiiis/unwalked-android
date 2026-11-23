package lt.ehu.unwalked.data.places

import com.uber.h3core.H3Core
import lt.ehu.unwalked.core.error.Outcome
import lt.ehu.unwalked.core.error.map
import lt.ehu.unwalked.core.network.PlacesByCellsRequest
import lt.ehu.unwalked.core.network.SafeApiCall
import lt.ehu.unwalked.core.network.UnwalkedApi
import lt.ehu.unwalked.domain.recommendation.PlaceCandidate

interface PlacesRepository {
    suspend fun inCells(cells: List<String>, limit: Int): Outcome<List<PlaceCandidate>>
}

class RemotePlacesRepository(
    private val api: UnwalkedApi,
    private val call: SafeApiCall,
    private val h3: H3Core,
) : PlacesRepository {
    override suspend fun inCells(cells: List<String>, limit: Int): Outcome<List<PlaceCandidate>> {
        if (cells.isEmpty()) return Outcome.Success(emptyList())
        return call { api.placesByCells(PlacesByCellsRequest(cells, limit = limit)) }.map { places ->
            places.map { PlaceCandidate(it.id, it.name, it.category, it.lat, it.lon, h3.stringToH3(it.h3Cell)) }
        }
    }
}
