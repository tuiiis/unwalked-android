package lt.ehu.unwalked.feature.walk

import lt.ehu.unwalked.domain.route.RoutePreference
import lt.ehu.unwalked.domain.route.RouteShape
import lt.ehu.unwalked.domain.validation.WalkLength
import lt.ehu.unwalked.domain.validation.WalkMeasure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WalkViewModelTest {
    private val vm = WalkViewModel()

    @Test
    fun sliderSnapsToStepAndUpdatesText() {
        vm.onSlider(4.26)
        assertEquals(4.5, vm.state.value.slider, 0.0)
        assertEquals("4.5", vm.state.value.text)
    }

    @Test
    fun typedValueOutsideRangeDisablesBuild() {
        vm.onText("14")
        assertEquals(WalkLength.ERROR, vm.state.value.error)
        assertFalse(vm.state.value.canBuild)
        assertNull(vm.request())
    }

    @Test
    fun fixingTheValueClearsTheError() {
        vm.onText("14")
        vm.onText("7")
        assertNull(vm.state.value.error)
        assertEquals(7.0, vm.state.value.slider, 0.0)
    }

    @Test
    fun switchingToTimeResetsToDefaultMinutes() {
        vm.onText("14")
        vm.onMeasure(WalkMeasure.Time)
        assertEquals("45", vm.state.value.text)
        assertNull(vm.state.value.error)
    }

    @Test
    fun requestCarriesKilometresShapeAndPreference() {
        vm.onMeasure(WalkMeasure.Time)
        vm.onText("60")
        vm.onShape(RouteShape.OneWay)
        vm.onPreference(RoutePreference.Places)
        val request = vm.request()!!
        assertEquals(4.8, request.kilometres, 1e-9)
        assertEquals(RouteShape.OneWay, request.shape)
        assertEquals(RoutePreference.Places, request.preference)
    }
}
