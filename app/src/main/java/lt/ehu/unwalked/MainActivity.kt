package lt.ehu.unwalked

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.lifecycleScope
import java.io.File
import kotlinx.coroutines.launch
import lt.ehu.unwalked.core.ui.theme.DarkColors
import lt.ehu.unwalked.core.ui.theme.LightColors
import lt.ehu.unwalked.core.ui.theme.UnwalkedTheme
import lt.ehu.unwalked.navigation.UnwalkedApp
import timber.log.Timber

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(LightColors.background.toArgb(), DarkColors.background.toArgb()),
            navigationBarStyle = SystemBarStyle.auto(LightColors.panel.toArgb(), DarkColors.panel.toArgb()),
        )
        super.onCreate(savedInstanceState)
        val container = (application as UnwalkedApplication).container
        if (BuildConfig.DEBUG) importTrack(container, intent)
        setContent {
            UnwalkedTheme {
                UnwalkedApp(container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (BuildConfig.DEBUG) importTrack((application as UnwalkedApplication).container, intent)
    }

    private fun importTrack(container: AppContainer, intent: Intent) {
        val name = intent.getStringExtra(EXTRA_IMPORT_TRACK) ?: return
        val file = File(filesDir, name)
        if (!file.exists()) return
        lifecycleScope.launch {
            val imported = container.history.importTrack(file.inputStream(), intent.getStringExtra(EXTRA_SOURCE) ?: "import")
            Timber.i("Imported %d fixes from %s", imported, name)
            Toast.makeText(this@MainActivity, "Imported $imported points", Toast.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val EXTRA_IMPORT_TRACK = "import_track"
        const val EXTRA_SOURCE = "source"
    }
}
