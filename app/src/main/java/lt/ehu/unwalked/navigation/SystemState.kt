package lt.ehu.unwalked.navigation

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager
import android.provider.Settings

object SystemState {
    fun batterySaver(context: Context): Boolean = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true

    fun online(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun openBatterySaver(context: Context) = open(context, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))

    fun openLocationSettings(context: Context) = open(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))

    private fun open(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
