package lt.ehu.unwalked.tracking

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import lt.ehu.unwalked.MainActivity
import lt.ehu.unwalked.R
import lt.ehu.unwalked.UnwalkedApplication
import lt.ehu.unwalked.data.history.TrackPipeline
import lt.ehu.unwalked.domain.tracking.Fix

class LocationService : LifecycleService() {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val pipeline by lazy {
        val container = (application as UnwalkedApplication).container
        TrackPipeline(container.database.tracks(), container.h3, "live")
    }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val fixes = result.locations.map { Fix(it.time, it.latitude, it.longitude, it.accuracy) }
            lifecycleScope.launch { fixes.forEach { pipeline.accept(it) } }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            client.removeLocationUpdates(callback)
            lifecycleScope.launch { pipeline.finish() }
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val started = runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        }
        if (started.isFailure) {
            stopSelf()
            return START_NOT_STICKY
        }
        requestUpdates()
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, INTERVAL_MILLIS)
            .setMinUpdateIntervalMillis(INTERVAL_MILLIS / 2)
            .setMaxUpdateDelayMillis(INTERVAL_MILLIS * 4)
            .build()
        client.removeLocationUpdates(callback)
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    override fun onDestroy() {
        client.removeLocationUpdates(callback)
        super.onDestroy()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, getString(R.string.channel_recording), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_walk)
            .setContentTitle(getString(R.string.recording_title))
            .setContentText(getString(R.string.recording_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val ACTION_STOP = "stop"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1
        private const val INTERVAL_MILLIS = 15_000L

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, LocationService::class.java)) }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, LocationService::class.java).setAction(ACTION_STOP))
        }
    }
}
