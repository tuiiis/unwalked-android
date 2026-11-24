package lt.ehu.unwalked.domain.stats

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CountryOutlinesTest {
    @Test
    fun readsLatitudeAndLongitudeFromEachRing() {
        val rings = CountryOutlines.parse("25.00,54.50 26.00,55.00 25.00,54.50\n\n10.00,-5.00 11.00,-6.00\n")

        assertEquals(2, rings.size)
        assertEquals(54.5 to 25.0, rings.first().first())
        assertEquals(-6.0 to 11.0, rings.last().last())
    }

    @Test
    fun bundledOutlinesCoverTheWorld() {
        val rings = CountryOutlines.parse(File("src/main/assets/${CountryOutlines.ASSET}").readText())
        val points = rings.flatten()

        assertEquals(288, rings.size)
        assertTrue(points.all { (lat, lon) -> lat in -90.0..90.0 && lon in -180.0..180.0 })
        assertTrue(points.any { (lat, lon) -> lat in 53.9..56.5 && lon in 20.9..26.9 })
    }
}
