package lt.ehu.unwalked.data.route

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import lt.ehu.unwalked.core.error.AppError
import lt.ehu.unwalked.core.error.Outcome
import lt.ehu.unwalked.domain.route.LatLon
import okhttp3.OkHttpClient
import okhttp3.Request

data class RoutedPath(val path: List<LatLon>, val metres: Double)

interface RouteRepository {
    suspend fun walking(waypoints: List<LatLon>): Outcome<RoutedPath>
}

class OsrmRouteRepository(
    private val client: OkHttpClient,
    private val json: Json,
    private val baseUrl: String = "https://routing.openstreetmap.de/routed-foot/route/v1/foot/",
) : RouteRepository {
    override suspend fun walking(waypoints: List<LatLon>): Outcome<RoutedPath> = withContext(Dispatchers.IO) {
        val coordinates = waypoints.joinToString(";") { "%.6f,%.6f".format(java.util.Locale.US, it.lon, it.lat) }
        val request = Request.Builder().url("$baseUrl$coordinates?overview=full&geometries=geojson&continue_straight=true").build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Outcome.Failure(AppError.Server(null, "Routing failed with HTTP ${response.code}"))
                val body = json.decodeFromString<OsrmResponse>(response.body.string())
                val route = body.routes.firstOrNull() ?: return@withContext Outcome.Failure(AppError.NotFound("No walking route found here."))
                Outcome.Success(RoutedPath(route.geometry.coordinates.map { LatLon(it[1], it[0]) }, route.distance))
            }
        } catch (io: IOException) {
            Outcome.Failure(AppError.Network(io.message ?: "Routing is unreachable"))
        } catch (other: Exception) {
            Outcome.Failure(AppError.Unexpected(other.message ?: "Routing failed"))
        }
    }

    @Serializable
    private data class OsrmResponse(val routes: List<OsrmRoute> = emptyList())

    @Serializable
    private data class OsrmRoute(val distance: Double, val geometry: OsrmGeometry)

    @Serializable
    private data class OsrmGeometry(val coordinates: List<List<Double>>)
}
