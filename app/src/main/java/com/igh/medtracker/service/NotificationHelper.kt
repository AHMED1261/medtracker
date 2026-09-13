package com.igh.medtracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.igh.medtracker.R
import com.igh.medtracker.data.MedRepository
import com.igh.medtracker.data.Medication
import com.igh.medtracker.ui.GlucoseCaptureActivity
import com.igh.medtracker.ui.LogActivity
import com.igh.medtracker.ui.MainActivity
import com.igh.medtracker.util.TimeUtils

/**
 * All medication notifications share GROUP_KEY so they present as one visual cluster.
 * SUMMARY_ID (also the foreground-service notification id) must always exist while the
 * service is running, even when there are zero "always show" medications, since
 * startForeground() requires an active notification at all times.
 *
 * Importance is deliberately LOW, not HIGH: from Android 8 onward the *channel* importance
 * is what governs behavior (a per-notification priority flag is ignored on O+). LOW keeps the
 * notification pinned and non-dismissible (via setOngoing) without a heads-up popup or sound
 * on every 15-minute refresh — which is what an always-visible, non-intrusive reminder needs.
 */
object NotificationHelper {

    const val CHANNEL_ID = "medtracker_channel"
    const val GROUP_KEY = "com.igh.medtracker.GROUP_MEDICATIONS"
    const val SUMMARY_ID = 1
    private const val ID_OFFSET = 1000

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.notification_channel_description)
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun notifIdFor(medicationId: Long): Int = (ID_OFFSET + medicationId).toInt()

    fun buildSummary(context: Context): Notification {
        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_summary_title))
            .setContentText(context.getString(R.string.notification_summary_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun buildForMedication(context: Context, medication: Medication): Notification {
        val subtitle = TimeUtils.medicationSubtitle(context, medication)
        val isGlucose = medication.type == Medication.TYPE_GLUCOSE

        val contentIntent = PendingIntent.getActivity(
            context, medication.id.toInt(),
            Intent(context, LogActivity::class.java).apply {
                putExtra(LogActivity.EXTRA_MEDICATION_ID, medication.id)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val actionPendingIntent = if (isGlucose) {
            PendingIntent.getActivity(
                context, (4000 + medication.id).toInt(),
                Intent(context, GlucoseCaptureActivity::class.java).apply {
                    putExtra(GlucoseCaptureActivity.EXTRA_MEDICATION_ID, medication.id)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            val logIntent = Intent(context, LogActionReceiver::class.java).apply {
                action = LogActionReceiver.ACTION_LOG
                putExtra(LogActionReceiver.EXTRA_MEDICATION_ID, medication.id)
            }
            PendingIntent.getBroadcast(
                context, (2000 + medication.id).toInt(), logIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val actionLabel = context.getString(if (isGlucose) R.string.capture_button else R.string.log_button)
        val actionIcon = if (isGlucose) R.drawable.ic_camera else R.drawable.ic_check

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(medication.name)
            .setContentText(subtitle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setGroup(GROUP_KEY)
            .setSortKey(String.format(java.util.Locale.US, "%05d", medication.sortOrder))
            .setContentIntent(contentIntent)
            .addAction(actionIcon, actionLabel, actionPendingIntent)
            .build()
    }

    /** Rebuilds and posts every "always show" medication's notification plus the summary,
     * and cancels any stale medication notifications (deleted, or toggled off). */
    suspend fun refreshAll(context: Context) {
        ensureChannel(context)
        val repo = MedRepository.getInstance(context)
        val alwaysShow = repo.getAlwaysShowMedications()
        val manager = NotificationManagerCompat.from(context)

        manager.notify(SUMMARY_ID, buildSummary(context))
        alwaysShow.forEach { med ->
            manager.notify(notifIdFor(med.id), buildForMedication(context, med))
        }

        val validIds = alwaysShow.map { notifIdFor(it.id) }.toSet()
        val platformManager = context.getSystemService(NotificationManager::class.java)
        platformManager.activeNotifications
            .filter { it.id != SUMMARY_ID && it.id !in validIds }
            .forEach { manager.cancel(it.id) }
    }

    /** Rebuilds a single medication's notification — used right after a log action so the
     * relative time updates immediately instead of waiting for the next 15-minute worker run. */
    suspend fun refreshOne(context: Context, medicationId: Long) {
        val repo = MedRepository.getInstance(context)
        val medication = repo.getMedication(medicationId) ?: return
        if (!medication.alwaysShow) return
        ensureChannel(context)
        NotificationManagerCompat.from(context)
            .notify(notifIdFor(medication.id), buildForMedication(context, medication))
    }

    fun cancelForMedication(context: Context, medicationId: Long) {
        NotificationManagerCompat.from(context).cancel(notifIdFor(medicationId))
    }
}
