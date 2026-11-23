package lt.ehu.unwalked.tracking

sealed interface RecordingStatus {
    data object Off : RecordingStatus
    data object Healthy : RecordingStatus
    data class Stopped(val minutesWithoutFix: Long, val batterySaver: Boolean) : RecordingStatus
}

object RecordingStatusRules {
    const val STALE_MINUTES = 25L

    fun evaluate(
        recordingEnabled: Boolean,
        lastFixMillis: Long?,
        startedAtMillis: Long,
        nowMillis: Long,
        batterySaver: Boolean,
        snoozedUntil: Long,
    ): RecordingStatus {
        if (!recordingEnabled) return RecordingStatus.Off
        if (nowMillis < snoozedUntil) return RecordingStatus.Healthy
        val reference = maxOf(lastFixMillis ?: 0L, startedAtMillis)
        val minutes = (nowMillis - reference) / 60_000
        return if (minutes >= STALE_MINUTES) RecordingStatus.Stopped(minutes, batterySaver) else RecordingStatus.Healthy
    }
}
