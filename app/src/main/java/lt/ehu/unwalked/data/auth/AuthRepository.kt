package lt.ehu.unwalked.data.auth

import java.time.OffsetDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import lt.ehu.unwalked.core.error.Outcome
import lt.ehu.unwalked.core.error.map
import lt.ehu.unwalked.core.network.CredentialsRequest
import lt.ehu.unwalked.core.network.SafeApiCall
import lt.ehu.unwalked.core.network.UnwalkedApi
import lt.ehu.unwalked.core.session.AccessToken
import lt.ehu.unwalked.core.session.TokenStore

interface AuthRepository {
    val session: StateFlow<AccessToken?>
    suspend fun signIn(email: String, password: String): Outcome<Unit>
    suspend fun register(email: String, password: String): Outcome<Unit>
    fun signOut()
}

class RemoteAuthRepository(
    private val api: UnwalkedApi,
    private val call: SafeApiCall,
    private val tokens: TokenStore,
    private val now: () -> Long = System::currentTimeMillis,
) : AuthRepository {
    private val mutableSession = MutableStateFlow(tokens.read()?.takeUnless { it.isExpired(now()) })
    override val session: StateFlow<AccessToken?> = mutableSession.asStateFlow()

    override suspend fun signIn(email: String, password: String): Outcome<Unit> {
        val normalized = email.trim().lowercase()
        return call { api.login(CredentialsRequest(normalized, password)) }.map { response ->
            val token = AccessToken(response.accessToken, OffsetDateTime.parse(response.expiresAt).toInstant().toEpochMilli(), normalized)
            tokens.write(token)
            mutableSession.value = token
        }
    }

    override suspend fun register(email: String, password: String): Outcome<Unit> =
        when (val created = call { api.register(CredentialsRequest(email.trim().lowercase(), password)) }) {
            is Outcome.Success -> signIn(email, password)
            is Outcome.Failure -> created
        }

    override fun signOut() {
        tokens.clear()
        mutableSession.value = null
    }
}
