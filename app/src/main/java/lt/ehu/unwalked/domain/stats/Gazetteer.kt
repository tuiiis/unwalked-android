package lt.ehu.unwalked.domain.stats

import java.util.Locale
import kotlin.math.floor
import lt.ehu.unwalked.domain.tracking.GeoMath

data class City(val name: String, val country: String, val lat: Double, val lon: Double, val population: Int) {
    val countryName: String get() = Locale("", country).getDisplayCountry(Locale.ENGLISH).ifBlank { country }
}

class Gazetteer(cities: List<City>) {
    private val cells = cities.groupBy { key(it.lat, it.lon) }

    fun nearest(lat: Double, lon: Double, maxKm: Double = MAX_KM): City? {
        val row = floor(lat).toInt()
        val column = floor(lon).toInt()
        var best: City? = null
        var bestKm = maxKm
        for (dy in -1..1) {
            for (dx in -1..1) {
                cells[(row + dy) * 1000 + (column + dx)]?.forEach { city ->
                    val km = GeoMath.distanceMeters(lat, lon, city.lat, city.lon) / 1000
                    if (km <= bestKm) {
                        bestKm = km
                        best = city
                    }
                }
            }
        }
        return best
    }

    private fun key(lat: Double, lon: Double) = floor(lat).toInt() * 1000 + floor(lon).toInt()

    companion object {
        private const val MAX_KM = 30.0

        fun parse(lines: Sequence<String>): Gazetteer = Gazetteer(
            lines.mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size < 5) null else City(parts[0], parts[1], parts[2].toDouble(), parts[3].toDouble(), parts[4].toIntOrNull() ?: 0)
            }.toList(),
        )
    }
}
