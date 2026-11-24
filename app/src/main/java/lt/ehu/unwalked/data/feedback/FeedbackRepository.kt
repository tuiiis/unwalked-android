package lt.ehu.unwalked.data.feedback

import java.util.UUID
import lt.ehu.unwalked.core.error.Outcome
import lt.ehu.unwalked.core.network.FeedbackRequest
import lt.ehu.unwalked.core.network.SafeApiCall
import lt.ehu.unwalked.core.network.UnwalkedApi
import lt.ehu.unwalked.data.local.FeedbackOutboxDao
import lt.ehu.unwalked.data.local.FeedbackOutboxEntity

enum class FeedbackAction(val wire: String) { Accepted("accepted"), Dismissed("dismissed"), Visited("visited") }

class FeedbackRepository(
    private val outbox: FeedbackOutboxDao,
    private val api: UnwalkedApi,
    private val call: SafeApiCall,
    private val consent: () -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun record(recommendationType: String, action: FeedbackAction, category: String?) {
        if (!consent()) return
        outbox.enqueue(FeedbackOutboxEntity(UUID.randomUUID().toString(), recommendationType, action.wire, category, now()))
        flush()
    }

    suspend fun flush(): Int {
        var sent = 0
        for (entry in outbox.pending(BATCH)) {
            val request = FeedbackRequest(entry.eventId, entry.recommendationType, entry.action, entry.category)
            when (val result = call { api.submitFeedback(request) }) {
                is Outcome.Success -> if (result.value.isSuccessful || result.value.code() == 400) {
                    outbox.remove(entry.eventId)
                    sent++
                } else {
                    return sent
                }

                is Outcome.Failure -> return sent
            }
        }
        return sent
    }

    private companion object {
        const val BATCH = 50
    }
}
