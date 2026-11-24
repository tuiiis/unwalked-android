package lt.ehu.unwalked.domain.recommendation

import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendationEngineTest {
    private val engine = RecommendationEngine()
    private val home = HabitualPoint(54.6872, 25.2797)

    private fun place(id: Long, name: String, category: String, northMetres: Double, cell: Long = id) =
        PlaceCandidate(id, name, category, home.lat + northMetres / 111_320.0, home.lon, cell)

    @Test
    fun placesFarFromUsualRoutesAreLeftOut() {
        val ranked = engine.rank(listOf(place(1, "Around the corner", "cafe", 200.0), place(2, "Across town", "cafe", 900.0)), emptySet(), listOf(home), emptySet())
        assertEquals(listOf("Around the corner"), ranked.map { it.place.name })
    }

    @Test
    fun dropsVisitedHiddenAndUnnamedPlaces() {
        val ranked = engine.rank(
            listOf(place(1, "Visited", "cafe", 200.0, cell = 99), place(2, "Hidden", "cafe", 200.0), place(3, "", "cafe", 200.0), place(4, "Fresh", "cafe", 200.0)),
            visitedCells = setOf(99),
            habitual = listOf(home),
            hiddenIds = setOf(2),
        )
        assertEquals(listOf("Fresh"), ranked.map { it.place.name })
    }

    @Test
    fun errandsAreNotRecommended() {
        val ranked = engine.rank(
            listOf(
                place(1, "UNIPARK", "parking_lot", 200.0),
                place(2, "Charger", "ev_charging_station", 200.0),
                place(3, "Parts", "auto_parts_store", 200.0),
                place(4, "Salon", "beauty_salon", 200.0),
                place(5, "Old Town gate", "historic_site", 200.0),
                place(6, "Dukes", "gastropub", 200.0),
            ),
            visitedCells = emptySet(),
            habitual = listOf(home),
            hiddenIds = emptySet(),
        )
        assertEquals(listOf("Old Town gate", "Dukes"), ranked.map { it.place.name })
        assertEquals(listOf(CategoryGroup.Culture, CategoryGroup.Food), ranked.map { it.categoryGroup })
    }

    @Test
    fun hookTellsWhyThePlaceIsWorthIt() {
        assertEquals(Hook.Overlooked, engine.hook(30.0, timesPassed = 12))
        assertEquals(Hook.OnYourWay, engine.hook(30.0, timesPassed = 2))
        assertEquals(Hook.AroundTheCorner, engine.hook(120.0, timesPassed = 40))
        assertEquals(Hook.JustBeyond, engine.hook(220.0, timesPassed = 40))
    }

    @Test
    fun overlookedPlacesComeFirstAndSayHowOftenYouPassed() {
        val street = listOf(HabitualPoint(home.lat, home.lon, walks = 15))
        val ranked = engine.rank(listOf(place(1, "Corner gallery", "art_gallery", 30.0), place(2, "Far park", "park", 200.0)), emptySet(), street, emptySet())
        val best = engine.filter(ranked, "", CategoryGroup.All, SortOrder.BestMatch)
        assertEquals(listOf("Corner gallery", "Far park"), best.map { it.place.name })
        assertEquals("You have walked past it 15 times", best.first().reason)
    }

    @Test
    fun walkingMinutesRoundUpAtEightyMetresPerMinute() {
        val ranked = engine.rank(listOf(place(1, "Park", "park", 230.0)), emptySet(), listOf(home), emptySet())
        assertEquals(3, ranked.single().walkingMinutes)
        assertEquals(CategoryGroup.Nature, ranked.single().categoryGroup)
    }

    @Test
    fun withoutHistoryNothingIsRecommended() {
        assertEquals(emptyList<Recommendation>(), engine.rank(listOf(place(1, "A", "cafe", 100.0)), emptySet(), emptyList(), emptySet()))
    }

    @Test
    fun filterSearchesNameAndCategoryAndSorts() {
        val ranked = engine.rank(
            listOf(place(1, "Near cafe", "cafe", 24.0), place(2, "Far cafe", "coffee_shop", 240.0), place(3, "Old church", "church", 120.0)),
            emptySet(),
            listOf(home),
            emptySet(),
        )
        assertEquals(listOf("Near cafe", "Old church", "Far cafe"), engine.filter(ranked, "", CategoryGroup.All, SortOrder.BestMatch).map { it.place.name })
        assertEquals(listOf("Near cafe", "Old church", "Far cafe"), engine.filter(ranked, "", CategoryGroup.All, SortOrder.Closest).map { it.place.name })
        assertEquals(listOf("Near cafe", "Far cafe"), engine.filter(ranked, "", CategoryGroup.Food, SortOrder.Closest).map { it.place.name })
        assertEquals(listOf("Far cafe"), engine.filter(ranked, "coffee", CategoryGroup.All, SortOrder.Closest).map { it.place.name })
        assertEquals(listOf("Old church"), engine.filter(ranked, "CHURCH", CategoryGroup.All, SortOrder.Closest).map { it.place.name })
    }

    @Test
    fun categoryGroupsFromOvertureCodes() {
        assertEquals(CategoryGroup.Food, CategoryGroup.of("coffee_shop"))
        assertEquals(CategoryGroup.Culture, CategoryGroup.of("art_gallery"))
        assertEquals(CategoryGroup.Shops, CategoryGroup.of("lingerie_store"))
        assertEquals(CategoryGroup.Other, CategoryGroup.of("car_rental_service"))
        assertEquals("Car rental service", CategoryNames.pretty("car_rental_service"))
    }
}
