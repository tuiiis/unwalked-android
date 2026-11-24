package lt.ehu.unwalked.feature.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import lt.ehu.unwalked.core.ui.components.ChipRow
import lt.ehu.unwalked.core.ui.components.MessageState
import lt.ehu.unwalked.core.ui.components.SkeletonBlock
import lt.ehu.unwalked.core.ui.components.StatTile
import lt.ehu.unwalked.core.ui.theme.Palette
import lt.ehu.unwalked.domain.recommendation.CategoryGroup
import lt.ehu.unwalked.domain.stats.City
import lt.ehu.unwalked.domain.stats.FunFact
import lt.ehu.unwalked.domain.stats.Period
import lt.ehu.unwalked.domain.stats.PlaceHabits
import lt.ehu.unwalked.domain.stats.Travel
import lt.ehu.unwalked.feature.map.label

@Composable
fun StatsScreen(state: StatsUiState, onPeriod: (Period) -> Unit, onOpenMap: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Stats", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        ChipRow(Period.entries, state.period, { it.label() }, onPeriod)
        val stats = state.stats
        val range = state.range
        when {
            state.loading || stats == null || range == null -> {
                SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(3) { SkeletonBlock(Modifier.weight(1f).height(64.dp)) }
                }
                SkeletonBlock(Modifier.fillMaxWidth().height(220.dp))
            }

            stats.isEmpty -> {
                Header(range.title, range.subtitle)
                MessageState(Icons.Outlined.QueryStats, "No walks in this period", "Stats appear after the first recorded walk.", action = "Open the map" to onOpenMap)
            }

            else -> {
                Header(range.title, range.subtitle)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(stats.newCells.toString(), "new streets", Modifier.weight(1f))
                    StatTile(stats.newPlaces.toString(), "new places", Modifier.weight(1f))
                    StatTile(String.format(Locale.US, "%.0f km", stats.walkedKm), "walked", Modifier.weight(1f))
                }
                stats.travel?.let { TravelCard(it) }
                stats.facts.filterIsInstance<FunFact.Distance>().firstOrNull()?.let { DistanceCard(it) }
                stats.facts.filterIsInstance<FunFact.Pace>().firstOrNull()?.let { PaceCard(it) }
                stats.facts.filterIsInstance<FunFact.TimeOfDay>().firstOrNull()?.let { TimeOfDayCard(it) }
                stats.facts.filterIsInstance<FunFact.FavouriteDay>().firstOrNull()?.let { WeekCard(it) }
                state.places?.takeIf { it.favourites.isNotEmpty() }?.let { PlacesCard(it) }
                val records = stats.facts.filter { it is FunFact.BestDay || it is FunFact.Streak }
                if (records.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        records.forEach { RecordCard(it, Modifier.weight(1f)) }
                    }
                }
                Column(Modifier.fillMaxWidth()) {
                    StatRow(
                        "Explored your usual area",
                        "${stats.exploredPercent}%" + (stats.exploredDelta?.takeIf { it != 0 }?.let { if (it > 0) ", up $it% this period" else ", down ${-it}%" } ?: ""),
                    )
                    HorizontalDivider(color = Palette.Line)
                    StatRow("Streets and squares you have visited", if (state.period == Period.AllTime || stats.totalCells == stats.visitedCells) "${stats.totalCells} in total" else "${stats.totalCells} in total, ${stats.visitedCells} in this period")
                    HorizontalDivider(color = Palette.Line)
                    StatRow(
                        "Longest walk",
                        stats.longestWalk?.let { String.format(Locale.US, "%.1f km on %s", it.kilometres, state.longestWalkDay) } ?: "No walks longer than 300 m yet",
                    )
                }
                Text("Cities from GeoNames, CC BY 4.0", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
            }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().background(Palette.Panel, MaterialTheme.shapes.large).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun Title(text: String, colour: Color = Palette.Foreground) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = colour)
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
}

@Composable
private fun TravelCard(travel: Travel) {
    Card {
        Title(if (travel.cities.size == 1) "Home turf: ${travel.home.name}" else "${travel.cities.size} cities, ${travel.countries.size} ${if (travel.countries.size == 1) "country" else "countries"}")
        Globe(travel, Modifier.fillMaxWidth().aspectRatio(1.25f).semantics { contentDescription = "Globe with ${travel.cities.size} cities you walked in" })
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            travel.countries.forEach { country ->
                Text(
                    "${flag(country.code)} ${country.name}",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.background(Palette.PanelHigh, MaterialTheme.shapes.small).padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        val local = travel.homeShare
        Row(Modifier.fillMaxWidth().height(10.dp).clip(MaterialTheme.shapes.small)) {
            if (local > 0) Box(Modifier.weight(local.toFloat()).fillMaxSize().background(Palette.Accent))
            if (local < 100) Box(Modifier.weight((100 - local).toFloat()).fillMaxSize().background(Palette.Warning))
        }
        Text(
            when {
                travel.awayDays == 0 -> "A true local: every walk in ${travel.home.name}"
                local >= 80 -> "Mostly local: $local% of days in ${travel.home.name}, ${travel.awayDays} ${days(travel.awayDays)} away"
                else -> "Explorer: ${travel.awayDays} ${days(travel.awayDays)} walking outside ${travel.home.name}"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        travel.cities.drop(1).take(MAX_LISTED_CITIES).takeIf { it.isNotEmpty() }?.let { others ->
            Caption(others.joinToString(" · ") { "${it.city.name} ${it.days}d" })
        }
    }
}

@Composable
private fun Globe(travel: Travel, modifier: Modifier) {
    val centreLat = Math.toRadians(travel.home.lat.coerceIn(-60.0, 60.0) - GLOBE_TILT)
    val centreLon = Math.toRadians(travel.home.lon)
    fun project(lat: Double, lon: Double): Triple<Float, Float, Boolean> {
        val phi = Math.toRadians(lat)
        val lambda = Math.toRadians(lon) - centreLon
        val x = cos(phi) * sin(lambda)
        val y = cos(centreLat) * sin(phi) - sin(centreLat) * cos(phi) * cos(lambda)
        val visible = sin(centreLat) * sin(phi) + cos(centreLat) * cos(phi) * cos(lambda) > 0
        return Triple(x.toFloat(), -y.toFloat(), visible)
    }
    val most = travel.cities.maxOf { it.days }.toFloat()
    val extent = travel.cities.map { project(it.city.lat, it.city.lon) }.filter { it.third }.maxOfOrNull { maxOf(abs(it.first), abs(it.second)) } ?: 0f
    val zoom = (GLOBE_FILL / extent.coerceAtLeast(0.01f)).coerceIn(1f, GLOBE_MAX_ZOOM)
    val labels = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
        }
    }
    Box(modifier.clip(MaterialTheme.shapes.medium)) {
        Canvas(Modifier.fillMaxSize()) {
            labels.textSize = 12.dp.toPx()
            val radius = minOf(size.width, size.height) / 2 * 0.92f * zoom
            val centre = Offset(size.width / 2, size.height / 2)
            drawCircle(Brush.radialGradient(listOf(Palette.AccentDeep, Palette.Background), centre - Offset(radius / 3, radius / 3), radius * 1.6f), radius, centre)
            drawCircle(Palette.Accent.copy(alpha = 0.35f), radius, centre, style = Stroke(1.dp.toPx()))
            fun line(points: List<Pair<Double, Double>>) {
                var path: Path? = null
                points.forEach { (lat, lon) ->
                    val (x, y, visible) = project(lat, lon)
                    val at = centre + Offset(x * radius, y * radius)
                    if (!visible) {
                        path?.let { drawPath(it, Palette.Accent.copy(alpha = 0.14f), style = Stroke(0.8.dp.toPx())) }
                        path = null
                    } else if (path == null) {
                        path = Path().apply { moveTo(at.x, at.y) }
                    } else {
                        path?.lineTo(at.x, at.y)
                    }
                }
                path?.let { drawPath(it, Palette.Accent.copy(alpha = 0.14f), style = Stroke(0.8.dp.toPx())) }
            }
            for (lon in -180..180 step 20) line((-90..90 step 3).map { it.toDouble() to lon.toDouble() })
            for (lat in -80..80 step 20) line((-180..180 step 3).map { lat.toDouble() to it.toDouble() })
            travel.cities.drop(1).forEach { arc(travel.home, it.city, centre, radius, ::project) }
            travel.cities.forEach { stay ->
                val (x, y, visible) = project(stay.city.lat, stay.city.lon)
                if (!visible) return@forEach
                val at = centre + Offset(x * radius, y * radius)
                val dot = 3.dp.toPx() + 6.dp.toPx() * sqrt(stay.days / most)
                val colour = if (stay.city == travel.home) Palette.Accent else Palette.Warning
                drawCircle(colour.copy(alpha = 0.25f), dot * 2.2f, at)
                drawCircle(colour, dot, at)
            }
            val placed = mutableListOf<Offset>()
            travel.cities.forEach { stay ->
                if (placed.size >= MAX_LABELS) return@forEach
                val (x, y, visible) = project(stay.city.lat, stay.city.lon)
                if (!visible) return@forEach
                val at = centre + Offset(x * radius, y * radius)
                if (placed.any { (it - at).getDistance() < LABEL_GAP_DP.dp.toPx() }) return@forEach
                placed += at
                drawContext.canvas.nativeCanvas.drawText(stay.city.name, at.x + 10.dp.toPx(), at.y + 4.dp.toPx(), labels)
            }
        }
    }
}

private fun DrawScope.arc(from: City, to: City, centre: Offset, radius: Float, project: (Double, Double) -> Triple<Float, Float, Boolean>) {
    val path = Path()
    var started = false
    val reach = minOf(1f, (ln(1 + abs(to.lon - from.lon) + abs(to.lat - from.lat)) / 3).toFloat())
    for (step in 0..ARC_STEPS) {
        val t = step / ARC_STEPS.toDouble()
        val (x, y, visible) = project(from.lat + (to.lat - from.lat) * t, from.lon + (to.lon - from.lon) * t)
        if (!visible) continue
        val lift = 1 + 0.08f * sin(PI * t).toFloat() * reach
        val at = centre + Offset(x * radius * lift, y * radius * lift)
        if (!started) path.moveTo(at.x, at.y) else path.lineTo(at.x, at.y)
        started = true
    }
    drawPath(path, Palette.Warning.copy(alpha = 0.7f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
}

private fun flag(code: String): String =
    if (code.length == 2 && code.all { it in 'A'..'Z' }) code.map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("") else ""

private fun days(count: Int) = if (count == 1) "day" else "days"

@Composable
private fun DistanceCard(fact: FunFact.Distance) {
    Card {
        Text(String.format(Locale.US, "%.0f km", fact.kilometres), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            if (fact.times < 1.5) "That is about ${fact.landmark.name}" else String.format(Locale.US, "That is %.0f times %s", fact.times, fact.landmark.name),
            style = MaterialTheme.typography.bodyMedium,
        )
        fact.next?.let { next ->
            LinearProgressIndicator(
                progress = { (fact.kilometres / next.kilometres).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small),
                color = Palette.Accent,
                trackColor = Palette.PanelHigh,
                drawStopIndicator = {},
            )
            Caption(String.format(Locale.US, "%.0f km more and you would have walked %s", next.kilometres - fact.kilometres, next.name))
        }
    }
}

@Composable
private fun PaceCard(fact: FunFact.Pace) {
    val title = when {
        fact.averageKmh < RELAXED_KMH -> "Relaxed stroller"
        fact.averageKmh < BRISK_KMH -> "Steady walker"
        else -> "Brisk walker"
    }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Canvas(Modifier.width(120.dp).height(70.dp).semantics { contentDescription = String.format(Locale.US, "Average pace %.1f kilometres per hour", fact.averageKmh) }) {
                val stroke = 10.dp.toPx()
                val box = Size(size.width - stroke, size.width - stroke)
                val topLeft = Offset(stroke / 2, stroke / 2)
                drawArc(Palette.PanelHigh, 180f, 180f, false, topLeft, box, style = Stroke(stroke, cap = StrokeCap.Round))
                val share = ((fact.averageKmh - GAUGE_MIN) / (GAUGE_MAX - GAUGE_MIN)).toFloat().coerceIn(0f, 1f)
                drawArc(Palette.Warning, 180f, 180f * share, false, topLeft, box, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column {
                Text(String.format(Locale.US, "%.1f km/h", fact.averageKmh), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.Warning)
            }
        }
        Caption(String.format(Locale.US, "Your fastest walk: %.1f km at %.1f km/h. A typical walking pace is about 5 km/h.", fact.fastestKm, fact.fastestKmh))
    }
}

@Composable
private fun TimeOfDayCard(fact: FunFact.TimeOfDay) {
    Card {
        Title(fact.band.title, Palette.Warning)
        Text("${fact.share}% of your distance ${fact.band.phrase}", style = MaterialTheme.typography.bodyMedium)
        Bars(fact.byHour, highlight = { fact.band.contains(it) }, Modifier.fillMaxWidth().height(56.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0", "6", "12", "18", "24").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = Palette.Muted) }
        }
    }
}

@Composable
private fun WeekCard(fact: FunFact.FavouriteDay) {
    Card {
        Text("${fact.day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} is your walking day", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Caption("${fact.share}% of the distance")
        Bars(fact.byWeekday, highlight = { it == fact.day.value - 1 }, Modifier.fillMaxWidth().height(64.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            DayOfWeek.entries.forEach { Text(it.getDisplayName(TextStyle.NARROW, Locale.ENGLISH), style = MaterialTheme.typography.labelSmall, color = Palette.Muted) }
        }
    }
}

@Composable
private fun PlacesCard(habits: PlaceHabits) {
    Card {
        Title("Your spots")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val total = habits.types.sumOf { it.second }.toFloat()
            Canvas(Modifier.size(96.dp).semantics { contentDescription = habits.types.joinToString { "${it.first.label} ${(100 * it.second / total).toInt()}%" } }) {
                var start = -90f
                habits.types.forEach { (group, visits) ->
                    val sweep = 360f * visits / total
                    drawArc(colour(group), start, sweep - 2f, false, Offset(8.dp.toPx(), 8.dp.toPx()), Size(size.width - 16.dp.toPx(), size.height - 16.dp.toPx()), style = Stroke(14.dp.toPx()))
                    start += sweep
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                habits.types.forEach { (group, visits) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(10.dp).background(colour(group), CircleShape))
                        Text("${group.label} ${(100 * visits / total).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        habits.favourites.forEachIndexed { i, place ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = colour(place.group), modifier = Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(place.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Caption(place.group.label)
                }
                Text("${place.visits}×", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
            }
        }
    }
}

private fun colour(group: CategoryGroup): Color = when (group) {
    CategoryGroup.Food -> Palette.Warning
    CategoryGroup.Nature -> Color(0xFF7FD8A6)
    CategoryGroup.Culture -> Color(0xFFC9A7FF)
    CategoryGroup.Shops -> Palette.Accent
    else -> Palette.Muted
}

@Composable
private fun Bars(values: List<Double>, highlight: (Int) -> Boolean, modifier: Modifier) {
    val top = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Canvas(modifier) {
        val slot = size.width / values.size
        val width = slot * 0.62f
        values.forEachIndexed { i, value ->
            val height = (value / top * size.height).toFloat().coerceAtLeast(2.dp.toPx())
            drawRoundRect(
                color = if (highlight(i)) Palette.Warning else Palette.AccentDeep,
                topLeft = Offset(i * slot + (slot - width) / 2, size.height - height),
                size = Size(width, height),
                cornerRadius = CornerRadius(width / 3),
            )
        }
    }
}

@Composable
private fun RecordCard(fact: FunFact, modifier: Modifier) {
    val (icon: ImageVector, number: String, caption: String) = when (fact) {
        is FunFact.BestDay -> Triple(Icons.Outlined.AutoAwesome, fact.newStreets.toString(), "new streets on ${fact.date.dayOfMonth} ${fact.date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}, your best day")
        is FunFact.Streak -> Triple(Icons.Outlined.LocalFireDepartment, fact.days.toString(), "days in a row with a walk")
        else -> return
    }
    Column(modifier.background(Palette.Panel, MaterialTheme.shapes.large).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = Palette.Warning, modifier = Modifier.size(22.dp))
        Text(number, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Caption(caption)
    }
}

@Composable
private fun StatRow(title: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
    }
}

private const val MAX_LABELS = 5
private const val LABEL_GAP_DP = 28
private const val GLOBE_FILL = 0.8f
private const val GLOBE_MAX_ZOOM = 6f
private const val MAX_LISTED_CITIES = 6
private const val GLOBE_TILT = 18.0
private const val ARC_STEPS = 32
private const val RELAXED_KMH = 4.0
private const val BRISK_KMH = 5.5
private const val GAUGE_MIN = 2.0
private const val GAUGE_MAX = 7.0
