package lt.ehu.unwalked.feature.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Museum
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.delay
import lt.ehu.unwalked.core.ui.components.ChipRow
import lt.ehu.unwalked.core.ui.components.MessageState
import lt.ehu.unwalked.core.ui.components.SkeletonBlock
import lt.ehu.unwalked.core.ui.theme.Palette
import lt.ehu.unwalked.domain.recommendation.CategoryGroup
import lt.ehu.unwalked.domain.recommendation.CategoryNames
import lt.ehu.unwalked.domain.recommendation.Recommendation
import lt.ehu.unwalked.domain.recommendation.RecommendationEngine
import lt.ehu.unwalked.domain.recommendation.SortOrder

data class DiscoverActions(
    val onBack: () -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onGroup: (CategoryGroup) -> Unit = {},
    val onOrder: (SortOrder) -> Unit = {},
    val onNotNow: (Recommendation) -> Unit = {},
    val onOpen: (Recommendation) -> Unit = {},
    val onUndo: () -> Unit = {},
    val onUndoTimeout: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onWiden: () -> Unit = {},
    val onOpenMap: () -> Unit = {},
    val photo: suspend (Recommendation) -> String? = { null },
    val photoCredit: Boolean = false,
)

@Composable
fun DiscoverScreen(state: DiscoverUiState, actions: DiscoverActions, focusSearch: Boolean = false) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusSearch) { if (focusSearch) runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = actions.onQuery,
                    placeholder = { Text("Search places") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { actions.onQuery("") }) { Icon(Icons.Outlined.Close, contentDescription = "Clear search") }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(26.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Palette.Panel, focusedContainerColor = Palette.Panel),
                    modifier = Modifier.weight(1f).focusRequester(focus).testTag("query"),
                )
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                ChipRow(CategoryGroup.entries - CategoryGroup.Other, state.group, { it.label }, actions.onGroup)
            }
            Content(state, actions, Modifier.weight(1f))
        }
        state.justHidden?.let { hidden ->
            LaunchedEffect(hidden) {
                delay(4_000)
                actions.onUndoTimeout()
            }
            Snackbar(
                action = { TextButton(onClick = actions.onUndo) { Text("Undo", color = Palette.Accent) } },
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            ) { Text("${hidden.place.name} hidden for 90 days") }
        }
    }
}

@Composable
private fun Content(state: DiscoverUiState, actions: DiscoverActions, modifier: Modifier) {
    when (val content = state.content) {
        DiscoverContent.Loading -> LoadingList(modifier)

        DiscoverContent.NoHistory -> MessageState(
            Icons.Outlined.Explore,
            "Nothing to compare with yet",
            "Recommendations need a few walks. Record a walk or load the sample week on the map.",
            modifier,
            "Open the map" to actions.onOpenMap,
        )

        is DiscoverContent.Failed -> MessageState(Icons.Outlined.CloudOff, "Could not load places", content.message, modifier, "Try again" to actions.onRetry)

        is DiscoverContent.Ready -> LazyColumn(
            modifier.testTag("results"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipRow(SortOrder.entries, state.order, { it.label }, actions.onOrder)
                    Text(
                        if (content.items.isEmpty()) "No matches" else "${content.items.size} new places near where you walk",
                        style = MaterialTheme.typography.titleSmall,
                        color = Palette.Muted,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
            if (content.items.isEmpty()) {
                item {
                    when {
                        content.total > 0 -> MessageState(Icons.Outlined.SearchOff, "Nothing matches", "Try another word or category.")

                        content.radiusMetres < RecommendationEngine.WIDER_METRES -> MessageState(
                            Icons.Outlined.SearchOff,
                            "Nothing new within ${content.radiusMetres.toInt()} m",
                            "You have been to every place we know near your usual routes.",
                            action = "Look within 1 km" to actions.onWiden,
                        )

                        else -> MessageState(Icons.Outlined.SearchOff, "Nothing new within 1 km", "You have been to every place we know near your usual routes.")
                    }
                }
            }
            items(content.items, key = { it.place.id }) { item -> PlaceCard(item, actions, Modifier.animateItem()) }
            item {
                Text(
                    "Places: Overture Maps Foundation, CDLA Permissive 2.0" + if (actions.photoCredit) "\nPhotos: Wikimedia Commons and Mapillary contributors, CC BY-SA" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PlaceCard(item: Recommendation, actions: DiscoverActions, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(Palette.Panel)
            .border(1.dp, Palette.Line, MaterialTheme.shapes.medium)
            .clickable(onClickLabel = "Show on map") { actions.onOpen(item) }
            .padding(12.dp)
            .testTag("place-${item.place.id}"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(64.dp)
                .background(Brush.linearGradient(listOf(Palette.AccentDeep, Palette.PanelHigh)), MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon(item.categoryGroup), contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(28.dp))
            val photo by produceState<String?>(null, item.place.id) { value = actions.photo(item) }
            photo?.let { url ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().clip(MaterialTheme.shapes.small),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.place.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${CategoryNames.pretty(item.place.category)} · ${item.detour}",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.badge, style = MaterialTheme.typography.labelLarge, color = Palette.Warning, modifier = Modifier.weight(1f))
                TextButton(onClick = { actions.onNotNow(item) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Not now") }
            }
        }
    }
}

@Composable
private fun LoadingList(modifier: Modifier) {
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(18.dp))
        repeat(4) {
            Row(
                Modifier.fillMaxWidth().background(Palette.Panel, MaterialTheme.shapes.medium).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SkeletonBlock(Modifier.size(64.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(16.dp))
                    SkeletonBlock(Modifier.fillMaxWidth(0.9f).height(12.dp))
                    SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(10.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun icon(group: CategoryGroup): ImageVector = when (group) {
    CategoryGroup.Food -> Icons.Outlined.LocalCafe
    CategoryGroup.Nature -> Icons.Outlined.Park
    CategoryGroup.Culture -> Icons.Outlined.Museum
    CategoryGroup.Shops -> Icons.Outlined.Storefront
    else -> Icons.Outlined.Place
}
