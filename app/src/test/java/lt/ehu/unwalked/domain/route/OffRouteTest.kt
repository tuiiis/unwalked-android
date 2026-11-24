package lt.ehu.unwalked.domain.route

import lt.ehu.unwalked.domain.tracking.Fix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OffRouteTest {
    private val lat = 54.6872
    private val lon = 25.2797
    private val metresPerLat = 111_320.0
    private val path = listOf(LatLon(lat, lon), LatLon(lat + 500 / metresPerLat, lon), LatLon(lat + 1000 / metresPerLat, lon))

    private fun fixEast(north: Double, east: Double, accuracy: Float = 10f) =
        Fix(0, lat + north / metresPerLat, lon + east / (metresPerLat * Math.cos(Math.toRadians(lat))), accuracy)

    @Test
    fun distanceIsMeasuredToTheNearestSegment() {
        assertEquals(40.0, OffRoute.distanceToPath(LatLon(fixEast(250.0, 40.0).lat, fixEast(250.0, 40.0).lon), path), 0.5)
    }

    @Test
    fun twoFixesFartherThanFiftyMetresTriggerARebuild() {
        val detector = OffRoute()
        assertFalse(detector.update(fixEast(300.0, 70.0), path))
        assertTrue(detector.update(fixEast(320.0, 75.0), path))
    }

    @Test
    fun aFixBackOnTheRouteResetsTheCount() {
        val detector = OffRoute()
        assertFalse(detector.update(fixEast(300.0, 70.0), path))
        assertFalse(detector.update(fixEast(310.0, 10.0), path))
        assertFalse(detector.update(fixEast(320.0, 75.0), path))
    }

    @Test
    fun inaccurateFixesAreIgnored() {
        val detector = OffRoute()
        assertFalse(detector.update(fixEast(300.0, 90.0, accuracy = 60f), path))
        assertFalse(detector.update(fixEast(320.0, 90.0, accuracy = 60f), path))
    }

    @Test
    fun rejoinStartsHereAndKeepsTheRestOfTheRoute() {
        val here = LatLon(lat + 300 / metresPerLat, lon + 0.001)
        val waypoints = OffRoute.rejoin(here, path)
        assertEquals(here, waypoints.first())
        assertEquals(path.last(), waypoints.last())
        assertTrue(waypoints.none { it == path.first() })
    }
}
