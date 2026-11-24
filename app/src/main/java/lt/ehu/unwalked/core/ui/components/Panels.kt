package lt.ehu.unwalked.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import lt.ehu.unwalked.core.ui.motion.Motion
import lt.ehu.unwalked.core.ui.theme.Palette

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .background(Palette.Panel, MaterialTheme.shapes.medium)
            .border(1.dp, Palette.Line, MaterialTheme.shapes.medium)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Palette.Panel, MaterialTheme.shapes.small)
            .border(1.dp, Palette.Line, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        AnimatedContent(value, transitionSpec = { Motion.counter(initialState.leadingNumber(), targetState.leadingNumber()) }, label = "stat") { shown ->
            Text(shown, style = MaterialTheme.typography.titleLarge)
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
fun WarningBanner(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    primaryAction: Pair<String, () -> Unit>? = null,
    secondaryAction: Pair<String, () -> Unit>? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Palette.WarningBackground, MaterialTheme.shapes.small)
            .border(1.dp, Palette.Warning.copy(alpha = 0.5f), MaterialTheme.shapes.small)
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.Warning)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.Foreground, modifier = Modifier.padding(top = 2.dp, end = 8.dp))
        Row {
            primaryAction?.let { (label, action) -> TextButton(onClick = action) { Text(label, color = Palette.Warning) } }
            secondaryAction?.let { (label, action) -> TextButton(onClick = action) { Text(label, color = Palette.Warning) } }
        }
    }
}

@Composable
fun MessageState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: Pair<String, () -> Unit>? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Palette.Muted)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center)
        action?.let { (label, onClick) ->
            Spacer(Modifier.width(0.dp))
            SecondaryButton(label, onClick)
        }
    }
}

private fun String.leadingNumber(): Double = takeWhile { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
