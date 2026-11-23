package lt.ehu.unwalked.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uber.h3core.H3Core
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import lt.ehu.unwalked.core.error.Outcome
import lt.ehu.unwalked.data.history.HistoryRepository
import lt.ehu.unwalked.data.places.PlacesRepository
import lt.ehu.unwalked.domain.stats.FavouritePlaces
import lt.ehu.unwalked.domain.stats.Period
import lt.ehu.unwalked.domain.stats.PeriodRange
import lt.ehu.unwalked.domain.stats.PeriodStats
import lt.ehu.unwalked.domain.stats.Periods
import lt.ehu.unwalked.domain.stats.PlaceHabits
import lt.ehu.unwalked.domain.stats.StatsCalculator

data class StatsUiState(
    val loading: Boolean = true,
    val period: Period = Period.Month,
    val range: PeriodRange? = null,
    val stats: PeriodStats? = null,
    val longestWalkDay: String? = null,
    val places: PlaceHabits? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val history: HistoryRepository,
    private val calculator: StatsCalculator,
    private val places: PlacesRepository? = null,
    private val h3: H3Core? = null,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val period = MutableStateFlow(Period.Month)

    private val ranges = combine(history.cellVisits, period) { cells, selected -> Periods.range(selected, now(), zone, cells.minOfOrNull { it.firstSeen }) }

    private val base = combine(history.cellVisits, history.stayVisits, period) { cells, stays, selected ->
        val range = Periods.range(selected, now(), zone, cells.minOfOrNull { it.firstSeen })
        val stats = calculator.calculate(range, cells, history.pointsSince(range.start), stays)
        StatsUiState(
            loading = false,
            period = selected,
            range = range,
            stats = stats,
            longestWalkDay = stats.longestWalk?.let { StatsCalculator.dayLabel(it.start, zone) },
        )
    }.flowOn(Dispatchers.Default)

    private val habits = combine(history.stayVisits, ranges) { stays, range -> FavouritePlaces.spots(stays, range.start, range.end) }
        .distinctUntilChanged()
        .mapLatest { spots -> placeHabits(spots) }
        .flowOn(Dispatchers.Default)

    val state: StateFlow<StatsUiState> = combine(base, habits.onStart { emit(null) }) { ui, found -> ui.copy(places = found) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun select(value: Period) {
        period.value = value
    }

    private suspend fun placeHabits(spots: List<Pair<Long, Int>>): PlaceHabits? {
        val repository = places ?: return null
        val grid = h3 ?: return null
        if (spots.isEmpty()) return null
        val found = repository.inCells(spots.map { grid.h3ToString(it.first) }, PLACE_LIMIT) as? Outcome.Success ?: return null
        return FavouritePlaces.pick(spots, found.value) { spot -> grid.cellToLatLng(spot).let { it.lat to it.lng } }
    }

    private companion object {
        const val PLACE_LIMIT = 200
    }
}
