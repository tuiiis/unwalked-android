package lt.ehu.unwalked.domain.tracking

class StayDetector(
    private val radiusMeters: Double = 60.0,
    private val minDurationMillis: Long = 5 * 60_000L,
    private val maxAccuracyMeters: Float = 60f,
) {
    private val cluster = mutableListOf<Fix>()
    private var sumLat = 0.0
    private var sumLon = 0.0

    fun accept(fix: Fix): Stay? {
        if (fix.accuracy > maxAccuracyMeters) return null
        if (cluster.isEmpty()) {
            add(fix)
            return null
        }
        if (GeoMath.distanceMeters(centroidLat(), centroidLon(), fix.lat, fix.lon) <= radiusMeters) {
            add(fix)
            return null
        }
        val closed = currentStay()
        reset()
        add(fix)
        return closed
    }

    fun flush(): Stay? {
        val closed = currentStay()
        reset()
        return closed
    }

    fun currentStay(): Stay? {
        if (cluster.isEmpty()) return null
        val start = cluster.first().time
        val end = cluster.last().time
        if (end - start < minDurationMillis) return null
        return Stay(start, end, centroidLat(), centroidLon(), cluster.size)
    }

    private fun add(fix: Fix) {
        cluster += fix
        sumLat += fix.lat
        sumLon += fix.lon
    }

    private fun reset() {
        cluster.clear()
        sumLat = 0.0
        sumLon = 0.0
    }

    private fun centroidLat() = sumLat / cluster.size
    private fun centroidLon() = sumLon / cluster.size
}
