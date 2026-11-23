package lt.ehu.unwalked.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkLengthTest {
    @Test
    fun acceptsBoundaries() {
        assertTrue(WalkLength.parse("1", WalkMeasure.Distance) is WalkLength.Valid)
        assertTrue(WalkLength.parse("10", WalkMeasure.Distance) is WalkLength.Valid)
        assertTrue(WalkLength.parse("15", WalkMeasure.Time) is WalkLength.Valid)
        assertTrue(WalkLength.parse("120", WalkMeasure.Time) is WalkLength.Valid)
    }

    @Test
    fun rejectsOutsideRange() {
        listOf("0.9", "10.1", "14", "-3").forEach { assertTrue(it, WalkLength.parse(it, WalkMeasure.Distance) is WalkLength.Invalid) }
        listOf("14", "121").forEach { assertTrue(it, WalkLength.parse(it, WalkMeasure.Time) is WalkLength.Invalid) }
    }

    @Test
    fun rejectsTextAndBlank() {
        assertEquals(WalkLength.Invalid(WalkLength.ERROR), WalkLength.parse("abc", WalkMeasure.Distance))
        assertEquals(WalkLength.Invalid(WalkLength.ERROR), WalkLength.parse("", WalkMeasure.Distance))
    }

    @Test
    fun acceptsDecimalComma() {
        assertEquals(3.5, (WalkLength.parse("3,5", WalkMeasure.Distance) as WalkLength.Valid).value, 0.0)
    }

    @Test
    fun convertsTimeToDistanceAtWalkingPace() {
        val valid = WalkLength.parse("60", WalkMeasure.Time) as WalkLength.Valid
        assertEquals(4.8, valid.kilometres, 1e-9)
    }
}
