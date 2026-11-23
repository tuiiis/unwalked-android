package lt.ehu.unwalked.domain.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpursTest {
    private fun east(metres: Double) = LatLon(54.6872, 25.2797 + metres / (111_320.0 * 0.5781))
    private fun northOf(base: LatLon, metres: Double) = LatLon(base.lat + metres / 111_320.0, base.lon)

    @Test
    fun deadEndOutAndBackIsRemoved() {
        val corner = east(200.0)
        val path = listOf(east(0.0), east(100.0), corner, northOf(corner, 50.0), northOf(corner, 100.0), northOf(corner, 50.0), corner, east(300.0), east(400.0))
        val trimmed = Spurs.trim(path)
        assertTrue(trimmed.none { it.lat > corner.lat + 1e-6 })
        assertEquals(400.0, Spurs.length(trimmed), 1.0)
    }

    @Test
    fun straightPathIsUntouched() {
        val path = (0..10).map { east(it * 50.0) }
        assertEquals(path, Spurs.trim(path))
    }

    @Test
    fun longLoopBackToStartIsKept() {
        val path = listOf(east(0.0), east(1_000.0), northOf(east(1_000.0), 1_000.0), northOf(east(0.0), 1_000.0), east(0.0))
        assertEquals(path, Spurs.trim(path))
    }
}
