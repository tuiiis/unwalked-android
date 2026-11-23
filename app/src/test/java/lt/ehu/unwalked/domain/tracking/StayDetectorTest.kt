package lt.ehu.unwalked.domain.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StayDetectorTest {
    private fun run(fixes: List<Fix>): List<Stay> {
        val detector = StayDetector()
        return fixes.mapNotNull { detector.accept(it) } + listOfNotNull(detector.flush())
    }

    @Test
    fun twentyMinutesInCafeWithJitterIsOneStay() {
        val walkIn = Tracks.line(0, Tracks.LAT, Tracks.LON, 400.0, 1.4)
        val cafe = Tracks.jitter(walkIn.last().time + 5_000, walkIn.last().lat, walkIn.last().lon, 20, 25.0)
        val walkOut = Tracks.line(cafe.last().time + 5_000, walkIn.last().lat, walkIn.last().lon, 400.0, 1.4)
        val stays = run(walkIn + cafe + walkOut)
        assertEquals(1, stays.size)
        println("cafe 20 min -> stay ${stays.single().durationMillis / 1000} s")
        assertTrue(stays.single().durationMillis in 19 * 60_000L..23 * 60_000L)
    }

    @Test
    fun threeMinuteStopIsNotAStay() {
        val stop = Tracks.jitter(0, Tracks.LAT, Tracks.LON, 3, 10.0)
        val walk = Tracks.line(stop.last().time + 5_000, Tracks.LAT, Tracks.LON, 500.0, 1.4)
        assertTrue(run(stop + walk).isEmpty())
    }

    @Test
    fun continuousWalkingProducesNoStay() {
        assertTrue(run(Tracks.line(0, Tracks.LAT, Tracks.LON, 3_000.0, 1.4)).isEmpty())
    }

    @Test
    fun inaccurateFixesAreIgnored() {
        val detector = StayDetector()
        assertNull(detector.accept(Fix(0, Tracks.LAT, Tracks.LON, 500f)))
        assertNull(detector.currentStay())
    }
}
