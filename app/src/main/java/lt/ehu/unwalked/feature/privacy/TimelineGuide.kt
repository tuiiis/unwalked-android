package lt.ehu.unwalked.feature.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import lt.ehu.unwalked.core.ui.theme.Palette

val TimelineExportSteps = listOf("Settings", "Location", "Location services", "Timeline", "Export Timeline data")

@Composable
fun TimelineGuide(onOpenSettings: () -> Unit, onChooseFile: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export your Timeline first") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Google keeps Timeline on your phone. Export it in Android settings, then come back and choose the file.", style = MaterialTheme.typography.bodyMedium)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, Palette.Line, MaterialTheme.shapes.medium)
                        .testTag("timelineSteps"),
                ) {
                    TimelineExportSteps.forEachIndexed { index, step ->
                        if (index > 0) HorizontalDivider(color = Palette.Line)
                        StepRow(index + 1, step, last = index == TimelineExportSteps.lastIndex)
                    }
                }
                Text("Open settings takes you straight to Location. Save Timeline.json, then come back and tap Choose file.", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            }
        },
        confirmButton = { Button(onClick = onChooseFile) { Text("Choose file") } },
        dismissButton = { OutlinedButton(onClick = onOpenSettings) { Text("Open settings") } },
    )
}

@Composable
private fun StepRow(number: Int, label: String, last: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (last) Palette.AccentDeep else Palette.Panel, if (last) MaterialTheme.shapes.medium else MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$number", style = MaterialTheme.typography.labelLarge, color = Palette.Muted, modifier = Modifier.width(24.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
            color = if (last) Palette.Accent else Palette.Foreground,
            modifier = Modifier.weight(1f),
        )
        if (!last) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Palette.Muted)
    }
}
