package lt.ehu.unwalked.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lt.ehu.unwalked.core.ui.map.HeatCell
import lt.ehu.unwalked.data.history.HistoryRepository
import lt.ehu.unwalked.data.settings.Preferences
import lt.ehu.unwalked.domain.route.WalkRoute
import lt.ehu.unwalked.domain.stats.CellVisit
import lt.ehu.unwalked.domain.stats.Period
import lt.ehu.unwalked.domain.stats.Periods
import lt.ehu.unwalked.domain.stats.StatsCalculator
import lt.ehu.unwalked.domain.tracking.Streak
import lt.ehu.unwalked.tracking.RecordingStatus
import lt.ehu.unwalked.tracking.RecordingStatusRules

data class MapSummary(val headline: String, val detail: String)

data class MapUiState(
    val loading: Boolean = true,
    val period: Period = Period.Year,
    val cells: List<HeatCell> = emptyList(),
    val streaks: List<Streak> = emptyList(),
    val summary: MapSummary? = null,
    val hasHistory: Boolean = false,
    val recording: RecordingStatus = RecordingStatus.Off,
    val walk: WalkRoute? = null,
    val importing: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModel(
    private val history: HistoryRepository,
    private val preferences: Preferences,
    private val activeWalk: MutableStateFlow<WalkRoute?>,
    private val batterySaver: () -> Boolean,
    private val calculator: StatsCalculator,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val period = MutableStateFlow(Period.Year)
    private val importing = MutableStateFlow(false)
    private val startedAt = MutableStateFlow(now())
    private val ticks = flow {
        while (true) {
            emit(now())
            delay(60_000)
        }
    }

    private val status = combine(preferences.recording, history.lastLiveFix, preferences.reminderSnoozedUntil, ticks, startedAt) { enabled, last, snoozed, time, started ->
        RecordingStatusRules.evaluate(enabled, last, started, time, batterySaver(), snoozed)
    }

    private val streaks = combine(history.cellVisits, period) { visits, selected ->
        Periods.range(selected, now(), zone, visits.minOfOrNull { it.firstSeen }).start
    }.distinctUntilChanged().mapLatest { since -> history.streaks(since) }

    private val base = combine(history.cellVisits, period, status, activeWalk, importing) { visits, selected, recording, walk, busy ->
        val range = Periods.range(selected, now(), zone, visits.minOfOrNull { it.firstSeen })
        val shown = visits.filter { it.lastSeen >= range.start }
        MapUiState(
            loading = false,
            period = selected,
            cells = shown.map { HeatCell(it.cell, it.passes) },
            summary = summary(visits, shown, range.title),
            hasHistory = visits.isNotEmpty(),
            recording = recording,
            walk = walk,
            importing = busy,
        )
    }

    val state: StateFlow<MapUiState> = combine(base, streaks.onStart { emit(emptyList()) }) { ui, lines -> ui.copy(streaks = lines) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    fun selectPeriod(value: Period) {
        period.value = value
    }

    fun startRecording() {
        startedAt.value = now()
        preferences.setRecording(true)
    }

    fun snoozeReminder() {
        val tomorrow = Instant.ofEpochMilli(now()).atZone(zone).toLocalDate().plusDays(1).atTime(LocalTime.of(9, 0)).atZone(zone)
        preferences.snoozeReminderUntil(tomorrow.toInstant().toEpochMilli())
    }

    fun endWalk() {
        activeWalk.value = null
    }

    fun importSample() {
        if (importing.value) return
        importing.value = true
        viewModelScope.launch {
            try {
                history.importSampleWeek()
            } finally {
                importing.value = false
            }
        }
    }

    private fun summary(all: List<CellVisit>, shown: List<CellVisit>, title: String): MapSummary? {
        if (all.isEmpty()) return null
        val monthStart = Periods.range(Period.Month, now(), zone, null).start
        val newThisMonth = all.count { it.firstSeen >= monthStart }
        val explored = calculator.explored(all)
        return MapSummary(
            headline = "You explored $explored% of your usual area",
            detail = "$title · ${shown.size} streets · $newThisMonth new this month",
        )
    }
}
