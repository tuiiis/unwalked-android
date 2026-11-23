package lt.ehu.unwalked.core.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import lt.ehu.unwalked.core.ui.theme.Palette

@Composable
fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option), style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Palette.Background.copy(alpha = 0.85f),
                    labelColor = Palette.Foreground,
                    selectedContainerColor = Palette.AccentDeep,
                    selectedLabelColor = Palette.Foreground,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = option == selected,
                    borderColor = Palette.Line,
                    selectedBorderColor = Palette.Accent,
                ),
            )
        }
    }
}
