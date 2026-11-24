package lt.ehu.unwalked

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import timber.log.Timber

class UnwalkedApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        container = AppContainer(this)
        MapLibre.getInstance(this)
        scope.launch {
            container.history.applyRetention(container.preferences.retention.value.days)
            container.feedback.flush()
        }
        scope.launch {
            delay(PREFETCH_DELAY_MILLIS)
            runCatching { container.photoPrefetch.warm() }
        }
    }
}

private const val PREFETCH_DELAY_MILLIS = 2_000L
