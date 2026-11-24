package lt.ehu.unwalked.core.ui.map

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt
import lt.ehu.unwalked.domain.route.LatLon
import lt.ehu.unwalked.domain.tracking.GeoMath
import lt.ehu.unwalked.domain.tracking.Streak

data class GlowLine(val points: List<LatLon>, val passes: Int, val onStreet: Boolean = true)

object StreetGlow {
    fun lit(streets: List<List<LatLon>>, passesAt: (Double, Double) -> Int, stepMeters: Double = 8.0): List<GlowLine> =
        streets.flatMap { litPieces(densify(it, stepMeters), passesAt) }.sortedBy { it.passes }

    fun walked(streets: List<List<LatLon>>, streaks: List<Streak>, metres: Double = TRACK_NEAR_METRES, stepMeters: Double = 8.0): List<GlowLine> {
        val walks = streaks.filter { it.points.size >= 2 }
        val nearby = nearTracks(streets, walks)
        val graph = StreetGraph(nearby)
        val tracks = (walks + stitch(walks, graph)).map { bridgeGaps(it, graph) }
        if (tracks.isEmpty()) return emptyList()
        val samples = nearby.distinct().map { densify(it, stepMeters) }.filter { it.size >= 2 }
        val metresPerLon = METRES_PER_LAT * cos(Math.toRadians(tracks.first().points.first().lat))
        fun cell(point: LatLon) = floor(point.lon * metresPerLon / metres).toLong() to floor(point.lat * METRES_PER_LAT / metres).toLong()
        fun key(x: Long, y: Long) = (x shl 32) xor (y and 0xffffffffL)
        val index = HashMap<Long, MutableList<Int>>()
        val midpoints = ArrayList<LatLon>()
        val directions = ArrayList<Pair<Double, Double>>()
        val owners = ArrayList<Long>()
        samples.forEachIndexed { street, line ->
            for (segment in 0 until line.size - 1) {
                val a = line[segment]
                val b = line[segment + 1]
                val mid = LatLon((a.lat + b.lat) / 2, (a.lon + b.lon) / 2)
                cell(mid).let { (x, y) -> index.getOrPut(key(x, y)) { mutableListOf() } += midpoints.size }
                midpoints += mid
                directions += unit((b.lon - a.lon) * metresPerLon, (b.lat - a.lat) * METRES_PER_LAT)
                owners += (street.toLong() shl 32) or segment.toLong()
            }
        }
        val buckets = samples.map { IntArray(it.size - 1) }
        val loose = mutableListOf<Pair<List<LatLon>, Int>>()
        tracks.forEach { streak ->
            val passes = bucket(streak.passes)
            val points = densify(streak.points, stepMeters)
            val chords = chordSamples(streak.points, stepMeters)
            val snapped = BooleanArray(points.size)
            var previous = -1
            points.forEachIndexed { p, point ->
                if (chords[p]) {
                    previous = -1
                    return@forEachIndexed
                }
                val (hx, hy) = heading(points, p, metresPerLon)
                val (x, y) = cell(point)
                var nearest = -1
                var best = metres * metres
                var stay = -1
                var stayBest = Double.MAX_VALUE
                for (dx in -1L..1L) {
                    for (dy in -1L..1L) {
                        index[key(x + dx, y + dy)]?.forEach { i ->
                            val (sx, sy) = directions[i]
                            if ((hx != 0.0 || hy != 0.0) && abs(hx * sx + hy * sy) < PARALLEL_COS) return@forEach
                            val ex = (midpoints[i].lon - point.lon) * metresPerLon
                            val ey = (midpoints[i].lat - point.lat) * METRES_PER_LAT
                            val distance = ex * ex + ey * ey
                            if (distance <= best) {
                                best = distance
                                nearest = i
                            }
                            if ((owners[i] ushr 32).toInt() == previous && distance < stayBest) {
                                stayBest = distance
                                stay = i
                            }
                        }
                    }
                }
                val chosen = if (stay >= 0 && nearest >= 0 && sqrt(stayBest) <= sqrt(best) + STICKY_METRES) stay else nearest
                if (chosen >= 0) {
                    val owner = owners[chosen]
                    previous = (owner ushr 32).toInt()
                    val line = buckets[previous]
                    val segment = (owner and 0xffffffffL).toInt()
                    line[segment] = maxOf(line[segment], passes)
                    snapped[p] = true
                } else {
                    previous = -1
                }
            }
            val free = BooleanArray(points.size) { !snapped[it] && !chords[it] }
            var start = 0
            while (start < points.size) {
                var end = start
                while (end + 1 < points.size && free[end + 1] == free[start]) end++
                if (free[start] && end > start) loose += smoothTrack(points.subList(start, end + 1)) to passes
                start = end + 1
            }
        }
        repeat(CORNER_PASSES) {
            fun litNear(street: Int, point: LatLon): Boolean {
                val (x, y) = cell(point)
                for (dx in -1L..1L) {
                    for (dy in -1L..1L) {
                        index[key(x + dx, y + dy)]?.forEach { i ->
                            val other = (owners[i] ushr 32).toInt()
                            if (other == street || buckets[other][(owners[i] and 0xffffffffL).toInt()] == 0) return@forEach
                            val ex = (midpoints[i].lon - point.lon) * metresPerLon
                            val ey = (midpoints[i].lat - point.lat) * METRES_PER_LAT
                            if (ex * ex + ey * ey <= CORNER_METRES * CORNER_METRES) return true
                        }
                    }
                }
                return false
            }
            samples.indices.forEach { street ->
                val line = buckets[street]
                runs(line).filter { (from, _) -> line[from] > 0 }.forEach { (from, to) ->
                    for (step in 1..CORNER_SEGMENTS) {
                        val segment = to + step
                        if (segment >= line.size || line[segment] > 0) break
                        if (litNear(street, samples[street][segment + 1])) {
                            line.fill(line[to], to + 1, segment + 1)
                            break
                        }
                    }
                    for (step in 1..CORNER_SEGMENTS) {
                        val segment = from - step
                        if (segment < 0 || line[segment] > 0) break
                        if (litNear(street, samples[street][segment])) {
                            line.fill(line[from], segment, from)
                            break
                        }
                    }
                }
            }
        }
        val lit = samples.indices.map { pieces(samples[it], buckets[it].copyOf(), keepLonely = true) }
        val joints = Joints(lit.flatMapIndexed { street, pieces -> pieces.map { street to it.points } }, metresPerLon)
        val bridged = samples.indices.map { street ->
            val line = samples[street]
            val touched = buckets[street].maxOrNull() ?: 0
            val connector = touched > 0 && length(line) < CONNECTOR_METRES &&
                joints.near(street, line.first()) && joints.near(street, line.last())
            if (connector) listOf(GlowLine(line, maxOf(touched, lit[street].maxOfOrNull { it.passes } ?: 0))) else lit[street]
        }.flatten()
        val onStreets = prune(bridged, metresPerLon)
        val candidates = onStreets.sortedByDescending { length(it.points) }.map { Triple(it.points, it.passes, 0.0) } +
            loose.sortedByDescending { length(it.first) }.map { Triple(it.first, it.second, OFF_STREET_MIN_METRES) }
        val merged = magnet(candidates, metresPerLon)
        val connected = prune(connect(merged.filter { it.points.size >= 2 }, metresPerLon), metresPerLon)
        return connected.map { it.copy(points = smoothLine(densify(it.points, stepMeters))) }.sortedBy { it.passes }
    }

    private fun magnet(candidates: List<Triple<List<LatLon>, Int, Double>>, metresPerLon: Double): List<GlowLine> {
        val anchors = Anchors(metresPerLon)
        val kept = mutableListOf<GlowLine>()
        candidates.forEach { (points, passes, minimum) ->
            val owners = IntArray(points.size) { p -> anchors.parallelNear(points, p) ?: -1 }
            var start = 0
            while (start < points.size) {
                var end = start
                while (end + 1 < points.size && (owners[end + 1] >= 0) == (owners[start] >= 0)) end++
                if (owners[start] >= 0) {
                    (start..end).map { owners[it] }.distinct().forEach { owner -> kept[owner] = kept[owner].copy(passes = maxOf(kept[owner].passes, passes)) }
                } else {
                    val piece = points.subList(start, end + 1)
                    if (piece.size >= 2 && length(piece) >= minimum && (minimum == 0.0 || reach(piece) >= OFF_STREET_REACH_METRES)) {
                        anchors.add(piece, owner = kept.size)
                        kept += GlowLine(piece, passes)
                    }
                }
                start = end + 1
            }
        }
        return kept
    }

    private fun connect(lines: List<GlowLine>, metresPerLon: Double): List<GlowLine> {
        fun gap(a: LatLon, b: LatLon): Double {
            val ex = (a.lon - b.lon) * metresPerLon
            val ey = (a.lat - b.lat) * METRES_PER_LAT
            return sqrt(ex * ex + ey * ey)
        }
        val ends = lines.indices.flatMap { listOf(it to true, it to false) }
        fun point(end: Pair<Int, Boolean>) = lines[end.first].points.let { if (end.second) it.first() else it.last() }
        val node = HashMap<Pair<Int, Boolean>, LatLon>()
        fun cell(point: LatLon) = floor(point.lon * metresPerLon / NODE_METRES).toLong() to floor(point.lat * METRES_PER_LAT / NODE_METRES).toLong()
        val grid = HashMap<Long, MutableList<Pair<Int, Boolean>>>()
        ends.forEach { end -> cell(point(end)).let { (x, y) -> grid.getOrPut((x shl 32) xor (y and 0xffffffffL)) { mutableListOf() } += end } }
        ends.forEach { end ->
            if (end in node) return@forEach
            val (x, y) = cell(point(end))
            val near = (-1L..1L).flatMap { dx -> (-1L..1L).flatMap { dy -> grid[((x + dx) shl 32) xor ((y + dy) and 0xffffffffL)].orEmpty() } }
            val cluster = near.filter { it !in node && gap(point(it), point(end)) <= NODE_METRES }
            if (cluster.size < 2) return@forEach
            val centre = LatLon(cluster.sumOf { point(it).lat } / cluster.size, cluster.sumOf { point(it).lon } / cluster.size)
            cluster.forEach { node[it] = centre }
        }
        val anchors = Anchors(metresPerLon)
        lines.forEachIndexed { owner, line -> anchors.add(line.points, owner) }
        return lines.mapIndexed { owner, line ->
            val points = trim(trim(line.points, node[owner to true], metresPerLon).reversed(), node[owner to false], metresPerLon).reversed()
            val head = node[owner to true]?.let { listOf(it) }
                ?: listOfNotNull(anchors.nearestOther(points.first(), owner, CONNECT_METRES)?.takeIf { ahead(points[1], points.first(), it, metresPerLon) }) + points.first()
            val tail = node[owner to false]?.let { listOf(it) }
                ?: listOf(points.last()) + listOfNotNull(anchors.nearestOther(points.last(), owner, CONNECT_METRES)?.takeIf { ahead(points[points.size - 2], points.last(), it, metresPerLon) })
            line.copy(points = head + points.subList(1, points.size - 1) + tail)
        }
    }

    private fun smoothLine(points: List<LatLon>): List<LatLon> {
        var current = points
        repeat(SMOOTH_PASSES) { current = smoothTrack(current) }
        return current
    }

    private class Anchors(private val metresPerLon: Double) {
        private val cells = HashMap<Long, MutableList<Triple<Int, LatLon, Pair<Double, Double>>>>()

        fun add(points: List<LatLon>, owner: Int) {
            points.forEachIndexed { p, point ->
                val direction = heading(points, p, metresPerLon)
                cells.getOrPut(key(point, MAGNET_METRES)) { mutableListOf() } += Triple(owner, point, direction)
            }
        }

        fun parallelNear(points: List<LatLon>, p: Int): Int? {
            val point = points[p]
            val (hx, hy) = heading(points, p, metresPerLon)
            return nearby(point, MAGNET_METRES).firstOrNull { (_, near, direction) ->
                distance(near, point) <= MAGNET_METRES && ((hx == 0.0 && hy == 0.0) || abs(hx * direction.first + hy * direction.second) >= PARALLEL_COS)
            }?.first
        }

        fun nearestOther(point: LatLon, owner: Int, reach: Double): LatLon? {
            val nearest = nearby(point, MAGNET_METRES).filter { it.first != owner }.minByOrNull { distance(it.second, point) }?.second ?: return null
            val gap = distance(nearest, point)
            return nearest.takeIf { gap in TOUCHING_METRES..reach }
        }

        private fun nearby(point: LatLon, size: Double): List<Triple<Int, LatLon, Pair<Double, Double>>> {
            val x = floor(point.lon * metresPerLon / size).toLong()
            val y = floor(point.lat * METRES_PER_LAT / size).toLong()
            val found = mutableListOf<Triple<Int, LatLon, Pair<Double, Double>>>()
            for (dx in -2L..2L) for (dy in -2L..2L) cells[pack(x + dx, y + dy)]?.let { found += it }
            return found
        }

        private fun distance(a: LatLon, b: LatLon): Double {
            val ex = (a.lon - b.lon) * metresPerLon
            val ey = (a.lat - b.lat) * METRES_PER_LAT
            return sqrt(ex * ex + ey * ey)
        }

        private fun key(point: LatLon, size: Double) = pack(floor(point.lon * metresPerLon / size).toLong(), floor(point.lat * METRES_PER_LAT / size).toLong())

        private fun pack(x: Long, y: Long) = (x shl 32) xor (y and 0xffffffffL)
    }

    private fun heading(points: List<LatLon>, p: Int, metresPerLon: Double): Pair<Double, Double> {
        val a = points[maxOf(0, p - HEADING_REACH)]
        val b = points[minOf(points.size - 1, p + HEADING_REACH)]
        return unit((b.lon - a.lon) * metresPerLon, (b.lat - a.lat) * METRES_PER_LAT)
    }

    private fun unit(x: Double, y: Double): Pair<Double, Double> {
        val length = sqrt(x * x + y * y)
        return if (length < 1e-6) 0.0 to 0.0 else x / length to y / length
    }

    private fun smoothTrack(points: List<LatLon>): List<LatLon> =
        points.indices.map { p ->
            if (p == 0 || p == points.size - 1) return@map points[p]
            val window = points.subList(maxOf(0, p - SMOOTH_REACH), minOf(points.size, p + SMOOTH_REACH + 1))
            LatLon(window.sumOf { it.lat } / window.size, window.sumOf { it.lon } / window.size)
        }

    private class Joints(lines: List<Pair<Int, List<LatLon>>>, private val metresPerLon: Double) {
        private val cells = HashMap<Long, MutableList<Pair<Int, LatLon>>>()

        init {
            lines.forEach { (owner, points) -> points.forEach { point -> cells.getOrPut(key(point)) { mutableListOf() } += owner to point } }
        }

        fun near(owner: Int, point: LatLon): Boolean {
            val x = floor(point.lon * metresPerLon / JOIN_METRES).toLong()
            val y = floor(point.lat * METRES_PER_LAT / JOIN_METRES).toLong()
            for (dx in -1L..1L) {
                for (dy in -1L..1L) {
                    cells[pack(x + dx, y + dy)]?.forEach { (other, near) ->
                        if (other == owner) return@forEach
                        val ex = (near.lon - point.lon) * metresPerLon
                        val ey = (near.lat - point.lat) * METRES_PER_LAT
                        if (ex * ex + ey * ey <= JOIN_METRES * JOIN_METRES) return true
                    }
                }
            }
            return false
        }

        private fun key(point: LatLon) = pack(floor(point.lon * metresPerLon / JOIN_METRES).toLong(), floor(point.lat * METRES_PER_LAT / JOIN_METRES).toLong())

        private fun pack(x: Long, y: Long) = (x shl 32) xor (y and 0xffffffffL)
    }

    private fun prune(lines: List<GlowLine>, metresPerLon: Double): List<GlowLine> {
        var current = lines
        repeat(PRUNE_PASSES) {
            val joints = Joints(current.mapIndexed { owner, line -> owner to line.points }, metresPerLon)
            current = current.filterIndexed { owner, line ->
                val joinedHead = joints.near(owner, line.points.first())
                val joinedTail = joints.near(owner, line.points.last())
                val length = length(line.points)
                when {
                    joinedHead && joinedTail -> true
                    joinedHead || joinedTail -> length >= STUB_METRES
                    else -> length >= LONE_METRES
                }
            }
        }
        return current
    }

    private fun length(points: List<LatLon>): Double = points.zipWithNext().sumOf { (a, b) -> GeoMath.distanceMeters(a.lat, a.lon, b.lat, b.lon) }

    fun bucket(passes: Int): Int = when {
        passes <= 0 -> 0
        passes <= 1 -> 1
        passes <= 3 -> 3
        passes <= 8 -> 8
        else -> 20
    }

    private fun nearTracks(streets: List<List<LatLon>>, walks: List<Streak>): List<List<LatLon>> {
        if (walks.isEmpty()) return emptyList()
        val metresPerLon = METRES_PER_LAT * cos(Math.toRadians(walks.first().points.first().lat))
        fun cell(point: LatLon) = floor(point.lon * metresPerLon / RELEVANT_METRES).toLong() to floor(point.lat * METRES_PER_LAT / RELEVANT_METRES).toLong()
        val covered = HashSet<Long>()
        walks.forEach { walk ->
            densify(walk.points, RELEVANT_METRES / 2).forEach { point ->
                val (x, y) = cell(point)
                for (dx in -1L..1L) for (dy in -1L..1L) covered += (x + dx shl 32) xor (y + dy and 0xffffffffL)
            }
        }
        return streets.filter { street ->
            densify(street, RELEVANT_METRES / 2).any { point -> cell(point).let { (x, y) -> (x shl 32) xor (y and 0xffffffffL) in covered } }
        }
    }

    private fun stitch(walks: List<Streak>, graph: StreetGraph): List<Streak> =
        walks.filter { it.endedAt > 0 }.sortedBy { it.startedAt }.zipWithNext().mapNotNull { (before, after) ->
            val pause = after.startedAt - before.endedAt
            val from = before.points.last()
            val to = after.points.first()
            val gap = GeoMath.distanceMeters(from.lat, from.lon, to.lat, to.lon)
            val plausible = pause in 1..STITCH_MILLIS && gap in BRIDGE_MIN_METRES..BRIDGE_MAX_METRES && gap <= pause / 1000.0 * STITCH_SPEED_MPS
            if (!plausible) return@mapNotNull null
            graph.route(from, to, LONG_DETOUR)?.let { path -> Streak(listOf(from) + densify(path, BRIDGE_STEP_METRES) + to, minOf(before.passes, after.passes)) }
        }

    private fun bridgeGaps(streak: Streak, graph: StreetGraph): Streak {
        val points = mutableListOf(streak.points.first())
        streak.points.zipWithNext().forEach { (a, b) ->
            val gap = GeoMath.distanceMeters(a.lat, a.lon, b.lat, b.lon)
            val alongStreet = gap <= CORNER_GAP_METRES && ALONG_STREET_CHECKS.all { t -> graph.onStreet(LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t), ALONG_STREET_METRES) }
            if (gap > BRIDGE_MIN_METRES && gap <= BRIDGE_MAX_METRES && !alongStreet) graph.route(a, b, if (gap <= CORNER_GAP_METRES) CORNER_DETOUR else LONG_DETOUR)?.let { points += densify(it, BRIDGE_STEP_METRES) }
            points += b
        }
        return streak.copy(points = points)
    }

    private fun trim(points: List<LatLon>, centre: LatLon?, metresPerLon: Double): List<LatLon> {
        centre ?: return points
        fun gap(point: LatLon): Double {
            val ex = (point.lon - centre.lon) * metresPerLon
            val ey = (point.lat - centre.lat) * METRES_PER_LAT
            return sqrt(ex * ex + ey * ey)
        }
        var drop = 0
        while (drop < points.size - 2 && gap(points[drop + 1]) <= NODE_METRES) drop++
        return points.drop(drop)
    }

    private fun ahead(inner: LatLon, end: LatLon, target: LatLon, metresPerLon: Double): Boolean {
        val (ox, oy) = unit((end.lon - inner.lon) * metresPerLon, (end.lat - inner.lat) * METRES_PER_LAT)
        val (tx, ty) = unit((target.lon - end.lon) * metresPerLon, (target.lat - end.lat) * METRES_PER_LAT)
        return ox * tx + oy * ty >= HOOK_COS
    }

    private fun reach(points: List<LatLon>): Double {
        val start = points.first()
        return points.maxOf { GeoMath.distanceMeters(start.lat, start.lon, it.lat, it.lon) }
    }

    private fun chordSamples(line: List<LatLon>, stepMeters: Double): BooleanArray {
        if (line.size < 2) return BooleanArray(line.size)
        val flags = mutableListOf(false)
        line.zipWithNext().forEach { (a, b) ->
            val length = GeoMath.distanceMeters(a.lat, a.lon, b.lat, b.lon)
            val steps = ceil(length / stepMeters).toInt().coerceAtLeast(1)
            repeat(steps) { flags += length > CHORD_METRES }
        }
        return flags.toBooleanArray()
    }

    private fun densify(line: List<LatLon>, stepMeters: Double): List<LatLon> {
        if (line.size < 2) return line
        val out = mutableListOf(line.first())
        line.zipWithNext().forEach { (a, b) ->
            val steps = ceil(GeoMath.distanceMeters(a.lat, a.lon, b.lat, b.lon) / stepMeters).toInt().coerceAtLeast(1)
            for (i in 1..steps) {
                val t = i.toDouble() / steps
                out += LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
            }
        }
        return out
    }

    private fun litPieces(samples: List<LatLon>, passesAt: (Double, Double) -> Int): List<GlowLine> {
        if (samples.size < 2) return emptyList()
        val buckets = IntArray(samples.size - 1) { i ->
            val a = samples[i]
            val b = samples[i + 1]
            bucket(passesAt((a.lat + b.lat) / 2, (a.lon + b.lon) / 2))
        }
        return pieces(samples, buckets)
    }

    private fun pieces(samples: List<LatLon>, buckets: IntArray, keepLonely: Boolean = false): List<GlowLine> {
        smooth(buckets, keepLonely)
        val pieces = mutableListOf<GlowLine>()
        var start = 0
        while (start < buckets.size) {
            var end = start
            while (end + 1 < buckets.size && buckets[end + 1] == buckets[start]) end++
            if (buckets[start] > 0) pieces += GlowLine(samples.subList(start, end + 2), buckets[start])
            start = end + 1
        }
        return pieces
    }

    private fun smooth(buckets: IntArray, keepLonely: Boolean) {
        runs(buckets).forEach { (from, to) ->
            val length = to - from + 1
            val left = buckets.getOrNull(from - 1) ?: 0
            val right = buckets.getOrNull(to + 1) ?: 0
            if (buckets[from] == 0 && length <= MAX_GAP_SEGMENTS && left > 0 && right > 0) buckets.fill(minOf(left, right), from, to + 1)
        }
        runs(buckets).forEach { (from, to) ->
            val length = to - from + 1
            if (buckets[from] == 0 || length >= MIN_RUN_SEGMENTS) return@forEach
            val left = buckets.getOrNull(from - 1) ?: 0
            val right = buckets.getOrNull(to + 1) ?: 0
            val lonely = left == 0 && right == 0
            if (lonely && (keepLonely || length >= MIN_LONELY_SEGMENTS)) return@forEach
            buckets.fill(maxOf(left, right), from, to + 1)
        }
    }

    private fun runs(buckets: IntArray): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        var start = 0
        while (start < buckets.size) {
            var end = start
            while (end + 1 < buckets.size && buckets[end + 1] == buckets[start]) end++
            result += start to end
            start = end + 1
        }
        return result
    }

    private const val TRACK_NEAR_METRES = 30.0
    private const val RELEVANT_METRES = 120.0
    private const val METRES_PER_LAT = 111_320.0
    private const val MAX_GAP_SEGMENTS = 10
    private const val MIN_RUN_SEGMENTS = 6
    private const val MIN_LONELY_SEGMENTS = 8
    private const val STICKY_METRES = 8.0
    private const val JOIN_METRES = 6.0
    private const val STUB_METRES = 40.0
    private const val LONE_METRES = 120.0
    private const val CONNECTOR_METRES = 60.0
    private const val PARALLEL_COS = 0.82
    private const val HEADING_REACH = 2
    private const val SMOOTH_REACH = 3
    private const val MAGNET_METRES = 20.0
    private const val CONNECT_METRES = 25.0
    private const val NODE_METRES = 10.0
    private const val CHORD_METRES = 60.0
    private const val BRIDGE_MIN_METRES = 25.0
    private const val STITCH_MILLIS = 20 * 60_000L
    private const val STITCH_SPEED_MPS = 3.0
    private const val CORNER_GAP_METRES = 80.0
    private const val ALONG_STREET_METRES = 8.0
    private val ALONG_STREET_CHECKS = listOf(0.25, 0.5, 0.75)
    private const val CORNER_DETOUR = 1.5
    private const val LONG_DETOUR = 1.25
    private const val BRIDGE_MAX_METRES = 1_500.0
    private const val BRIDGE_STEP_METRES = 20.0
    private const val OFF_STREET_REACH_METRES = 40.0
    private const val HOOK_COS = -0.2
    private const val TOUCHING_METRES = 0.5
    private const val SMOOTH_PASSES = 3
    private const val OFF_STREET_MIN_METRES = 50.0
    private const val PRUNE_PASSES = 2
    private const val CORNER_PASSES = 2
    private const val CORNER_SEGMENTS = 3
    private const val CORNER_METRES = 8.0
}
