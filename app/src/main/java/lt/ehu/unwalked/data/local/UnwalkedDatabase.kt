package lt.ehu.unwalked.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PointEntity::class, CellEntity::class, StayEntity::class, HiddenPlaceEntity::class, FeedbackOutboxEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class UnwalkedDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao
    abstract fun hiddenPlaces(): HiddenPlaceDao
    abstract fun feedbackOutbox(): FeedbackOutboxDao

    companion object {
        fun create(context: Context): UnwalkedDatabase =
            Room.databaseBuilder(context, UnwalkedDatabase::class.java, "unwalked.db").build()
    }
}
