package lt.ehu.unwalked.core.ui.map

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt
import lt.ehu.unwalked.domain.route.LatLon

class StreetGraph(streets: List<List<LatLon>>) {
    private val metresPerLon = METRES_PER_LAT * cos(Math.toRadians(streets.firstOrNull()?.firstOrNull()?.lat ?: 0.0))
    private var xs = DoubleArray(INITIAL)
    private var ys = DoubleArray(INITIAL)
    private var count = 0
    private val cells = HashMap<Long, IntArray>()
    private val offsets: IntArray
    private val targets: IntArray
    private val lengths: DoubleArray
    private val cost: DoubleArray
    private val parent: IntArray
    private val touched = IntArrayList()

    init {
        val from = IntArrayList()
        val to = IntArrayList()
        streets.filter { it.size >= 2 }.forEach { line ->
            var previous = -1
            split(line).forEach { point ->
                val current = node(point.lon * metresPerLon, point.lat * METRES_PER_LAT)
                if (previous >= 0 && previous != current) {
                    from.add(previous)
                    to.add(current)
                }
                previous = current
            }
        }
        val degree = IntArray(count)
        for (i in 0 until from.size) {
            degree[from[i]]++
            degree[to[i]]++
        }
        val linked = IntArray(count) { -1 }
        for (i in 0 until from.size) {
            if (degree[from[i]] == 1) linked[from[i]] = to[i]
            if (degree[to[i]] == 1) linked[to[i]] = from[i]
        }
        for (end in 0 until count) {
            if (degree[end] != 1) continue
            val other = nearest(xs[end], ys[end], JUNCTION_METRES, exclude = end, skip = linked[end]) ?: continue
            from.add(end)
            to.add(other)
            degree[end]++
            degree[other]++
        }
        offsets = IntArray(count + 1)
        for (i in 0 until count) offsets[i + 1] = offsets[i] + degree[i]
        targets = IntArray(offsets[count])
        lengths = DoubleArray(offsets[count])
        val fill = offsets.copyOf(count)
        for (i in 0 until from.size) {
            val a = from[i]
            val b = to[i]
            val length = distance(a, b)
            targets[fill[a]] = b
            lengths[fill[a]++] = length
            targets[fill[b]] = a
            lengths[fill[b]++] = length
        }
        cost = DoubleArray(count) { Double.MAX_VALUE }
        parent = IntArray(count) { -1 }
    }

    fun route(from: LatLon, to: LatLon, maxDetour: Double = MAX_DETOUR): List<LatLon>? {
        val fx = from.lon * metresPerLon
        val fy = from.lat * METRES_PER_LAT
        val tx = to.lon * metresPerLon
        val ty = to.lat * METRES_PER_LAT
        val start = nearest(fx, fy, SNAP_METRES, connected = true) ?: return null
        val goal = nearest(tx, ty, SNAP_METRES, connected = true) ?: return null
        if (start == goal) return null
        val budget = sqrt((tx - fx) * (tx - fx) + (ty - fy) * (ty - fy)) * maxDetour + SLACK_METRES
        val heap = Heap()
        touched.clear()
        visit(start, 0.0, -1)
        heap.push(start, distance(start, goal))
        var found = false
        while (heap.isNotEmpty()) {
            val current = heap.pop()
            if (current == goal) {
                found = true
                break
            }
            val spent = cost[current]
            for (edge in offsets[current] until offsets[current + 1]) {
                val next = targets[edge]
                val total = spent + lengths[edge]
                if (total > budget || total >= cost[next]) continue
                visit(next, total, current)
                heap.push(next, total + distance(next, goal))
            }
        }
        val path = if (found) {
            val nodes = IntArrayList()
            var at = goal
            while (at >= 0) {
                nodes.add(at)
                at = parent[at]
            }
            (nodes.size - 1 downTo 0).map { LatLon(ys[nodes[it]] / METRES_PER_LAT, xs[nodes[it]] / metresPerLon) }
        } else {
            null
        }
        for (i in 0 until touched.size) {
            cost[touched[i]] = Double.MAX_VALUE
            parent[touched[i]] = -1
        }
        return path
    }

    fun onStreet(point: LatLon, reach: Double): Boolean = nearest(point.lon * metresPerLon, point.lat * METRES_PER_LAT, reach) != null

    private fun visit(node: Int, total: Double, from: Int) {
        if (cost[node] == Double.MAX_VALUE) touched.add(node)
        cost[node] = total
        parent[node] = from
    }

    private fun split(line: List<LatLon>): List<LatLon> {
        val out = mutableListOf(line.first())
        line.zipWithNext().forEach { (a, b) ->
            val dx = (b.lon - a.lon) * metresPerLon
            val dy = (b.lat - a.lat) * METRES_PER_LAT
            val steps = ceil(sqrt(dx * dx + dy * dy) / STEP_METRES).toInt().coerceAtLeast(1)
            for (i in 1..steps) out += LatLon(a.lat + (b.lat - a.lat) * i / steps, a.lon + (b.lon - a.lon) * i / steps)
        }
        return out
    }

    private fun node(x: Double, y: Double): Int {
        val cx = floor(x / MERGE_METRES).toLong()
        val cy = floor(y / MERGE_METRES).toLong()
        for (dx in -1L..1L) for (dy in -1L..1L) {
            cells[pack(cx + dx, cy + dy)]?.forEach { candidate ->
                val ex = xs[candidate] - x
                val ey = ys[candidate] - y
                if (ex * ex + ey * ey <= MERGE_METRES * MERGE_METRES) return candidate
            }
        }
        if (count == xs.size) {
            xs = xs.copyOf(count * 2)
            ys = ys.copyOf(count * 2)
        }
        xs[count] = x
        ys[count] = y
        val key = pack(cx, cy)
        cells[key] = (cells[key] ?: IntArray(0)) + count
        return count++
    }

    private fun nearest(x: Double, y: Double, reach: Double, exclude: Int = -1, skip: Int = -1, connected: Boolean = false): Int? {
        val steps = (reach / MERGE_METRES).toLong() + 1
        val cx = floor(x / MERGE_METRES).toLong()
        val cy = floor(y / MERGE_METRES).toLong()
        var best = -1
        var bestDistance = reach * reach
        for (dx in -steps..steps) for (dy in -steps..steps) {
            cells[pack(cx + dx, cy + dy)]?.forEach { candidate ->
                if (candidate == exclude || candidate == skip) return@forEach
                if (connected && offsets[candidate] == offsets[candidate + 1]) return@forEach
                val ex = xs[candidate] - x
                val ey = ys[candidate] - y
                val d = ex * ex + ey * ey
                if (d <= bestDistance) {
                    bestDistance = d
                    best = candidate
                }
            }
        }
        return best.takeIf { it >= 0 }
    }

    private fun distance(a: Int, b: Int): Double {
        val ex = xs[a] - xs[b]
        val ey = ys[a] - ys[b]
        return sqrt(ex * ex + ey * ey)
    }

    private fun pack(x: Long, y: Long) = (x shl 32) xor (y and 0xffffffffL)

    private class IntArrayList {
        private var items = IntArray(INITIAL)
        var size = 0
            private set

        fun add(value: Int) {
            if (size == items.size) items = items.copyOf(size * 2)
            items[size++] = value
        }

        operator fun get(index: Int) = items[index]

        fun clear() {
            size = 0
        }
    }

    private class Heap {
        private var nodes = IntArray(INITIAL)
        private var keys = DoubleArray(INITIAL)
        private var size = 0

        fun isNotEmpty() = size > 0

        fun push(node: Int, key: Double) {
            if (size == nodes.size) {
                nodes = nodes.copyOf(size * 2)
                keys = keys.copyOf(size * 2)
            }
            var at = size++
            while (at > 0) {
                val up = (at - 1) / 2
                if (keys[up] <= key) break
                nodes[at] = nodes[up]
                keys[at] = keys[up]
                at = up
            }
            nodes[at] = node
            keys[at] = key
        }

        fun pop(): Int {
            val top = nodes[0]
            val lastNode = nodes[--size]
            val lastKey = keys[size]
            var at = 0
            while (true) {
                var child = 2 * at + 1
                if (child >= size) break
                if (child + 1 < size && keys[child + 1] < keys[child]) child++
                if (keys[child] >= lastKey) break
                nodes[at] = nodes[child]
                keys[at] = keys[child]
                at = child
            }
            nodes[at] = lastNode
            keys[at] = lastKey
            return top
        }
    }

    companion object {
        private const val METRES_PER_LAT = 111_320.0
        private const val MERGE_METRES = 3.0
        private const val STEP_METRES = 15.0
        private const val JUNCTION_METRES = 8.0
        private const val SNAP_METRES = 30.0
        private const val MAX_DETOUR = 1.5
        private const val SLACK_METRES = 60.0
        private const val INITIAL = 64
    }
}
