package lt.ehu.unwalked.core.error

sealed interface AppError {
    val message: String

    data class Validation(val fields: Map<String, List<String>>, override val message: String) : AppError
    data class Unauthorized(override val message: String) : AppError
    data class Forbidden(override val message: String) : AppError
    data class NotFound(override val message: String) : AppError
    data class Conflict(override val message: String) : AppError
    data class Server(val traceId: String?, override val message: String) : AppError
    data class Network(override val message: String) : AppError
    data class Unexpected(override val message: String) : AppError
}

sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}
