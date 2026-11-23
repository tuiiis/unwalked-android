package lt.ehu.unwalked.core.network

import kotlinx.serialization.Serializable

@Serializable
data class CredentialsRequest(val email: String, val password: String)

@Serializable
data class RegisterResponse(val userId: String)

@Serializable
data class LoginResponse(val accessToken: String, val tokenType: String, val expiresAt: String)

@Serializable
data class PlaceDto(
    val id: Long,
    val externalId: String,
    val name: String,
    val category: String,
    val confidence: Double,
    val lat: Double,
    val lon: Double,
    val h3Cell: String,
    val distanceMeters: Double? = null,
)

@Serializable
data class PlacesByCellsRequest(val cells: List<String>, val category: String? = null, val limit: Int? = null)

@Serializable
data class FeedbackRequest(
    val eventId: String,
    val recommendationType: String,
    val action: String,
    val category: String? = null,
)

@Serializable
data class ProblemDetails(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val traceId: String? = null,
    val errors: Map<String, List<String>>? = null,
)
