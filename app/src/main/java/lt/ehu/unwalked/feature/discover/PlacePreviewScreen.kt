package lt.ehu.unwalked.feature.discover

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import lt.ehu.unwalked.core.ui.components.PrimaryButton
import lt.ehu.unwalked.core.ui.theme.Palette
import lt.ehu.unwalked.domain.recommendation.CategoryNames

@Composable
fun PlacePreviewScreen(
    name: String,
    category: String,
    reason: String,
    photo: suspend () -> String?,
    onBack: () -> Unit,
    onRoute: () -> Unit,
    map: @Composable (Modifier) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Palette.Background)) {
        Row(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .height(52.dp)
                .background(Palette.Panel, RoundedCornerShape(26.dp))
                .border(1.dp, Palette.Line, RoundedCornerShape(26.dp))
                .clickable(role = Role.Button, onClickLabel = "Back to places", onClick = onBack),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to places") }
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Icon(Icons.Outlined.Search, contentDescription = null, tint = Palette.Muted, modifier = Modifier.padding(end = 16.dp))
        }
        Box(Modifier.fillMaxWidth().weight(1f)) { map(Modifier.fillMaxSize()) }
        Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(72.dp).background(Brush.linearGradient(listOf(Palette.AccentDeep, Palette.PanelHigh)), MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Place, contentDescription = null, tint = Palette.Accent)
                    val url by produceState<String?>(null) { value = photo() }
                    url?.let {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(it).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize().clip(MaterialTheme.shapes.small),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${CategoryNames.pretty(category)} · $reason", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2)
                }
            }
            PrimaryButton("Build a route here", onRoute)
        }
    }
}
