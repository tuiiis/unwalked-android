package lt.ehu.unwalked.domain.tracking

data class Gap(val start: Long, val end: Long) {
    val durationMillis: Long get() = end - start
}

object TrackStats {
    fun gaps(times: List<Long>, thresholdMillis: Long): List<Gap> =
        times.zipWithNext().filter { (a, b) -> b - a > thresholdMillis }.map { (a, b) -> Gap(a, b) }

    fun longestGapMillis(times: List<Long>): Long =
        times.zipWithNext().maxOfOrNull { (a, b) -> b - a } ?: 0L

    fun lengthMeters(fixes: List<Fix>): Double =
        fixes.zipWithNext().sumOf { (a, b) -> GeoMath.distanceMeters(a, b) }
}
