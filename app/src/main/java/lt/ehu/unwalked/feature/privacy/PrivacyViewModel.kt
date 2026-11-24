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
import lt.ehu.unwalked.data.history.SampleWalks
import lt.ehu.unwalked.data.local.FeedbackOutboxDao
import lt.ehu.unwalked.data.settings.Preferences
import lt.ehu.unwalked.data.settings.Retention
import lt.ehu.unwalked.domain.route.LatLon
import timber.log.Timber

enum class LongTask(val title: String, val body: String, val cancelled: String, val failed: String) {
    Timeline("Importing Google Timeline", "This can take a minute. Keep the app open.", "Import cancelled. Nothing was added.", "Could not read this file. Choose the Timeline.json exported from Android settings."),
    Sample("Generating sample walks", "Building walks on real streets around your last location. This takes about a minute. Keep the app open.", "Generation cancelled. Nothing was added.", "Could not build sample walks. Check the internet connection and try again."),
}

data class PrivacyUiState(
    val email: String? = null,
    val studyConsent: Boolean = false,
    val retention: Retention = Retention.Forever,
    val busy: Boolean = false,
    val task: LongTask? = null,
)

class PrivacyViewModel(
    private val preferences: Preferences,
    private val history: HistoryRepository,
    private val auth: AuthRepository,
    private val outbox: FeedbackOutboxDao,
    private val errors: ErrorReporter,
    private val stopRecording: () -> Unit,
    private val sample: SampleWalks,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val task = MutableStateFlow<LongTask?>(null)
    private var taskJob: Job? = null

    val state: StateFlow<PrivacyUiState> = combine(
        auth.session,
        preferences.studyConsent,
        combine(preferences.retention, busy, task) { retention, working, running -> Extra(retention, working, running) },
    ) { session, consent, extra ->
        PrivacyUiState(session?.email, consent, extra.retention, extra.busy || extra.task != null, extra.task)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrivacyUiState())

    fun setStudyConsent(value: Boolean) {
        preferences.setStudyConsent(value)
        if (!value) viewModelScope.launch { outbox.clear() }
    }

    fun setRetention(value: Retention) {
        preferences.setRetention(value)
        viewModelScope.launch { history.applyRetention(value.days) }
    }

    fun export(output: OutputStream) = work("export", "Your data was saved.", "Could not save your data. Try another folder.") { output.use { history.export(it) } }

    fun deleteAll() = work("delete history", "All location history was deleted.", "Could not delete the history. Try again.") {
        stopRecording()
        preferences.setRecording(false)
        history.deleteAll()
        outbox.clear()
    }

    fun importGoogleTimeline(input: InputStream) = run(LongTask.Timeline, { added ->
        if (added == 0) "Nothing new to add from this file." else "Added ${NumberFormat.getIntegerInstance(Locale.US).format(added)} walking points from Google Timeline."
    }) { history.importGoogleTimeline(input) }

    fun loadSample() = run(LongTask.Sample, { added ->
        if (added == 0) "Could not build sample walks. Check the internet connection and try again." else "Added half a year of sample walks near your last location."
    }) {
        val home = history.lastKnownPosition()?.let { LatLon(it.lat, it.lon) } ?: FALLBACK_HOME
        history.importSample(sample.generate(home, now()))
    }

    fun cancelTask() {
        taskJob?.cancel()
    }

    fun signOut() = auth.signOut()

    private fun work(operation: String, done: String, failed: String, block: suspend () -> Unit) = work(operation, { _: Unit -> done }, failed, block)

    private fun run(kind: LongTask, done: (Int) -> String, block: suspend () -> Int) {
        if (busy.value || task.value != null) return
        task.value = kind
        taskJob = viewModelScope.launch {
            val started = now()
            try {
                val added = block()
                Timber.i("%s finished in %d ms, %d points added", kind, now() - started, added)
                errors.info(done(added))
            } catch (cancelled: CancellationException) {
                Timber.i("%s cancelled after %d ms", kind, now() - started)
                errors.info(kind.cancelled)
                throw cancelled
            } catch (failure: Exception) {
                Timber.e(failure, "%s failed after %d ms", kind, now() - started)
                errors.info(kind.failed)
            } finally {
                task.value = null
                taskJob = null
            }
        }
    }

    private fun <T> work(operation: String, done: (T) -> String, failed: String, block: suspend () -> T) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                errors.info(done(block()))
                Timber.i("%s finished", operation)
            } catch (cancelled: CancellationException) {
                Timber.i("%s cancelled", operation)
                throw cancelled
            } catch (failure: Exception) {
                Timber.e(failure, "%s failed", operation)
                errors.info(failed)
            } finally {
                busy.value = false
            }
        }
    }

    private data class Extra(val retention: Retention, val busy: Boolean, val task: LongTask?)

    private companion object {
        val FALLBACK_HOME = LatLon(54.6872, 25.2797)
    }
}
