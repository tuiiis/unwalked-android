package lt.ehu.unwalked.core.ui.map

import kotlin.math.abs
import lt.ehu.unwalked.domain.route.LatLon
import lt.ehu.unwalked.domain.tracking.Streak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreetGlowTest {
    private val street = listOf(LatLon(54.6872, 25.2700), LatLon(54.6872, 25.2800))

    private fun walk(passes: Int, vararg corners: LatLon): Streak =
        Streak(corners.toList().zipWithNext().flatMap { (a, b) -> (0 until 20).map { LatLon(a.lat + (b.lat - a.lat) * it / 20, a.lon + (b.lon - a.lon) * it / 20) } } + corners.last(), passes)

    @Test
    fun onlyTheStreetTheTrackFollowedLightsUp() {
        val walked = walk(2, LatLon(54.68725, 25.2700), LatLon(54.68725, 25.2800))
        val parallel = listOf(LatLon(54.6878, 25.2700), LatLon(54.6878, 25.2800))
        val lit = StreetGlow.walked(listOf(street, parallel), listOf(walked))
        assertEquals(1, lit.size)
        assertEquals(3, lit.single().passes)
        assertTrue(lit.single().points.all { abs(it.lat - 54.6872) < 1e-7 })
    }

    @Test
    fun sidewalkNextToTheRoadTakesTheWalkAlone() {
        val sidewalk = listOf(LatLon(54.68728, 25.2700), LatLon(54.68728, 25.2800))
        val walked = walk(1, LatLon(54.68729, 25.2700), LatLon(54.68729, 25.2800))
        val lit = StreetGlow.walked(listOf(street, sidewalk), listOf(walked))
        assertEquals(1, lit.size)
        assertTrue(lit.single().points.all { abs(it.lat - 54.68728) < 1e-7 })
    }

    @Test
    fun gpsWobbleBetweenSidewalkAndRoadStaysOnOneLine() {
        val sidewalk = listOf(LatLon(54.68725, 25.2700), LatLon(54.68725, 25.2800))
        val wobble = Streak((0..100).map { LatLon(if (it % 6 < 3) 54.68721 else 54.68726, 25.2700 + it * 0.0001) }, 1)
        val lit = StreetGlow.walked(listOf(street, sidewalk), listOf(wobble))
        assertEquals(1, lit.map { it.points.first().lat }.distinct().size)
    }

    @Test
    fun shortSideStubIsDropped() {
        val stub = listOf(LatLon(54.6872, 25.2750), LatLon(54.68745, 25.2750))
        val walked = walk(1, LatLon(54.6872, 25.2700), LatLon(54.6872, 25.2750), LatLon(54.68745, 25.2750), LatLon(54.6872, 25.2750), LatLon(54.6872, 25.2800))
        val lit = StreetGlow.walked(listOf(street, stub), listOf(walked))
        assertTrue(lit.toString(), lit.all { line -> line.points.all { abs(it.lat - 54.6872) < 1e-7 } })
    }

    @Test
    fun shortConnectorBetweenWalkedStreetsStays() {
        val north = listOf(LatLon(54.68745, 25.2700), LatLon(54.68745, 25.2800))
        val connector = listOf(LatLon(54.6872, 25.2750), LatLon(54.68745, 25.2750))
        val walked = walk(1, LatLon(54.6872, 25.2700), LatLon(54.6872, 25.2750), LatLon(54.68745, 25.2750), LatLon(54.68745, 25.2800))
        val lit = StreetGlow.walked(listOf(street, north, connector), listOf(walked))
        assertTrue(lit.toString(), lit.any { line -> line.points.count { abs(it.lon - 25.2750) < 0.00002 } >= 3 && line.points.any { it.lat > 54.6873 } })
    }

    @Test
    fun crossingAStreetDoesNotLightItButKeepsTheWalk() {
        val across = walk(5, LatLon(54.6860, 25.2750), LatLon(54.6885, 25.2750))
        val lit = StreetGlow.walked(listOf(street), listOf(across))
        assertEquals(1, lit.size)
        assertTrue(lit.single().points.all { abs(it.lon - 25.2750) < 1e-9 })
    }

    @Test
    fun turningAtACornerLeavesNoGap() {
        val side = listOf(LatLon(54.6872, 25.2750), LatLon(54.6890, 25.2750))
        val turn = walk(1, LatLon(54.68722, 25.2700), LatLon(54.68722, 25.27485), LatLon(54.68735, 25.27502), LatLon(54.6890, 25.27502))
        val lit = StreetGlow.walked(listOf(street, side), listOf(turn))
        val ends = lit.flatMap { listOf(it.points.first(), it.points.last()) }
        val corner = ends.filter { abs(it.lat - 54.6872) < 0.0001 && abs(it.lon - 25.2750) < 0.0002 }
        assertTrue(lit.toString(), corner.size >= 2 && corner.zipWithNext().all { (a, b) -> abs(a.lat - b.lat) < 1e-9 && abs(a.lon - b.lon) < 1e-9 })
    }

    @Test
    fun separateWalksSnapTogetherAtTheirEnds() {
        val north = listOf(LatLon(54.6880, 25.2700), LatLon(54.6880, 25.2800))
        val first = walk(1, LatLon(54.6872, 25.2700), LatLon(54.6872, 25.2800))
        val path = walk(1, LatLon(54.68735, 25.2760), LatLon(54.68787, 25.2760))
        val second = walk(1, LatLon(54.6880, 25.2700), LatLon(54.6880, 25.2800))
        val lit = StreetGlow.walked(listOf(street, north), listOf(first, path, second))
        val link = lit.single { line -> line.points.any { abs(it.lon - 25.2760) < 0.00002 && it.lat in 54.6874..54.6878 } }
        assertEquals(54.6872, link.points.first().lat, 0.00002)
        assertEquals(54.6880, link.points.last().lat, 0.00002)
    }

    @Test
    fun straightJumpBetweenDistantFixesIsNotDrawn() {
        val jump = Streak(listOf(LatLon(54.6900, 25.2700), LatLon(54.6930, 25.2760)), 1)
        assertTrue(StreetGlow.walked(listOf(street), listOf(jump)).isEmpty())
    }

    @Test
    fun sparseFixesAreFilledInAlongTheStreets() {
        val side = listOf(LatLon(54.6872, 25.2750), LatLon(54.6890, 25.2750))
        val sparse = Streak(listOf(LatLon(54.68722, 25.2720), LatLon(54.68722, 25.2745), LatLon(54.6880, 25.27502), LatLon(54.6888, 25.27502)), 1)
        val lit = StreetGlow.walked(listOf(street, side), listOf(sparse))
        val along = lit.flatMap { it.points }
        assertTrue(lit.toString(), along.any { abs(it.lon - 25.2750) < 0.00003 && it.lat in 54.6874..54.6878 })
        assertTrue(lit.toString(), along.any { abs(it.lat - 54.6872) < 0.00003 && it.lon in 25.2730..25.2740 })
    }

    @Test
    fun gapWithoutAStreetBetweenStaysEmpty() {
        val far = listOf(LatLon(54.7000, 25.2700), LatLon(54.7000, 25.2800))
        val jump = Streak(listOf(LatLon(54.6872, 25.2700), LatLon(54.7000, 25.2700)), 1)
        assertTrue(StreetGlow.walked(listOf(street, far), listOf(jump)).isEmpty())
    }

    @Test
    fun walksMinutesApartAreJoinedAlongTheStreets() {
        val side = listOf(LatLon(54.6872, 25.2750), LatLon(54.6890, 25.2750))
        val first = walk(1, LatLon(54.6872, 25.2700), LatLon(54.6872, 25.2735)).copy(startedAt = 0L, endedAt = 600_000L)
        val second = walk(1, LatLon(54.6878, 25.2750), LatLon(54.6889, 25.2750)).copy(startedAt = 900_000L, endedAt = 1_200_000L)
        val lit = StreetGlow.walked(listOf(street, side), listOf(first, second)).flatMap { it.points }
        assertTrue(lit.any { abs(it.lat - 54.6872) < 0.00003 && it.lon in 25.2740..25.2748 })
        assertTrue(lit.any { abs(it.lon - 25.2750) < 0.00003 && it.lat in 54.6873..54.6877 })
    }

    @Test
    fun walksOnDifferentDaysStayApart() {
        val side = listOf(LatLon(54.6872, 25.2750), LatLon(54.6890, 25.2750))
        val first = walk(1, LatLon(54.6872, 25.2700), LatLon(54.6872, 25.2735)).copy(startedAt = 0L, endedAt = 600_000L)
        val second = walk(1, LatLon(54.6878, 25.2750), LatLon(54.6889, 25.2750)).copy(startedAt = 86_400_000L, endedAt = 87_000_000L)
        val lit = StreetGlow.walked(listOf(street, side), listOf(first, second)).flatMap { it.points }
        assertTrue(lit.none { abs(it.lon - 25.2750) < 0.00003 && it.lat in 54.68735..54.6876 })
    }

    @Test
    fun walkThroughAParkBecomesOneCleanLine() {
        val jitter = Streak((0..60).map { LatLon(54.6900 + (if (it % 2 == 0) 0.00002 else -0.00002), 25.2700 + it * 0.0001) }, 1)
        val lit = StreetGlow.walked(listOf(street), listOf(jitter))
        assertEquals(1, lit.size)
        assertTrue(lit.single().points.drop(3).dropLast(3).all { abs(it.lat - 54.6900) < 0.00001 })
    }

    @Test
    fun repeatedWalksOfTheSameParkPathMerge() {
        val first = Streak((0..60).map { LatLon(54.6900, 25.2700 + it * 0.0001) }, 1)
        val second = Streak((0..60).map { LatLon(54.69005, 25.2700 + it * 0.0001) }, 3)
        val lit = StreetGlow.walked(listOf(street), listOf(first, second))
        assertEquals(1, lit.size)
        assertEquals(3, lit.single().passes)
    }

    @Test
    fun parkPathReachesTheStreetItLeaves() {
        val leaving = walk(1, LatLon(54.6872, 25.2700), LatLon(54.6872, 25.2750), LatLon(54.68735, 25.2752), LatLon(54.6890, 25.2752))
        val lit = StreetGlow.walked(listOf(street), listOf(leaving))
        val park = lit.single { line -> line.points.last().lat > 54.688 }
        assertEquals(54.6872, park.points.first().lat, 0.00005)
    }

    @Test
    fun shortGpsDropoutDoesNotBreakTheStreet() {
        val before = walk(1, LatLon(54.68725, 25.2700), LatLon(54.68725, 25.2745))
        val after = walk(1, LatLon(54.68725, 25.2752), LatLon(54.68725, 25.2800))
        assertEquals(1, StreetGlow.walked(listOf(street), listOf(before, after)).size)
    }

    @Test
    fun unvisitedStreetStaysDark() {
        assertTrue(StreetGlow.lit(listOf(street), passesAt = { _, _ -> 0 }).isEmpty())
    }

    @Test
    fun visitedPartOfStreetLightsUp() {
        val lit = StreetGlow.lit(listOf(street), passesAt = { _, lon -> if (lon in 25.2740..25.2760) 2 else 0 })
        assertEquals(1, lit.size)
        assertEquals(3, lit.single().passes)
        assertTrue(lit.single().points.all { it.lon in 25.2735..25.2765 })
    }

    @Test
    fun busierPiecesAreDrawnLast() {
        val lit = StreetGlow.lit(listOf(street), passesAt = { _, lon -> if (lon > 25.2750) 30 else 1 })
        assertEquals(listOf(1, 20), lit.map { it.passes })
        assertEquals(lit[0].points.last(), lit[1].points.first())
    }

    @Test
    fun shortGapsBetweenLitPiecesAreFilled() {
        val lit = StreetGlow.lit(listOf(street), passesAt = { _, lon -> if (lon in 25.2750..25.2753) 0 else 2 })
        assertEquals(1, lit.size)
    }

    @Test
    fun tinySpecksAreDropped() {
        val lit = StreetGlow.lit(listOf(street), passesAt = { _, lon -> if (lon in 25.27500..25.27510) 2 else 0 })
        assertTrue(lit.isEmpty())
    }

    @Test
    fun bucketsFollowTheColourStops() {
        assertEquals(listOf(0, 1, 3, 3, 8, 20), listOf(0, 1, 2, 3, 5, 40).map { StreetGlow.bucket(it) })
    }
}
