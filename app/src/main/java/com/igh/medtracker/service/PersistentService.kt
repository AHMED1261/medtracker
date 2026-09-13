package com.igh.medtracker.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PersistentService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val summary = NotificationHelper.buildSummary(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NotificationHelper.SUMMARY_ID, summary, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NotificationHelper.SUMMARY_ID, summary)
        }
        scope.launch { NotificationHelper.refreshAll(applicationContext) }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Some OEMs stop the service when the app is swiped from Recents. Ask the system to
        // restart it immediately rather than waiting for the next 15-minute WorkManager pass.
        ContextCompat.startForegroundService(applicationContext, Intent(applicationContext, PersistentService::class.java))
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    companion object {
        /**
         * On Android 12+, starting a foreground service from a pure background context (e.g. a
         * WorkManager job whose process the system killed and restarted) can be blocked by the
         * OS with ForegroundServiceStartNotAllowedException. That's a platform restriction with
         * no reliable code-level bypass — this just avoids crashing the caller when it happens;
         * the next 15-minute WorkManager run will try again.
         */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, PersistentService::class.java))
            } catch (e: Exception) {
                // Swallow: see note above. Nothing actionable to do here besides retry later.
            }
        }
    }
}
