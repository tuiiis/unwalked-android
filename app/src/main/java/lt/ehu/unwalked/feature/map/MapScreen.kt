package lt.ehu.unwalked.feature.map

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import lt.ehu.unwalked.core.ui.components.ChipRow
import lt.ehu.unwalked.core.ui.components.PrimaryButton
import lt.ehu.unwalked.core.ui.components.SecondaryButton
import lt.ehu.unwalked.core.ui.components.SkeletonBlock
import lt.ehu.unwalked.core.ui.components.WarningBanner
import lt.ehu.unwalked.core.ui.motion.Motion
import lt.ehu.unwalked.core.ui.theme.Palette
import lt.ehu.unwalked.domain.stats.Period
import lt.ehu.unwalked.tracking.RecordingStatus

data class MapActions(
    val onSearch: () -> Unit = {},
    val onProfile: () -> Unit = {},
    val onPeriod: (Period) -> Unit = {},
    val onFindNew: () -> Unit = {},
    val onStartRecording: () -> Unit = {},
    val onFixRecording: (Boolean) -> Unit = {},
    val onLater: () -> Unit = {},
    val onEndWalk: () -> Unit = {},
)

fun Period.label(): String = when (this) {
    Period.Month -> "Month"
    Period.Quarter -> "Quarter"
    Period.Year -> "Year"
    Period.AllTime -> "All time"
}

@Composable
fun MapScreen(state: MapUiState, actions: MapActions, map: @Composable (Modifier) -> Unit) {
    Column(Modifier.fillMaxSize().background(Palette.Background)) {
        Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SearchBar(onSearch = actions.onSearch, onProfile = actions.onProfile)
            ChipRow(Period.entries, state.period, { it.label() }, actions.onPeriod)
            val stopped = state.recording as? RecordingStatus.Stopped
            var lastStopped by remember { mutableStateOf<RecordingStatus.Stopped?>(null) }
            stopped?.let { lastStopped = it }
            AnimatedVisibility(stopped != null, enter = Motion.appear, exit = Motion.disappear) {
                val stopped = lastStopped ?: return@AnimatedVisibility
                WarningBanner(
                    title = "Recording stopped ${ago(stopped.minutesWithoutFix)}",
                    body = if (stopped.batterySaver) {
                        "Battery saver is on, so the phone paused location. Your map has a gap for this time."
                    } else {
                        "The phone has not sent a location for a while. Your map has a gap for this time."
                    },
                    primaryAction = (if (stopped.batterySaver) "Turn off battery saver" else "Check location settings") to { actions.onFixRecording(stopped.batterySaver) },
                    secondaryAction = "Later" to actions.onLater,
                )
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            map(Modifier.fillMaxSize().semantics { contentDescription = state.summary?.let { "Map of your walks. ${it.headline}. ${it.detail}" } ?: "Map of your walks, empty" })
            BottomCard(state, actions, Modifier.align(Alignment.BottomCenter).padding(12.dp))
        }
    }
}

@Composable
private fun SearchBar(onSearch: () -> Unit, onProfile: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Palette.Panel, RoundedCornerShape(26.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(26.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(52.dp)
                .clickable(role = Role.Button, onClickLabel = "Search places", onClick = onSearch)
                .padding(start = 16.dp)
                .testTag("search"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = Palette.Muted)
            Spacer(Modifier.size(12.dp))
            Text("Search places", color = Palette.Muted, style = MaterialTheme.typography.bodyLarge)
        }
        IconButton(onClick = onProfile, modifier = Modifier.padding(end = 4.dp)) {
            Icon(Icons.Outlined.AccountCircle, contentDescription = "Data and privacy", tint = Palette.Foreground)
        }
    }
}

@Composable
private fun BottomCard(state: MapUiState, actions: MapActions, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Palette.Panel.copy(alpha = 0.96f), MaterialTheme.shapes.large)
            .border(1.dp, Palette.Line, MaterialTheme.shapes.large)
            .animateContentSize(tween(Motion.MEDIUM))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val walk = state.walk
        val summary = state.summary
        when {
            state.loading -> {
                SkeletonBlock(Modifier.fillMaxWidth(0.8f).height(22.dp))
                SkeletonBlock(Modifier.fillMaxWidth(0.55f).height(14.dp))
                Spacer(Modifier.height(6.dp))
                SkeletonBlock(Modifier.fillMaxWidth().height(52.dp))
            }

            walk != null -> {
                Text("Walking", style = MaterialTheme.typography.labelMedium, color = Palette.Warning)
                if (walk.path.isNotEmpty()) {
                    Text(walk.title, style = MaterialTheme.typography.titleMedium)
                    Text("${walk.newCells} new streets on the way · ${walk.unknownPercent}% unwalked", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                }
                Spacer(Modifier.height(6.dp))
                SecondaryButton("End walk", actions.onEndWalk, Modifier.fillMaxWidth())
            }

            summary == null -> {
                Text("Your map is empty", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (state.recording == RecordingStatus.Off) {
                        "Turn on recording and go for a walk. The map fills in as you move."
                    } else {
                        "Recording is on. Go for a walk and the map fills in as you move."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Muted,
                )
                Spacer(Modifier.height(6.dp))
                if (state.recording == RecordingStatus.Off) {
                    PrimaryButton("Start recording", actions.onStartRecording, Modifier.testTag("startRecording"))
                }
            }

            else -> {
                AnimatedContent(summary, transitionSpec = { Motion.crossfade() }, label = "summary") { shown ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(shown.headline, style = MaterialTheme.typography.titleMedium)
                        Text(shown.detail, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                    }
                }
                Spacer(Modifier.height(6.dp))
                PrimaryButton("Find something new", actions.onFindNew, Modifier.testTag("findNew"))
                if (state.recording == RecordingStatus.Off) {
                    SecondaryButton("Start recording", actions.onStartRecording, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private fun ago(minutes: Long): String = when {
    minutes < 60 -> "$minutes min ago"
    minutes < 24 * 60 -> "${minutes / 60} h ago"
    else -> "${minutes / (24 * 60)} days ago"
}
