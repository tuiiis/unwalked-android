package lt.ehu.unwalked.domain.stats

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TravelStatsTest {
    private val zone = ZoneOffset.UTC
    private val vilnius = City("Vilnius", "LT", 54.6892, 25.2798, 542366)
    private val kaunas = City("Kaunas", "LT", 54.9016, 23.9091, 289380)
    private val riga = City("Riga", "LV", 56.9460, 24.1059, 742572)
    private val gazetteer = Gazetteer(listOf(vilnius, kaunas, riga))

    private fun day(date: String, city: City, samples: Int = 30) = (0 until samples).map {
        TimedPoint(LocalDate.parse(date).atTime(10, 0).toInstant(zone).toEpochMilli() + it * 60_000L, city.lat + it * 0.0001, city.lon)
    }

    @Test
    fun findsTheNearestCityWithinReach() {
        assertEquals(vilnius, gazetteer.nearest(54.70, 25.30))
        assertNull(gazetteer.nearest(55.40, 26.60))
    }

    @Test
    fun homeIsWhereMostDaysAreAndTripsAreCounted() {
        val points = day("2026-11-01", vilnius) + day("2026-11-02", vilnius) + day("2026-11-03", kaunas) + day("2026-11-04", riga) + day("2026-11-05", vilnius)
        val travel = TravelStats.of(points, gazetteer, zone)!!
        assertEquals(vilnius, travel.home)
        assertEquals(listOf("Vilnius", "Kaunas", "Riga"), travel.cities.map { it.city.name })
        assertEquals(listOf("LT", "LV"), travel.countries.map { it.code })
        assertEquals("Latvia", travel.countries.last().name)
        assertEquals(2, travel.awayDays)
        assertEquals(60, travel.homeShare)
    }

    @Test
    fun aFewStrayFixesDoNotMakeATrip() {
        val points = day("2026-11-01", vilnius) + day("2026-11-01", riga, samples = 5)
        assertEquals(listOf("Vilnius"), TravelStats.of(points, gazetteer, zone)!!.cities.map { it.city.name })
    }

    @Test
    fun paceIsDistanceOverTime() {
        val start = LocalDate.parse("2026-11-01").atTime(9, 0).toInstant(zone).toEpochMilli()
        val walks = listOf(Walk(start, 5.0, start + 3_600_000), Walk(start + 86_400_000, 3.0, start + 86_400_000 + 1_800_000))
        val pace = FunFacts.of(walks, emptyList(), zone).filterIsInstance<FunFact.Pace>().single()
        assertEquals(5.33, pace.averageKmh, 0.01)
        assertEquals(6.0, pace.fastestKmh, 0.01)
    }
}
