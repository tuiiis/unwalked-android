package lt.ehu.unwalked.data.history

import android.util.JsonReader
import android.util.JsonToken
import java.io.InputStream
import java.time.OffsetDateTime
import lt.ehu.unwalked.domain.tracking.Fix
import lt.ehu.unwalked.domain.tracking.WalkingFilter

object GoogleTimeline {
    fun parse(input: InputStream): List<Fix> {
        val found = Found()
        JsonReader(input.bufferedReader()).use { reader ->
            when (reader.peek()) {
                JsonToken.BEGIN_ARRAY -> eachElement(reader) { segment(it, found) }
                JsonToken.BEGIN_OBJECT -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "semanticSegments" -> eachElement(reader) { segment(it, found) }
                            "rawSignals" -> eachElement(reader) { signal(it, found) }
                            "locations" -> eachElement(reader) { record(it, found) }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                else -> throw IllegalArgumentException("Not a Google Timeline file")
            }
        }
        require(found.fixes.isNotEmpty() || found.walking.isNotEmpty()) { "No location history found in this file" }
        return WalkingFilter.keepWalking(found.fixes, found.walking.takeIf { it.isNotEmpty() })
    }

    private class Found {
        val fixes = mutableListOf<Fix>()
        val walking = mutableListOf<LongRange>()
    }

    private fun segment(value: Map<String, Any?>, found: Found) {
        val start = (value["startTime"] as? String)?.let(::instant)
        val end = (value["endTime"] as? String)?.let(::instant)
        (value["timelinePath"] as? List<*>)?.forEach { entry ->
            val point = entry as? Map<*, *> ?: return@forEach
            val position = (point["point"] as? String)?.let(::latLng) ?: return@forEach
            val time = (point["time"] as? String)?.let(::instant)
                ?: (point["durationMinutesOffsetFromStartTime"] as? String)?.toLongOrNull()?.let { offset -> start?.plus(offset * 60_000) }
                ?: return@forEach
            found.fixes += Fix(time, position.first, position.second, PATH_ACCURACY)
        }
        val activity = value["activity"] as? Map<*, *> ?: return
        val type = ((activity["topCandidate"] as? Map<*, *>)?.get("type") as? String)?.uppercase() ?: return
        if (start != null && end != null && type in WALKING) found.walking += start..end
    }

    private fun signal(value: Map<String, Any?>, found: Found) {
        val position = value["position"] as? Map<*, *> ?: return
        val latLng = (position["LatLng"] as? String ?: position["latLng"] as? String)?.let(::latLng) ?: return
        val time = (position["timestamp"] as? String)?.let(::instant) ?: return
        val accuracy = (position["accuracyMeters"] as? Number)?.toFloat() ?: PATH_ACCURACY
        found.fixes += Fix(time, latLng.first, latLng.second, accuracy)
    }

    private fun record(value: Map<String, Any?>, found: Found) {
        val lat = (value["latitudeE7"] as? Number)?.toDouble()?.div(1e7) ?: return
        val lon = (value["longitudeE7"] as? Number)?.toDouble()?.div(1e7) ?: return
        val time = (value["timestamp"] as? String)?.let(::instant)
            ?: (value["timestampMs"] as? String)?.toLongOrNull()
            ?: return
        val accuracy = (value["accuracy"] as? Number)?.toFloat() ?: PATH_ACCURACY
        found.fixes += Fix(time, lat, lon, accuracy)
    }

    private fun eachElement(reader: JsonReader, block: (Map<String, Any?>) -> Unit) {
        reader.beginArray()
        while (reader.hasNext()) {
            @Suppress("UNCHECKED_CAST")
            (read(reader) as? Map<String, Any?>)?.let(block)
        }
        reader.endArray()
    }

    private fun read(reader: JsonReader): Any? = when (reader.peek()) {
        JsonToken.BEGIN_OBJECT -> {
            val map = HashMap<String, Any?>()
            reader.beginObject()
            while (reader.hasNext()) map[reader.nextName()] = read(reader)
            reader.endObject()
            map
        }
        JsonToken.BEGIN_ARRAY -> {
            val list = ArrayList<Any?>()
            reader.beginArray()
            while (reader.hasNext()) list += read(reader)
            reader.endArray()
            list
        }
        JsonToken.NUMBER -> reader.nextDouble()
        JsonToken.BOOLEAN -> reader.nextBoolean()
        JsonToken.NULL -> reader.nextNull().let { null }
        else -> reader.nextString()
    }

    private fun instant(text: String): Long? = runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()

    private fun latLng(text: String): Pair<Double, Double>? {
        val numbers = NUMBER.findAll(text.removePrefix("geo:")).map { it.value.toDouble() }.toList()
        return if (numbers.size >= 2) numbers[0] to numbers[1] else null
    }

    private val NUMBER = Regex("-?\\d+(?:\\.\\d+)?")
    private val WALKING = setOf("WALKING", "ON_FOOT", "RUNNING", "HIKING")
    private const val PATH_ACCURACY = 20f
}
