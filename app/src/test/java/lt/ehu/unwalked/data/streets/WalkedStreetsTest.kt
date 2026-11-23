package lt.ehu.unwalked.data.streets

import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlinx.coroutines.test.runTest
import lt.ehu.unwalked.domain.route.LatLon
import lt.ehu.unwalked.domain.tracking.Streak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkedStreetsTest {
    private val key = TileKey(14, 9340, 5205)

    @Test
    fun readsLinesAndTheirClassFromTheWantedLayer() {
        val bytes = tile(layer("water", listOf(Line("lake", listOf(0 to 0, 10 to 10)))), layer("transportation", listOf(Line("path", listOf(0 to 0, 100 to 0, 100 to 50)))))
        val layer = VectorTile.lines(bytes, "transportation")!!
        assertEquals(4096, layer.extent)
        assertEquals(listOf(TileLine("path", listOf(0 to 0, 100 to 0, 100 to 50))), layer.lines)
    }

    @Test
    fun missingLayerGivesNothing() {
        assertNull(VectorTile.lines(tile(layer("water", emptyList())), "transportation"))
    }

    @Test
    fun keepsOnlyTheTilesOwnPartAndSkipsRails() {
        val bytes = tile(layer("transportation", listOf(Line("minor", listOf(-300 to 10, -100 to 10, 200 to 10)), Line("rail", listOf(0 to 50, 200 to 50)))))
        val streets = WalkedStreets.streets(key, bytes)
        assertEquals(1, streets.size)
        assertEquals(key.toLatLon(-100.0, 10.0, 4096), streets.single().first())
        assertEquals(key.toLatLon(200.0, 10.0, 4096), streets.single().last())
    }

    @Test
    fun tileKeyRoundTripsThroughCoordinates() {
        val corner = key.toLatLon(10.0, 10.0, 4096)
        assertEquals(key, TileKey.containing(corner, 14))
    }

    @Test
    fun walkedStreetIsFoundAndRememberedForTheSameTracks() = runTest {
        val street = Line("path", listOf(500 to 2000, 3500 to 2000))
        val bytes = tile(layer("transportation", listOf(street)))
        var requests = 0
        val source = object : TileSource {
            override suspend fun tile(key: TileKey): ByteArray? {
                requests++
                return if (key == this@WalkedStreetsTest.key) bytes else null
            }
        }
        val along = (0..20).map { key.toLatLon(800.0 + it * 120, 2001.0, 4096) }
        val streaks = listOf(Streak(along, 2))
        val walked = WalkedStreets(source)
        val first = walked.lines(streaks)
        val second = walked.lines(streaks)
        assertEquals(1, first.size)
        assertTrue(first.single().points.all { abs(it.lat - key.toLatLon(0.0, 2000.0, 4096).lat) < 1e-6 })
        assertEquals(first, second)
        assertEquals(1, requests)
    }

    @Test
    fun savedNetworkIsReusedAfterRestart() = runTest {
        val bytes = tile(layer("transportation", listOf(Line("path", listOf(500 to 2000, 3500 to 2000)))))
        var requests = 0
        val source = object : TileSource {
            override suspend fun tile(key: TileKey): ByteArray? {
                requests++
                return if (key == this@WalkedStreetsTest.key) bytes else null
            }
        }
        val streaks = listOf(Streak((0..20).map { key.toLatLon(800.0 + it * 120, 2001.0, 4096) }, 2))
        val directory = kotlin.io.path.createTempDirectory().toFile()
        val first = WalkedStreets(source, directory).lines(streaks)
        val again = WalkedStreets(source, directory).lines(streaks)
        assertEquals(first, again)
        assertEquals(1, requests)
        directory.deleteRecursively()
    }

    @Test
    fun switchingBackToAnEarlierPeriodNeedsNoRebuild() = runTest {
        val bytes = tile(layer("transportation", listOf(Line("path", listOf(500 to 2000, 3500 to 2000)))))
        var requests = 0
        val source = object : TileSource {
            override suspend fun tile(key: TileKey): ByteArray? {
                requests++
                return bytes
            }
        }
        val month = listOf(Streak((0..10).map { key.toLatLon(800.0 + it * 120, 2001.0, 4096) }, 2))
        val year = listOf(Streak((0..20).map { key.toLatLon(800.0 + it * 120, 2001.0, 4096) }, 2))
        val directory = kotlin.io.path.createTempDirectory().toFile()
        val walked = WalkedStreets(source, directory)
        walked.lines(month)
        walked.lines(year)
        val afterBoth = requests
        WalkedStreets(source, directory).lines(month)
        assertEquals(afterBoth, requests)
        assertEquals(2, directory.listFiles { it -> it.name.startsWith("network-") }!!.size)
        directory.deleteRecursively()
    }

    @Test
    fun readsTheTileUrlFromTileJson() {
        val json = """{"tilejson":"3.0.0","tiles":["https://tiles.example/planet/v1/{z}/{x}/{y}.pbf"],"maxzoom":14}"""
        assertEquals("https://tiles.example/planet/v1/{z}/{x}/{y}.pbf", CachedTileSource.tileTemplate(json))
    }

    private data class Line(val kind: String, val points: List<Pair<Int, Int>>)

    private fun tile(vararg layers: ByteArray): ByteArray = ByteArrayOutputStream().apply { layers.forEach { write(message(3, it)) } }.toByteArray()

    private fun layer(name: String, lines: List<Line>): ByteArray {
        val kinds = lines.map { it.kind }.distinct()
        return ByteArrayOutputStream().apply {
            write(15 shl 3)
            write(varint(2))
            write(message(1, name.toByteArray()))
            lines.forEach { line -> write(message(2, feature(kinds.indexOf(line.kind), line.points))) }
            write(message(3, "class".toByteArray()))
            kinds.forEach { write(message(4, message(1, it.toByteArray()))) }
            write(5 shl 3)
            write(varint(4096))
        }.toByteArray()
    }

    private fun feature(kind: Int, points: List<Pair<Int, Int>>): ByteArray {
        val commands = mutableListOf(command(1, 1))
        var x = 0
        var y = 0
        points.forEachIndexed { i, (px, py) ->
            if (i == 1) commands += command(2, points.size - 1)
            commands += zigzag(px - x)
            commands += zigzag(py - y)
            x = px
            y = py
        }
        return ByteArrayOutputStream().apply {
            write(message(2, packed(listOf(0, kind))))
            write(3 shl 3)
            write(varint(2))
            write(message(4, packed(commands)))
        }.toByteArray()
    }

    private fun command(id: Int, count: Int) = (count shl 3) or id

    private fun zigzag(n: Int) = (n shl 1) xor (n shr 31)

    private fun packed(values: List<Int>): ByteArray = ByteArrayOutputStream().apply { values.forEach { write(varint(it.toLong())) } }.toByteArray()

    private fun message(field: Int, body: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write(varint(((field shl 3) or 2).toLong()))
        write(varint(body.size.toLong()))
        write(body)
    }.toByteArray()

    private fun varint(value: Long): ByteArray {
        val out = ByteArrayOutputStream()
        var v = value
        while (v >= 0x80) {
            out.write(((v and 0x7f) or 0x80).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
        return out.toByteArray()
    }

    private fun varint(value: Int) = varint(value.toLong())
}
