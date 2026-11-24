package lt.ehu.unwalked.data.places

import com.uber.h3core.H3Core
import kotlinx.coroutines.test.runTest
import lt.ehu.unwalked.core.network.NetworkModule
import lt.ehu.unwalked.core.network.SafeApiCall
import lt.ehu.unwalked.core.session.InMemoryTokenStore
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AreaPreparationTest {
    private val server = MockWebServer()
    private val h3 = H3Core.newInstance()
    private lateinit var places: RemotePlacesRepository
    private val oldTown = listOf(h3.latLngToCellAddress(54.8972, 23.8861, 11), h3.latLngToCellAddress(54.8975, 23.8870, 11))
    private val area = h3.h3ToString(h3.cellToParent(h3.stringToH3(oldTown.first()), 5))

    @Before
    fun start() {
        server.start()
        val api = NetworkModule.api(server.url("/").toString(), NetworkModule.okHttp(InMemoryTokenStore(null), verbose = false))
        places = RemotePlacesRepository(api, SafeApiCall(NetworkModule.json), h3, pollMillis = 1)
    }

    @After
    fun stop() = server.close()

    private fun answer(code: Int, status: String) =
        server.enqueue(MockResponse.Builder().code(code).body("""{"cell":"$area","status":"$status"}""").build())

    @Test
    fun waitsWhileANewAreaLoads() = runTest {
        answer(202, "Loading")
        answer(200, "Loading")
        answer(200, "Ready")
        var told = 0

        places.prepareAreas(oldTown) { told++ }

        assertEquals(1, told)
        assertEquals(3, server.requestCount)
        assertEquals("PUT /api/v1/areas/$area", server.takeRequest().let { "${it.method} ${it.url.encodedPath}" })
    }

    @Test
    fun readyAreaNeedsOneRequest() = runTest {
        answer(200, "Ready")
        var told = 0

        places.prepareAreas(oldTown) { told++ }

        assertEquals(0, told)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun withoutWaitingOnlyStartsTheLoad() = runTest {
        answer(202, "Loading")

        places.prepareAreas(oldTown, wait = false)

        assertEquals(1, server.requestCount)
    }

    @Test
    fun limitsAreNotAnError() = runTest {
        server.enqueue(MockResponse.Builder().code(429).addHeader("Content-Type", "application/problem+json").body("""{"title":"Too many requests.","status":429}""").build())

        places.prepareAreas(oldTown)

        assertEquals(1, server.requestCount)
    }
}
