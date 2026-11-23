package lt.ehu.unwalked.domain.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FunFactsTest {
    private val zone = ZoneOffset.UTC

    private fun at(date: String, hour: Int, minute: Int = 0) =
        LocalDate.parse(date).atTime(hour, minute).toInstant(zone).toEpochMilli()

    private val walks = listOf(
        Walk(at("2026-11-07", 7, 5), 5.0),
        Walk(at("2026-11-08", 18), 2.0),
        Walk(at("2026-11-09", 21, 40), 3.0),
        Walk(at("2026-11-14", 10), 20.0),
    )

    @Test
    fun distanceIsComparedWithTheLargestLandmarkItCovers() {
        val fact = FunFacts.of(walks, emptyList(), zone).filterIsInstance<FunFact.Distance>().single()
        assertEquals("the road from Vilnius to Trakai", fact.landmark.name)
        assertEquals("a marathon", fact.next?.name)
    }

    @Test
    fun walkingHoursFavouriteDayAndStreak() {
        val facts = FunFacts.of(walks, emptyList(), zone)
        val time = facts.filterIsInstance<FunFact.TimeOfDay>().single()
        assertEquals(DayBand.Morning, time.band)
        assertEquals(83, time.share)
        assertEquals(20.0, time.byHour[10], 1e-9)
        val day = facts.filterIsInstance<FunFact.FavouriteDay>().single()
        assertEquals(DayOfWeek.SATURDAY, day.day)
        assertEquals(25.0, day.byWeekday[DayOfWeek.SATURDAY.value - 1], 1e-9)
        assertEquals(3, facts.filterIsInstance<FunFact.Streak>().single().days)
    }

    @Test
    fun bestDayCountsNewStreets() {
        val cells = (1..6).map { CellVisit(it.toLong(), 0, at("2026-11-12", 12), at("2026-11-12", 12)) } +
            CellVisit(99, 0, at("2026-11-13", 12), at("2026-11-13", 12))
        val best = FunFacts.of(emptyList(), cells, zone).filterIsInstance<FunFact.BestDay>().single()
        assertEquals(LocalDate.parse("2026-11-12"), best.date)
        assertEquals(6, best.newStreets)
    }

    @Test
    fun gpsDriftAtNightDoesNotMakeYouANightOwl() {
        val drift = (1..10).map { Walk(at(LocalDate.parse("2026-11-01").plusDays(it.toLong()).toString(), 2, 30), 0.1) }
        val time = FunFacts.of(walks + drift, emptyList(), zone).filterIsInstance<FunFact.TimeOfDay>().single()
        assertEquals(DayBand.Morning, time.band)
    }

    @Test
    fun fewWalksGiveNoHabitFacts() {
        val facts = FunFacts.of(walks.take(1), emptyList(), zone)
        assertTrue(facts.none { it is FunFact.TimeOfDay || it is FunFact.FavouriteDay || it is FunFact.Streak })
    }
}
