package lt.ehu.unwalked.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "point", indices = [Index("time")])
data class PointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val lat: Double,
    val lon: Double,
    val accuracy: Float,
    val source: String,
)

@Entity(tableName = "cell", primaryKeys = ["h3", "resolution"])
data class CellEntity(
    val h3: Long,
    val resolution: Int,
    val passes: Int,
    val firstSeen: Long,
    val lastSeen: Long,
)

@Entity(tableName = "stay", indices = [Index("start")])
data class StayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val start: Long,
    val end: Long,
    val lat: Double,
    val lon: Double,
    val points: Int,
    val source: String,
)

@Entity(tableName = "hidden_place")
data class HiddenPlaceEntity(
    @PrimaryKey val placeId: Long,
    val hiddenUntil: Long,
)

@Entity(tableName = "feedback_outbox")
data class FeedbackOutboxEntity(
    @PrimaryKey val eventId: String,
    val recommendationType: String,
    val action: String,
    val category: String?,
    val createdAt: Long,
)
