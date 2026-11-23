package lt.ehu.unwalked.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import lt.ehu.unwalked.UnwalkedApplication

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as UnwalkedApplication).container
        if (container.preferences.recording.value && Permissions.hasLocation(context)) LocationService.start(context)
    }
}
