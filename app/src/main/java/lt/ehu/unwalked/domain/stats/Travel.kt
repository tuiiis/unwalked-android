package lt.ehu.unwalked.domain.stats

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

data class CityDays(val city: City, val days: Int)

data class CountryDays(val code: String, val name: String, val days: Int)

data class Travel(
    val cities: List<CityDays>,
    val countries: List<CountryDays>,
    val home: City,
    val homeShare: Int,
    val awayDays: Int,
)

object TravelStats {
    fun of(points: List<TimedPoint>, gazetteer: Gazetteer, zone: ZoneId): Travel? {
        val memo = HashMap<Long, City?>()
        val days = HashMap<LocalDate, MutableMap<City, Int>>()
        points.forEachIndexed { i, point ->
            if (i % SAMPLE_EVERY != 0) return@forEachIndexed
            val key = (Math.round(point.lat * GRID) shl 32) xor (Math.round(point.lon * GRID) and 0xffffffffL)
            val city = memo.getOrPut(key) { gazetteer.nearest(point.lat, point.lon) } ?: return@forEachIndexed
            val day = Instant.ofEpochMilli(point.time).atZone(zone).toLocalDate()
            days.getOrPut(day) { HashMap() }.merge(city, 1, Int::plus)
        }
        val cityDays = HashMap<City, Int>()
        days.values.forEach { counts -> counts.filterValues { it >= MIN_SAMPLES_PER_DAY }.keys.forEach { cityDays.merge(it, 1, Int::plus) } }
        if (cityDays.isEmpty()) return null
        val cities = cityDays.map { (city, count) -> CityDays(city, count) }.sortedByDescending { it.days }
        val home = cities.first().city
        val countries = cities.groupBy { it.city.country }
            .map { (code, list) -> CountryDays(code, list.first().city.countryName, days.values.count { counts -> counts.keys.any { it.country == code && counts.getValue(it) >= MIN_SAMPLES_PER_DAY } }) }
            .sortedByDescending { it.days }
        val counted = days.values.filter { counts -> counts.values.any { it >= MIN_SAMPLES_PER_DAY } }
        val homeDays = counted.count { counts -> (counts[home] ?: 0) >= MIN_SAMPLES_PER_DAY }
        val away = counted.count { counts -> counts.any { (city, samples) -> city != home && samples >= MIN_SAMPLES_PER_DAY } }
        return Travel(cities, countries, home, (100.0 * homeDays / counted.size).roundToInt(), away)
    }

    private const val SAMPLE_EVERY = 5
    private const val GRID = 20.0
    private const val MIN_SAMPLES_PER_DAY = 3
}
