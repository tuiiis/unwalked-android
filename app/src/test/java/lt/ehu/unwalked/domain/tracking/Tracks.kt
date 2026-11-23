package lt.ehu.unwalked.domain.tracking

import kotlin.math.cos
import kotlin.random.Random

object Tracks {
    const val LAT = 54.6872
    const val LON = 25.2797

    fun offset(lat: Double, lon: Double, northMeters: Double, eastMeters: Double): Pair<Double, Double> =
        lat + northMeters / 111_320.0 to lon + eastMeters / (111_320.0 * cos(Math.toRadians(lat)))

    fun line(
        start: Long,
        fromLat: Double,
        fromLon: Double,
        eastMeters: Double,
        speedMps: Double,
        stepMillis: Long = 5_000,
    ): List<Fix> {
        val seconds = eastMeters / speedMps
        val steps = (seconds * 1000 / stepMillis).toInt()
        return (0..steps).map { i ->
            val (lat, lon) = offset(fromLat, fromLon, 0.0, eastMeters * i / steps)
            Fix(start + i * stepMillis, lat, lon, 8f)
        }
    }

    fun noisyLine(start: Long, eastMeters: Double, speedMps: Double, noiseMeters: Double, seed: Int = 7): List<Fix> {
        val random = java.util.Random(seed.toLong())
        return line(start, LAT, LON, eastMeters, speedMps).map { fix ->
            val (lat, lon) = offset(fix.lat, fix.lon, random.nextGaussian() * noiseMeters, random.nextGaussian() * noiseMeters)
            fix.copy(lat = lat, lon = lon)
        }
    }

    fun jitter(start: Long, lat: Double, lon: Double, minutes: Int, radiusMeters: Double, seed: Int = 1): List<Fix> {
        val random = Random(seed)
        return (0 until minutes * 12).map { i ->
            val (jLat, jLon) = offset(
                lat,
                lon,
                (random.nextDouble() * 2 - 1) * radiusMeters,
                (random.nextDouble() * 2 - 1) * radiusMeters,
            )
            Fix(start + i * 5_000L, jLat, jLon, 15f)
        }
    }
}
