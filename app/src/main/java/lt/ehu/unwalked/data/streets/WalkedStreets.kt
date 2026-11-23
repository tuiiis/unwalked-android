package lt.ehu.unwalked.data.streets

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import lt.ehu.unwalked.core.ui.map.GlowLine
import lt.ehu.unwalked.core.ui.map.StreetGlow
import lt.ehu.unwalked.domain.route.LatLon
import lt.ehu.unwalked.domain.tracking.Streak
import okhttp3.OkHttpClient
import okhttp3.Request

interface TileSource {
    suspend fun tile(key: TileKey): ByteArray?
}

class CachedTileSource(
    private val client: OkHttpClient,
    private val directory: File,
    private val tileJsonUrl: String,
) : TileSource {
    private val lock = Mutex()
    private var template: String? = null

    override suspend fun tile(key: TileKey): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(directory, "${key.zoom}/${key.x}/${key.y}.pbf")
        if (file.isFile) return@withContext file.readBytes()
        val url = template() ?: return@withContext null
        val bytes = get(url.replace("{z}", "${key.zoom}").replace("{x}", "${key.x}").replace("{y}", "${key.y}")) ?: return@withContext null
        file.parentFile?.mkdirs()
        val partial = File(file.path + ".part")
        partial.writeBytes(bytes)
        partial.renameTo(file)
        bytes
    }

    private suspend fun template(): String? = lock.withLock {
        template?.let { return@withLock it }
        val saved = File(directory, TEMPLATE_FILE)
        val found = if (saved.isFile) {
            saved.readText()
        } else {
            get(tileJsonUrl)?.toString(Charsets.UTF_8)?.let(::tileTemplate)?.also {
                directory.mkdirs()
                saved.writeText(it)
            }
        }
        template = found
        found
    }

    private fun get(url: String): ByteArray? = try {
        client.newCall(Request.Builder().url(url).build()).execute().use { if (it.isSuccessful) it.body.bytes() else null }
    } catch (io: IOException) {
        null
    }

    companion object {
        private const val TEMPLATE_FILE = "template.txt"
        private val TILES = Regex("\"tiles\"\\s*:\\s*\\[\\s*\"([^\"]+)\"")

        fun tileTemplate(tileJson: String): String? = TILES.find(tileJson)?.groupValues?.get(1)
    }
}

class WalkedStreets(
    private val tiles: TileSource,
    private val directory: File? = null,
    private val zoom: Int = STREET_ZOOM,
    private val maxTiles: Int = MAX_TILES,
) {
    private val lock = Mutex()
    private val recent = LinkedHashMap<List<Streak>, List<GlowLine>>()
    private val running = HashMap<List<Streak>, Deferred<List<GlowLine>>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun lines(streaks: List<Streak>): List<GlowLine> {
        if (streaks.isEmpty()) return emptyList()
        val job = lock.withLock {
            recent[streaks]?.let { return it }
            running.getOrPut(streaks) { scope.async { compute(streaks) } }
        }
        return job.await()
    }

    private suspend fun compute(streaks: List<Streak>): List<GlowLine> = try {
        withContext(Dispatchers.IO) { read(streaks) }?.also { remember(streaks, it) } ?: build(streaks)
    } finally {
        lock.withLock { running.remove(streaks) }
    }

    private suspend fun build(streaks: List<Streak>): List<GlowLine> {
        val loaded = coroutineScope {
            tilesFor(streaks).chunked(PARALLEL).flatMap { chunk -> chunk.map { key -> async(Dispatchers.IO) { key to tiles.tile(key) } }.awaitAll() }
        }
        val result = runCatching { StreetGlow.walked(loaded.flatMap { (key, bytes) -> bytes?.let { streets(key, it) }.orEmpty() }, streaks) }.getOrDefault(emptyList())
        if (loaded.all { it.second != null }) {
            remember(streaks, result)
            withContext(Dispatchers.IO) { write(streaks, result) }
        }
        return result
    }

    private suspend fun remember(streaks: List<Streak>, lines: List<GlowLine>) = lock.withLock {
        recent.remove(streaks)
        recent[streaks] = lines
        while (recent.size > KEPT) recent.remove(recent.keys.first())
    }

    private fun file(streaks: List<Streak>): File? = directory?.let { File(it, "network-$FORMAT-${Integer.toHexString(streaks.hashCode())}.bin") }

    private fun read(streaks: List<Streak>): List<GlowLine>? {
        val file = file(streaks)?.takeIf { it.isFile } ?: return null
        file.setLastModified(System.currentTimeMillis())
        return runCatching {
            DataInputStream(file.inputStream().buffered()).use { input ->
                List(input.readInt()) {
                    val passes = input.readInt()
                    val onStreet = input.readBoolean()
                    GlowLine(List(input.readInt()) { LatLon(input.readDouble(), input.readDouble()) }, passes, onStreet)
                }
            }
        }.getOrNull()
    }

    private fun write(streaks: List<Streak>, lines: List<GlowLine>) {
        val file = file(streaks) ?: return
        runCatching {
            file.parentFile?.mkdirs()
            file.parentFile?.listFiles { other -> other.name.startsWith("network-") && other != file }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(KEPT - 1)
                ?.forEach { it.delete() }
            val partial = File(file.path + ".part")
            DataOutputStream(partial.outputStream().buffered()).use { output ->
                output.writeInt(lines.size)
                lines.forEach { line ->
                    output.writeInt(line.passes)
                    output.writeBoolean(line.onStreet)
                    output.writeInt(line.points.size)
                    line.points.forEach {
                        output.writeDouble(it.lat)
                        output.writeDouble(it.lon)
                    }
                }
            }
            partial.renameTo(file)
        }
    }

    fun tilesFor(streaks: List<Streak>): List<TileKey> =
        streaks.flatMap { it.points }
            .groupingBy { TileKey.containing(it, zoom) }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(maxTiles)
            .map { it.key }

    companion object {
        private const val STREET_ZOOM = 14
        private const val FORMAT = 1
        private const val KEPT = 4
        private const val MAX_TILES = 300
        private const val PARALLEL = 6
        private val IGNORED = setOf("motorway", "rail", "transit", "ferry", "raceway", "bus_guideway", "busway")

        fun streets(key: TileKey, bytes: ByteArray): List<List<LatLon>> {
            val layer = runCatching { VectorTile.lines(bytes, "transportation") }.getOrNull() ?: return emptyList()
            return layer.lines
                .filter { it.kind !in IGNORED }
                .flatMap { line -> inside(line.points, layer.extent) }
                .map { piece -> piece.map { (x, y) -> key.toLatLon(x.toDouble(), y.toDouble(), layer.extent) } }
        }

        private fun inside(points: List<Pair<Int, Int>>, extent: Int): List<List<Pair<Int, Int>>> {
            val pieces = mutableListOf<List<Pair<Int, Int>>>()
            var current: MutableList<Pair<Int, Int>>? = null
            points.zipWithNext().forEach { (a, b) ->
                val x = (a.first + b.first) / 2.0
                val y = (a.second + b.second) / 2.0
                if (x in 0.0..extent.toDouble() && y in 0.0..extent.toDouble()) {
                    current = (current ?: mutableListOf(a).also { pieces += it }).apply { add(b) }
                } else {
                    current = null
                }
            }
            return pieces
        }
    }
}
