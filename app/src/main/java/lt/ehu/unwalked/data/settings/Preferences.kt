package lt.ehu.unwalked.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Retention(val label: String, val days: Long?) {
    Forever("Forever", null),
    Year("1 year", 365),
    Quarter("3 months", 90),
}

class Preferences(context: Context) {
    private val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)

    private val mutableStudyConsent = MutableStateFlow(prefs.getBoolean(STUDY, false))
    private val mutableRetention = MutableStateFlow(Retention.valueOf(prefs.getString(RETENTION, Retention.Forever.name)!!))
    private val mutableRecording = MutableStateFlow(prefs.getBoolean(RECORDING, false))
    private val mutableSnoozedUntil = MutableStateFlow(prefs.getLong(SNOOZE, 0L))
    private val mutableFoundSomethingNew = MutableStateFlow(prefs.getBoolean(FOUND_NEW, false))

    val studyConsent: StateFlow<Boolean> = mutableStudyConsent.asStateFlow()
    val retention: StateFlow<Retention> = mutableRetention.asStateFlow()
    val recording: StateFlow<Boolean> = mutableRecording.asStateFlow()
    val reminderSnoozedUntil: StateFlow<Long> = mutableSnoozedUntil.asStateFlow()
    val foundSomethingNew: StateFlow<Boolean> = mutableFoundSomethingNew.asStateFlow()

    fun setStudyConsent(value: Boolean) = save(STUDY, value) { mutableStudyConsent.value = it }
    fun setRecording(value: Boolean) = save(RECORDING, value) { mutableRecording.value = it }
    fun setFoundSomethingNew(value: Boolean) = save(FOUND_NEW, value) { mutableFoundSomethingNew.value = it }

    fun setRetention(value: Retention) {
        prefs.edit().putString(RETENTION, value.name).apply()
        mutableRetention.value = value
    }

    fun snoozeReminderUntil(millis: Long) {
        prefs.edit().putLong(SNOOZE, millis).apply()
        mutableSnoozedUntil.value = millis
    }

    private fun save(key: String, value: Boolean, update: (Boolean) -> Unit) {
        prefs.edit().putBoolean(key, value).apply()
        update(value)
    }

    private companion object {
        const val STUDY = "study_consent"
        const val RETENTION = "retention"
        const val RECORDING = "recording"
        const val SNOOZE = "reminder_snoozed_until"
        const val FOUND_NEW = "found_something_new"
    }
}
