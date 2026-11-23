package lt.ehu.unwalked.domain.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackStatsTest {
    @Test
    fun findsGapsAboveThreshold() {
        val times = listOf(0L, 5_000, 10_000, 400_000, 405_000, 1_000_000)
        assertEquals(listOf(Gap(10_000, 400_000), Gap(405_000, 1_000_000)), TrackStats.gaps(times, 120_000))
        assertEquals(595_000L, TrackStats.longestGapMillis(times))
    }

    @Test
    fun lengthOfStraightLine() {
        val fixes = Tracks.line(0, Tracks.LAT, Tracks.LON, 1_000.0, 1.4)
        assertEquals(1_000.0, TrackStats.lengthMeters(fixes), 2.0)
    }
}
