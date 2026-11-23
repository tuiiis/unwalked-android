package lt.ehu.unwalked.core.network

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import lt.ehu.unwalked.core.session.TokenStore
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object NetworkModule {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun okHttp(tokens: TokenStore, verbose: Boolean): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor(tokens))
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = if (verbose) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
                redactHeader("Authorization")
            },
        )
        .build()

    fun api(baseUrl: String, client: OkHttpClient): UnwalkedApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(UnwalkedApi::class.java)

    private fun authInterceptor(tokens: TokenStore) = Interceptor { chain ->
        val request = chain.request()
        val token = tokens.read()?.takeUnless { it.isExpired(System.currentTimeMillis()) }
        val needsToken = request.url.encodedPath.contains("/shares")
        if (token != null && needsToken) {
            chain.proceed(request.newBuilder().header("Authorization", "Bearer ${token.value}").build())
        } else {
            chain.proceed(request)
        }
    }
}
