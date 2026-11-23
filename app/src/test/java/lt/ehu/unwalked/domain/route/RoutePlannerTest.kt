package lt.ehu.unwalked.domain.route

import kotlin.math.PI
import lt.ehu.unwalked.domain.tracking.GeoMath
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutePlannerTest {
    private val planner = RoutePlanner(detourFactor = 1.0)
    private val start = LatLon(54.6872, 25.2797)
    private fun distance(a: LatLon, b: LatLon) = GeoMath.distanceMeters(a.lat, a.lon, b.lat, b.lon)

    @Test
    fun loopStartsAndEndsAtTheStart() {
        val plan = planner.plan(start, 4.0, RouteShape.Loop, 90.0)
        assertEquals(start, plan.waypoints.first())
        assertEquals(start, plan.waypoints.last())
        assertEquals(5, plan.waypoints.size)
    }

    @Test
    fun loopWaypointsLieOnACircleOfTheRightSize() {
        val plan = planner.plan(start, 4.0, RouteShape.Loop, 0.0)
        val radius = 4000 / (2 * PI)
        val centre = RoutePlanner.destination(start, 0.0, radius)
        plan.waypoints.forEach { assertEquals(radius, distance(centre, it), 1.0) }
    }

    @Test
    fun oneWayEndsAwayFromStart() {
        val plan = planner.plan(start, 5.0, RouteShape.OneWay, 45.0)
        assertEquals(4000.0, distance(start, plan.waypoints.last()), 5.0)
    }

    @Test
    fun scoreIsTheShareOfNewSamples() {
        val plan = planner.plan(start, 4.0, RouteShape.Loop, 0.0)
        assertEquals(1.0, planner.score(plan, { true }), 0.0)
        assertEquals(0.0, planner.score(plan, { false }), 0.0)
        assertEquals(0.5, planner.score(plan, { it.lon > start.lon }), 0.1)
    }

    @Test
    fun eightBearingsByDefault() {
        assertEquals(8, planner.candidates(start, 3.0, RouteShape.Loop).map { it.bearingDegrees }.distinct().size)
    }
}
