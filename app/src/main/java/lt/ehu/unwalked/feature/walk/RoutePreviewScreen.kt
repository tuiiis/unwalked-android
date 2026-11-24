package lt.ehu.unwalked.feature.walk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.WrongLocation
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.launch
import lt.ehu.unwalked.core.ui.components.MessageState
import lt.ehu.unwalked.core.ui.components.PrimaryButton
import lt.ehu.unwalked.core.ui.components.SecondaryButton
import lt.ehu.unwalked.core.ui.components.SkeletonBlock
import lt.ehu.unwalked.core.ui.components.StatTile
import lt.ehu.unwalked.core.ui.theme.Palette
import lt.ehu.unwalked.domain.recommendation.CategoryNames
import lt.ehu.unwalked.domain.recommendation.PlaceCandidate
import lt.ehu.unwalked.domain.route.WalkRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePreviewScreen(
    state: RouteUiState,
    onBack: () -> Unit,
    onChange: () -> Unit,
    onStart: (WalkRoute) -> Unit,
    onRetry: () -> Unit,
    onAccept: () -> Unit,
    map: @Composable (Modifier, WalkRoute?, Long?, (Long?) -> Unit) -> Unit,
) {
    val route = (state as? RouteUiState.Ready)?.route ?: (state as? RouteUiState.Closest)?.route
    var selected by remember(route) { mutableStateOf<Long?>(null) }
    var peek by remember { mutableStateOf(SHEET_MIN_PEEK) }
    val density = LocalDensity.current
    val scaffold = rememberBottomSheetScaffoldState()
    val scope = rememberCoroutineScope()
    BottomSheetScaffold(
        scaffoldState = scaffold,
        sheetPeekHeight = peek,
        sheetContainerColor = Palette.Background,
        sheetContent = {
            LazyColumn(Modifier.fillMaxWidth().testTag("routeSheet")) {
                item {
                    Column(
                        Modifier
                            .onSizeChanged { peek = with(density) { it.height.toDp() } + SHEET_HANDLE }
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) { Summary(state, onChange, onStart, onRetry, onAccept) }
                }
                if (route != null && route.places.isNotEmpty()) {
                    item {
                        Text(
                            "New places on this route",
                            style = MaterialTheme.typography.titleSmall,
                            color = Palette.Muted,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
                        )
                    }
                    items(route.places, key = { it.id }) { place ->
                        PlaceRow(place, place.id == selected) {
                            selected = place.id
                            scope.launch { scaffold.bottomSheetState.partialExpand() }
                        }
                        HorizontalDivider(color = Palette.Line, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    item { Box(Modifier.height(24.dp)) }
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize().background(Palette.Background)) {
            Row(Modifier.statusBarsPadding().padding(start = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                Text("Your route", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                map(Modifier.fillMaxSize(), route, selected) { selected = it }
                if (state is RouteUiState.Building || state is RouteUiState.Slow) {
                    Row(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(12.dp)
                            .background(Palette.Panel, MaterialTheme.shapes.extraLarge)
                            .border(1.dp, Palette.Line, MaterialTheme.shapes.extraLarge)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Looking for streets you have not walked", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                route?.places?.firstOrNull { it.id == selected }?.let { place ->
                    SelectedPlace(place, Modifier.align(Alignment.TopCenter).padding(12.dp)) { selected = null }
                }
            }
        }
    }
}

@Composable
private fun Summary(state: RouteUiState, onChange: () -> Unit, onStart: (WalkRoute) -> Unit, onRetry: () -> Unit, onAccept: () -> Unit) {
    when (state) {
        RouteUiState.Building -> {
            SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { repeat(3) { SkeletonBlock(Modifier.weight(1f).height(64.dp)) } }
            SkeletonBlock(Modifier.fillMaxWidth().height(52.dp))
        }

        RouteUiState.Slow -> MessageState(Icons.Outlined.HourglassEmpty, "This takes longer than usual", "", action = "Try a shorter walk" to onChange)

        is RouteUiState.Failed -> MessageState(Icons.Outlined.WrongLocation, "Could not build a route", state.message, action = "Try again" to onRetry)

        is RouteUiState.Ready -> {
            Text(state.route.title, style = MaterialTheme.typography.titleMedium)
            RouteTiles(state.route)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Change", onChange, Modifier.weight(1f))
                PrimaryButton("Start walk", { onStart(state.route) }, Modifier.weight(1f))
            }
        }

        is RouteUiState.Closest -> {
            val length = kilometres(state.route)
            Text("Closest route is $length", style = MaterialTheme.typography.titleMedium)
            RouteTiles(state.route)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Change", onChange, Modifier.weight(1f))
                PrimaryButton("Use $length", onAccept, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PlaceRow(place: PlaceCandidate, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) Palette.AccentDeep else Palette.Background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(place.name, style = MaterialTheme.typography.titleSmall)
        Text(CategoryNames.pretty(place.category), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
    }
}

@Composable
private fun SelectedPlace(place: PlaceCandidate, modifier: Modifier, onClose: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Palette.Panel, MaterialTheme.shapes.medium)
            .border(1.dp, Palette.Line, MaterialTheme.shapes.medium)
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
            .testTag("selectedPlace"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(place.name, style = MaterialTheme.typography.titleSmall)
            Text(CategoryNames.pretty(place.category), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Close") }
    }
}

@Composable
private fun RouteTiles(route: WalkRoute) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile(route.newCells.toString(), "new streets", Modifier.weight(1f))
        StatTile(route.newPlaces.toString(), if (route.newPlaces == 1) "new place" else "new places", Modifier.weight(1f))
        StatTile("${route.unknownPercent}%", "unwalked", Modifier.weight(1f))
    }
}

private fun kilometres(route: WalkRoute): String = String.format(Locale.US, "%.1f km", route.metres / 1000)

private val SHEET_HANDLE: Dp = 48.dp
private val SHEET_MIN_PEEK: Dp = 240.dp
