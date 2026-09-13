package com.igh.medtracker

import android.app.Application
import com.igh.medtracker.service.NotificationHelper
import com.igh.medtracker.service.PersistentService
import com.igh.medtracker.service.RefreshWorker

class MedTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        PersistentService.start(this)
        RefreshWorker.schedule(this)
    }
}
