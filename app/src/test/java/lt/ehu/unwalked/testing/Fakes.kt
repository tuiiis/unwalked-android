package lt.ehu.unwalked.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import lt.ehu.unwalked.core.error.AppError
import lt.ehu.unwalked.core.error.Outcome
import lt.ehu.unwalked.core.network.CredentialsRequest
import lt.ehu.unwalked.core.network.FeedbackRequest
import lt.ehu.unwalked.core.network.LoginResponse
import lt.ehu.unwalked.core.network.PlaceDto
import lt.ehu.unwalked.core.network.PlacesByCellsRequest
import lt.ehu.unwalked.core.network.RegisterResponse
import lt.ehu.unwalked.core.network.UnwalkedApi
import lt.ehu.unwalked.core.session.AccessToken
import lt.ehu.unwalked.data.auth.AuthRepository
import lt.ehu.unwalked.data.local.FeedbackOutboxDao
import lt.ehu.unwalked.data.local.FeedbackOutboxEntity
import lt.ehu.unwalked.data.local.HiddenPlaceDao
import lt.ehu.unwalked.data.local.HiddenPlaceEntity
import lt.ehu.unwalked.data.places.PlacesRepository
import lt.ehu.unwalked.domain.recommendation.Habits
import lt.ehu.unwalked.domain.recommendation.HabitualPoint
import lt.ehu.unwalked.domain.recommendation.HabitualSource
import lt.ehu.unwalked.domain.recommendation.PlaceCandidate
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

class FakeAuthRepository(var result: Outcome<Unit> = Outcome.Success(Unit)) : AuthRepository {
    val calls = mutableListOf<Pair<String, String>>()
    override val session: StateFlow<AccessToken?> = MutableStateFlow(null)
    override suspend fun signIn(email: String, password: String): Outcome<Unit> = result.also { calls += email to password }
    override suspend fun register(email: String, password: String): Outcome<Unit> = result.also { calls += email to password }
    override fun signOut() = Unit
}

class FakePlacesRepository(var result: Outcome<List<PlaceCandidate>>) : PlacesRepository {
    var requestedCells: List<String> = emptyList()
    override suspend fun inCells(cells: List<String>, limit: Int): Outcome<List<PlaceCandidate>> = result.also { requestedCells = cells }
}

class FakeHabitualSource(private val visited: Map<Long, Int>, private val centres: List<HabitualPoint>) : HabitualSource {
    override suspend fun habitual(since: Long) = Habits(visited.keys, visited, centres)
}

class FakeHiddenPlaceDao : HiddenPlaceDao {
    private val entries = MutableStateFlow<Map<Long, Long>>(emptyMap())
    override suspend fun hide(entry: HiddenPlaceEntity) {
        entries.value = entries.value + (entry.placeId to entry.hiddenUntil)
    }
    override fun hiddenIds(now: Long): Flow<List<Long>> = entries.map { map -> map.filterValues { it > now }.keys.toList() }
    override suspend fun unhide(placeId: Long) {
        entries.value = entries.value - placeId
    }
}

class FakeOutboxDao : FeedbackOutboxDao {
    val entries = linkedMapOf<String, FeedbackOutboxEntity>()
    override suspend fun enqueue(entry: FeedbackOutboxEntity) {
        entries.putIfAbsent(entry.eventId, entry)
    }
    override suspend fun pending(limit: Int) = entries.values.take(limit)
    override suspend fun remove(eventId: String) {
        entries.remove(eventId)
    }
    override suspend fun clear() = entries.clear()
}

class FakeApi(var feedbackCode: Int = 201, var failWith: Exception? = null) : UnwalkedApi {
    val feedback = mutableListOf<FeedbackRequest>()
    override suspend fun register(body: CredentialsRequest) = RegisterResponse("id")
    override suspend fun login(body: CredentialsRequest) = LoginResponse("token", "Bearer", "2030-01-01T00:00:00Z")
    override suspend fun placesNear(lat: Double, lon: Double, radius: Double, category: String?, limit: Int?) = emptyList<PlaceDto>()
    override suspend fun placesByCells(body: PlacesByCellsRequest) = emptyList<PlaceDto>()
    override suspend fun submitFeedback(body: FeedbackRequest): Response<Unit> {
        failWith?.let { throw it }
        feedback += body
        return if (feedbackCode in 200..299) Response.success(feedbackCode, Unit) else Response.error(feedbackCode, "".toResponseBody(null))
    }
}

val networkDown = Outcome.Failure(AppError.Network("timeout"))
