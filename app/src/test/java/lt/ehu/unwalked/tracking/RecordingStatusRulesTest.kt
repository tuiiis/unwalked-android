package lt.ehu.unwalked.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingStatusRulesTest {
    private val minute = 60_000L
    private val now = 1_000 * minute

    @Test
    fun offWhenRecordingIsDisabled() {
        assertEquals(RecordingStatus.Off, RecordingStatusRules.evaluate(false, null, 0, now, false, 0))
    }

    @Test
    fun healthyWithRecentFix() {
        assertEquals(RecordingStatus.Healthy, RecordingStatusRules.evaluate(true, now - 5 * minute, 0, now, false, 0))
    }

    @Test
    fun stoppedAfterTwentyFiveMinutes() {
        assertEquals(RecordingStatus.Stopped(25, true), RecordingStatusRules.evaluate(true, now - 25 * minute, 0, now, true, 0))
    }

    @Test
    fun freshStartIsNotReportedAsStopped() {
        assertEquals(RecordingStatus.Healthy, RecordingStatusRules.evaluate(true, null, now - 2 * minute, now, false, 0))
    }

    @Test
    fun snoozedReminderStaysQuiet() {
        assertEquals(RecordingStatus.Healthy, RecordingStatusRules.evaluate(true, now - 90 * minute, 0, now, false, now + minute))
    }
}
