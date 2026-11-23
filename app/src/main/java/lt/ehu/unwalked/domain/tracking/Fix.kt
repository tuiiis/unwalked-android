package lt.ehu.unwalked.domain.tracking

data class Fix(
    val time: Long,
    val lat: Double,
    val lon: Double,
    val accuracy: Float = 10f,
)

data class Stay(
    val start: Long,
    val end: Long,
    val lat: Double,
    val lon: Double,
    val points: Int,
) {
    val durationMillis: Long get() = end - start
}

data class CellPass(
    val cell: Long,
    val resolution: Int,
    val time: Long,
)
