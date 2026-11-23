package lt.ehu.unwalked.data.photos

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameMatchTest {
    @Test
    fun ignoresCaseAndLithuanianLetters() {
        assertTrue(NameMatch.similar("Šv. Onos bažnyčia", "sv onos baznycia"))
    }

    @Test
    fun acceptsTheShorterNameInsideTheLonger() {
        assertTrue(NameMatch.similar("MO muziejus", "MO muziejus, Vilnius"))
    }

    @Test
    fun rejectsDifferentPlacesNearby() {
        assertFalse(NameMatch.similar("Caffeine", "Vilniaus katedra"))
    }

    @Test
    fun rejectsTooShortNames() {
        assertFalse(NameMatch.similar("MO", "MO"))
    }
}
