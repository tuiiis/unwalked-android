package lt.ehu.unwalked.domain.validation

enum class WalkMeasure(val min: Double, val max: Double, val step: Double, val unit: String) {
    Distance(1.0, 10.0, 0.5, "km"),
    Time(15.0, 120.0, 5.0, "min"),
}

sealed interface WalkLength {
    data class Valid(val value: Double, val measure: WalkMeasure) : WalkLength {
        val kilometres: Double get() = if (measure == WalkMeasure.Distance) value else value / 60.0 * WALKING_KMH
        val minutes: Double get() = if (measure == WalkMeasure.Time) value else value / WALKING_KMH * 60.0
    }

    data class Invalid(val message: String) : WalkLength

    companion object {
        const val WALKING_KMH = 4.8
        const val ERROR = "Enter 1 to 10 km. By time: 15 min to 2 h."

        fun parse(text: String, measure: WalkMeasure): WalkLength {
            val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return Invalid(ERROR)
            if (value.isNaN() || value < measure.min || value > measure.max) return Invalid(ERROR)
            return Valid(value, measure)
        }

        fun format(value: Double): String =
            if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(java.util.Locale.US, value)
    }
}
