package lt.ehu.unwalked.data

import java.io.IOException
import kotlinx.coroutines.test.runTest
import lt.ehu.unwalked.core.network.NetworkModule
import lt.ehu.unwalked.core.network.SafeApiCall
import lt.ehu.unwalked.data.feedback.FeedbackAction
import lt.ehu.unwalked.data.feedback.FeedbackRepository
import lt.ehu.unwalked.testing.FakeApi
import lt.ehu.unwalked.testing.FakeOutboxDao
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedbackRepositoryTest {
    private val outbox = FakeOutboxDao()
    private val api = FakeApi()
    private val repository = FeedbackRepository(outbox, api, SafeApiCall(NetworkModule.json), consent = { true })

    @Test
    fun sentEventsLeaveTheOutbox() = runTest {
        repository.record("nearby_new_place", FeedbackAction.Accepted, "cafe")
        assertEquals(0, outbox.entries.size)
        assertEquals("accepted", api.feedback.single().action)
    }

    @Test
    fun offlineEventsStayAndAreRetriedWithTheSameId() = runTest {
        api.failWith = IOException("offline")
        repository.record("walking_route", FeedbackAction.Dismissed, null)
        val eventId = outbox.entries.keys.single()
        api.failWith = null
        assertEquals(1, repository.flush())
        assertEquals(eventId, api.feedback.single().eventId)
    }

    @Test
    fun repeatsAnsweredWithOkAreDone() = runTest {
        api.feedbackCode = 200
        repository.record("unvisited_cell", FeedbackAction.Visited, null)
        assertEquals(0, outbox.entries.size)
    }

    @Test
    fun serverErrorKeepsTheEvent() = runTest {
        api.feedbackCode = 503
        repository.record("unvisited_cell", FeedbackAction.Visited, null)
        assertEquals(1, outbox.entries.size)
    }
}
