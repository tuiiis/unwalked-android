package lt.ehu.unwalked.core.error

import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class UserMessage(val text: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null)

class ErrorReporter(private val log: (String, String) -> Unit = { tag, text -> Log.w(tag, text) }) {
    private val mutableMessages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val mutableSignOuts = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val messages: SharedFlow<UserMessage> = mutableMessages.asSharedFlow()
    val signOuts: SharedFlow<Unit> = mutableSignOuts.asSharedFlow()

    fun report(error: AppError, retry: (() -> Unit)? = null) {
        log(TAG, error.toString())
        when (error) {
            is AppError.Unauthorized -> {
                mutableSignOuts.tryEmit(Unit)
                mutableMessages.tryEmit(UserMessage("Your session has ended. Sign in again."))
            }
            is AppError.Validation -> Unit
            else -> mutableMessages.tryEmit(UserMessage(userText(error), retry?.let { "Retry" }, retry))
        }
    }

    fun log(error: AppError) {
        log(TAG, error.toString())
    }

    fun info(text: String) {
        mutableMessages.tryEmit(UserMessage(text))
    }

    companion object {
        private const val TAG = "Unwalked"

        fun userText(error: AppError): String = when (error) {
            is AppError.Network -> "No connection to the server. Check the internet and try again."
            is AppError.Server -> "Something went wrong on our side." + (error.traceId?.let { " Code $it." } ?: "")
            is AppError.Forbidden -> "You do not have access to this."
            is AppError.NotFound -> "This item no longer exists."
            is AppError.Conflict -> error.message
            is AppError.Unauthorized -> error.message
            is AppError.Validation -> error.message
            is AppError.Unexpected -> "Something went wrong. Try again."
        }
    }
}
