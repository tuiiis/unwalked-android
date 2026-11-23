package lt.ehu.unwalked.domain.route

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class LatLon(val lat: Double, val lon: Double)

enum class RouteShape(val label: String) { Loop("Loop"), OneWay("Different end point") }

enum class RoutePreference(val label: String) { NewCells("New streets"), Places("Places") }

data class RoutePlan(val waypoints: List<LatLon>, val bearingDegrees: Double)

class RoutePlanner(private val detourFactor: Double = 1.25) {
    fun candidates(start: LatLon, kilometres: Double, shape: RouteShape, bearings: List<Double> = (0 until 8).map { it * 45.0 }): List<RoutePlan> =
        bearings.map { bearing -> plan(start, kilometres, shape, bearing) }

    fun plan(start: LatLon, kilometres: Double, shape: RouteShape, bearingDegrees: Double): RoutePlan {
        val metres = kilometres * 1000 / detourFactor
        return when (shape) {
            RouteShape.Loop -> {
                val radius = metres / (2 * PI)
                val centre = destination(start, bearingDegrees, radius)
                val back = bearingDegrees + 180
                val points = listOf(90.0, 180.0, 270.0).map { offset -> destination(centre, back + offset, radius) }
                RoutePlan(listOf(start) + points + start, bearingDegrees)
            }
            RouteShape.OneWay -> {
                val end = destination(start, bearingDegrees, metres * 0.8)
                val middle = destination(destination(start, bearingDegrees, metres * 0.4), bearingDegrees + 90, metres * 0.2)
                RoutePlan(listOf(start, middle, end), bearingDegrees)
            }
        }
    }

    fun score(plan: RoutePlan, isNew: (LatLon) -> Boolean, samplesPerLeg: Int = 12): Double {
        val samples = plan.waypoints.zipWithNext().flatMap { (a, b) ->
            (0 until samplesPerLeg).map { step ->
                val t = step.toDouble() / samplesPerLeg
                LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
            }
        }
        if (samples.isEmpty()) return 0.0
        return samples.count(isNew).toDouble() / samples.size
    }

    companion object {
        private const val EARTH_RADIUS = 6_371_008.8

        fun destination(from: LatLon, bearingDegrees: Double, metres: Double): LatLon {
            val angular = metres / EARTH_RADIUS
            val bearing = Math.toRadians(bearingDegrees)
            val lat1 = Math.toRadians(from.lat)
            val lon1 = Math.toRadians(from.lon)
            val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing))
            val lon2 = lon1 + atan2(sin(bearing) * sin(angular) * cos(lat1), cos(angular) - sin(lat1) * sin(lat2))
            return LatLon(Math.toDegrees(lat2), Math.toDegrees(lon2))
        }
    }
}
