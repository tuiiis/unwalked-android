package lt.ehu.unwalked.domain.stats

import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import lt.ehu.unwalked.domain.tracking.GeoMath

data class CellVisit(val cell: Long, val areaCell: Long, val firstSeen: Long, val lastSeen: Long, val passes: Int = 1)

data class TimedPoint(val time: Long, val lat: Double, val lon: Double, val accuracy: Float = 10f)

data class StayVisit(val start: Long, val placeKey: Long)

data class Walk(val start: Long, val kilometres: Double, val end: Long = start) {
    val hours: Double get() = (end - start) / 3_600_000.0
}

data class PeriodStats(
    val newCells: Int,
    val visitedCells: Int,
    val newPlaces: Int,
    val walkedKm: Double,
    val exploredPercent: Int,
    val exploredDelta: Int?,
    val longestWalk: Walk?,
    val totalCells: Int,
    val facts: List<FunFact> = emptyList(),
    val travel: Travel? = null,
) {
    val isEmpty: Boolean get() = visitedCells == 0 && walkedKm == 0.0
}

class StatsCalculator(private val childrenPerArea: Int, private val zone: ZoneId = ZoneId.systemDefault(), private val gazetteer: () -> Gazetteer? = { null }) {
    fun calculate(
        range: PeriodRange,
        cells: List<CellVisit>,
        points: List<TimedPoint>,
        stays: List<StayVisit>,
    ): PeriodStats {
        val inRange = { time: Long -> time >= range.start && time <= range.end }
        val visited = cells.filter { inRange(it.lastSeen) || inRange(it.firstSeen) }
        val newCells = cells.count { inRange(it.firstSeen) }
        val walks = walks(points.filter { inRange(it.time) })
        val firstStayByPlace = stays.groupBy { it.placeKey }.mapValues { (_, visits) -> visits.minOf { it.start } }
        val newPlaces = firstStayByPlace.values.count(inRange)
        val explored = explored(cells.filter { it.firstSeen <= range.end })
        val previousExplored = range.previousStart?.let { explored(cells.filter { it.firstSeen < range.start }) }
        return PeriodStats(
            newCells = newCells,
            visitedCells = visited.size,
            newPlaces = newPlaces,
            walkedKm = walks.sumOf { it.kilometres },
            exploredPercent = explored,
            exploredDelta = previousExplored?.let { explored - it },
            longestWalk = walks.maxByOrNull { it.kilometres }?.takeIf { it.kilometres >= MIN_WALK_KM },
            totalCells = cells.size,
            facts = FunFacts.of(walks, cells.filter { inRange(it.firstSeen) }, zone),
            travel = gazetteer()?.let { TravelStats.of(points.filter { inRange(it.time) }, it, zone) },
        )
    }

    fun explored(cells: List<CellVisit>): Int {
        if (cells.isEmpty()) return 0
        val areas = cells.map { it.areaCell }.toSet().size
        return (100.0 * cells.size / (areas * childrenPerArea)).roundToInt().coerceIn(0, 100)
    }

    fun walks(points: List<TimedPoint>): List<Walk> {
        val usable = points.filter { it.accuracy <= MAX_ACCURACY_METERS }
        if (usable.size < 2) return emptyList()
        val result = mutableListOf<Walk>()
        var first = usable.first()
        var anchor = first
        var metres = 0.0
        var reach = 0.0

        fun close() {
            if (metres > 0 && reach >= MIN_REACH_METERS) result += Walk(first.time, metres / 1000.0, anchor.time)
        }

        usable.drop(1).forEach { point ->
            val seconds = (point.time - anchor.time) / 1000.0
            val distance = GeoMath.distanceMeters(anchor.lat, anchor.lon, point.lat, point.lon)
            val walking = seconds in 0.0..MAX_GAP_SECONDS && (seconds == 0.0 || distance / seconds <= MAX_WALKING_SPEED)
            if (!walking) {
                close()
                first = point
                anchor = point
                metres = 0.0
                reach = 0.0
            } else if (distance >= MIN_STEP_METERS) {
                metres += distance
                anchor = point
                reach = maxOf(reach, GeoMath.distanceMeters(first.lat, first.lon, point.lat, point.lon))
            }
        }
        close()
        return result
    }

    companion object {
        private const val MAX_GAP_SECONDS = 300.0
        private const val MAX_WALKING_SPEED = 3.0
        private const val MIN_WALK_KM = 0.3
        private const val MAX_ACCURACY_METERS = 35f
        private const val MIN_STEP_METERS = 15.0
        private const val MIN_REACH_METERS = 200.0

        fun dayLabel(millis: Long, zone: ZoneId): String {
            val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
            return "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}"
        }
    }
}
