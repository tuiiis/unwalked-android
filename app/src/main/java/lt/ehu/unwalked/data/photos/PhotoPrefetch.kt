package lt.ehu.unwalked.data.photos

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import lt.ehu.unwalked.core.error.Outcome
import lt.ehu.unwalked.data.places.PlacesRepository
import lt.ehu.unwalked.domain.recommendation.CandidateCells
import lt.ehu.unwalked.domain.recommendation.CategoryGroup
import lt.ehu.unwalked.domain.recommendation.HabitualSource
import lt.ehu.unwalked.domain.recommendation.RecommendationEngine
import lt.ehu.unwalked.domain.recommendation.SortOrder

class PhotoPrefetch(
    private val context: Context,
    private val habitual: HabitualSource,
    private val places: PlacesRepository,
    private val candidates: CandidateCells,
    private val photos: PlacePhotos,
    private val engine: RecommendationEngine = RecommendationEngine(),
) {
    suspend fun warm(limit: Int = LIMIT): Int {
        val habits = habitual.habitual()
        if (habits.visited.isEmpty()) return 0
        val cells = candidates.around(habits.usual, RecommendationEngine.MAX_METRES_FROM_USUAL)
        val found = places.inCells(cells, PLACE_LIMIT) as? Outcome.Success ?: return 0
        val ranked = engine.rank(found.value, habits.visited, habits.centres, emptySet())
        val top = engine.filter(ranked, "", CategoryGroup.All, SortOrder.BestMatch).take(limit)
        val loader = SingletonImageLoader.get(context)
        var loaded = 0
        top.chunked(PARALLEL).forEach { batch ->
            coroutineScope { batch.map { async { photos.thumbnail(it.place.id, it.place.lat, it.place.lon, it.place.name) } }.awaitAll() }
                .filterNotNull()
                .forEach { url ->
                    loader.enqueue(ImageRequest.Builder(context).data(url).build())
                    loaded++
                }
        }
        return loaded
    }

    private companion object {
        const val LIMIT = 30
        const val PLACE_LIMIT = 200
        const val PARALLEL = 4
    }
}
