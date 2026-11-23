package lt.ehu.unwalked.domain.recommendation

import kotlin.math.ceil
import lt.ehu.unwalked.domain.tracking.GeoMath

data class HabitualPoint(val lat: Double, val lon: Double, val walks: Int = 1)

data class Habits(val visited: Set<Long>, val usual: Map<Long, Int>, val centres: List<HabitualPoint>)

interface HabitualSource {
    suspend fun habitual(since: Long = 0L): Habits
}

class RecommendationEngine(private val walkingMetresPerMinute: Double = 80.0, private val maxMetresFromUsual: Double = MAX_METRES_FROM_USUAL) {
    fun rank(
        candidates: List<PlaceCandidate>,
        visitedCells: Set<Long>,
        habitual: List<HabitualPoint>,
        hiddenIds: Set<Long>,
    ): List<Recommendation> {
        if (habitual.isEmpty()) return emptyList()
        return candidates
            .asSequence()
            .filter { it.cell !in visitedCells && it.id !in hiddenIds && it.name.isNotBlank() && CategoryGroup.of(it.category) != CategoryGroup.Other }
            .distinctBy { it.id }
            .map { place ->
                val distances = habitual.map { it to GeoMath.distanceMeters(it.lat, it.lon, place.lat, place.lon) }
                val metres = distances.minOf { it.second }
                val passed = distances.filter { it.second <= ON_THE_WAY_METRES }.maxOfOrNull { it.first.walks } ?: 0
                Recommendation(
                    place = place,
                    categoryGroup = CategoryGroup.of(place.category),
                    metresFromHabitual = metres,
                    walkingMinutes = ceil(metres / walkingMetresPerMinute).toInt().coerceAtLeast(1),
                    hook = hook(metres, passed),
                    timesPassed = passed,
                )
            }
            .filter { it.metresFromHabitual <= maxMetresFromUsual }
            .toList()
    }

    fun hook(metresFromHabitual: Double, timesPassed: Int): Hook = when {
        metresFromHabitual <= ON_THE_WAY_METRES && timesPassed >= OVERLOOKED_PASSES -> Hook.Overlooked
        metresFromHabitual <= ON_THE_WAY_METRES -> Hook.OnYourWay
        metresFromHabitual <= AROUND_THE_CORNER_METRES -> Hook.AroundTheCorner
        else -> Hook.JustBeyond
    }

    fun filter(
        recommendations: List<Recommendation>,
        query: String,
        group: CategoryGroup,
        order: SortOrder,
    ): List<Recommendation> {
        val needle = query.trim().lowercase()
        val filtered = recommendations.filter { recommendation ->
            (group == CategoryGroup.All || recommendation.categoryGroup == group) &&
                (needle.isEmpty() || recommendation.place.name.lowercase().contains(needle) || recommendation.place.category.replace('_', ' ').contains(needle))
        }
        return when (order) {
            SortOrder.BestMatch -> filtered.sortedWith(compareBy<Recommendation> { it.hook.ordinal }.thenByDescending { it.timesPassed }.thenBy { it.metresFromHabitual })
            SortOrder.Closest -> filtered.sortedBy { it.metresFromHabitual }
        }
    }

    companion object {
        const val MAX_METRES_FROM_USUAL = 250.0
        const val ON_THE_WAY_METRES = 60.0
        const val AROUND_THE_CORNER_METRES = 150.0
        const val OVERLOOKED_PASSES = 10
    }
}
