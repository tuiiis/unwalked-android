package lt.ehu.unwalked.core.ui.map

import lt.ehu.unwalked.domain.route.LatLon
import lt.ehu.unwalked.domain.tracking.GeoMath
import lt.ehu.unwalked.domain.tracking.Streak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailMorphTest {
    private fun street(lat: Double) = Streak((0..10).map { LatLon(lat, 25.270 + it * 0.001) }, 1)

    private val parallel = listOf(street(54.6800), street(54.6803))

    private fun gap(lines: List<GlowLine>): Double {
        val a = lines[0].points[lines[0].points.size / 2]
        val b = lines[1].points[lines[1].points.size / 2]
        return GeoMath.distanceMeters(a.lat, a.lon, b.lat, b.lon)
    }

    @Test
    fun streetZoomKeepsTheWalkedPath() {
        val lines = TrailMorph.build(parallel).shape(13.0)
        assertEquals(2, lines.size)
        assertEquals(33.0, gap(lines), 2.0)
    }

    @Test
    fun zoomingOutPullsParallelPathsTogetherStepByStep() {
        val morph = TrailMorph.build(parallel)
        val gaps = listOf(12.5, 11.8, 11.0, 10.0).map { gap(morph.shape(it)) }
        assertTrue(gaps.toString(), gaps.zipWithNext().all { (a, b) -> b < a })
        assertTrue(gaps.toString(), gaps.last() < 5.0)
    }

    @Test
    fun inBetweenZoomsBendGradually() {
        val morph = TrailMorph.build(parallel)
        val steps = (0..20).map { gap(morph.shape(12.5 - it * 0.05)) }
        val jumps = steps.zipWithNext().map { (a, b) -> a - b }
        assertTrue(jumps.toString(), jumps.all { it >= -0.01 && it < 6.0 })
    }

    @Test
    fun litStreetsReplaceRawTracksAtStreetZoom() {
        val streets = listOf(GlowLine((0..10).map { LatLon(54.6800, 25.270 + it * 0.001) }, 3))
        val wobbly = Streak((0..10).map { LatLon(54.6801 + (it % 2) * 0.0002, 25.270 + it * 0.001) }, 1)
        val lines = TrailMorph.build(listOf(wobbly), streets).shape(12.5)
        assertEquals(1, lines.size)
        assertEquals(3, lines.single().passes)
        assertTrue(lines.single().onStreet)
        assertTrue(lines.single().points.all { kotlin.math.abs(it.lat - 54.6800) < 1e-6 })
    }

    @Test
    fun withoutANetworkTracksAreDrawnSoft() {
        val lines = TrailMorph.build(listOf(street(54.6806))).shape(12.5)
        assertEquals(listOf(false), lines.map { it.onStreet })
    }

    @Test
    fun litStreetsBendIntoOneBand() {
        val streets = listOf(54.6800, 54.6803).map { lat -> GlowLine((0..10).map { LatLon(lat, 25.270 + it * 0.001) }, 3) }
        val morph = TrailMorph.build(emptyList(), streets)
        val steps = (0..40).map { gap(morph.shape(12.5 - it * 0.05)) }
        assertEquals(33.0, steps.first(), 2.0)
        assertTrue(steps.toString(), steps.last() < steps.first() / 2)
        assertTrue(steps.toString(), steps.zipWithNext().all { (a, b) -> a - b in -0.01..6.0 })
    }

    @Test
    fun emptyHistoryDrawsNothing() {
        assertTrue(TrailMorph.build(emptyList()).shape(10.0).isEmpty())
    }
}
