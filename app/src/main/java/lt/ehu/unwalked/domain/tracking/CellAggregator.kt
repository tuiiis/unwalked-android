package lt.ehu.unwalked.domain.tracking

import com.uber.h3core.H3Core

class CellAggregator(
    private val h3: H3Core,
    val resolution: Int,
    private val maxWalkingSpeedMps: Double = 3.0,
    private val maxAccuracyMeters: Float = 50f,
    private val maxGapMillis: Long = 5 * 60_000L,
    private val reentryCooldownMillis: Long = 30 * 60_000L,
    private val speedWindowMillis: Long = 30_000L,
) {
    private val window = ArrayDeque<Fix>()
    private var previous: Fix? = null
    private var previousCell: Long? = null
    private val lastPass = object : LinkedHashMap<Long, Long>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Long>?) = size > 4096
    }

    var vehicleSegments = 0
        private set
    var walkingSegments = 0
        private set

    fun accept(fix: Fix): List<CellPass> {
        if (fix.accuracy > maxAccuracyMeters) return emptyList()
        val cell = h3.latLngToCell(fix.lat, fix.lon, resolution)
        val prev = previous
        val prevCell = previousCell
        previous = fix
        previousCell = cell
        if (prev == null || prevCell == null) return listOfNotNull(pass(cell, fix.time)).also { window.addLast(fix) }
        val dt = fix.time - prev.time
        if (dt <= 0) return emptyList()
        if (dt > maxGapMillis) {
            window.clear()
            window.addLast(fix)
            return listOfNotNull(pass(cell, fix.time))
        }
        if (windowSpeed(fix) > maxWalkingSpeedMps) {
            vehicleSegments++
            return emptyList()
        }
        walkingSegments++
        if (cell == prevCell) return emptyList()
        return pathBetween(prevCell, cell).drop(1).mapNotNull { pass(it, fix.time) }
    }

    private fun windowSpeed(fix: Fix): Double {
        window.addLast(fix)
        while (window.size > 2 && fix.time - window[1].time >= speedWindowMillis) window.removeFirst()
        val reference = window.first()
        val seconds = (fix.time - reference.time) / 1000.0
        return if (seconds <= 0) 0.0 else GeoMath.distanceMeters(reference, fix) / seconds
    }

    private fun pathBetween(from: Long, to: Long): List<Long> =
        try {
            h3.gridPathCells(from, to)
        } catch (e: Exception) {
            listOf(from, to)
        }

    private fun pass(cell: Long, time: Long): CellPass? {
        val last = lastPass[cell]
        lastPass[cell] = time
        if (last != null && time - last < reentryCooldownMillis) return null
        return CellPass(cell, resolution, time)
    }
}
