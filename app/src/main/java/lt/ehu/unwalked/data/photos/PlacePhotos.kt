package lt.ehu.unwalked.data.photos

import android.content.SharedPreferences
import java.net.URLDecoder
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import lt.ehu.unwalked.domain.tracking.GeoMath
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class PlacePhotos(
    private val token: String,
    private val store: SharedPreferences? = null,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val cache = HashMap<Long, String?>()
    private val mutex = Mutex()

    val streetLevel: Boolean get() = token.isNotBlank()

    suspend fun thumbnail(id: Long, lat: Double, lon: Double, name: String): String? {
        mutex.withLock { if (cache.containsKey(id)) return cache[id] }
        store?.getString(key(id), null)?.let { saved ->
            val url = saved.ifEmpty { null }
            mutex.withLock { cache[id] = url }
            return url
        }
        val url = withContext(Dispatchers.IO) {
            runCatching { wikidata(lat, lon, name) }.getOrNull()
                ?: runCatching { commonsNearby(lat, lon, name) }.getOrNull()
                ?: if (streetLevel) runCatching { mapillary(lat, lon) }.getOrNull() else null
        }
        mutex.withLock { cache[id] = url }
        store?.edit()?.putString(key(id), url.orEmpty())?.apply()
        return url
    }

    private fun commonsNearby(lat: Double, lon: Double, name: String): String? {
        val search = "https://commons.wikimedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("generator", "geosearch")
            .addQueryParameter("ggscoord", "$lat|$lon")
            .addQueryParameter("ggsradius", "$COMMONS_RADIUS_METRES")
            .addQueryParameter("ggsnamespace", "6")
            .addQueryParameter("ggslimit", "20")
            .addQueryParameter("prop", "imageinfo|coordinates")
            .addQueryParameter("iiprop", "url")
            .addQueryParameter("iiurlwidth", "$COMMONS_WIDTH")
            .addQueryParameter("format", "json")
            .build()
        val pages = get(search)?.let { JSONObject(it).optJSONObject("query")?.optJSONObject("pages") } ?: return null
        val files = pages.keys().asSequence().map { pages.getJSONObject(it) }.mapNotNull { page ->
            val title = page.optString("title").removePrefix("File:")
            val thumb = page.optJSONArray("imageinfo")?.optJSONObject(0)?.optString("thumburl")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (!PHOTO_EXTENSIONS.any { title.lowercase().endsWith(it) }) return@mapNotNull null
            val at = page.optJSONArray("coordinates")?.optJSONObject(0) ?: return@mapNotNull null
            Triple(title.substringBeforeLast('.'), thumb, GeoMath.distanceMeters(lat, lon, at.getDouble("lat"), at.getDouble("lon")))
        }.toList()
        return (files.firstOrNull { NameMatch.similar(it.first, name) } ?: files.filter { it.third <= COMMONS_CLOSE_METRES }.minByOrNull { it.third })?.second
    }

    private fun wikidata(lat: Double, lon: Double, name: String): String? {
        val query = """
            SELECT ?image (GROUP_CONCAT(DISTINCT ?label; separator="|") AS ?names) WHERE {
              SERVICE wikibase:around { ?item wdt:P625 ?where . bd:serviceParam wikibase:center "Point($lon $lat)"^^geo:wktLiteral . bd:serviceParam wikibase:radius "$WIKIDATA_RADIUS_KM" . }
              ?item wdt:P18 ?image .
              ?item rdfs:label|skos:altLabel ?label . FILTER(LANG(?label) IN ("lt", "en", "ru", "pl"))
            } GROUP BY ?image LIMIT 20
        """.trimIndent()
        val search = "https://query.wikidata.org/sparql".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("format", "json")
            .build()
        val file = get(search)?.let { body ->
            val rows = JSONObject(body).getJSONObject("results").getJSONArray("bindings")
            (0 until rows.length()).map { rows.getJSONObject(it) }.firstOrNull { row ->
                row.getJSONObject("names").getString("value").split('|').any { NameMatch.similar(it, name) }
            }?.getJSONObject("image")?.getString("value")?.substringAfter("Special:FilePath/")
        } ?: return null
        val info = "https://commons.wikimedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("titles", "File:${URLDecoder.decode(file, "UTF-8")}")
            .addQueryParameter("prop", "imageinfo")
            .addQueryParameter("iiprop", "url")
            .addQueryParameter("iiurlwidth", "$COMMONS_WIDTH")
            .addQueryParameter("format", "json")
            .build()
        return get(info)?.let { body ->
            val pages = JSONObject(body).getJSONObject("query").getJSONObject("pages")
            pages.keys().asSequence().map { pages.getJSONObject(it) }.firstNotNullOfOrNull { page ->
                page.optJSONArray("imageinfo")?.optJSONObject(0)?.optString("thumburl")?.takeIf { it.isNotBlank() }
            }
        }
    }

    private fun get(url: HttpUrl): String? =
        client.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { if (it.isSuccessful) it.body.string() else null }

    private fun mapillary(lat: Double, lon: Double): String? {
        val dLat = RADIUS_METERS / 111_320.0
        val dLon = RADIUS_METERS / (111_320.0 * cos(Math.toRadians(lat)))
        val url = "https://graph.mapillary.com/images".toHttpUrl().newBuilder()
            .addQueryParameter("access_token", token)
            .addQueryParameter("fields", "thumb_256_url,computed_geometry,compass_angle")
            .addQueryParameter("bbox", "${lon - dLon},${lat - dLat},${lon + dLon},${lat + dLat}")
            .addQueryParameter("limit", "30")
            .build()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val data = JSONObject(response.body.string()).optJSONArray("data") ?: return null
            val shots = (0 until data.length()).mapNotNull { i -> Shot.from(data.getJSONObject(i)) }
            return shots.minByOrNull { it.score(lat, lon) }?.url
        }
    }

    private class Shot(val url: String, val lat: Double, val lon: Double, val compass: Double?) {
        fun score(placeLat: Double, placeLon: Double): Double {
            val distance = GeoMath.distanceMeters(lat, lon, placeLat, placeLon)
            val facing = compass?.let { angleBetween(it, bearing(lat, lon, placeLat, placeLon)) } ?: 90.0
            return distance + facing
        }

        companion object {
            fun from(json: JSONObject): Shot? {
                val url = json.optString("thumb_256_url").takeIf { it.isNotBlank() } ?: return null
                val coordinates = json.optJSONObject("computed_geometry")?.optJSONArray("coordinates") ?: return null
                val compass = if (json.has("compass_angle")) json.getDouble("compass_angle") else null
                return Shot(url, coordinates.getDouble(1), coordinates.getDouble(0), compass)
            }
        }
    }

    private companion object {
        const val RADIUS_METERS = 60.0
        const val WIKIDATA_RADIUS_KM = 0.08
        const val COMMONS_WIDTH = 320
        const val COMMONS_RADIUS_METRES = 60
        const val COMMONS_CLOSE_METRES = 25.0
        val PHOTO_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp")

        fun key(id: Long) = "photo_$id"
        const val USER_AGENT = "Unwalked/0.1 (student diploma project)"

        fun bearing(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
            val dLon = (toLon - fromLon) * cos(Math.toRadians(fromLat))
            return (Math.toDegrees(atan2(dLon, toLat - fromLat)) + 360) % 360
        }

        fun angleBetween(a: Double, b: Double): Double = abs(((a - b + 540) % 360) - 180)
    }
}
