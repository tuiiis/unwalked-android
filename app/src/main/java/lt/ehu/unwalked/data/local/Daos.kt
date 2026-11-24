package lt.ehu.unwalked.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Insert
    suspend fun insertPoint(point: PointEntity)

    @Query("SELECT * FROM point WHERE time >= :since ORDER BY time")
    suspend fun pointsSince(since: Long): List<PointEntity>

    @Query("SELECT MAX(time) FROM point WHERE source = 'live'")
    fun lastLiveFix(): Flow<Long?>

    @Query("SELECT * FROM point WHERE source = 'live' ORDER BY time DESC LIMIT 1")
    fun latestLivePoint(): Flow<PointEntity?>

    @Query("UPDATE cell SET passes = passes + 1, lastSeen = :time WHERE h3 = :h3 AND resolution = :resolution")
    suspend fun incrementCell(h3: Long, resolution: Int, time: Long): Int

    @Insert
    suspend fun insertCell(cell: CellEntity)

    @Transaction
    suspend fun addPass(h3: Long, resolution: Int, time: Long) {
        if (incrementCell(h3, resolution, time) == 0) insertCell(CellEntity(h3, resolution, 1, time, time))
    }

    @Query("SELECT * FROM cell WHERE resolution = :resolution")
    fun cells(resolution: Int): Flow<List<CellEntity>>

    @Query("SELECT * FROM cell WHERE resolution = :resolution")
    suspend fun cellsNow(resolution: Int): List<CellEntity>

    @Insert
    suspend fun insertStay(stay: StayEntity)

    @Query("SELECT * FROM stay ORDER BY start DESC")
    fun stays(): Flow<List<StayEntity>>

    @Query("SELECT * FROM stay ORDER BY start")
    suspend fun staysNow(): List<StayEntity>

    @Query("DELETE FROM point WHERE time < :before")
    suspend fun deletePointsBefore(before: Long)

    @Query("DELETE FROM stay WHERE `end` < :before")
    suspend fun deleteStaysBefore(before: Long)

    @Query("DELETE FROM cell WHERE lastSeen < :before")
    suspend fun deleteCellsBefore(before: Long)

    @Transaction
    suspend fun deleteOlderThan(before: Long) {
        deletePointsBefore(before)
        deleteStaysBefore(before)
        deleteCellsBefore(before)
    }

    @Query("SELECT COUNT(*) FROM point")
    suspend fun pointCount(): Int

    @Query("SELECT COUNT(*) FROM point WHERE source = :source")
    suspend fun pointCount(source: String): Int

    @Query("SELECT MAX(time) FROM point WHERE source = :source")
    suspend fun lastPointTime(source: String): Long?

    @Query("SELECT MIN(time) FROM point WHERE source NOT IN (:excluded)")
    suspend fun firstPointTimeExcept(excluded: List<String>): Long?

    @Query("DELETE FROM point")
    suspend fun clearPoints()

    @Query("DELETE FROM cell")
    suspend fun clearCells()

    @Query("DELETE FROM stay")
    suspend fun clearStays()

    @Transaction
    suspend fun clearAll() {
        clearPoints()
        clearCells()
        clearStays()
    }
}

@Dao
interface HiddenPlaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hide(entry: HiddenPlaceEntity)

    @Query("SELECT placeId FROM hidden_place WHERE hiddenUntil > :now")
    fun hiddenIds(now: Long): Flow<List<Long>>

    @Query("DELETE FROM hidden_place WHERE placeId = :placeId")
    suspend fun unhide(placeId: Long)
}

@Dao
interface FeedbackOutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(entry: FeedbackOutboxEntity)

    @Query("SELECT * FROM feedback_outbox ORDER BY createdAt LIMIT :limit")
    suspend fun pending(limit: Int): List<FeedbackOutboxEntity>

    @Query("DELETE FROM feedback_outbox WHERE eventId = :eventId")
    suspend fun remove(eventId: String)

    @Query("DELETE FROM feedback_outbox")
    suspend fun clear()
}
