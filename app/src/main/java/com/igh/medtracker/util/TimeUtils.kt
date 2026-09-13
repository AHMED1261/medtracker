package com.igh.medtracker.util

import android.content.Context
import com.igh.medtracker.R
import com.igh.medtracker.data.Medication
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

object TimeUtils {

    /** "الآن" / "منذ 45 دقيقة" / "منذ 2 ساعة و 15 دقيقة" / "منذ 3 يوم". */
    fun relativeTime(context: Context, timestampMillis: Long?): String {
        if (timestampMillis == null) return context.getString(R.string.no_dose_yet)
        val diff = System.currentTimeMillis() - timestampMillis
        if (diff < 60_000) return context.getString(R.string.time_now)

        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val days = hours / 24

        return when {
            hours < 1 -> context.getString(R.string.time_minutes_ago, minutes.toInt())
            days >= 1 -> context.getString(R.string.time_days_ago, days.toInt())
            minutes == 0L -> context.getString(R.string.time_hours_ago, hours.toInt())
            else -> context.getString(R.string.time_hours_minutes_ago, hours.toInt(), minutes.toInt())
        }
    }

    /** Card/notification subtitle: adds the last glucose reading in front of the relative time
     * for TYPE_GLUCOSE medications; unchanged relative-time text for everything else. */
    fun medicationSubtitle(context: Context, medication: Medication): String {
        val relative = relativeTime(context, medication.lastDoseTime)
        val value = medication.lastGlucoseValue
        if (medication.type == Medication.TYPE_GLUCOSE && value != null) {
            return context.getString(
                R.string.glucose_subtitle_format,
                formatNumber(value),
                medication.lastGlucoseUnit.orEmpty(),
                relative
            )
        }
        return relative
    }

    fun formatNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private val dateTimeFormat = SimpleDateFormat("yyyy/MM/dd - HH:mm", Locale.ENGLISH)

    fun formatAbsolute(timestampMillis: Long): String = dateTimeFormat.format(timestampMillis)

    fun calendarFor(timestampMillis: Long): Calendar =
        Calendar.getInstance().apply { timeInMillis = timestampMillis }
}
