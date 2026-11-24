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
    private val mutableBlurHome = MutableStateFlow(prefs.getBoolean(BLUR, true))
    private val mutableRetention = MutableStateFlow(Retention.valueOf(prefs.getString(RETENTION, Retention.Forever.name)!!))
    private val mutableRecording = MutableStateFlow(prefs.getBoolean(RECORDING, false))
    private val mutableSnoozedUntil = MutableStateFlow(prefs.getLong(SNOOZE, 0L))
    private val mutableDeveloperOptions = MutableStateFlow(prefs.getBoolean(DEVELOPER, false))

    val studyConsent: StateFlow<Boolean> = mutableStudyConsent.asStateFlow()
    val blurHome: StateFlow<Boolean> = mutableBlurHome.asStateFlow()
    val retention: StateFlow<Retention> = mutableRetention.asStateFlow()
    val recording: StateFlow<Boolean> = mutableRecording.asStateFlow()
    val reminderSnoozedUntil: StateFlow<Long> = mutableSnoozedUntil.asStateFlow()
    val developerOptions: StateFlow<Boolean> = mutableDeveloperOptions.asStateFlow()

    fun setStudyConsent(value: Boolean) = save(STUDY, value) { mutableStudyConsent.value = it }
    fun setBlurHome(value: Boolean) = save(BLUR, value) { mutableBlurHome.value = it }
    fun setRecording(value: Boolean) = save(RECORDING, value) { mutableRecording.value = it }
    fun setDeveloperOptions(value: Boolean) = save(DEVELOPER, value) { mutableDeveloperOptions.value = it }

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
        const val BLUR = "blur_home"
        const val RETENTION = "retention"
        const val RECORDING = "recording"
        const val SNOOZE = "reminder_snoozed_until"
        const val DEVELOPER = "developer_options"
    }
}
