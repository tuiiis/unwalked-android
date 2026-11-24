package lt.ehu.unwalked

import android.content.Context
import androidx.room.withTransaction
import com.uber.h3core.H3Core
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import lt.ehu.unwalked.core.error.ErrorReporter
import lt.ehu.unwalked.core.network.NetworkModule
import lt.ehu.unwalked.core.network.SafeApiCall
import lt.ehu.unwalked.core.network.UnwalkedApi
import lt.ehu.unwalked.core.session.KeystoreTokenStore
import lt.ehu.unwalked.core.session.TokenStore
import lt.ehu.unwalked.core.ui.map.MapStyle
import lt.ehu.unwalked.data.auth.AuthRepository
import lt.ehu.unwalked.data.auth.RemoteAuthRepository
import lt.ehu.unwalked.data.feedback.FeedbackRepository
import lt.ehu.unwalked.data.history.Cells
import lt.ehu.unwalked.data.history.HistoryRepository
import lt.ehu.unwalked.data.local.UnwalkedDatabase
import lt.ehu.unwalked.data.photos.PhotoPrefetch
import lt.ehu.unwalked.data.photos.PlacePhotos
import lt.ehu.unwalked.data.places.PlacesRepository
import lt.ehu.unwalked.data.places.RemotePlacesRepository
import lt.ehu.unwalked.data.route.OsrmRouteRepository
import lt.ehu.unwalked.data.route.RouteRepository
import lt.ehu.unwalked.data.settings.Preferences
import lt.ehu.unwalked.data.streets.CachedTileSource
import lt.ehu.unwalked.data.streets.WalkedStreets
import lt.ehu.unwalked.domain.recommendation.CandidateCells
import lt.ehu.unwalked.domain.route.WalkRoute
import lt.ehu.unwalked.domain.stats.Gazetteer
import lt.ehu.unwalked.domain.tracking.Streak
import okhttp3.OkHttpClient

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val h3: H3Core get() = Cells.h3
    val database: UnwalkedDatabase by lazy { UnwalkedDatabase.create(appContext) }
    val preferences: Preferences by lazy { Preferences(appContext) }
    val errors = ErrorReporter()
    val activeWalk = MutableStateFlow<WalkRoute?>(null)
    val shownStreaks = MutableStateFlow<List<Streak>>(emptyList())
    val shownSince = MutableStateFlow(0L)

    private val tokens: TokenStore by lazy { KeystoreTokenStore(appContext) }
    private val http: OkHttpClient by lazy { NetworkModule.okHttp(tokens, BuildConfig.DEBUG) }
    private val api: UnwalkedApi by lazy { NetworkModule.api(BuildConfig.API_BASE_URL, http) }
    private val call by lazy { SafeApiCall(NetworkModule.json) }

    val auth: AuthRepository by lazy { RemoteAuthRepository(api, call, tokens) }
    val history: HistoryRepository by lazy { HistoryRepository(appContext, database.tracks(), h3) { block -> database.withTransaction { block() } } }
    val places: PlacesRepository by lazy { RemotePlacesRepository(api, call, h3) }
    val photos: PlacePhotos by lazy { PlacePhotos(BuildConfig.MAPILLARY_TOKEN, appContext.getSharedPreferences("photos", Context.MODE_PRIVATE)) }
    val photoPrefetch: PhotoPrefetch by lazy { PhotoPrefetch(appContext, history, places, CandidateCells(h3, Cells.NEAR_QUERY_RESOLUTION), photos) }
    val routes: RouteRepository by lazy { OsrmRouteRepository(http, NetworkModule.json) }
    val gazetteer: Gazetteer by lazy { appContext.assets.open("cities.tsv").bufferedReader().useLines { Gazetteer.parse(it) } }
    val walkedStreets: WalkedStreets by lazy { WalkedStreets(CachedTileSource(OkHttpClient(), File(appContext.cacheDir, "streets"), MapStyle.TILES), File(appContext.cacheDir, "walked")) }
    val feedback: FeedbackRepository by lazy {
        FeedbackRepository(database.feedbackOutbox(), api, call, consent = { preferences.studyConsent.value })
    }
}
