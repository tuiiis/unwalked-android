package lt.ehu.unwalked.feature.discover

import com.uber.h3core.H3Core
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import lt.ehu.unwalked.core.error.ErrorReporter
import lt.ehu.unwalked.core.error.Outcome
import lt.ehu.unwalked.core.network.NetworkModule
import lt.ehu.unwalked.core.network.SafeApiCall
import lt.ehu.unwalked.data.feedback.FeedbackRepository
import lt.ehu.unwalked.domain.recommendation.CandidateCells
import lt.ehu.unwalked.domain.recommendation.CategoryGroup
import lt.ehu.unwalked.domain.recommendation.HabitualPoint
import lt.ehu.unwalked.domain.recommendation.PlaceCandidate
import lt.ehu.unwalked.domain.recommendation.RecommendationEngine
import lt.ehu.unwalked.domain.recommendation.SortOrder
import lt.ehu.unwalked.testing.FakeApi
import lt.ehu.unwalked.testing.FakeHabitualSource
import lt.ehu.unwalked.testing.FakeHiddenPlaceDao
import lt.ehu.unwalked.testing.FakeOutboxDao
import lt.ehu.unwalked.testing.FakePlacesRepository
import lt.ehu.unwalked.testing.MainDispatcherRule
import lt.ehu.unwalked.testing.networkDown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DiscoverViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val h3 = H3Core.newInstance()
    private val home = HabitualPoint(54.6872, 25.2797)
    private val homeCell = h3.latLngToCell(home.lat, home.lon, 11)
    private val api = FakeApi()
    private val outbox = FakeOutboxDao()
    private var consent = true

    private fun place(id: Long, name: String, category: String, northMetres: Double): PlaceCandidate {
        val lat = home.lat + northMetres / 111_320.0
        return PlaceCandidate(id, name, category, lat, home.lon, h3.latLngToCell(lat, home.lon, 11))
    }

    private val places = FakePlacesRepository(
        Outcome.Success(listOf(place(1, "Cat Cafe", "cafe", 60.0), place(2, "Bernardine Garden", "park", 180.0), place(3, "Home bakery", "bakery", 0.0))),
    )

    private fun viewModel(history: FakeHabitualSource = FakeHabitualSource(mapOf(homeCell to 4), listOf(home))) = DiscoverViewModel(
        history,
        places,
        FakeHiddenPlaceDao(),
        FeedbackRepository(outbox, api, SafeApiCall(NetworkModule.json), consent = { consent }),
        CandidateCells(h3, 9),
        RecommendationEngine(),
        ErrorReporter(log = { _, _ -> }),
    )

    private suspend fun DiscoverViewModel.ready() = state.first { it.content is DiscoverContent.Ready }.content as DiscoverContent.Ready

    @Test
    fun ranksUnvisitedPlacesClosestToUsualRoutesFirst() = runTest {
        val vm = viewModel()
        assertEquals(listOf("Cat Cafe", "Bernardine Garden"), vm.ready().items.map { it.place.name })
        assertEquals(7, places.requestedCells.size)
    }

    @Test
    fun searchCategoryAndOrderFilterTheList() = runTest {
        val vm = viewModel()
        vm.onOrder(SortOrder.Closest)
        assertEquals(listOf("Cat Cafe", "Bernardine Garden"), vm.ready().items.map { it.place.name })
        vm.onGroup(CategoryGroup.Nature)
        assertEquals(listOf("Bernardine Garden"), vm.ready().items.map { it.place.name })
        vm.onGroup(CategoryGroup.All)
        vm.onQuery("cat")
        assertEquals(listOf("Cat Cafe"), vm.ready().items.map { it.place.name })
    }

    @Test
    fun notNowHidesAndSendsDismissedFeedback() = runTest {
        val vm = viewModel()
        val cafe = vm.ready().items.first { it.place.id == 1L }
        vm.notNow(cafe)
        assertEquals(listOf("Bernardine Garden"), vm.ready().items.map { it.place.name })
        assertEquals("dismissed", api.feedback.single().action)
        vm.undoHide()
        assertEquals(2, vm.ready().items.size)
    }

    @Test
    fun noFeedbackWithoutConsent() = runTest {
        consent = false
        val vm = viewModel()
        vm.go(vm.ready().items.first())
        assertTrue(api.feedback.isEmpty())
        assertTrue(outbox.entries.isEmpty())
    }

    @Test
    fun emptyHistoryAsksForWalksFirst() = runTest {
        val vm = viewModel(FakeHabitualSource(emptyMap(), emptyList()))
        assertEquals(DiscoverContent.NoHistory, vm.state.first { it.content != DiscoverContent.Loading }.content)
    }

    @Test
    fun networkFailureShowsRetryableState() = runTest {
        places.result = networkDown
        val vm = viewModel()
        val failed = vm.state.first { it.content is DiscoverContent.Failed }.content as DiscoverContent.Failed
        assertEquals("Cannot reach the server. Check the connection and try again.", failed.message)
        places.result = Outcome.Success(emptyList())
        vm.load()
        assertEquals(0, vm.ready().total)
    }
}
