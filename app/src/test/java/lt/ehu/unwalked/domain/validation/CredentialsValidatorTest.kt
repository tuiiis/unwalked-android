package lt.ehu.unwalked.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialsValidatorTest {
    @Test
    fun acceptsValidRegistration() {
        assertTrue(CredentialsValidator.register("ona@example.test", "long enough password", "long enough password").isValid)
    }

    @Test
    fun rejectsEmptyEmail() {
        assertEquals("Enter your email.", CredentialsValidator.email("  "))
    }

    @Test
    fun rejectsEmailWithoutDomain() {
        assertEquals("Enter an email like name@example.com.", CredentialsValidator.email("ona@example"))
    }

    @Test
    fun trimsEmailBeforeChecking() {
        assertNull(CredentialsValidator.email("  ona@example.test "))
    }

    @Test
    fun passwordShorterThanTenIsRejected() {
        assertEquals("Use at least 10 characters.", CredentialsValidator.password("123456789"))
        assertNull(CredentialsValidator.password("1234567890"))
    }

    @Test
    fun passwordLongerThan128IsRejected() {
        assertEquals("Use at most 128 characters.", CredentialsValidator.password("a".repeat(129)))
    }

    @Test
    fun mismatchedConfirmationIsReported() {
        val errors = CredentialsValidator.register("ona@example.test", "long enough password", "long enough passwort")
        assertEquals("Passwords do not match.", errors.confirmation)
    }

    @Test
    fun signInDoesNotEnforceLengthPolicy() {
        assertTrue(CredentialsValidator.signIn("ona@example.test", "short").isValid)
    }
}
