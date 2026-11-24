package lt.ehu.unwalked.domain.route

import lt.ehu.unwalked.domain.recommendation.PlaceCandidate

data class WalkRoute(
    val path: List<LatLon>,
    val metres: Double,
    val minutes: Int,
    val shape: RouteShape,
    val newCells: Int,
    val newPlaces: Int,
    val unknownPercent: Int,
    val places: List<PlaceCandidate> = emptyList(),
) {
    val title: String
        get() {
            val km = "%.1f".format(java.util.Locale.US, metres / 1000)
            return when (shape) {
                RouteShape.Loop -> "$km km loop · about $minutes min"
                RouteShape.OneWay -> "$km km walk · about $minutes min"
            }
        }
}
