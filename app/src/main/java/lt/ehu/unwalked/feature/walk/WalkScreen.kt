package lt.ehu.unwalked.feature.walk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import lt.ehu.unwalked.core.ui.components.PrimaryButton
import lt.ehu.unwalked.core.ui.components.SegmentedChoice
import lt.ehu.unwalked.core.ui.theme.Palette
import lt.ehu.unwalked.domain.route.RoutePreference
import lt.ehu.unwalked.domain.route.RouteShape
import lt.ehu.unwalked.domain.validation.WalkLength

data class WalkActions(
    val onLength: (Double) -> Unit = {},
    val onShape: (RouteShape) -> Unit = {},
    val onPreference: (RoutePreference) -> Unit = {},
    val onBuild: () -> Unit = {},
)

@Composable
fun WalkScreen(state: WalkFormState, actions: WalkActions) {
    val summary = "${WalkLength.kilometres(state.kilometres)} · about ${WalkLength.duration(state.minutes)}"
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("New walk", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text("Length", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(WalkLength.kilometres(state.kilometres), style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), modifier = Modifier.testTag("lengthValue"))
        }
        Text("about ${WalkLength.duration(state.minutes)} on foot", style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = Palette.Muted, modifier = Modifier.align(Alignment.End))
        Slider(
            value = state.kilometres.toFloat(),
            onValueChange = { actions.onLength(it.toDouble()) },
            valueRange = WalkLength.MIN_KM.toFloat()..WalkLength.MAX_KM.toFloat(),
            steps = WalkLength.STEPS,
            modifier = Modifier.testTag("lengthSlider").semantics {
                contentDescription = "Walk length"
                stateDescription = summary
            },
        )
        Row(Modifier.fillMaxWidth()) {
            Text("${WalkLength.kilometres(WalkLength.MIN_KM)} · ${WalkLength.duration(WalkLength.minutes(WalkLength.MIN_KM))}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.weight(1f))
            Text("${WalkLength.kilometres(WalkLength.MAX_KM)} · ${WalkLength.duration(WalkLength.minutes(WalkLength.MAX_KM))}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        Text("The route starts where you are.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        Label("Route shape")
        SegmentedChoice(RouteShape.entries, state.shape, { it.label }, actions.onShape)
        Label("Prefer")
        SegmentedChoice(RoutePreference.entries, state.preference, { it.label }, actions.onPreference)
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Build route", actions.onBuild, modifier = Modifier.testTag("buildRoute"))
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.Muted, modifier = Modifier.padding(top = 8.dp))
}
