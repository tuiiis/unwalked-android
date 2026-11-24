package lt.ehu.unwalked.data.history

import android.content.Context
import com.uber.h3core.H3Core
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import lt.ehu.unwalked.data.local.CellEntity
import lt.ehu.unwalked.data.local.TrackDao
import lt.ehu.unwalked.domain.recommendation.Habits
import lt.ehu.unwalked.domain.recommendation.HabitualPoint
import lt.ehu.unwalked.domain.recommendation.HabitualSource
import lt.ehu.unwalked.domain.recommendation.UsualCells
import lt.ehu.unwalked.domain.stats.CellVisit
import lt.ehu.unwalked.domain.stats.StayVisit
import lt.ehu.unwalked.domain.stats.TimedPoint
import lt.ehu.unwalked.domain.tracking.Fix
import lt.ehu.unwalked.domain.tracking.GpxParser
import lt.ehu.unwalked.domain.tracking.Streak
import lt.ehu.unwalked.domain.tracking.Streaks
import org.json.JSONArray
import org.json.JSONObject

class HistoryRepository(
    private val context: Context,
    private val dao: TrackDao,
    private val h3: H3Core,
    private val transaction: suspend (suspend () -> Unit) -> Unit = { it() },
) : HabitualSource {
    val cells: Flow<List<CellEntity>> = dao.cells(Cells.RESOLUTION)

    val lastLiveFix: Flow<Long?> = dao.lastLiveFix()

    val latestLiveFix: Flow<Fix?> = dao.latestLivePoint().map { it?.let { point -> Fix(point.time, point.lat, point.lon, point.accuracy) } }

    val cellVisits: Flow<List<CellVisit>> = cells.map { list ->
        list.map { CellVisit(it.h3, h3.cellToParent(it.h3, Cells.AREA_RESOLUTION), it.firstSeen, it.lastSeen, it.passes) }
    }

    val stayVisits: Flow<List<StayVisit>> = dao.stays().map { stays ->
        stays.map { StayVisit(it.start, h3.latLngToCell(it.lat, it.lon, PLACE_KEY_RESOLUTION)) }
    }

    suspend fun pointsSince(since: Long): List<TimedPoint> = withContext(Dispatchers.IO) {
        dao.pointsSince(since).map { TimedPoint(it.time, it.lat, it.lon, it.accuracy) }
    }

    override suspend fun habitual(since: Long): Habits = withContext(Dispatchers.Default) {
        val cells = dao.cellsNow(Cells.RESOLUTION)
        val walks = streaks(since)
        val usual = UsualCells.from(walks, cellOf = { lat, lon -> h3.latLngToCell(lat, lon, Cells.RESOLUTION) })
            .ifEmpty { cells.associate { it.h3 to it.passes } }
        val centres = usual.map { (cell, walks) -> h3.cellToLatLng(cell).let { centre -> HabitualPoint(centre.lat, centre.lng, walks) } }
        Habits(cells.map { it.h3 }.toSet(), usual, centres)
    }

    suspend fun streaks(since: Long): List<Streak> = withContext(Dispatchers.Default) {
        val passes = dao.cellsNow(Cells.RESOLUTION).associate { it.h3 to it.passes }
        val fixes = dao.pointsSince(since).map { Fix(it.time, it.lat, it.lon, it.accuracy) }
        Streaks.build(fixes, passesAt = { lat, lon -> passes[h3.latLngToCell(lat, lon, Cells.RESOLUTION)] ?: 1 })
    }

    suspend fun hasHistory(): Boolean = dao.pointCount() > 0

    suspend fun lastKnownPosition(): HabitualPoint? = withContext(Dispatchers.IO) {
        dao.pointsSince(0).lastOrNull()?.let { HabitualPoint(it.lat, it.lon) }
    }

    suspend fun importSample(fixes: List<Fix>): Int = withContext(Dispatchers.IO) {
        if (fixes.isEmpty()) return@withContext 0
        val pipeline = TrackPipeline(dao, h3, SAMPLE)
        transaction {
            fixes.sortedBy { it.time }.forEach {
                ensureActive()
                pipeline.accept(it)
            }
            pipeline.finish()
        }
        fixes.size
    }

    suspend fun importTrack(input: InputStream, source: String): Int = withContext(Dispatchers.IO) {
        if (dao.pointCount(source) > 0) return@withContext 0
        val fixes = input.use { GpxParser.parse(it) }.sortedBy { it.time }
        val pipeline = TrackPipeline(dao, h3, source)
        fixes.chunked(IMPORT_BATCH).forEach { batch -> transaction { batch.forEach { pipeline.accept(it) } } }
        transaction { pipeline.finish() }
        fixes.size
    }

    suspend fun importGoogleTimeline(input: InputStream): Int = withContext(Dispatchers.IO) {
        val after = dao.lastPointTime(GOOGLE) ?: Long.MIN_VALUE
        val before = dao.firstPointTimeExcept(listOf(GOOGLE, SAMPLE)) ?: Long.MAX_VALUE
        val fixes = input.use { GoogleTimeline.parse(it) }.filter { it.time > after && it.time < before }
        ensureActive()
        val pipeline = TrackPipeline(dao, h3, GOOGLE)
        transaction {
            fixes.forEach {
                ensureActive()
                pipeline.accept(it)
            }
            pipeline.finish()
        }
        fixes.size
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) { dao.clearAll() }

    suspend fun applyRetention(days: Long?, nowMillis: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        if (days != null) dao.deleteOlderThan(nowMillis - days * DAY_MILLIS)
    }

    suspend fun export(output: OutputStream) = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("format", "unwalked-export-1")
        root.put(
            "cells",
            JSONArray(
                dao.cellsNow(Cells.RESOLUTION).map {
                    JSONObject().put("h3", h3.h3ToString(it.h3)).put("passes", it.passes).put("firstSeen", it.firstSeen).put("lastSeen", it.lastSeen)
                },
            ),
        )
        root.put(
            "visits",
            JSONArray(
                dao.staysNow().map {
                    JSONObject().put("start", it.start).put("end", it.end).put("lat", it.lat).put("lon", it.lon)
                },
            ),
        )
        root.put(
            "points",
            JSONArray(
                dao.pointsSince(0).map {
                    JSONObject().put("time", it.time).put("lat", it.lat).put("lon", it.lon).put("accuracy", it.accuracy.toDouble())
                },
            ),
        )
        output.write(root.toString().toByteArray(Charsets.UTF_8))
    }

    private companion object {
        const val SAMPLE = "sample"
        const val PLACE_KEY_RESOLUTION = 10
        const val DAY_MILLIS = 24 * 60 * 60_000L
        const val IMPORT_BATCH = 2_000
        const val GOOGLE = "google"
    }
}
