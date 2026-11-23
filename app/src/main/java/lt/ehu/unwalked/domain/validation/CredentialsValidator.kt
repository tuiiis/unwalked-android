package lt.ehu.unwalked.domain.validation

data class CredentialErrors(val email: String? = null, val password: String? = null, val confirmation: String? = null) {
    val isValid: Boolean get() = email == null && password == null && confirmation == null
}

object CredentialsValidator {
    const val MIN_PASSWORD = 10
    const val MAX_PASSWORD = 128
    private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun email(value: String): String? = when {
        value.isBlank() -> "Enter your email."
        !emailPattern.matches(value.trim()) -> "Enter an email like name@example.com."
        else -> null
    }

    fun password(value: String): String? = when {
        value.isBlank() -> "Enter a password."
        value.length < MIN_PASSWORD -> "Use at least $MIN_PASSWORD characters."
        value.length > MAX_PASSWORD -> "Use at most $MAX_PASSWORD characters."
        else -> null
    }

    fun confirmation(password: String, confirmation: String): String? =
        if (password != confirmation) "Passwords do not match." else null

    fun signIn(email: String, password: String) = CredentialErrors(
        email = email(email),
        password = if (password.isBlank()) "Enter a password." else null,
    )

    fun register(email: String, password: String, confirmation: String) = CredentialErrors(
        email = email(email),
        password = password(password),
        confirmation = confirmation(password, confirmation),
    )
}
