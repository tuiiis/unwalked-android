package lt.ehu.unwalked.domain.route

import lt.ehu.unwalked.domain.tracking.GeoMath

object Spurs {
    fun trim(path: List<LatLon>, closeMeters: Double = 12.0, maxSpurMeters: Double = 400.0): List<LatLon> {
        val out = mutableListOf<LatLon>()
        var i = 0
        while (i < path.size) {
            val start = path[i]
            out += start
            var jump = -1
            var walked = 0.0
            var j = i + 1
            while (j < path.size) {
                walked += distance(path[j - 1], path[j])
                if (walked > maxSpurMeters) break
                if (j > i + 1 && walked > closeMeters * 3 && distance(start, path[j]) < closeMeters) jump = j
                j++
            }
            i = if (jump > 0) jump + 1 else i + 1
            if (jump > 0 && jump == path.lastIndex) out += path[jump]
        }
        return out
    }

    fun length(path: List<LatLon>): Double = path.zipWithNext().sumOf { (a, b) -> distance(a, b) }

    private fun distance(a: LatLon, b: LatLon) = GeoMath.distanceMeters(a.lat, a.lon, b.lat, b.lon)
}
