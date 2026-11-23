package lt.ehu.unwalked.domain.stats

import lt.ehu.unwalked.domain.recommendation.CategoryGroup
import lt.ehu.unwalked.domain.recommendation.PlaceCandidate
import lt.ehu.unwalked.domain.tracking.GeoMath

data class FavouritePlace(val name: String, val category: String, val group: CategoryGroup, val visits: Int)

data class PlaceHabits(val favourites: List<FavouritePlace>, val types: List<Pair<CategoryGroup, Int>>)

object FavouritePlaces {
    fun spots(stays: List<StayVisit>, from: Long, to: Long): List<Pair<Long, Int>> {
        val ranked = stays.filter { it.start in from..to }.groupingBy { it.placeKey }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value }
        val total = ranked.sumOf { it.second }
        val withoutHome = if (ranked.isNotEmpty() && ranked.first().second >= total * HOME_SHARE) ranked.drop(1) else ranked
        return withoutHome.take(MAX_SPOTS)
    }

    fun pick(spots: List<Pair<Long, Int>>, places: List<PlaceCandidate>, centreOf: (Long) -> Pair<Double, Double>): PlaceHabits {
        val named = spots.mapNotNull { (spot, visits) ->
            val (lat, lon) = centreOf(spot)
            places.filter { CategoryGroup.of(it.category) != CategoryGroup.Other && it.name.isNotBlank() }
                .map { it to GeoMath.distanceMeters(lat, lon, it.lat, it.lon) }
                .filter { it.second <= NEAR_METRES }
                .minByOrNull { it.second }
                ?.first
                ?.let { FavouritePlace(it.name, it.category, CategoryGroup.of(it.category), visits) }
        }
        val favourites = named.groupBy { it.name }.map { (_, same) -> same.first().copy(visits = same.sumOf { it.visits }) }.sortedByDescending { it.visits }.take(MAX_FAVOURITES)
        val types = named.groupBy { it.group }.map { (group, list) -> group to list.sumOf { it.visits } }.sortedByDescending { it.second }
        return PlaceHabits(favourites, types)
    }

    private const val HOME_SHARE = 0.3
    private const val MAX_SPOTS = 40
    private const val NEAR_METRES = 60.0
    private const val MAX_FAVOURITES = 5
}
