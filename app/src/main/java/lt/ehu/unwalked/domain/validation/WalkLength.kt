package lt.ehu.unwalked.domain.validation

import java.util.Locale
import kotlin.math.roundToInt

object WalkLength {
    const val WALKING_KMH = 4.8
    const val MIN_KM = 1.0
    const val MAX_KM = 10.0
    const val STEP_KM = 0.5
    const val STEPS = ((MAX_KM - MIN_KM) / STEP_KM).toInt() - 1
    private const val MINUTE_STEP = 5

    fun snap(kilometres: Double): Double = (Math.round(kilometres / STEP_KM) * STEP_KM).coerceIn(MIN_KM, MAX_KM)

    fun minutes(kilometres: Double): Int = ((kilometres / WALKING_KMH * 60 / MINUTE_STEP).roundToInt() * MINUTE_STEP).coerceAtLeast(MINUTE_STEP)

    fun kilometres(value: Double): String = "%.1f km".format(Locale.US, value)

    fun duration(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}
