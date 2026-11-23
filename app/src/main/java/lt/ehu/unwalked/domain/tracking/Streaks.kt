package lt.ehu.unwalked.domain.tracking

import lt.ehu.unwalked.domain.route.LatLon

data class Streak(val points: List<LatLon>, val passes: Int, val startedAt: Long = 0, val endedAt: Long = 0)

object Streaks {
    fun build(
        fixes: List<Fix>,
        passesAt: (Double, Double) -> Int,
        maxAccuracyMeters: Float = 35f,
        maxGapMillis: Long = 2 * 60_000L,
        maxSpeedMps: Double = 3.0,
        minStepMeters: Double = 15.0,
        minReachMeters: Double = 80.0,
    ): List<Streak> {
        val streaks = mutableListOf<Streak>()
        var run = mutableListOf<Fix>()

        fun close() {
            if (run.size >= 2 && reach(run) >= minReachMeters) streaks += split(smooth(run), passesAt)
            run = mutableListOf()
        }

        for (fix in fixes) {
            if (fix.accuracy > maxAccuracyMeters) continue
            val last = run.lastOrNull()
            if (last == null) {
                run += fix
                continue
            }
            val dt = fix.time - last.time
            if (dt <= 0) continue
            val step = GeoMath.distanceMeters(last, fix)
            if (dt > maxGapMillis || step / (dt / 1000.0) > maxSpeedMps) {
                close()
                run += fix
                continue
            }
            if (step >= minStepMeters) run += fix
        }
        close()
        return streaks.sortedBy { it.passes }
    }

    fun bucket(passes: Int): Int = when {
        passes <= 1 -> 1
        passes <= 3 -> 3
        passes <= 8 -> 8
        else -> 20
    }

    private fun reach(run: List<Fix>): Double = run.maxOf { GeoMath.distanceMeters(run.first(), it) }

    private fun smooth(run: List<Fix>, radius: Int = 2): List<Fix> =
        run.indices.map { i ->
            if (i == 0 || i == run.lastIndex) return@map run[i]
            val window = run.subList(maxOf(0, i - radius), minOf(run.size, i + radius + 1))
            run[i].copy(lat = window.sumOf { it.lat } / window.size, lon = window.sumOf { it.lon } / window.size)
        }

    private fun split(run: List<Fix>, passesAt: (Double, Double) -> Int): List<Streak> {
        val pieces = mutableListOf<Streak>()
        var current = mutableListOf(run.first().toLatLon())
        var startedAt = run.first().time
        var currentBucket = -1
        for (i in 1 until run.size) {
            val a = run[i - 1]
            val b = run[i]
            val segmentBucket = bucket(passesAt((a.lat + b.lat) / 2, (a.lon + b.lon) / 2))
            if (currentBucket != -1 && segmentBucket != currentBucket) {
                pieces += Streak(current, currentBucket, startedAt, a.time)
                current = mutableListOf(a.toLatLon())
                startedAt = a.time
            }
            current += b.toLatLon()
            currentBucket = segmentBucket
        }
        pieces += Streak(current, currentBucket, startedAt, run.last().time)
        return pieces
    }

    private fun Fix.toLatLon() = LatLon(lat, lon)
}
