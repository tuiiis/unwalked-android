package lt.ehu.unwalked.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lt.ehu.unwalked.core.error.AppError
import lt.ehu.unwalked.core.error.ErrorReporter
import lt.ehu.unwalked.core.error.Outcome
import lt.ehu.unwalked.data.feedback.FeedbackAction
import lt.ehu.unwalked.data.feedback.FeedbackRepository
import lt.ehu.unwalked.data.local.HiddenPlaceDao
import lt.ehu.unwalked.data.local.HiddenPlaceEntity
import lt.ehu.unwalked.data.places.PlacesRepository
import lt.ehu.unwalked.domain.recommendation.CandidateCells
import lt.ehu.unwalked.domain.recommendation.CategoryGroup
import lt.ehu.unwalked.domain.recommendation.HabitualSource
import lt.ehu.unwalked.domain.recommendation.Recommendation
import lt.ehu.unwalked.domain.recommendation.RecommendationEngine
import lt.ehu.unwalked.domain.recommendation.SortOrder

sealed interface DiscoverContent {
    data object Loading : DiscoverContent
    data object NoHistory : DiscoverContent
    data class Failed(val message: String) : DiscoverContent
    data class Ready(val items: List<Recommendation>, val total: Int) : DiscoverContent
}

data class DiscoverUiState(
    val query: String = "",
    val group: CategoryGroup = CategoryGroup.All,
    val order: SortOrder = SortOrder.BestMatch,
    val content: DiscoverContent = DiscoverContent.Loading,
    val justHidden: Recommendation? = null,
)

class DiscoverViewModel(
    private val habitual: HabitualSource,
    private val places: PlacesRepository,
    private val hidden: HiddenPlaceDao,
    private val feedback: FeedbackRepository,
    private val candidates: CandidateCells,
    private val engine: RecommendationEngine,
    private val errors: ErrorReporter,
    private val now: () -> Long = System::currentTimeMillis,
    private val since: () -> Long = { 0L },
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val group = MutableStateFlow(CategoryGroup.All)
    private val order = MutableStateFlow(SortOrder.BestMatch)
    private val loaded = MutableStateFlow<DiscoverContent>(DiscoverContent.Loading)
    private val justHidden = MutableStateFlow<Recommendation?>(null)
    private val all = MutableStateFlow<List<Recommendation>>(emptyList())

    val state: StateFlow<DiscoverUiState> = combine(
        combine(query, group, order) { q, g, o -> Triple(q, g, o) },
        loaded,
        all,
        hidden.hiddenIds(now()),
        justHidden,
    ) { (q, g, o), content, items, hiddenIds, lastHidden ->
        val visible = items.filter { it.place.id !in hiddenIds }
        DiscoverUiState(
            query = q,
            group = g,
            order = o,
            content = if (content is DiscoverContent.Ready) DiscoverContent.Ready(engine.filter(visible, q, g, o), visible.size) else content,
            justHidden = lastHidden,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoverUiState())

    init {
        load()
    }

    fun load() {
        loaded.value = DiscoverContent.Loading
        viewModelScope.launch {
            val habits = habitual.habitual(since())
            if (habits.visited.isEmpty()) {
                loaded.value = DiscoverContent.NoHistory
                return@launch
            }
            when (val result = places.inCells(candidates.around(habits.usual), LIMIT)) {
                is Outcome.Success -> {
                    all.value = engine.rank(result.value, habits.visited, habits.centres, emptySet())
                    loaded.value = DiscoverContent.Ready(emptyList(), 0)
                }

                is Outcome.Failure -> {
                    loaded.value = DiscoverContent.Failed(failureText(result.error))
                    errors.log(result.error)
                }
            }
        }
    }

    fun onQuery(value: String) {
        query.value = value
    }

    fun onGroup(value: CategoryGroup) {
        group.value = value
    }

    fun onOrder(value: SortOrder) {
        order.value = value
    }

    fun notNow(item: Recommendation) {
        viewModelScope.launch {
            hidden.hide(HiddenPlaceEntity(item.place.id, now() + HIDE_MILLIS))
            justHidden.value = item
            feedback.record(TYPE, FeedbackAction.Dismissed, item.place.category)
        }
    }

    fun undoHide() {
        val item = justHidden.value ?: return
        justHidden.value = null
        viewModelScope.launch { hidden.unhide(item.place.id) }
    }

    fun dismissUndo() {
        justHidden.value = null
    }

    fun go(item: Recommendation) {
        viewModelScope.launch { feedback.record(TYPE, FeedbackAction.Accepted, item.place.category) }
    }

    private fun failureText(error: AppError): String = when (error) {
        is AppError.Network -> "Cannot reach the server. Check the connection and try again."
        else -> ErrorReporter.userText(error)
    }

    companion object {
        const val TYPE = "nearby_new_place"
        private const val LIMIT = 200
        private const val HIDE_MILLIS = 90L * 24 * 60 * 60_000
    }
}
