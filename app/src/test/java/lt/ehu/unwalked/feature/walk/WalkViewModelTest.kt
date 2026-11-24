package lt.ehu.unwalked.feature.walk

import lt.ehu.unwalked.domain.route.RoutePreference
import lt.ehu.unwalked.domain.route.RouteShape
import org.junit.Assert.assertEquals
import org.junit.Test

class WalkViewModelTest {
    private val vm = WalkViewModel()

    @Test
    fun sliderSnapsToHalfKilometres() {
        vm.onLength(4.26)
        assertEquals(4.5, vm.state.value.kilometres, 0.0)
    }

    @Test
    fun sliderStaysWithinOneToTenKilometres() {
        vm.onLength(0.2)
        assertEquals(1.0, vm.state.value.kilometres, 0.0)
        vm.onLength(14.0)
        assertEquals(10.0, vm.state.value.kilometres, 0.0)
    }

    @Test
    fun timeFollowsTheDistance() {
        vm.onLength(2.5)
        assertEquals(30, vm.state.value.minutes)
    }

    @Test
    fun requestCarriesKilometresShapeAndPreference() {
        vm.onLength(6.0)
        vm.onShape(RouteShape.OneWay)
        vm.onPreference(RoutePreference.Places)
        val request = vm.request()
        assertEquals(6.0, request.kilometres, 0.0)
        assertEquals(RouteShape.OneWay, request.shape)
        assertEquals(RoutePreference.Places, request.preference)
    }
}
