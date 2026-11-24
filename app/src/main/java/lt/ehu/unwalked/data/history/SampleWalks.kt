package lt.ehu.unwalked.data.history

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import lt.ehu.unwalked.core.error.Outcome
import lt.ehu.unwalked.data.route.RouteRepository
import lt.ehu.unwalked.domain.route.LatLon
import lt.ehu.unwalked.domain.tracking.Fix
import lt.ehu.unwalked.domain.tracking.GeoMath

class SampleWalks(
    private val routes: RouteRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun generate(home: LatLon, nowMillis: Long, days: Int = DAYS, random: Random = Random(nowMillis)): List<Fix> {
        val core = loop(home, random, USUAL_MIN_METRES, USUAL_MAX_METRES, bearing = random.nextDouble(0.0, 360.0))
        val eras = (0 until ERAS).map { era ->
            (0 until ROUTES_PER_ERA).mapNotNull { index ->
                val bearing = era * 360.0 / ERAS + index * 360.0 / (ERAS * ROUTES_PER_ERA) + random.nextDouble(-20.0, 20.0)
                loop(home, random, USUAL_MIN_METRES, USUAL_MAX_METRES * (1 + era * ERA_GROWTH), bearing)
            }
        }
        val explorations = (0 until EXPLORATIONS).mapNotNull { loop(home, random, FAR_MIN_METRES, FAR_MAX_METRES, bearing = random.nextDouble(0.0, 360.0)) }
        if (core == null && eras.all { it.isEmpty() }) return emptyList()

        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val exploreOn = explorations.indices.associateBy { index -> days - ((index + 0.5) * days / explorations.size).toInt() }
        val fixes = mutableListOf<Fix>()
        for (offset in days downTo 1) {
            val era = ((days - offset) * ERAS / days).coerceAtMost(ERAS - 1)
            val usual = listOfNotNull(core) + eras[era]
            if (usual.isEmpty()) continue
            val day = today.minusDays(offset.toLong())
            if (random.nextDouble() < MORNING_CHANCE[era]) {
                fixes += walk(usual[random.nextInt(usual.size)], day.atTime(LocalTime.of(8, random.nextInt(0, 40))).atZone(zone).toInstant().toEpochMilli(), random)
            }
            val exploration = exploreOn[offset]?.let { explorations[it] }
            if (exploration != null || random.nextDouble() < EVENING_CHANCE[era]) {
                val path = exploration ?: usual[random.nextInt(usual.size)]
                fixes += walk(path, day.atTime(LocalTime.of(18, random.nextInt(0, 50))).atZone(zone).toInstant().toEpochMilli(), random)
            }
        }
        return fixes.filter { it.time < nowMillis }
    }

    private suspend fun loop(home: LatLon, random: Random, minMetres: Double, maxMetres: Double, bearing: Double): List<LatLon>? {
        val reach = random.nextDouble(minMetres, maxMetres)
        val turn = random.nextDouble(35.0, 70.0)
        val waypoints = listOf(home, offset(home, bearing - turn / 2, reach), offset(home, bearing + turn / 2, reach), home)
        return (routes.walking(waypoints) as? Outcome.Success)?.value?.path?.takeIf { it.size >= 2 }
    }

    private fun walk(path: List<LatLon>, start: Long, random: Random): List<Fix> {
        val speed = WALKING_MPS * random.nextDouble(0.85, 1.15)
        val fixes = mutableListOf<Fix>()
        var time = start
        var carried = 0.0
        path.zipWithNext().forEach { (a, b) ->
            val length = GeoMath.distanceMeters(a.lat, a.lon, b.lat, b.lon)
            var along = STEP_METRES - carried
            while (along <= length) {
                val t = along / length
                fixes += jitter(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t, time, random)
                time += (STEP_METRES / speed * 1000).toLong()
                along += STEP_METRES
            }
            carried = length - (along - STEP_METRES)
        }
        return fixes
    }

    private fun jitter(lat: Double, lon: Double, time: Long, random: Random): Fix {
        val north = random.nextDouble(-JITTER_METRES, JITTER_METRES)
        val east = random.nextDouble(-JITTER_METRES, JITTER_METRES)
        return Fix(time, lat + north / METRES_PER_LAT, lon + east / (METRES_PER_LAT * cos(Math.toRadians(lat))), ACCURACY)
    }

    private fun offset(from: LatLon, bearing: Double, metres: Double): LatLon {
        val radians = Math.toRadians(bearing)
        val north = metres * cos(radians)
        val east = metres * sin(radians)
        return LatLon(from.lat + north / METRES_PER_LAT, from.lon + east / (METRES_PER_LAT * cos(Math.toRadians(from.lat))))
    }

    companion object {
        const val DAYS = 182
        private const val ERAS = 3
        private const val ROUTES_PER_ERA = 2
        private const val ERA_GROWTH = 0.4
        private val MORNING_CHANCE = doubleArrayOf(0.3, 0.5, 0.8)
        private val EVENING_CHANCE = doubleArrayOf(0.2, 0.35, 0.6)
        private const val EXPLORATIONS = 12
        private const val USUAL_MIN_METRES = 350.0
        private const val USUAL_MAX_METRES = 800.0
        private const val FAR_MIN_METRES = 700.0
        private const val FAR_MAX_METRES = 1_500.0
        private const val WALKING_MPS = 1.35
        private const val STEP_METRES = 8.0
        private const val JITTER_METRES = 3.0
        private const val ACCURACY = 8f
        private const val METRES_PER_LAT = 111_320.0
    }
}
