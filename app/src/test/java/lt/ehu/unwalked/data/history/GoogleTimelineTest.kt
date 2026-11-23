package lt.ehu.unwalked.data.history

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GoogleTimelineTest {
    private fun parse(json: String) = GoogleTimeline.parse(json.byteInputStream())

    @Test
    fun androidExportKeepsOnlyWalkingSegments() {
        val fixes = parse(
            """
            {"semanticSegments": [
              {"startTime": "2025-05-01T10:00:00.000+03:00", "endTime": "2025-05-01T10:10:00.000+03:00",
               "timelinePath": [
                 {"point": "54.6800000°, 25.2700000°", "time": "2025-05-01T10:00:00.000+03:00"},
                 {"point": "54.6810000°, 25.2700000°", "time": "2025-05-01T10:02:00.000+03:00"}
               ]},
              {"startTime": "2025-05-01T10:00:00.000+03:00", "endTime": "2025-05-01T10:02:00.000+03:00",
               "activity": {"topCandidate": {"type": "WALKING", "probability": 0.9}}},
              {"startTime": "2025-05-01T12:00:00.000+03:00", "endTime": "2025-05-01T12:10:00.000+03:00",
               "timelinePath": [
                 {"point": "54.7000000°, 25.3000000°", "time": "2025-05-01T12:00:00.000+03:00"},
                 {"point": "54.7100000°, 25.3000000°", "time": "2025-05-01T12:01:00.000+03:00"}
               ]},
              {"startTime": "2025-05-01T12:00:00.000+03:00", "endTime": "2025-05-01T12:10:00.000+03:00",
               "activity": {"topCandidate": {"type": "IN_PASSENGER_VEHICLE"}}},
              {"startTime": "2025-05-01T13:00:00.000+03:00", "endTime": "2025-05-01T14:00:00.000+03:00",
               "visit": {"topCandidate": {"placeLocation": {"latLng": "54.68°, 25.27°"}}}}
            ], "userLocationProfile": {"frequentPlaces": []}}
            """,
        )
        assertTrue(fixes.size > 2)
        assertTrue(fixes.all { it.lat in 54.6799..54.6811 })
    }

    @Test
    fun iosExportUsesMinuteOffsets() {
        val fixes = parse(
            """
            [
              {"startTime": "2025-05-01T10:00:00.000+03:00", "endTime": "2025-05-01T10:10:00.000+03:00",
               "timelinePath": [
                 {"point": "geo:54.680000,25.270000", "durationMinutesOffsetFromStartTime": "0"},
                 {"point": "geo:54.681000,25.270000", "durationMinutesOffsetFromStartTime": "2"}
               ]},
              {"startTime": "2025-05-01T10:00:00.000+03:00", "endTime": "2025-05-01T10:02:00.000+03:00",
               "activity": {"topCandidate": {"type": "walking"}}}
            ]
            """,
        )
        assertEquals(1_746_082_800_000L, fixes.first().time)
        assertEquals(1_746_082_920_000L, fixes.last().time)
    }

    @Test
    fun takeoutRecordsDropDriving() {
        val fixes = parse(
            """
            {"locations": [
              {"latitudeE7": 546800000, "longitudeE7": 252700000, "accuracy": 10, "timestamp": "2020-01-01T10:00:00Z"},
              {"latitudeE7": 546805000, "longitudeE7": 252700000, "accuracy": 10, "timestamp": "2020-01-01T10:01:00Z"},
              {"latitudeE7": 547805000, "longitudeE7": 252700000, "accuracy": 10, "timestamp": "2020-01-01T10:05:00Z"},
              {"latitudeE7": 547905000, "longitudeE7": 252700000, "accuracy": 10, "timestamp": "2020-01-01T10:07:00Z"}
            ]}
            """,
        )
        assertTrue(fixes.isNotEmpty())
        assertTrue(fixes.all { it.lat < 54.69 })
    }

    @Test(expected = IllegalArgumentException::class)
    fun unrelatedJsonIsRejected() {
        parse("""{"something": "else"}""")
    }
}
