package lt.ehu.unwalked.core.network

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import lt.ehu.unwalked.core.error.AppError
import lt.ehu.unwalked.core.error.Outcome
import retrofit2.HttpException

class SafeApiCall(private val json: Json) {
    suspend operator fun <T> invoke(block: suspend () -> T): Outcome<T> = try {
        Outcome.Success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (http: HttpException) {
        Outcome.Failure(fromHttp(http.code(), http.response()?.errorBody()?.string()))
    } catch (io: IOException) {
        Outcome.Failure(AppError.Network(io.message ?: "Network error"))
    } catch (other: Exception) {
        Outcome.Failure(AppError.Unexpected(other.message ?: other.javaClass.simpleName))
    }

    fun fromHttp(status: Int, body: String?): AppError {
        val problem = body?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString<ProblemDetails>(it) }.getOrNull() }
        val text = problem?.detail ?: problem?.title ?: "HTTP $status"
        return when (status) {
            400 -> AppError.Validation(problem?.errors.orEmpty().mapKeys { it.key.lowercase() }, text)
            401 -> AppError.Unauthorized(text)
            403 -> AppError.Forbidden(text)
            404 -> AppError.NotFound(text)
            409 -> AppError.Conflict(text)
            in 500..599 -> AppError.Server(problem?.traceId, text)
            else -> AppError.Unexpected(text)
        }
    }
}
