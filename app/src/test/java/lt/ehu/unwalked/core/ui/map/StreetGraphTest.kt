package lt.ehu.unwalked.core.ui.map

import lt.ehu.unwalked.domain.route.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreetGraphTest {
    private val west = listOf(LatLon(54.6872, 25.2700), LatLon(54.6872, 25.2750))
    private val north = listOf(LatLon(54.6872, 25.2750), LatLon(54.6890, 25.2750))

    @Test
    fun routesAroundACorner() {
        val path = StreetGraph(listOf(west, north)).route(LatLon(54.68721, 25.2705), LatLon(54.6889, 25.27501))!!
        assertTrue(path.any { kotlin.math.abs(it.lat - 54.6872) < 1e-7 && kotlin.math.abs(it.lon - 25.2750) < 1e-7 })
        assertEquals(54.6889, path.last().lat, 0.0002)
    }

    @Test
    fun refusesALongDetour() {
        val east = listOf(LatLon(54.6872, 25.2800), LatLon(54.6872, 25.2850))
        val bridge = listOf(LatLon(54.6872, 25.2750), LatLon(54.7000, 25.2750), LatLon(54.7000, 25.2800), LatLon(54.6872, 25.2800))
        assertNull(StreetGraph(listOf(west, bridge, east)).route(LatLon(54.6872, 25.2700), LatLon(54.6872, 25.2850)))
    }

    @Test
    fun joinsLinesThatMeetAtATileEdge() {
        val left = listOf(LatLon(54.6872, 25.2700), LatLon(54.6872, 25.27500))
        val right = listOf(LatLon(54.687201, 25.275001), LatLon(54.6872, 25.2800))
        assertTrue(StreetGraph(listOf(left, right)).route(LatLon(54.6872, 25.2701), LatLon(54.6872, 25.2799)) != null)
    }
}
