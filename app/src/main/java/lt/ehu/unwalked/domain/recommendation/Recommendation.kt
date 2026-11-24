package lt.ehu.unwalked.domain.recommendation

data class PlaceCandidate(
    val id: Long,
    val name: String,
    val category: String,
    val lat: Double,
    val lon: Double,
    val cell: Long,
)

data class Recommendation(
    val place: PlaceCandidate,
    val categoryGroup: CategoryGroup,
    val metresFromHabitual: Double,
    val walkingMinutes: Int,
    val hook: Hook,
    val timesPassed: Int = 0,
) {
    val reason: String get() = when (hook) {
        Hook.Overlooked -> "You have walked past it $timesPassed times"
        Hook.OnYourWay -> "Right on a street you know"
        Hook.AroundTheCorner -> "A short turn off your usual route"
        Hook.JustBeyond -> "Just past where you usually turn"
    }
    val badge: String get() = if (hook == Hook.Overlooked) "${hook.title} · passed $timesPassed times" else hook.title
    val detour: String get() = "$walkingMinutes min off your route"
}

enum class Hook(val title: String) {
    Overlooked("Overlooked"),
    OnYourWay("On your way"),
    AroundTheCorner("Around the corner"),
    JustBeyond("Just beyond"),
}

enum class SortOrder(val label: String) {
    BestMatch("Best match"),
    Closest("Closest"),
}

enum class CategoryGroup(val label: String) {
    All("All"),
    Food("Food and drink"),
    Nature("Parks"),
    Culture("Culture"),
    Shops("Shops"),
    Other("Other"),
    ;

    companion object {
        private val keywords = listOf(
            Food to listOf("cafe", "coffee", "restaurant", "bar", "pub", "bakery", "food", "pizza", "bistro", "tea", "dessert", "ice", "brewery", "wine", "cafeteria", "steakhouse", "sushi", "burger", "gastropub", "pastry", "juice", "deli", "kebab", "ramen", "noodles", "cocktail"),
            Nature to listOf("park", "garden", "forest", "lake", "river", "beach", "playground", "viewpoint", "nature", "trail", "square", "plaza", "botanical", "zoo"),
            Culture to listOf("museum", "gallery", "church", "theatre", "theater", "monument", "landmark", "library", "art", "historic", "historical", "cinema", "music", "cathedral", "castle", "worship", "attraction", "memorial", "sculpture", "fountain", "statue", "venue"),
            Shops to listOf("store", "shop", "shopping", "market", "supermarket", "boutique", "mall", "bookstore", "florist", "clothing"),
        )

        private val errands = setOf("auto", "car", "building", "supply", "hardware", "parts", "equipment", "industrial", "construction", "tire", "motorcycle", "locker", "charging", "parking", "repair")

        fun of(category: String): CategoryGroup {
            val tokens = category.split('_').toSet()
            if (tokens.any(errands::contains)) return Other
            return keywords.firstOrNull { (_, words) -> words.any(tokens::contains) }?.first ?: Other
        }
    }
}

object CategoryNames {
    fun pretty(code: String): String = code.split('_').joinToString(" ").replaceFirstChar { it.uppercase() }
}
