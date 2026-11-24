package lt.ehu.unwalked.domain.stats

object CountryOutlines {
    const val ASSET = "countries.txt"

    fun parse(text: String): List<List<Pair<Double, Double>>> =
        text.lineSequence()
            .filter { it.isNotBlank() }
            .map { ring ->
                ring.trim().split(' ').map { point ->
                    val (lon, lat) = point.split(',')
                    lat.toDouble() to lon.toDouble()
                }
            }
            .toList()
}
