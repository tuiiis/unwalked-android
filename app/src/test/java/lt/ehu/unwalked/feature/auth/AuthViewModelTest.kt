package lt.ehu.unwalked.feature.auth

import lt.ehu.unwalked.core.error.AppError
import lt.ehu.unwalked.core.error.ErrorReporter
import lt.ehu.unwalked.core.error.Outcome
import lt.ehu.unwalked.testing.FakeAuthRepository
import lt.ehu.unwalked.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AuthViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repository = FakeAuthRepository()
    private val reporter = ErrorReporter(log = { _, _ -> })

    private fun viewModel(mode: AuthMode = AuthMode.SignIn) = AuthViewModel(mode, repository, reporter)

    @Test
    fun invalidFormIsNotSubmitted() {
        val vm = viewModel(AuthMode.Register)
        vm.onEmail("ona")
        vm.onPassword("short")
        vm.submit()
        assertEquals("Enter an email like name@example.com.", vm.state.value.errors.email)
        assertEquals("Use at least 10 characters.", vm.state.value.errors.password)
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun errorsUpdateWhileTypingAfterFirstSubmit() {
        val vm = viewModel()
        vm.submit()
        assertEquals("Enter your email.", vm.state.value.errors.email)
        vm.onEmail("ona@example.test")
        assertNull(vm.state.value.errors.email)
    }

    @Test
    fun validFormCallsRepository() {
        val vm = viewModel()
        vm.onEmail("ona@example.test")
        vm.onPassword("Unwalked-test-1")
        vm.submit()
        assertEquals(listOf("ona@example.test" to "Unwalked-test-1"), repository.calls)
        assertFalse(vm.state.value.submitting)
    }

    @Test
    fun wrongCredentialsShowFormError() {
        repository.result = Outcome.Failure(AppError.Unauthorized("Email or password is incorrect."))
        val vm = viewModel()
        vm.onEmail("ona@example.test")
        vm.onPassword("wrong")
        vm.submit()
        assertEquals("Email or password is incorrect.", vm.state.value.formError)
    }

    @Test
    fun takenEmailIsShownOnTheEmailField() {
        repository.result = Outcome.Failure(AppError.Conflict("An account with this email already exists."))
        val vm = viewModel(AuthMode.Register)
        vm.onEmail("ona@example.test")
        vm.onPassword("long enough password")
        vm.onConfirmation("long enough password")
        vm.submit()
        assertEquals("An account with this email already exists. Sign in instead.", vm.state.value.errors.email)
    }

    @Test
    fun serverValidationIsMappedToFields() {
        repository.result = Outcome.Failure(AppError.Validation(mapOf("password" to listOf("Password must be 10-128 characters long.")), "invalid"))
        val vm = viewModel(AuthMode.Register)
        vm.onEmail("ona@example.test")
        vm.onPassword("long enough password")
        vm.onConfirmation("long enough password")
        vm.submit()
        assertEquals("Password must be 10-128 characters long.", vm.state.value.errors.password)
    }

    @Test
    fun networkFailureShowsMessage() {
        repository.result = Outcome.Failure(AppError.Network("timeout"))
        val vm = viewModel()
        vm.onEmail("ona@example.test")
        vm.onPassword("x")
        vm.submit()
        assertEquals("No connection to the server. Check the internet and try again.", vm.state.value.formError)
    }
}
