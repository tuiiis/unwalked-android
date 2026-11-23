package lt.ehu.unwalked.core.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface UnwalkedApi {
    @POST("api/v1/auth/register")
    suspend fun register(@Body body: CredentialsRequest): RegisterResponse

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: CredentialsRequest): LoginResponse

    @GET("api/v1/places/near")
    suspend fun placesNear(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radius") radius: Double,
        @Query("category") category: String? = null,
        @Query("limit") limit: Int? = null,
    ): List<PlaceDto>

    @POST("api/v1/places/by-cells")
    suspend fun placesByCells(@Body body: PlacesByCellsRequest): List<PlaceDto>

    @POST("api/v1/feedback")
    suspend fun submitFeedback(@Body body: FeedbackRequest): Response<Unit>
}
