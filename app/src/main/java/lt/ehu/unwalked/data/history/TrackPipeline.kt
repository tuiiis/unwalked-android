package lt.ehu.unwalked.data.history

import com.uber.h3core.H3Core
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import lt.ehu.unwalked.data.local.PointEntity
import lt.ehu.unwalked.data.local.StayEntity
import lt.ehu.unwalked.data.local.TrackDao
import lt.ehu.unwalked.domain.tracking.CellAggregator
import lt.ehu.unwalked.domain.tracking.Fix
import lt.ehu.unwalked.domain.tracking.StayDetector

class TrackPipeline(private val dao: TrackDao, private val h3: H3Core, private val source: String) {
    private val aggregator = CellAggregator(h3, Cells.RESOLUTION)
    private val stays = StayDetector()
    private val mutex = Mutex()

    suspend fun accept(fix: Fix) = mutex.withLock {
        dao.insertPoint(PointEntity(time = fix.time, lat = fix.lat, lon = fix.lon, accuracy = fix.accuracy, source = source))
        aggregator.accept(fix).forEach { dao.addPass(it.cell, it.resolution, it.time) }
        stays.accept(fix)?.let { stay ->
            dao.insertStay(StayEntity(start = stay.start, end = stay.end, lat = stay.lat, lon = stay.lon, points = stay.points, source = source))
        }
    }

    suspend fun finish() = mutex.withLock {
        stays.flush()?.let { stay ->
            dao.insertStay(StayEntity(start = stay.start, end = stay.end, lat = stay.lat, lon = stay.lon, points = stay.points, source = source))
        }
    }
}
