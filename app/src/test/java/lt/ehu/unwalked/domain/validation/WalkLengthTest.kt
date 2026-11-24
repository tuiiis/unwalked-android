package lt.ehu.unwalked.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Test

class WalkLengthTest {
    @Test
    fun snapsToHalfKilometreSteps() {
        assertEquals(3.5, WalkLength.snap(3.4), 0.0)
        assertEquals(4.0, WalkLength.snap(3.76), 0.0)
    }

    @Test
    fun minutesAreRoundedToFiveAtWalkingPace() {
        assertEquals(15, WalkLength.minutes(1.0))
        assertEquals(45, WalkLength.minutes(3.5))
        assertEquals(125, WalkLength.minutes(10.0))
    }

    @Test
    fun formatsDistanceAndDuration() {
        assertEquals("3.0 km", WalkLength.kilometres(3.0))
        assertEquals("3.5 km", WalkLength.kilometres(3.5))
        assertEquals("10.0 km", WalkLength.kilometres(10.0))
        assertEquals("45 min", WalkLength.duration(45))
        assertEquals("1 h", WalkLength.duration(60))
        assertEquals("2 h 5 min", WalkLength.duration(125))
    }

    @Test
    fun sliderHasOneStopPerHalfKilometre() {
        assertEquals(17, WalkLength.STEPS)
    }
}
