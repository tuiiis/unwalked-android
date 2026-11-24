package lt.ehu.unwalked.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lt.ehu.unwalked.core.error.AppError
import lt.ehu.unwalked.core.error.ErrorReporter
import lt.ehu.unwalked.core.error.Outcome
import lt.ehu.unwalked.data.auth.AuthRepository
import lt.ehu.unwalked.domain.validation.CredentialErrors
import lt.ehu.unwalked.domain.validation.CredentialsValidator

enum class AuthMode { SignIn, Register }

data class AuthUiState(
    val mode: AuthMode,
    val email: String = "",
    val password: String = "",
    val confirmation: String = "",
    val errors: CredentialErrors = CredentialErrors(),
    val formError: String? = null,
    val submitting: Boolean = false,
    val submitted: Boolean = false,
)

class AuthViewModel(
    mode: AuthMode,
    private val auth: AuthRepository,
    private val errors: ErrorReporter,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AuthUiState(mode))
    val state: StateFlow<AuthUiState> = mutableState.asStateFlow()

    fun onEmail(value: String) = mutableState.update {
        it.copy(email = value, formError = null, errors = if (it.submitted) validate(it.copy(email = value)) else it.errors)
    }

    fun onPassword(value: String) = mutableState.update {
        it.copy(password = value, formError = null, errors = if (it.submitted) validate(it.copy(password = value)) else it.errors)
    }

    fun onConfirmation(value: String) = mutableState.update {
        it.copy(confirmation = value, formError = null, errors = if (it.submitted) validate(it.copy(confirmation = value)) else it.errors)
    }

    fun submit() {
        val current = mutableState.value
        if (current.submitting) return
        val found = validate(current)
        mutableState.update { it.copy(errors = found, submitted = true, formError = null) }
        if (!found.isValid) return
        mutableState.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val result = when (current.mode) {
                AuthMode.SignIn -> auth.signIn(current.email, current.password)
                AuthMode.Register -> auth.register(current.email, current.password)
            }
            mutableState.update { it.copy(submitting = false) }
            if (result is Outcome.Failure) handle(result.error)
        }
    }

    private fun handle(error: AppError) {
        when (error) {
            is AppError.Unauthorized -> mutableState.update { it.copy(formError = "Email or password is incorrect.") }

            is AppError.Conflict -> mutableState.update { it.copy(errors = it.errors.copy(email = "An account with this email already exists. Sign in instead.")) }

            is AppError.Validation -> mutableState.update {
                it.copy(
                    errors = it.errors.copy(
                        email = error.fields["email"]?.firstOrNull() ?: it.errors.email,
                        password = error.fields["password"]?.firstOrNull() ?: it.errors.password,
                    ),
                    formError = if (error.fields.isEmpty()) error.message else null,
                )
            }

            else -> {
                mutableState.update { it.copy(formError = ErrorReporter.userText(error)) }
                errors.report(error, retry = ::submit)
            }
        }
    }

    private fun validate(state: AuthUiState): CredentialErrors = when (state.mode) {
        AuthMode.SignIn -> CredentialsValidator.signIn(state.email, state.password)
        AuthMode.Register -> CredentialsValidator.register(state.email, state.password, state.confirmation)
    }
}
