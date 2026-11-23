package lt.ehu.unwalked.core.network

import kotlinx.coroutines.test.runTest
import lt.ehu.unwalked.core.error.AppError
import lt.ehu.unwalked.core.error.Outcome
import lt.ehu.unwalked.core.session.AccessToken
import lt.ehu.unwalked.core.session.InMemoryTokenStore
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiErrorHandlingTest {
    private val server = MockWebServer()
    private val tokens = InMemoryTokenStore(AccessToken("secret", Long.MAX_VALUE, "ona@example.test"))
    private lateinit var api: UnwalkedApi
    private val call = SafeApiCall(NetworkModule.json)

    @Before
    fun start() {
        server.start()
        api = NetworkModule.api(server.url("/").toString(), NetworkModule.okHttp(tokens, verbose = false))
    }

    @After
    fun stop() = server.close()

    private fun respond(code: Int, body: String) =
        server.enqueue(MockResponse.Builder().code(code).addHeader("Content-Type", "application/problem+json").body(body).build())

    @Test
    fun parsesPlaces() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""[{"id":8382,"externalId":"x","name":"Cat Cafe","category":"cafe","confidence":0.997,"lat":54.68,"lon":25.28,"h3Cell":"8b1f4030c6e0fff","extra":1}]""").build())
        val result = call { api.placesByCells(PlacesByCellsRequest(listOf("891f4030c63ffff"), limit = 2)) }
        assertEquals("Cat Cafe", (result as Outcome.Success).value.single().name)
        assertEquals("""{"cells":["891f4030c63ffff"],"limit":2}""", server.takeRequest().body!!.utf8())
    }

    @Test
    fun validationProblemKeepsFieldMessages() = runTest {
        respond(400, """{"title":"One or more validation errors occurred.","status":400,"errors":{"Email":["Email address is not valid."]},"traceId":"t1"}""")
        val error = (call { api.login(CredentialsRequest("bad", "x")) } as Outcome.Failure).error as AppError.Validation
        assertEquals(listOf("Email address is not valid."), error.fields["email"])
    }

    @Test
    fun unauthorizedUsesDetail() = runTest {
        respond(401, """{"title":"Authentication failed.","status":401,"detail":"Email or password is incorrect."}""")
        assertEquals(AppError.Unauthorized("Email or password is incorrect."), (call { api.login(CredentialsRequest("a@b.c", "x")) } as Outcome.Failure).error)
    }

    @Test
    fun conflictAndNotFoundAreMapped() = runTest {
        respond(409, """{"title":"Conflict.","status":409,"detail":"An account with this email already exists."}""")
        assertTrue((call { api.register(CredentialsRequest("a@b.c", "long enough")) } as Outcome.Failure).error is AppError.Conflict)
        respond(404, "")
        assertTrue((call { api.placesNear(1.0, 1.0, 100.0) } as Outcome.Failure).error is AppError.NotFound)
    }

    @Test
    fun serverErrorKeepsTraceId() = runTest {
        respond(500, """{"title":"An unexpected error occurred.","status":500,"traceId":"0HN:1"}""")
        assertEquals(AppError.Server("0HN:1", "An unexpected error occurred."), (call { api.placesNear(1.0, 1.0, 100.0) } as Outcome.Failure).error)
    }

    @Test
    fun forbiddenAndUnknownStatus() = runTest {
        respond(403, "not json")
        assertEquals(AppError.Forbidden("HTTP 403"), (call { api.placesNear(1.0, 1.0, 100.0) } as Outcome.Failure).error)
        respond(418, "")
        assertTrue((call { api.placesNear(1.0, 1.0, 100.0) } as Outcome.Failure).error is AppError.Unexpected)
    }

    @Test
    fun connectionFailureIsNetworkError() = runTest {
        server.close()
        assertTrue((call { api.placesNear(1.0, 1.0, 100.0) } as Outcome.Failure).error is AppError.Network)
    }

    @Test
    fun tokenIsSentOnlyToShares() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("[]").build())
        call { api.placesNear(1.0, 1.0, 100.0) }
        assertNull(server.takeRequest().headers["Authorization"])
    }
}
