package lt.ehu.unwalked.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lt.ehu.unwalked.core.error.AppError
import lt.ehu.unwalked.core.error.ErrorReporter
import lt.ehu.unwalked.core.error.Outcome
import lt.ehu.unwalked.core.ui.theme.UnwalkedTheme
import lt.ehu.unwalked.feature.auth.AuthMode
import lt.ehu.unwalked.feature.auth.AuthScreen
import lt.ehu.unwalked.feature.auth.AuthViewModel
import lt.ehu.unwalked.testing.FakeAuthRepository
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class AuthScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val repository = FakeAuthRepository()

    private fun show(mode: AuthMode): AuthViewModel {
        val vm = AuthViewModel(mode, repository, ErrorReporter(log = { _, _ -> }))
        compose.setContent {
            UnwalkedTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                AuthScreen(state, vm::onEmail, vm::onPassword, vm::onConfirmation, vm::submit, onSwitchMode = {})
            }
        }
        return vm
    }

    @Test
    fun emptySubmitShowsFieldErrorsAndSendsNothing() {
        show(AuthMode.SignIn)
        compose.onNodeWithTag("submit").performClick()
        compose.onNodeWithText("Enter your email.").assertIsDisplayed()
        compose.onNodeWithText("Enter a password.").assertIsDisplayed()
        assertEquals(0, repository.calls.size)
    }

    @Test
    fun registrationChecksPasswordRulesThenSubmits() {
        show(AuthMode.Register)
        compose.onNodeWithTag("email").performTextInput("ona@example.test")
        compose.onNodeWithTag("password").performTextInput("short")
        compose.onNodeWithTag("confirmation").performTextInput("short")
        compose.onNodeWithTag("submit").performClick()
        compose.onNodeWithText("Use at least 10 characters.").assertIsDisplayed()

        compose.onNodeWithTag("password").performTextInput(" but longer")
        compose.onNodeWithTag("confirmation").performTextInput(" but longer")
        compose.onNodeWithTag("submit").performClick()
        compose.waitForIdle()
        assertEquals(listOf("ona@example.test" to "short but longer"), repository.calls)
    }

    @Test
    fun wrongPasswordShowsMessageUnderTheForm() {
        repository.result = Outcome.Failure(AppError.Unauthorized("Email or password is incorrect."))
        show(AuthMode.SignIn)
        compose.onNodeWithTag("email").performTextInput("ona@example.test")
        compose.onNodeWithTag("password").performTextInput("wrong password")
        compose.onNodeWithTag("submit").performClick()
        compose.onNodeWithTag("formError").assertIsDisplayed()
        compose.onNodeWithText("Email or password is incorrect.").assertIsDisplayed()
    }
}
