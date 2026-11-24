package lt.ehu.unwalked.feature.walk

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import lt.ehu.unwalked.domain.route.LatLon
import lt.ehu.unwalked.domain.route.RoutePreference
import lt.ehu.unwalked.domain.route.RouteShape
import lt.ehu.unwalked.domain.validation.WalkLength
import lt.ehu.unwalked.domain.validation.WalkMeasure

data class WalkFormState(
    val measure: WalkMeasure = WalkMeasure.Distance,
    val slider: Double = 3.5,
    val text: String = "3.5",
    val error: String? = null,
    val shape: RouteShape = RouteShape.Loop,
    val preference: RoutePreference = RoutePreference.NewCells,
) {
    val canBuild: Boolean get() = error == null
}

data class RouteRequest(val kilometres: Double, val shape: RouteShape, val preference: RoutePreference, val destination: LatLon? = null)

class WalkViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(WalkFormState())
    val state: StateFlow<WalkFormState> = mutableState.asStateFlow()

    fun onMeasure(measure: WalkMeasure) = mutableState.update {
        if (it.measure == measure) return@update it
        val value = if (measure == WalkMeasure.Time) DEFAULT_MINUTES else DEFAULT_KM
        it.copy(measure = measure, slider = value, text = WalkLength.format(value), error = null)
    }

    fun onSlider(value: Double) = mutableState.update {
        val stepped = (Math.round(value / it.measure.step) * it.measure.step).coerceIn(it.measure.min, it.measure.max)
        it.copy(slider = stepped, text = WalkLength.format(stepped), error = null)
    }

    fun onText(value: String) = mutableState.update {
        when (val parsed = WalkLength.parse(value, it.measure)) {
            is WalkLength.Valid -> it.copy(text = value, slider = parsed.value, error = null)
            is WalkLength.Invalid -> it.copy(text = value, error = parsed.message)
        }
    }

    fun onShape(shape: RouteShape) = mutableState.update { it.copy(shape = shape) }

    fun onPreference(preference: RoutePreference) = mutableState.update { it.copy(preference = preference) }

    fun request(): RouteRequest? {
        val current = mutableState.value
        return when (val parsed = WalkLength.parse(current.text, current.measure)) {
            is WalkLength.Valid -> RouteRequest(parsed.kilometres, current.shape, current.preference)

            is WalkLength.Invalid -> {
                mutableState.update { it.copy(error = parsed.message) }
                null
            }
        }
    }

    private companion object {
        const val DEFAULT_KM = 3.5
        const val DEFAULT_MINUTES = 45.0
    }
}
