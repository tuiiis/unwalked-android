package lt.ehu.unwalked.domain.stats

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

sealed interface FunFact {
    data class Distance(val kilometres: Double, val landmark: Landmark, val times: Double, val next: Landmark?) : FunFact
    data class TimeOfDay(val band: DayBand, val share: Int, val byHour: List<Double>) : FunFact
    data class FavouriteDay(val day: DayOfWeek, val share: Int, val byWeekday: List<Double>) : FunFact
    data class Streak(val days: Int) : FunFact
    data class BestDay(val date: LocalDate, val newStreets: Int) : FunFact
    data class Pace(val averageKmh: Double, val fastestKmh: Double, val fastestKm: Double) : FunFact
}

data class Landmark(val name: String, val kilometres: Double)

enum class DayBand(val title: String, val phrase: String, val hours: IntRange) {
    Morning("Morning walker", "before noon", 5..10),
    Afternoon("Afternoon stroller", "in the afternoon", 11..16),
    Evening("Evening wanderer", "after 17:00", 17..21),
    Night("Night owl", "after dark", 22..28);

    fun contains(hour: Int): Boolean = hour in hours || hour + 24 in hours
}

object FunFacts {
    val landmarks = listOf(
        Landmark("Gedimino Avenue end to end", 1.8),
        Landmark("the road from Vilnius to Trakai", 28.0),
        Landmark("a marathon", 42.2),
        Landmark("the road from Vilnius to Kaunas", 103.0),
        Landmark("the road from Vilnius to Riga", 300.0),
    )

    fun of(walks: List<Walk>, newCells: List<CellVisit>, zone: ZoneId): List<FunFact> {
        val facts = mutableListOf<FunFact>()
        val total = walks.sumOf { it.kilometres }
        landmarks.lastOrNull { it.kilometres <= total }?.let { landmark ->
            facts += FunFact.Distance(total, landmark, total / landmark.kilometres, landmarks.firstOrNull { it.kilometres > total })
        }
        bestDay(newCells, zone)?.let { facts += it }
        val real = walks.filter { it.kilometres >= MIN_HABIT_KM }
        val realTotal = real.sumOf { it.kilometres }
        if (real.size >= MIN_WALKS && realTotal > 0) {
            val byHour = DoubleArray(HOURS)
            val byWeekday = DoubleArray(DayOfWeek.entries.size)
            real.forEach { walk ->
                val time = Instant.ofEpochMilli(walk.start).atZone(zone)
                byHour[time.hour] += walk.kilometres
                byWeekday[time.dayOfWeek.value - 1] += walk.kilometres
            }
            val band = DayBand.entries.maxBy { band -> (0 until HOURS).filter(band::contains).sumOf { byHour[it] } }
            val bandShare = (0 until HOURS).filter(band::contains).sumOf { byHour[it] } / realTotal
            facts += FunFact.TimeOfDay(band, (100 * bandShare).roundToInt(), byHour.toList())
            val favourite = byWeekday.indices.maxBy { byWeekday[it] }
            if (byWeekday.count { it > 0 } > 1) facts += FunFact.FavouriteDay(DayOfWeek.of(favourite + 1), (100 * byWeekday[favourite] / realTotal).roundToInt(), byWeekday.toList())
        }
        longestStreak(walks, zone).takeIf { it >= MIN_STREAK }?.let { facts += FunFact.Streak(it) }
        val timed = walks.filter { it.kilometres >= MIN_PACE_KM && it.hours >= MIN_PACE_HOURS }
        if (timed.isNotEmpty()) {
            val fastest = timed.maxBy { it.kilometres / it.hours }
            facts += FunFact.Pace(timed.sumOf { it.kilometres } / timed.sumOf { it.hours }, fastest.kilometres / fastest.hours, fastest.kilometres)
        }
        return facts
    }

    private fun bestDay(newCells: List<CellVisit>, zone: ZoneId): FunFact.BestDay? =
        newCells.groupBy { Instant.ofEpochMilli(it.firstSeen).atZone(zone).toLocalDate() }
            .maxByOrNull { it.value.size }
            ?.takeIf { it.value.size >= MIN_NEW_STREETS }
            ?.let { FunFact.BestDay(it.key, it.value.size) }

    private fun longestStreak(walks: List<Walk>, zone: ZoneId): Int {
        val days = walks.map { Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() }.toSortedSet()
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        days.forEach { day ->
            run = if (previous?.plusDays(1) == day) run + 1 else 1
            best = maxOf(best, run)
            previous = day
        }
        return best
    }

    private const val MIN_WALKS = 3
    private const val MIN_HABIT_KM = 0.3
    private const val MIN_PACE_KM = 0.8
    private const val MIN_PACE_HOURS = 0.15
    private const val HOURS = 24
    private const val MIN_STREAK = 2
    private const val MIN_NEW_STREETS = 5
}
