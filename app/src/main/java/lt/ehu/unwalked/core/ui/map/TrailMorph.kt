package lt.ehu.unwalked.core.ui.map

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt
import lt.ehu.unwalked.domain.route.LatLon
import lt.ehu.unwalked.domain.tracking.Streak

class TrailMorph private constructor(
    private val origin: LatLon,
    private val metresPerLon: Double,
    private val passes: IntArray,
    private val onStreet: BooleanArray,
    private val levels: List<Array<DoubleArray>>,
) {
    fun shape(zoom: Double): List<GlowLine> {
        if (passes.isEmpty()) return emptyList()
        val position = ((LEVELS.first().zoom - zoom) / LEVEL_SPAN).coerceIn(0.0, (LEVELS.size - 1).toDouble())
        val lower = floor(position).toInt().coerceAtMost(LEVELS.size - 2)
        val t = ease(position - lower)
        val from = levels[lower]
        val to = levels[lower + 1]
        val stride = 2.0.pow(((LEVELS.first().zoom - zoom) - 1).coerceAtLeast(0.0)).toInt().coerceIn(1, MAX_STRIDE)
        return passes.indices.map { line ->
            val a = from[line]
            val b = to[line]
            val count = a.size / 2
            val indices = (0 until count step stride).toMutableList().apply { if (last() != count - 1) add(count - 1) }
            GlowLine(
                indices.map { i ->
                    val x = a[2 * i] + (b[2 * i] - a[2 * i]) * t
                    val y = a[2 * i + 1] + (b[2 * i + 1] - a[2 * i + 1]) * t
                    LatLon(origin.lat + y / METRES_PER_LAT, origin.lon + x / metresPerLon)
                },
                passes[line],
                onStreet[line],
            )
        }.sortedBy { it.passes }
    }

    private class Level(val zoom: Double, val radius: Double)

    companion object {
        private val LEVELS = listOf(
            Level(12.5, 0.0),
            Level(11.5, 45.0),
            Level(10.5, 120.0),
            Level(9.5, 300.0),
            Level(8.5, 700.0),
        )
        private const val LEVEL_SPAN = 1.0
        private const val STEP_METRES = 10.0
        private const val METRES_PER_LAT = 111_320.0
        private const val ITERATIONS = 3
        private const val MAX_STRIDE = 16

        val EMPTY = TrailMorph(LatLon(0.0, 0.0), 1.0, IntArray(0), BooleanArray(0), emptyList())

        fun build(streaks: List<Streak>, streets: List<GlowLine> = emptyList()): TrailMorph {
            val lines = (if (streets.isNotEmpty()) streets.map { it.copy(onStreet = true) } else streaks.map { GlowLine(it.points, it.passes, onStreet = false) })
                .filter { it.points.size >= 2 }
            val usable = lines.map { Streak(it.points, it.passes) }
            if (usable.isEmpty()) return EMPTY
            val origin = LatLon(usable.map { it.points.first().lat }.average(), usable.map { it.points.first().lon }.average())
            val metresPerLon = METRES_PER_LAT * cos(Math.toRadians(origin.lat))
            val base = usable.map { streak ->
                resample(streak.points.map { (it.lon - origin.lon) * metresPerLon to (it.lat - origin.lat) * METRES_PER_LAT })
            }.toTypedArray()
            val levels = mutableListOf(base)
            LEVELS.drop(1).forEach { level -> levels += gather(levels.last(), level.radius) }
            return TrailMorph(origin, metresPerLon, usable.map { it.passes }.toIntArray(), lines.map { it.onStreet }.toBooleanArray(), levels)
        }

        private fun gather(previous: Array<DoubleArray>, radius: Double): Array<DoubleArray> {
            var current = previous.map { it.copyOf() }.toTypedArray()
            repeat(ITERATIONS) {
                val grid = Grid(current, radius)
                current = current.map { line ->
                    val pulled = line.copyOf()
                    for (i in 0 until line.size / 2) {
                        val (mx, my) = grid.mean(line[2 * i], line[2 * i + 1])
                        pulled[2 * i] += (mx - line[2 * i]) * PULL
                        pulled[2 * i + 1] += (my - line[2 * i + 1]) * PULL
                    }
                    smooth(pulled, (radius / STEP_METRES).toInt().coerceAtLeast(1))
                }.toTypedArray()
            }
            return current
        }

        private const val PULL = 0.6

        private class Grid(lines: Array<DoubleArray>, private val size: Double) {
            private val cells = HashMap<Long, DoubleArray>()

            init {
                lines.forEach { line ->
                    for (i in 0 until line.size / 2) {
                        val bucket = cells.getOrPut(key(floor(line[2 * i] / size).toLong(), floor(line[2 * i + 1] / size).toLong())) { DoubleArray(3) }
                        bucket[0] += line[2 * i]
                        bucket[1] += line[2 * i + 1]
                        bucket[2] += 1.0
                    }
                }
            }

            fun mean(x: Double, y: Double): Pair<Double, Double> {
                val cx = floor(x / size).toLong()
                val cy = floor(y / size).toLong()
                var sx = 0.0
                var sy = 0.0
                var weight = 0.0
                for (dx in -1L..1L) for (dy in -1L..1L) {
                    val bucket = cells[key(cx + dx, cy + dy)] ?: continue
                    val px = bucket[0] / bucket[2]
                    val py = bucket[1] / bucket[2]
                    val d = sqrt((px - x) * (px - x) + (py - y) * (py - y)) / size
                    val w = bucket[2] * exp(-d * d)
                    sx += px * w
                    sy += py * w
                    weight += w
                }
                return if (weight == 0.0) x to y else sx / weight to sy / weight
            }

            private fun key(x: Long, y: Long) = (x shl 32) xor (y and 0xffffffffL)
        }

        private fun smooth(line: DoubleArray, window: Int): DoubleArray {
            val count = line.size / 2
            if (count < 3) return line
            val out = line.copyOf()
            for (i in 1 until count - 1) {
                val from = maxOf(0, i - window)
                val to = minOf(count - 1, i + window)
                var sx = 0.0
                var sy = 0.0
                for (j in from..to) {
                    sx += line[2 * j]
                    sy += line[2 * j + 1]
                }
                val n = to - from + 1
                out[2 * i] = sx / n
                out[2 * i + 1] = sy / n
            }
            return out
        }

        private fun resample(points: List<Pair<Double, Double>>): DoubleArray {
            val lengths = points.zipWithNext().map { (a, b) -> sqrt((b.first - a.first).let { it * it } + (b.second - a.second).let { it * it }) }
            val total = lengths.sum()
            val steps = ceil(total / STEP_METRES).toInt().coerceAtLeast(1)
            val out = DoubleArray((steps + 1) * 2)
            var segment = 0
            var walked = 0.0
            for (s in 0..steps) {
                val target = total * s / steps
                while (segment < lengths.size - 1 && walked + lengths[segment] < target) {
                    walked += lengths[segment]
                    segment++
                }
                val a = points[segment]
                val b = points[segment + 1]
                val t = if (lengths[segment] == 0.0) 0.0 else ((target - walked) / lengths[segment]).coerceIn(0.0, 1.0)
                out[2 * s] = a.first + (b.first - a.first) * t
                out[2 * s + 1] = a.second + (b.second - a.second) * t
            }
            return out
        }

        private fun ease(t: Double): Double = t * t * (3 - 2 * t)
    }
}
