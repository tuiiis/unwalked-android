package lt.ehu.unwalked.domain.stats

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsCalculatorTest {
    private val zone = ZoneOffset.UTC
    private val now = LocalDateTime.of(2026, 11, 21, 12, 0).toInstant(zone).toEpochMilli()
    private val calculator = StatsCalculator(childrenPerArea = 10)
    private fun day(month: Int, day: Int) = LocalDateTime.of(2026, month, day, 10, 0).toInstant(zone).toEpochMilli()

    @Test
    fun monthRangeStartsOnTheFirst() {
        val range = Periods.range(Period.Month, now, zone, null)
        assertEquals(day(11, 1) - 10 * 3_600_000, range.start)
        assertEquals("November 2026", range.title)
    }

    @Test
    fun quarterStartsOnTheFirstDayOfItsQuarter() {
        val range = Periods.range(Period.Quarter, now, zone, null)
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0).toInstant(zone).toEpochMilli(), range.start)
        assertEquals(LocalDateTime.of(2026, 7, 1, 0, 0).toInstant(zone).toEpochMilli(), range.previousStart)
        assertEquals("Q4 2026", range.title)
    }

    @Test
    fun allTimeSubtitleNamesTheFirstDay() {
        assertEquals("Since 3 March 2026", Periods.range(Period.AllTime, now, zone, day(3, 3)).subtitle)
        assertEquals("No walks recorded yet", Periods.range(Period.AllTime, now, zone, null).subtitle)
    }

    @Test
    fun countsNewCellsAndPlacesOnlyInsideThePeriod() {
        val cells = listOf(
            CellVisit(1, 100, day(10, 5), day(11, 3)),
            CellVisit(2, 100, day(11, 4), day(11, 4)),
            CellVisit(3, 200, day(11, 10), day(11, 12)),
        )
        val stays = listOf(StayVisit(day(10, 5), 7), StayVisit(day(11, 2), 7), StayVisit(day(11, 3), 8))
        val stats = calculator.calculate(Periods.range(Period.Month, now, zone, null), cells, emptyList(), stays)
        assertEquals(2, stats.newCells)
        assertEquals(3, stats.visitedCells)
        assertEquals(1, stats.newPlaces)
        assertEquals(15, stats.exploredPercent)
        assertEquals(5, stats.exploredDelta)
    }

    @Test
    fun walksSplitOnLongGapsAndVehicleSpeed() {
        val start = day(11, 5)
        val walk = (0..10).map { TimedPoint(start + it * 60_000L, 54.68 + it * 0.0007, 25.28) }
        val drive = (1..5).map { TimedPoint(walk.last().time + it * 60_000L, walk.last().lat + it * 0.01, 25.28) }
        val walks = calculator.walks(walk + drive)
        assertEquals(1, walks.size)
        assertEquals(0.78, walks.single().kilometres, 0.02)
    }

    @Test
    fun gpsJitterAtHomeIsNotWalking() {
        val start = day(11, 5)
        val random = java.util.Random(3)
        val jitter = (0 until 2_000).map {
            TimedPoint(start + it * 5_000L, 54.68 + random.nextGaussian() * 0.0002, 25.28 + random.nextGaussian() * 0.0003, 20f)
        }
        assertTrue(calculator.walks(jitter).isEmpty())
    }

    @Test
    fun inaccurateFixesDoNotCount() {
        val start = day(11, 5)
        val walk = (0..10).map { TimedPoint(start + it * 60_000L, 54.68 + it * 0.0007, 25.28, 80f) }
        assertTrue(calculator.walks(walk).isEmpty())
    }

    @Test
    fun shortWalksAreNotTheLongest() {
        val start = day(11, 5)
        val points = (0..2).map { TimedPoint(start + it * 60_000L, 54.68 + it * 0.0005, 25.28) }
        assertNull(calculator.calculate(Periods.range(Period.Month, now, zone, null), emptyList(), points, emptyList()).longestWalk)
    }

    @Test
    fun emptyHistoryIsEmpty() {
        assertTrue(calculator.calculate(Periods.range(Period.Year, now, zone, null), emptyList(), emptyList(), emptyList()).isEmpty)
    }
}
