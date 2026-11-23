package lt.ehu.unwalked.domain.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreaksTest {
    private val once: (Double, Double) -> Int = { _, _ -> 1 }

    @Test
    fun walkBecomesOneStreak() {
        val streaks = Streaks.build(Tracks.line(0, Tracks.LAT, Tracks.LON, 500.0, 1.4), once)
        assertEquals(1, streaks.size)
        assertTrue(streaks.single().points.size >= 2)
    }

    @Test
    fun drivingIsDropped() {
        assertTrue(Streaks.build(Tracks.line(0, Tracks.LAT, Tracks.LON, 3_000.0, 12.0), once).isEmpty())
    }

    @Test
    fun standingStillLeavesNoScribble() {
        assertTrue(Streaks.build(Tracks.jitter(0, Tracks.LAT, Tracks.LON, 30, 10.0), once).isEmpty())
    }

    @Test
    fun gapSplitsTheWalk() {
        val first = Tracks.line(0, Tracks.LAT, Tracks.LON, 300.0, 1.4)
        val second = Tracks.line(first.last().time + 10 * 60_000L, Tracks.LAT, Tracks.LON + 0.01, 300.0, 1.4)
        assertEquals(2, Streaks.build(first + second, once).size)
    }

    @Test
    fun eachWalkKnowsWhenItHappened() {
        val first = Tracks.line(0, Tracks.LAT, Tracks.LON, 300.0, 1.4)
        val second = Tracks.line(first.last().time + 10 * 60_000L, Tracks.LAT, Tracks.LON + 0.01, 300.0, 1.4)
        val walks = Streaks.build(first + second, once).sortedBy { it.startedAt }
        assertEquals(first.first().time, walks[0].startedAt)
        assertEquals(first.last().time, walks[0].endedAt)
        assertEquals(second.first().time, walks[1].startedAt)
    }

    @Test
    fun inaccurateFixesAreSkipped() {
        val fixes = Tracks.line(0, Tracks.LAT, Tracks.LON, 500.0, 1.4).map { it.copy(accuracy = 60f) }
        assertTrue(Streaks.build(fixes, once).isEmpty())
    }

    @Test
    fun busierPartsAreDrawnLast() {
        val fixes = Tracks.line(0, Tracks.LAT, Tracks.LON, 600.0, 1.4)
        val middle = fixes[fixes.size / 2].lon
        val streaks = Streaks.build(fixes, passesAt = { _, lon -> if (lon > middle) 12 else 1 })
        assertEquals(listOf(1, 20), streaks.map { it.passes })
    }
}
