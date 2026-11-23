package lt.ehu.unwalked.feature.walk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import lt.ehu.unwalked.core.ui.components.PrimaryButton
import lt.ehu.unwalked.core.ui.components.SegmentedChoice
import lt.ehu.unwalked.core.ui.theme.Palette
import lt.ehu.unwalked.domain.route.RoutePreference
import lt.ehu.unwalked.domain.route.RouteShape
import lt.ehu.unwalked.domain.validation.WalkLength
import lt.ehu.unwalked.domain.validation.WalkMeasure

data class WalkActions(
    val onMeasure: (WalkMeasure) -> Unit = {},
    val onSlider: (Double) -> Unit = {},
    val onText: (String) -> Unit = {},
    val onShape: (RouteShape) -> Unit = {},
    val onPreference: (RoutePreference) -> Unit = {},
    val onBuild: () -> Unit = {},
)

@Composable
fun WalkScreen(state: WalkFormState, actions: WalkActions) {
    val unit = state.measure.unit
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("New walk", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Label("Set by")
        SegmentedChoice(WalkMeasure.entries, state.measure, { if (it == WalkMeasure.Distance) "Distance" else "Time" }, actions.onMeasure)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Length", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(display(state), style = MaterialTheme.typography.titleSmall)
        }
        Slider(
            value = state.slider.toFloat().coerceIn(state.measure.min.toFloat(), state.measure.max.toFloat()),
            onValueChange = { actions.onSlider(it.toDouble()) },
            valueRange = state.measure.min.toFloat()..state.measure.max.toFloat(),
            modifier = Modifier.testTag("lengthSlider").semantics {
                contentDescription = "Walk length"
                stateDescription = display(state)
            },
        )
        Row(Modifier.fillMaxWidth()) {
            Text("${WalkLength.format(state.measure.min)} $unit", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.weight(1f))
            Text("${WalkLength.format(state.measure.max)} $unit", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        OutlinedTextField(
            value = state.text,
            onValueChange = actions.onText,
            label = { Text("Or type, $unit") },
            singleLine = true,
            isError = state.error != null,
            supportingText = {
                val message = state.error
                if (message != null) Text(message, modifier = Modifier.semantics { error(message) }) else Text("The route starts where you are.")
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().testTag("lengthInput"),
        )
        Label("Route shape")
        SegmentedChoice(RouteShape.entries, state.shape, { it.label }, actions.onShape)
        Label("Prefer")
        SegmentedChoice(RoutePreference.entries, state.preference, { it.label }, actions.onPreference)
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Build route", actions.onBuild, enabled = state.canBuild, modifier = Modifier.testTag("buildRoute"))
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.Muted, modifier = Modifier.padding(top = 8.dp))
}

private fun display(state: WalkFormState): String = when (state.measure) {
    WalkMeasure.Distance -> "${WalkLength.format(state.slider)} km"
    WalkMeasure.Time -> {
        val minutes = state.slider.toInt()
        if (minutes >= 60) "${minutes / 60} h ${(minutes % 60).takeIf { it > 0 }?.let { "$it min" } ?: ""}".trim() else "$minutes min"
    }
}
