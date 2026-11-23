package lt.ehu.unwalked.data.streets

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan
import lt.ehu.unwalked.domain.route.LatLon

data class TileKey(val zoom: Int, val x: Int, val y: Int) {
    fun toLatLon(px: Double, py: Double, extent: Int): LatLon {
        val scale = 1 shl zoom
        val lon = (x + px / extent) / scale * 360.0 - 180.0
        val lat = Math.toDegrees(atan(sinh(PI * (1 - 2 * (y + py / extent) / scale))))
        return LatLon(lat, lon)
    }

    companion object {
        fun containing(point: LatLon, zoom: Int): TileKey {
            val scale = 1 shl zoom
            val x = ((point.lon + 180.0) / 360.0 * scale).toInt().coerceIn(0, scale - 1)
            val radians = Math.toRadians(point.lat)
            val y = ((1 - ln(tan(radians) + 1 / cos(radians)) / PI) / 2 * scale).toInt().coerceIn(0, scale - 1)
            return TileKey(zoom, x, y)
        }
    }
}

data class TileLine(val kind: String, val points: List<Pair<Int, Int>>)

data class TileLayer(val extent: Int, val lines: List<TileLine>)

object VectorTile {
    fun lines(bytes: ByteArray, layerName: String, kindKey: String = "class"): TileLayer? {
        val tile = Reader(bytes, 0, bytes.size)
        while (tile.hasMore()) {
            val (field, wire) = tile.tag()
            if (field == LAYERS && wire == LENGTH) {
                val layer = layer(tile.message(), layerName, kindKey)
                if (layer != null) return layer
            } else {
                tile.skip(wire)
            }
        }
        return null
    }

    private class RawFeature(val type: Int, val tags: IntArray, val geometry: IntArray)

    private fun layer(reader: Reader, wanted: String, kindKey: String): TileLayer? {
        var name = ""
        var extent = DEFAULT_EXTENT
        val keys = mutableListOf<String>()
        val values = mutableListOf<String?>()
        val features = mutableListOf<RawFeature>()
        while (reader.hasMore()) {
            val (field, wire) = reader.tag()
            when {
                field == LAYER_NAME && wire == LENGTH -> name = reader.string()
                field == LAYER_FEATURES && wire == LENGTH -> features += feature(reader.message())
                field == LAYER_KEYS && wire == LENGTH -> keys += reader.string()
                field == LAYER_VALUES && wire == LENGTH -> values += value(reader.message())
                field == LAYER_EXTENT && wire == VARINT -> extent = reader.varint().toInt()
                else -> reader.skip(wire)
            }
        }
        if (name != wanted) return null
        val kindIndex = keys.indexOf(kindKey)
        val lines = features.filter { it.type == LINESTRING }.flatMap { feature ->
            val kind = (0 until feature.tags.size / 2)
                .firstOrNull { feature.tags[2 * it] == kindIndex }
                ?.let { values.getOrNull(feature.tags[2 * it + 1]) }
                .orEmpty()
            geometry(feature.geometry).map { TileLine(kind, it) }
        }
        return TileLayer(extent, lines)
    }

    private fun feature(reader: Reader): RawFeature {
        var type = 0
        var tags = IntArray(0)
        var geometry = IntArray(0)
        while (reader.hasMore()) {
            val (field, wire) = reader.tag()
            when {
                field == FEATURE_TAGS && wire == LENGTH -> tags = reader.packed()
                field == FEATURE_TYPE && wire == VARINT -> type = reader.varint().toInt()
                field == FEATURE_GEOMETRY && wire == LENGTH -> geometry = reader.packed()
                else -> reader.skip(wire)
            }
        }
        return RawFeature(type, tags, geometry)
    }

    private fun value(reader: Reader): String? {
        var text: String? = null
        while (reader.hasMore()) {
            val (field, wire) = reader.tag()
            if (field == VALUE_STRING && wire == LENGTH) text = reader.string() else reader.skip(wire)
        }
        return text
    }

    private fun geometry(commands: IntArray): List<List<Pair<Int, Int>>> {
        val lines = mutableListOf<MutableList<Pair<Int, Int>>>()
        var x = 0
        var y = 0
        var i = 0
        while (i < commands.size) {
            val command = commands[i] and 0x7
            val count = commands[i] ushr 3
            i++
            when (command) {
                MOVE_TO, LINE_TO -> repeat(count) {
                    if (i + 1 >= commands.size) return lines.filter { it.size >= 2 }
                    x += zigzag(commands[i])
                    y += zigzag(commands[i + 1])
                    i += 2
                    if (command == MOVE_TO) lines += mutableListOf(x to y) else lines.lastOrNull()?.add(x to y)
                }
                else -> Unit
            }
        }
        return lines.filter { it.size >= 2 }
    }

    private fun zigzag(n: Int): Int = (n ushr 1) xor -(n and 1)

    private class Reader(private val bytes: ByteArray, private var position: Int, private val end: Int) {
        fun hasMore() = position < end

        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val byte = bytes[position++].toInt() and 0xff
                result = result or ((byte and 0x7f).toLong() shl shift)
                if (byte < 0x80) return result
                shift += 7
            }
        }

        fun tag(): Pair<Int, Int> {
            val key = varint().toInt()
            return (key ushr 3) to (key and 0x7)
        }

        fun message(): Reader {
            val length = varint().toInt()
            val reader = Reader(bytes, position, position + length)
            position += length
            return reader
        }

        fun string(): String {
            val length = varint().toInt()
            val text = String(bytes, position, length, Charsets.UTF_8)
            position += length
            return text
        }

        fun packed(): IntArray {
            val inner = message()
            val out = ArrayList<Int>()
            while (inner.hasMore()) out += inner.varint().toInt()
            return out.toIntArray()
        }

        fun skip(wire: Int) {
            when (wire) {
                VARINT -> varint()
                FIXED64 -> position += 8
                LENGTH -> position += varint().toInt()
                FIXED32 -> position += 4
                else -> position = end
            }
        }
    }

    private const val VARINT = 0
    private const val FIXED64 = 1
    private const val LENGTH = 2
    private const val FIXED32 = 5
    private const val LAYERS = 3
    private const val LAYER_NAME = 1
    private const val LAYER_FEATURES = 2
    private const val LAYER_KEYS = 3
    private const val LAYER_VALUES = 4
    private const val LAYER_EXTENT = 5
    private const val FEATURE_TAGS = 2
    private const val FEATURE_TYPE = 3
    private const val FEATURE_GEOMETRY = 4
    private const val VALUE_STRING = 1
    private const val LINESTRING = 2
    private const val MOVE_TO = 1
    private const val LINE_TO = 2
    private const val DEFAULT_EXTENT = 4096
}
