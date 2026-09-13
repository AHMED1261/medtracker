package com.igh.medtracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.igh.medtracker.data.MedRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LogActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_LOG) return
        val medicationId = intent.getLongExtra(EXTRA_MEDICATION_ID, -1L)
        if (medicationId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = MedRepository.getInstance(context)
                val result = repo.logDoseNow(medicationId)
                if (result.success) {
                    NotificationHelper.refreshOne(context, medicationId)
                }
                // If the cooldown blocked it, the notification text simply won't change —
                // there's no reliable, non-intrusive way to surface a toast from a background
                // receiver, so silence here is intentional rather than an oversight.
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_LOG = "com.igh.medtracker.action.LOG"
        const val EXTRA_MEDICATION_ID = "medication_id"
    }
}
