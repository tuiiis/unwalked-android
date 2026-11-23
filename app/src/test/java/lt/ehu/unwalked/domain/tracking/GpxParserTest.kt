package lt.ehu.unwalked.domain.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class GpxParserTest {
    @Test
    fun parsesPointsTimesAndHdop() {
        val gpx = """
            <?xml version="1.0"?>
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
              <trk><trkseg>
                <trkpt lat="54.6872" lon="25.2797"><time>2026-10-18T10:00:00Z</time></trkpt>
                <trkpt lat="54.6873" lon="25.2799"><time>2026-10-18T10:00:05Z</time><hdop>3</hdop></trkpt>
                <trkpt lat="54.6874" lon="25.2800"></trkpt>
              </trkseg></trk>
            </gpx>
        """.trimIndent()
        val fixes = GpxParser.parse(gpx.byteInputStream())
        assertEquals(2, fixes.size)
        assertEquals(5_000L, fixes[1].time - fixes[0].time)
        assertEquals(15f, fixes[1].accuracy)
        assertEquals(25.2799, fixes[1].lon, 1e-9)
    }
}
