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

data class WalkFormState(
    val kilometres: Double = 3.5,
    val shape: RouteShape = RouteShape.Loop,
    val preference: RoutePreference = RoutePreference.NewCells,
) {
    val minutes: Int get() = WalkLength.minutes(kilometres)
}

data class RouteRequest(val kilometres: Double, val shape: RouteShape, val preference: RoutePreference, val destination: LatLon? = null)

class WalkViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(WalkFormState())
    val state: StateFlow<WalkFormState> = mutableState.asStateFlow()

    fun onLength(kilometres: Double) = mutableState.update { it.copy(kilometres = WalkLength.snap(kilometres)) }

    fun onShape(shape: RouteShape) = mutableState.update { it.copy(shape = shape) }

    fun onPreference(preference: RoutePreference) = mutableState.update { it.copy(preference = preference) }

    fun request(): RouteRequest = mutableState.value.let { RouteRequest(it.kilometres, it.shape, it.preference) }
}
