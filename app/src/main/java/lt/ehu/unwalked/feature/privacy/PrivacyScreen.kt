package lt.ehu.unwalked.feature.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import lt.ehu.unwalked.core.ui.theme.Palette
import lt.ehu.unwalked.data.settings.Retention

data class PrivacyActions(
    val onBack: () -> Unit = {},
    val onBlurHome: (Boolean) -> Unit = {},
    val onStudyConsent: (Boolean) -> Unit = {},
    val onRetention: (Retention) -> Unit = {},
    val onExport: () -> Unit = {},
    val onImportTimeline: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onSample: () -> Unit = {},
    val onSignOut: () -> Unit = {},
)

@Composable
fun PrivacyScreen(state: PrivacyUiState, online: Boolean, actions: PrivacyActions) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var chooseRetention by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
            Text("Data and privacy", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        }
        SwitchRow("Blur home area when sharing", "Radius $HOME_BLUR_METRES m", state.blurHome, actions.onBlurHome)
        Divider()
        LinkRow("Keep history", state.retention.label, onClick = { chooseRetention = true })
        Divider()
        LinkRow("Export my data", "JSON file with your visits and walked streets", enabled = !state.busy, onClick = actions.onExport)
        Divider()
        LinkRow(
            "Import Google Timeline",
            "Timeline.json from Settings › Location › Timeline › Export. Only walks are added",
            enabled = !state.busy,
            onClick = actions.onImportTimeline,
        )
        Divider()
        LinkRow(
            "Delete all location history",
            if (online) "Visits, walked streets and statistics" else "Needs an internet connection",
            color = MaterialTheme.colorScheme.error,
            enabled = online && !state.busy,
            onClick = { confirmDelete = true },
            tag = "deleteHistory",
        )
        Divider()
        SectionTitle("Study")
        SwitchRow(
            "Help the study",
            "Send anonymous reactions to recommendations: the type, whether you opened it or chose Not now, and the place category. Never your location.",
            state.studyConsent,
            actions.onStudyConsent,
        )
        Divider()
        SectionTitle("Demo")
        LinkRow("Load a sample week in Vilnius", "Adds a week of walks so the map, stats and recommendations have data", enabled = !state.busy, onClick = actions.onSample)
        Divider()
        SectionTitle("Account")
        LinkRow("Sign out", state.email ?: "", onClick = actions.onSignOut)
        Spacer(Modifier.height(96.dp))
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all history?") },
            text = { Text("All visits, walked streets and your statistics will be removed from this phone. This cannot be undone. Export your data first if you want a copy.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        actions.onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Danger, contentColor = Palette.OnDanger),
                    modifier = Modifier.testTag("confirmDelete"),
                ) { Text("Delete") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    if (chooseRetention) {
        AlertDialog(
            onDismissRequest = { chooseRetention = false },
            title = { Text("Keep history") },
            text = {
                Column {
                    Retention.entries.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = option == state.retention, role = Role.RadioButton) {
                                    chooseRetention = false
                                    actions.onRetention(option)
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == state.retention, onClick = null)
                            Text(option.label, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                    Text("Older visits are removed automatically.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
            },
            confirmButton = {},
            dismissButton = { OutlinedButton(onClick = { chooseRetention = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = Palette.Line, modifier = Modifier.padding(horizontal = 16.dp))

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = Palette.Accent,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun LinkRow(
    title: String,
    subtitle: String,
    color: Color = Palette.Foreground,
    enabled: Boolean = true,
    onClick: () -> Unit,
    tag: String = title,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) color else color.copy(alpha = 0.45f))
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Palette.Muted)
    }
}

private const val HOME_BLUR_METRES = 300
