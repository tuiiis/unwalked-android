package lt.ehu.unwalked.domain.stats

import lt.ehu.unwalked.domain.recommendation.CategoryGroup
import lt.ehu.unwalked.domain.recommendation.PlaceCandidate
import org.junit.Assert.assertEquals
import org.junit.Test

class FavouritePlacesTest {
    private val centres = mapOf(1L to (54.6872 to 25.2797), 2L to (54.6900 to 25.2700), 3L to (54.7000 to 25.3000))

    @Test
    fun homeIsLeftOutOfFavourites() {
        val stays = List(10) { StayVisit(100L + it, 9L) } + List(3) { StayVisit(200L + it, 1L) } + List(2) { StayVisit(300L + it, 2L) }
        assertEquals(listOf(1L to 3, 2L to 2), FavouritePlaces.spots(stays, 0, 1_000))
    }

    @Test
    fun spotsAreNamedByTheNearestPlaceAndGroupedByType() {
        val places = listOf(
            PlaceCandidate(1, "Caffeine", "coffee_shop", 54.68722, 25.27972, 0),
            PlaceCandidate(2, "Parking", "parking_lot", 54.68721, 25.27971, 0),
            PlaceCandidate(3, "Bernardine Garden", "park", 54.69002, 25.27003, 0),
            PlaceCandidate(4, "Far museum", "museum", 54.6950, 25.2900, 0),
        )
        val habits = FavouritePlaces.pick(listOf(1L to 5, 2L to 2, 3L to 4), places) { centres.getValue(it) }
        assertEquals(listOf("Caffeine", "Bernardine Garden"), habits.favourites.map { it.name })
        assertEquals(listOf(CategoryGroup.Food to 5, CategoryGroup.Nature to 2), habits.types)
    }
}
