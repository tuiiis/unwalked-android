package lt.ehu.unwalked.domain.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkingFilterTest {
    @Test
    fun sparseWalkIsFilledEveryFifteenMetres() {
        val walk = listOf(Fix(0, 54.680, 25.27), Fix(120_000, 54.681, 25.27))
        val filled = WalkingFilter.keepWalking(walk, null)
        assertEquals(9, filled.size)
        assertEquals(120_000L, filled.last().time)
    }

    @Test
    fun longGapsAreNotBridged() {
        val fixes = listOf(Fix(0, 54.680, 25.27), Fix(60_000, 54.6805, 25.27), Fix(3_600_000, 54.70, 25.27), Fix(3_660_000, 54.7005, 25.27))
        val kept = WalkingFilter.keepWalking(fixes, null)
        assertTrue(kept.none { it.lat in 54.682..54.699 })
    }

    @Test
    fun pointsOutsideWalkingIntervalsAreDropped() {
        val fixes = listOf(Fix(0, 54.680, 25.27), Fix(60_000, 54.6805, 25.27), Fix(600_000, 54.690, 25.27), Fix(660_000, 54.6905, 25.27))
        val kept = WalkingFilter.keepWalking(fixes, listOf(0L..60_000L))
        assertTrue(kept.all { it.lat < 54.681 })
    }
}
