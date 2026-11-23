package lt.ehu.unwalked.core.session

data class AccessToken(val value: String, val expiresAtMillis: Long, val email: String) {
    fun isExpired(nowMillis: Long): Boolean = nowMillis >= expiresAtMillis
}

interface TokenStore {
    fun read(): AccessToken?
    fun write(token: AccessToken)
    fun clear()
}

class InMemoryTokenStore(private var token: AccessToken? = null) : TokenStore {
    override fun read(): AccessToken? = token
    override fun write(token: AccessToken) {
        this.token = token
    }
    override fun clear() {
        token = null
    }
}
