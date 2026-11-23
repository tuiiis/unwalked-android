package lt.ehu.unwalked.domain.recommendation

import kotlin.math.ceil
import lt.ehu.unwalked.domain.tracking.GeoMath
import lt.ehu.unwalked.domain.tracking.Streak

object UsualCells {
    fun from(walks: List<Streak>, cellOf: (Double, Double) -> Long, minWalks: Int = MIN_WALKS, minCells: Int = MIN_CELLS): Map<Long, Int> {
        val counts = HashMap<Long, Int>()
        walks.forEach { walk ->
            val cells = HashSet<Long>()
            walk.points.zipWithNext().forEach { (a, b) ->
                val steps = ceil(GeoMath.distanceMeters(a.lat, a.lon, b.lat, b.lon) / STEP_METRES).toInt().coerceAtLeast(1)
                for (i in 0..steps) cells += cellOf(a.lat + (b.lat - a.lat) * i / steps, a.lon + (b.lon - a.lon) * i / steps)
            }
            cells.forEach { counts.merge(it, 1, Int::plus) }
        }
        val repeated = counts.filterValues { it >= minWalks }
        return if (repeated.size >= minCells) repeated else counts
    }

    private const val STEP_METRES = 15.0
    private const val MIN_WALKS = 2
    private const val MIN_CELLS = 20
}
