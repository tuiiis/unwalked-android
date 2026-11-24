package lt.ehu.unwalked.navigation

import kotlinx.serialization.Serializable

@Serializable data object AuthGraph

@Serializable data object SignIn

@Serializable data object Register

@Serializable data object MainGraph

@Serializable data object MapHome

@Serializable data object Stats

@Serializable data object Walk

@Serializable data class Discover(val focusSearch: Boolean = false)

@Serializable data class RoutePreview(val kilometres: Double, val shape: String, val preference: String, val toLat: Double = Double.NaN, val toLon: Double = Double.NaN)

@Serializable data class PlacePreview(val id: Long, val name: String, val category: String, val reason: String, val lat: Double, val lon: Double)

@Serializable data object Privacy
