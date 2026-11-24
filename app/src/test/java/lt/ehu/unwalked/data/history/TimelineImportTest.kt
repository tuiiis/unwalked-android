package lt.ehu.unwalked.data.history

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.uber.h3core.H3Core
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import lt.ehu.unwalked.data.local.PointEntity
import lt.ehu.unwalked.data.local.TrackDao
import lt.ehu.unwalked.data.local.UnwalkedDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TimelineImportTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(context, UnwalkedDatabase::class.java).allowMainThreadQueries().build()

    @After
    fun close() = database.close()

    private fun timeline(points: Int): String {
        val path = (0 until points).joinToString(",") { i ->
            """{"point": "${54.68 + i * 0.00001}°, 25.27°", "time": "2025-05-01T10:${"%02d".format(i / 60)}:${"%02d".format(i % 60)}.000+03:00"}"""
        }
        return """{"semanticSegments": [
            {"startTime": "2025-05-01T10:00:00.000+03:00", "endTime": "2025-05-01T11:00:00.000+03:00", "timelinePath": [$path]},
            {"startTime": "2025-05-01T10:00:00.000+03:00", "endTime": "2025-05-01T11:00:00.000+03:00", "activity": {"topCandidate": {"type": "WALKING"}}}
        ]}"""
    }

    @Test
    fun cancelledImportLeavesNoPoints() = runBlocking {
        val halfway = CompletableDeferred<Unit>()
        val real = database.tracks()
        val counting = object : TrackDao by real {
            var inserted = 0

            override suspend fun insertPoint(point: PointEntity) {
                real.insertPoint(point)
                if (++inserted == 2_500) halfway.complete(Unit)
            }
        }
        val history = HistoryRepository(context, counting, H3Core.newInstance()) { block -> database.withTransaction { block() } }
        val import = async(Dispatchers.IO) { history.importGoogleTimeline(timeline(6_000).byteInputStream()) }
        halfway.await()
        import.cancel()
        runCatching { import.await() }

        assertTrue(import.isCancelled)
        assertEquals(0, real.pointCount("google"))
    }

    @Test
    fun importingTheSameFileTwiceAddsNothingTheSecondTime() = runBlocking {
        val history = HistoryRepository(context, database.tracks(), H3Core.newInstance()) { block -> database.withTransaction { block() } }

        val first = history.importGoogleTimeline(timeline(300).byteInputStream())
        val second = history.importGoogleTimeline(timeline(300).byteInputStream())

        assertTrue(first > 0)
        assertEquals(0, second)
        assertEquals(first, database.tracks().pointCount("google"))
    }

    @Test
    fun finishedImportKeepsAllPoints() = runBlocking {
        val history = HistoryRepository(context, database.tracks(), H3Core.newInstance()) { block -> database.withTransaction { block() } }

        val added = history.importGoogleTimeline(timeline(300).byteInputStream())

        assertEquals(added, database.tracks().pointCount("google"))
        assertTrue(added > 0)
    }
}
