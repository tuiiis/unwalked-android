package lt.ehu.unwalked.domain.stats

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

enum class Period { Month, Quarter, Year, AllTime }

data class PeriodRange(val start: Long, val end: Long, val previousStart: Long?, val title: String, val subtitle: String)

object Periods {
    fun range(period: Period, nowMillis: Long, zone: ZoneId, firstDataMillis: Long?): PeriodRange {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return when (period) {
            Period.Month -> {
                val first = today.withDayOfMonth(1)
                PeriodRange(
                    start = startOf(first, zone),
                    end = nowMillis,
                    previousStart = startOf(first.minusMonths(1), zone),
                    title = "${first.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${first.year}",
                    subtitle = "What is new this month",
                )
            }
            Period.Quarter -> {
                val first = today.withMonth((today.monthValue - 1) / 3 * 3 + 1).withDayOfMonth(1)
                PeriodRange(
                    start = startOf(first, zone),
                    end = nowMillis,
                    previousStart = startOf(first.minusMonths(3), zone),
                    title = "Q${(first.monthValue - 1) / 3 + 1} ${first.year}",
                    subtitle = "What is new this quarter",
                )
            }
            Period.Year -> {
                val first = today.withDayOfYear(1)
                PeriodRange(
                    start = startOf(first, zone),
                    end = nowMillis,
                    previousStart = startOf(first.minusYears(1), zone),
                    title = "Year ${first.year}",
                    subtitle = "What is new this year",
                )
            }
            Period.AllTime -> {
                val since = firstDataMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
                PeriodRange(
                    start = 0L,
                    end = nowMillis,
                    previousStart = null,
                    title = "All time",
                    subtitle = since?.let { "Since ${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${it.year}" } ?: "No walks recorded yet",
                )
            }
        }
    }

    private fun startOf(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()
}
