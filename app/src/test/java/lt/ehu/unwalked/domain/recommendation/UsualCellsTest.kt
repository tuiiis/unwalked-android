package lt.ehu.unwalked.domain.recommendation

import lt.ehu.unwalked.domain.route.LatLon
import lt.ehu.unwalked.domain.tracking.Streak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsualCellsTest {
    private val cellOf: (Double, Double) -> Long = { lat, lon -> ((lat * 1000).toLong() shl 32) or (lon * 1000).toLong() }

    private fun walk(fromLon: Double, toLon: Double, lat: Double = 54.687) = Streak(listOf(LatLon(lat, fromLon), LatLon(lat, toLon)), 1)

    @Test
    fun oneOffTripIsNotUsual() {
        val daily = List(5) { walk(25.270, 25.290) }
        val once = walk(25.400, 25.420, lat = 54.750)
        val usual = UsualCells.from(daily + once, cellOf, minCells = 5)
        assertTrue(usual.keys.none { (it shr 32) == 54750L })
        assertTrue(usual.isNotEmpty())
    }

    @Test
    fun aFewWalksStillCount() {
        val usual = UsualCells.from(listOf(walk(25.270, 25.275)), cellOf, minCells = 50)
        assertTrue(usual.isNotEmpty())
        assertEquals(setOf(1), usual.values.toSet())
    }
}
