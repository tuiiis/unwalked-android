package lt.ehu.unwalked.domain.tracking

import kotlin.math.ceil

object WalkingFilter {
    fun keepWalking(fixes: List<Fix>, walking: List<LongRange>?): List<Fix> {
        val sorted = fixes.sortedBy { it.time }.distinctBy { it.time }
        val candidates = if (walking == null) sorted else sorted.filter { fix -> walking.any { fix.time in (it.first - MARGIN_MILLIS)..(it.last + MARGIN_MILLIS) } }
        return runs(candidates).filter { it.size >= 2 }.flatMap(::densify)
    }

    private fun runs(fixes: List<Fix>): List<List<Fix>> {
        val result = mutableListOf<MutableList<Fix>>()
        fixes.forEach { fix ->
            val run = result.lastOrNull()
            val previous = run?.last()
            if (previous != null && walkable(previous, fix)) run += fix else result += mutableListOf(fix)
        }
        return result
    }

    private fun walkable(a: Fix, b: Fix): Boolean {
        val seconds = (b.time - a.time) / 1000.0
        if (seconds <= 0 || seconds > MAX_GAP_SECONDS) return false
        return GeoMath.distanceMeters(a.lat, a.lon, b.lat, b.lon) / seconds <= MAX_SPEED
    }

    private fun densify(run: List<Fix>): List<Fix> {
        val out = mutableListOf(run.first())
        run.zipWithNext().forEach { (a, b) ->
            val steps = ceil(GeoMath.distanceMeters(a.lat, a.lon, b.lat, b.lon) / STEP_METERS).toInt().coerceAtLeast(1)
            for (i in 1..steps) {
                val t = i.toDouble() / steps
                out += Fix(
                    time = a.time + ((b.time - a.time) * t).toLong(),
                    lat = a.lat + (b.lat - a.lat) * t,
                    lon = a.lon + (b.lon - a.lon) * t,
                    accuracy = maxOf(a.accuracy, b.accuracy),
                )
            }
        }
        return out
    }

    private const val MARGIN_MILLIS = 60_000L
    private const val MAX_GAP_SECONDS = 600.0
    private const val MAX_SPEED = 3.0
    private const val STEP_METERS = 15.0
}
