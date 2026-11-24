package lt.ehu.unwalked.domain.route

import kotlin.math.cos
import kotlin.math.sqrt
import lt.ehu.unwalked.domain.tracking.Fix
import lt.ehu.unwalked.domain.tracking.GeoMath

class OffRoute(
    private val thresholdMetres: Double = THRESHOLD_METRES,
    private val fixesNeeded: Int = FIXES_NEEDED,
    private val maxAccuracyMetres: Float = MAX_ACCURACY_METRES,
) {
    private var away = 0

    fun update(fix: Fix, path: List<LatLon>): Boolean {
        if (path.size < 2 || fix.accuracy > maxAccuracyMetres) return false
        away = if (distanceToPath(LatLon(fix.lat, fix.lon), path) > thresholdMetres) away + 1 else 0
        if (away < fixesNeeded) return false
        away = 0
        return true
    }

    fun reset() {
        away = 0
    }

    companion object {
        const val THRESHOLD_METRES = 50.0
        const val FIXES_NEEDED = 2
        const val MAX_ACCURACY_METRES = 35f
        private const val METRES_PER_LAT = 111_320.0
        private const val MAX_WAYPOINTS = 8

        fun distanceToPath(point: LatLon, path: List<LatLon>): Double {
            val metresPerLon = METRES_PER_LAT * cos(Math.toRadians(point.lat))
            return path.zipWithNext().minOf { (a, b) ->
                val ax = (a.lon - point.lon) * metresPerLon
                val ay = (a.lat - point.lat) * METRES_PER_LAT
                val bx = (b.lon - point.lon) * metresPerLon
                val by = (b.lat - point.lat) * METRES_PER_LAT
                val dx = bx - ax
                val dy = by - ay
                val length = dx * dx + dy * dy
                val t = if (length == 0.0) 0.0 else (-(ax * dx + ay * dy) / length).coerceIn(0.0, 1.0)
                val x = ax + t * dx
                val y = ay + t * dy
                sqrt(x * x + y * y)
            }
        }

        fun rejoin(from: LatLon, path: List<LatLon>): List<LatLon> {
            val nearest = path.indices.minBy { GeoMath.distanceMeters(from.lat, from.lon, path[it].lat, path[it].lon) }
            val rest = path.drop(nearest + 1)
            if (rest.isEmpty()) return listOf(from, path.last())
            val step = maxOf(1, rest.size / MAX_WAYPOINTS)
            val sampled = rest.filterIndexed { i, _ -> i % step == 0 }.take(MAX_WAYPOINTS)
            return listOf(from) + sampled + rest.last()
        }
    }
}
