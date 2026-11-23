package lt.ehu.unwalked.feature.walk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.WrongLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import lt.ehu.unwalked.core.ui.components.MessageState
import lt.ehu.unwalked.core.ui.components.PrimaryButton
import lt.ehu.unwalked.core.ui.components.SecondaryButton
import lt.ehu.unwalked.core.ui.components.SkeletonBlock
import lt.ehu.unwalked.core.ui.components.StatTile
import lt.ehu.unwalked.core.ui.theme.Palette
import lt.ehu.unwalked.domain.route.WalkRoute

@Composable
fun RoutePreviewScreen(
    state: RouteUiState,
    onBack: () -> Unit,
    onChange: () -> Unit,
    onStart: (WalkRoute) -> Unit,
    onRetry: () -> Unit,
    map: @Composable (Modifier, WalkRoute?) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Palette.Background)) {
        Row(Modifier.statusBarsPadding().padding(start = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
            Text("Your route", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            map(Modifier.fillMaxSize(), (state as? RouteUiState.Ready)?.route)
            if (state is RouteUiState.Building) {
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
        }
        Column(Modifier.background(Palette.Background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state) {
                RouteUiState.Building -> {
                    SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { repeat(3) { SkeletonBlock(Modifier.weight(1f).height(64.dp)) } }
                    SkeletonBlock(Modifier.fillMaxWidth().height(52.dp))
                }
                is RouteUiState.Failed -> MessageState(Icons.Outlined.WrongLocation, "Could not build a route", state.message, action = "Try again" to onRetry)
                is RouteUiState.Ready -> {
                    Text(state.route.title, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(state.route.newCells.toString(), "new streets", Modifier.weight(1f))
                        StatTile(state.route.newPlaces.toString(), if (state.route.newPlaces == 1) "new place" else "new places", Modifier.weight(1f))
                        StatTile("${state.route.unknownPercent}%", "unwalked", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("Change", onChange, Modifier.weight(1f))
                        PrimaryButton("Start walk", { onStart(state.route) }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
