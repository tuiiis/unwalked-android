package lt.ehu.unwalked.feature.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.InputStream
import java.io.OutputStream
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lt.ehu.unwalked.core.error.ErrorReporter
import lt.ehu.unwalked.data.auth.AuthRepository
import lt.ehu.unwalked.data.feedback.FeedbackRepository
import lt.ehu.unwalked.data.history.HistoryRepository
import lt.ehu.unwalked.data.local.FeedbackOutboxDao
import lt.ehu.unwalked.data.settings.Preferences
import lt.ehu.unwalked.data.settings.Retention

data class PrivacyUiState(
    val email: String? = null,
    val blurHome: Boolean = true,
    val studyConsent: Boolean = false,
    val retention: Retention = Retention.Forever,
    val busy: Boolean = false,
    val developerOptions: Boolean = false,
    val importing: Boolean = false,
)

class PrivacyViewModel(
    private val preferences: Preferences,
    private val history: HistoryRepository,
    private val auth: AuthRepository,
    private val outbox: FeedbackOutboxDao,
    private val errors: ErrorReporter,
    private val stopRecording: () -> Unit,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val importing = MutableStateFlow(false)
    private var importJob: Job? = null
    private var versionTaps = 0

    val state: StateFlow<PrivacyUiState> = combine(
        auth.session,
        preferences.blurHome,
        preferences.studyConsent,
        combine(preferences.retention, busy, preferences.developerOptions, importing) { retention, working, developer, timeline -> Extra(retention, working, developer, timeline) },
    ) { session, blur, consent, extra ->
        PrivacyUiState(session?.email, blur, consent, extra.retention, extra.busy || extra.importing, extra.developerOptions, extra.importing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrivacyUiState())

    fun setBlurHome(value: Boolean) = preferences.setBlurHome(value)

    fun setStudyConsent(value: Boolean) {
        preferences.setStudyConsent(value)
        if (!value) viewModelScope.launch { outbox.clear() }
    }

    fun setRetention(value: Retention) {
        preferences.setRetention(value)
        viewModelScope.launch { history.applyRetention(value.days) }
    }

    fun export(output: OutputStream) = work("Your data was saved.") { output.use { history.export(it) } }

    fun deleteAll() = work("All location history was deleted.") {
        stopRecording()
        preferences.setRecording(false)
        history.deleteAll()
        outbox.clear()
    }

    fun importGoogleTimeline(input: InputStream) {
        if (busy.value || importing.value) return
        importing.value = true
        importJob = viewModelScope.launch {
            try {
                val added = history.importGoogleTimeline(input)
                errors.info(if (added == 0) "Nothing new to add from this file." else "Added ${NumberFormat.getIntegerInstance(Locale.US).format(added)} walking points from Google Timeline.")
            } catch (cancelled: CancellationException) {
                errors.info("Import cancelled. Nothing was added.")
                throw cancelled
            } catch (failure: Exception) {
                errors.info("That did not work: ${failure.message ?: failure.javaClass.simpleName}")
            } finally {
                importing.value = false
                importJob = null
            }
        }
    }

    fun cancelImport() {
        importJob?.cancel()
    }

    fun loadSample() = work("A sample week in Vilnius was added to your map.") { history.importSampleWeek() }

    fun tapVersion() {
        if (preferences.developerOptions.value) return
        versionTaps += 1
        if (versionTaps < TAPS_TO_UNLOCK) return
        preferences.setDeveloperOptions(true)
        errors.info("Developer options are on.")
    }

    fun signOut() = auth.signOut()

    private fun work(done: String, block: suspend () -> Unit) = work({ _: Unit -> done }, block)

    private fun <T> work(done: (T) -> String, block: suspend () -> T) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                errors.info(done(block()))
            } catch (failure: Exception) {
                errors.info("That did not work: ${failure.message ?: failure.javaClass.simpleName}")
            } finally {
                busy.value = false
            }
        }
    }

    private data class Extra(val retention: Retention, val busy: Boolean, val developerOptions: Boolean, val importing: Boolean)

    private companion object {
        const val TAPS_TO_UNLOCK = 7
    }
}
