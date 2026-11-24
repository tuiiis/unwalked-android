package lt.ehu.unwalked.data.history

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.uber.h3core.H3Core
import java.time.ZoneOffset
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import lt.ehu.unwalked.core.error.Outcome
import lt.ehu.unwalked.data.local.UnwalkedDatabase
import lt.ehu.unwalked.data.route.RouteRepository
import lt.ehu.unwalked.data.route.RoutedPath
import lt.ehu.unwalked.domain.route.LatLon
import lt.ehu.unwalked.domain.tracking.GeoMath
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SampleWalksTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(context, UnwalkedDatabase::class.java).allowMainThreadQueries().build()
    private val home = LatLon(54.6872, 25.2797)
    private val now = 1_795_000_000_000L
    private val straight = object : RouteRepository {
        override suspend fun walking(waypoints: List<LatLon>): Outcome<RoutedPath> =
            Outcome.Success(RoutedPath(waypoints, waypoints.zipWithNext().sumOf { (a, b) -> GeoMath.distanceMeters(a.lat, a.lon, b.lat, b.lon) }))
    }

    @After
    fun close() = database.close()

    @Test
    fun halfAYearOfWalksAtWalkingPaceBeforeNow() = runBlocking {
        val fixes = SampleWalks(straight, ZoneOffset.UTC).generate(home, now, random = Random(7))
        val days = fixes.map { it.time / 86_400_000 }.distinct()
        val speeds = fixes.zipWithNext().filter { (a, b) -> b.time - a.time < 60_000 }.map { (a, b) -> GeoMath.distanceMeters(a, b) / ((b.time - a.time) / 1000.0) }

        assertTrue(days.size > 90)
        assertTrue(fixes.all { it.time < now && it.time > now - 183L * 86_400_000 })
        assertTrue(speeds.average() in 0.8..2.0)
    }

    @Test
    fun recentMonthsAreBusierAndUseOtherStreets() = runBlocking {
        val fixes = SampleWalks(straight, ZoneOffset.UTC).generate(home, now, random = Random(7))
        val h3 = H3Core.newInstance()
        val monthAgo = now - 30L * 86_400_000
        val lastMonth = fixes.filter { it.time >= monthAgo }
        val earliestMonth = fixes.filter { it.time < now - 152L * 86_400_000 }
        val cells = { list: List<lt.ehu.unwalked.domain.tracking.Fix> -> list.map { h3.latLngToCell(it.lat, it.lon, 11) }.toSet() }

        val before = fixes.filter { it.time < monthAgo }

        assertTrue(lastMonth.size > earliestMonth.size)
        assertTrue(cells(fixes).size > cells(lastMonth).size * 1.3)
        assertTrue((cells(lastMonth) - cells(before)).size > 20)
    }

    @Test
    fun generatedWalksFillTheMap() = runBlocking {
        val history = HistoryRepository(context, database.tracks(), H3Core.newInstance()) { block -> database.withTransaction { block() } }

        val added = history.importSample(SampleWalks(straight, ZoneOffset.UTC).generate(home, now, random = Random(7)))

        assertTrue(added > 0)
        assertTrue(database.tracks().pointCount("sample") > 0)
        assertTrue(history.habitual().visited.size > 100)
    }

    @Test
    fun noRoutesMeansNoWalks() = runBlocking {
        val offline = object : RouteRepository {
            override suspend fun walking(waypoints: List<LatLon>): Outcome<RoutedPath> = Outcome.Failure(lt.ehu.unwalked.core.error.AppError.Network("offline"))
        }
        assertTrue(SampleWalks(offline).generate(home, now).isEmpty())
    }
}
