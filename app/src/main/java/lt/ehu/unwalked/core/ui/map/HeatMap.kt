package lt.ehu.unwalked.core.ui.map

import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.uber.h3core.H3Core
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import lt.ehu.unwalked.core.ui.theme.Palette
import lt.ehu.unwalked.domain.route.LatLon
import lt.ehu.unwalked.domain.tracking.GeoMath
import lt.ehu.unwalked.domain.tracking.Streak
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.TransitionOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

data class HeatCell(val cell: Long, val passes: Int)

data class MapMarker(val lat: Double, val lon: Double, val label: String? = null)

@Composable
fun HeatMap(
    h3: H3Core,
    cells: List<HeatCell>,
    modifier: Modifier = Modifier,
    streaks: List<Streak> = emptyList(),
    route: List<LatLon> = emptyList(),
    marker: MapMarker? = null,
    position: MapMarker? = null,
    fitToRoute: Boolean = false,
    coveredBottom: Float = 0f,
    initialCentre: LatLon = LatLon(54.6872, 25.2797),
    walkedStreets: suspend (List<Streak>) -> List<GlowLine> = { emptyList() },
    showLoading: Boolean = true,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var centred by remember { mutableStateOf(false) }
    var morph by remember { mutableStateOf(TrailMorph.EMPTY) }
    var revealed by remember { mutableStateOf(false) }
    var building by remember { mutableStateOf(false) }
    val zoom = remember { MutableStateFlow(13.0) }
    val mapView = remember { MapView(context).apply { onCreate(Bundle()) } }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { loaded ->
            map = loaded
            loaded.addOnCameraMoveListener { zoom.value = loaded.cameraPosition.zoom }
            loaded.addOnCameraIdleListener { zoom.value = loaded.cameraPosition.zoom }
            loaded.uiSettings.isAttributionEnabled = true
            loaded.uiSettings.isLogoEnabled = false
            loaded.uiSettings.isCompassEnabled = false
            loaded.uiSettings.isRotateGesturesEnabled = false
            loaded.cameraPosition = CameraPosition.Builder().target(LatLng(initialCentre.lat, initialCentre.lon)).zoom(13.2).build()
            loaded.setStyle(Style.Builder().fromJson(MapStyle.dark)) { ready ->
                ready.addSource(GeoJsonSource(TRAIL))
                ready.addSource(GeoJsonSource(ROUTE))
                ready.addSource(GeoJsonSource(MARKER))
                ready.addSource(GeoJsonSource(POSITION))
                trailLayers().forEach { ready.addLayerBelow(it, "water-name") }
                ready.addLayer(
                    LineLayer("$ROUTE-casing", ROUTE).withProperties(
                        PropertyFactory.lineColor(Palette.Background.toArgb()),
                        PropertyFactory.lineWidth(8f),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    ),
                )
                ready.addLayer(
                    LineLayer(ROUTE, ROUTE).withProperties(
                        PropertyFactory.lineColor(Palette.Route.toArgb()),
                        PropertyFactory.lineWidth(4.5f),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    ),
                )
                ready.addLayer(
                    CircleLayer(MARKER, MARKER).withProperties(
                        PropertyFactory.circleRadius(9f),
                        PropertyFactory.circleColor(Palette.Warning.toArgb()),
                        PropertyFactory.circleStrokeColor(Palette.Background.toArgb()),
                        PropertyFactory.circleStrokeWidth(3f),
                    ),
                )
                ready.addLayer(
                    CircleLayer(POSITION, POSITION).withProperties(
                        PropertyFactory.circleRadius(8f),
                        PropertyFactory.circleColor(android.graphics.Color.WHITE),
                        PropertyFactory.circleStrokeColor(Palette.Accent.toArgb()),
                        PropertyFactory.circleStrokeWidth(4f),
                    ),
                )
                style = ready
            }
        }
    }

    LaunchedEffect(style, morph) {
        val ready = style ?: return@LaunchedEffect
        val shape = morph
        zoom.map { (it.coerceIn(MORPH_MIN_ZOOM, MORPH_MAX_ZOOM) / MORPH_QUANTUM).roundToInt() * MORPH_QUANTUM }
            .distinctUntilChanged()
            .conflate()
            .collect { level ->
                val collection = withContext(Dispatchers.Default) { lineFeatures(shape.shape(level)) }
                (ready.getSource(TRAIL) as? GeoJsonSource)?.setGeoJson(collection)
                if (!revealed && shape !== TrailMorph.EMPTY) {
                    ready.showTrail(true, REVEAL_MILLIS)
                    revealed = true
                }
            }
    }

    LaunchedEffect(style, streaks) {
        val ready = style ?: return@LaunchedEffect
        val network = async { walkedStreets(streaks) }
        val cached = withTimeoutOrNull(QUICK_MILLIS) { network.await() }
        if (cached == null) {
            building = true
            ready.showTrail(false, SWAP_MILLIS)
        }
        val streets = cached ?: network.await()
        val rebuilt = withContext(Dispatchers.Default) { TrailMorph.build(streaks, streets) }
        if (revealed && cached != null) {
            ready.showTrail(false, SWAP_MILLIS)
            delay(SWAP_MILLIS)
        }
        revealed = false
        morph = rebuilt
        building = false
    }

    LaunchedEffect(style, cells) {
        style ?: return@LaunchedEffect
        val target = map
        if (!centred && target != null && cells.isNotEmpty() && route.isEmpty() && marker == null) {
            centred = true
            val centre = busiestArea(h3, cells)
            target.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(centre.lat, centre.lon), SUMMARY_ZOOM))
            target.scrollBy(0f, -mapView.height * SUMMARY_SHIFT)
            val settled = target.cameraPosition
            target.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(settled).zoom(settled.zoom - LANDING_ZOOM_OUT).build()))
            target.animateCamera(CameraUpdateFactory.newCameraPosition(settled), LANDING_MILLIS)
        }
    }

    LaunchedEffect(style, route, fitToRoute) {
        val ready = style ?: return@LaunchedEffect
        val line = if (route.size < 2) {
            FeatureCollection.fromFeatures(emptyList())
        } else {
            FeatureCollection.fromFeature(
                Feature.fromGeometry(LineString.fromLngLats(route.map { Point.fromLngLat(it.lon, it.lat) })),
            )
        }
        (ready.getSource(ROUTE) as? GeoJsonSource)?.setGeoJson(line)
        if (fitToRoute && route.size >= 2) {
            val bounds = LatLngBounds.Builder().includes(route.map { LatLng(it.lat, it.lon) }).build()
            map?.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120, 160, 120, 120 + (mapView.height * coveredBottom).toInt()))
        }
    }

    LaunchedEffect(style, marker) {
        val ready = style ?: return@LaunchedEffect
        (ready.getSource(MARKER) as? GeoJsonSource)?.setGeoJson(point(marker))
        val target = map ?: return@LaunchedEffect
        marker ?: return@LaunchedEffect
        val nearest = cells.minByOrNull { cell -> h3.cellToLatLng(cell.cell).let { GeoMath.distanceMeters(it.lat, it.lng, marker.lat, marker.lon) } }?.let { h3.cellToLatLng(it.cell) }
        val update = if (nearest == null || GeoMath.distanceMeters(nearest.lat, nearest.lng, marker.lat, marker.lon) > CONTEXT_MAX_METERS) {
            CameraUpdateFactory.newLatLngZoom(LatLng(marker.lat, marker.lon), FOCUS_ZOOM)
        } else {
            val bounds = LatLngBounds.Builder().include(LatLng(marker.lat, marker.lon)).include(LatLng(nearest.lat, nearest.lng)).build()
            val padding = (mapView.width * 0.22f).toInt()
            val fitted = target.getCameraForLatLngBounds(bounds, intArrayOf(padding, padding * 2, padding, padding * 3))
            if (fitted == null) {
                CameraUpdateFactory.newLatLngZoom(LatLng(marker.lat, marker.lon), FOCUS_ZOOM)
            } else {
                CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(fitted).zoom(minOf(fitted.zoom, FOCUS_ZOOM)).build())
            }
        }
        target.animateCamera(update, FOCUS_MILLIS)
    }

    LaunchedEffect(style, position) {
        val ready = style ?: return@LaunchedEffect
        (ready.getSource(POSITION) as? GeoJsonSource)?.setGeoJson(point(position))
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        AnimatedVisibility(
            visible = building && showLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center).padding(bottom = (coveredBottom * LOADER_LIFT_DP).dp),
        ) {
            CircularProgressIndicator(color = Palette.Accent, strokeWidth = 3.dp, modifier = Modifier.size(36.dp).semantics { contentDescription = "Building your map" })
        }
    }
}

private fun point(marker: MapMarker?): FeatureCollection =
    FeatureCollection.fromFeatures(listOfNotNull(marker?.let { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) }))

private fun glowColor(): Expression =
    Expression.interpolate(
        Expression.linear(),
        Expression.get("passes"),
        Expression.stop(1, Expression.color(Palette.Heat[0].toArgb())),
        Expression.stop(3, Expression.color(Palette.Heat[1].toArgb())),
        Expression.stop(8, Expression.color(Palette.Heat[2].toArgb())),
        Expression.stop(20, Expression.color(Palette.Heat[3].toArgb())),
    )

private fun byZoom(vararg stops: Pair<Float, Float>): Expression =
    Expression.interpolate(
        Expression.exponential(1.6f),
        Expression.zoom(),
        *stops.map { Expression.stop(it.first, it.second) }.toTypedArray(),
    )

private fun trailLayers(): List<LineLayer> = listOf(
    trailLayer(
        "$TRAIL-off-bloom",
        onStreet = false,
        width = byZoom(8.5f to 30f, 10f to 24f, 12f to 19f, 13.5f to 22f, 17f to 34f),
        blur = byZoom(8.5f to 24f, 10f to 19f, 12f to 15f, 13.5f to 18f, 17f to 30f),
    ),
    trailLayer(
        "$TRAIL-off-core",
        onStreet = false,
        width = byZoom(8.5f to 9f, 10f to 7.5f, 12f to 5.5f, 13.5f to 6f, 17f to 10f),
        blur = byZoom(8.5f to 3f, 10f to 2f, 12f to 1f, 13.5f to 1f, 17f to 3f),
    ),
    trailLayer(
        "$TRAIL-bloom",
        onStreet = true,
        width = byZoom(8.5f to 30f, 10f to 24f, 12.5f to 22f, 14f to 15f, 15f to 18f, 17f to 34f),
        blur = byZoom(8.5f to 24f, 10f to 19f, 12.5f to 18f, 14f to 11f, 15f to 16f, 17f to 30f),
    ),
    trailLayer(
        "$TRAIL-halo",
        onStreet = true,
        width = byZoom(8.5f to 12f, 10f to 10f, 12.5f to 8f, 14f to 5f, 15f to 6f, 17f to 11f),
        blur = byZoom(8.5f to 8f, 10f to 6.5f, 12.5f to 5f, 14f to 2.5f, 15f to 3.5f, 17f to 7f),
    ),
    trailLayer(
        "$TRAIL-core",
        onStreet = true,
        width = byZoom(8.5f to 6f, 10f to 4.5f, 12.5f to 3f, 14f to 1.6f, 15f to 2f, 17f to 3.5f),
        blur = null,
    ),
)

private fun trailLayer(id: String, onStreet: Boolean, width: Expression, blur: Expression?): LineLayer =
    LineLayer(id, TRAIL)
        .withFilter(Expression.eq(Expression.get(ON_STREET), onStreet))
        .withProperties(
            *listOfNotNull(
                PropertyFactory.lineColor(glowColor()),
                PropertyFactory.lineWidth(width),
                blur?.let { PropertyFactory.lineBlur(it) },
                PropertyFactory.lineOpacity(0f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ).toTypedArray(),
        )

private fun busiestArea(h3: H3Core, cells: List<HeatCell>): LatLon {
    val area = cells.groupBy { h3.cellToParent(it.cell, BUSIEST_AREA_RESOLUTION) }.maxBy { (_, inside) -> inside.sumOf { it.passes } }.value
    val weight = area.sumOf { it.passes }.toDouble()
    val centres = area.map { h3.cellToLatLng(it.cell) to it.passes }
    return LatLon(centres.sumOf { (c, w) -> c.lat * w } / weight, centres.sumOf { (c, w) -> c.lng * w } / weight)
}

private val TRAIL_OPACITY = mapOf(
    "$TRAIL-off-bloom" to 0.15f,
    "$TRAIL-off-core" to 0.75f,
    "$TRAIL-bloom" to 0.3f,
    "$TRAIL-halo" to 0.6f,
    "$TRAIL-core" to 1f,
)

private fun Style.showTrail(shown: Boolean, millis: Long) {
    TRAIL_OPACITY.forEach { (id, opacity) ->
        (getLayer(id) as? LineLayer)?.apply {
            lineOpacityTransition = TransitionOptions(millis, 0)
            setProperties(PropertyFactory.lineOpacity(if (shown) opacity else 0f))
        }
    }
}

private fun lineFeatures(lines: List<GlowLine>): FeatureCollection =
    FeatureCollection.fromFeatures(
        lines.map { line ->
            Feature.fromGeometry(LineString.fromLngLats(line.points.map { Point.fromLngLat(it.lon, it.lat) })).apply {
                addNumberProperty("passes", line.passes)
                addBooleanProperty(ON_STREET, line.onStreet)
            }
        },
    )

private const val TRAIL = "trail"
private const val ROUTE = "route"
private const val ON_STREET = "onStreet"
private const val MORPH_MAX_ZOOM = 12.5
private const val MORPH_MIN_ZOOM = 8.5
private const val QUICK_MILLIS = 150L
private const val LOADER_LIFT_DP = 600f
private const val REVEAL_MILLIS = 450L
private const val SWAP_MILLIS = 200L
private const val MORPH_QUANTUM = 0.05
private const val SUMMARY_SHIFT = 0.2f
private const val SUMMARY_ZOOM = 13.6
private const val LANDING_ZOOM_OUT = 1.4
private const val LANDING_MILLIS = 1_100
private const val FOCUS_ZOOM = 14.5
private const val FOCUS_MILLIS = 700
private const val CONTEXT_MAX_METERS = 1_500.0
private const val BUSIEST_AREA_RESOLUTION = 8
private const val MARKER = "marker"
private const val POSITION = "position"
