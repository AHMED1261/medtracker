package com.igh.medtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int,
    val alwaysShow: Boolean = true,
    // Denormalized cache of the most recent dose timestamp (epoch millis) for fast list/notification
    // rendering without a join. It is recomputed from dose_logs (MAX(timestamp)) after every
    // insert/edit/delete of a log for this medication, so it always reflects the true latest dose,
    // even when an older log entry is edited/deleted rather than the newest one.
    val lastDoseTime: Long? = null,
    // TYPE_NORMAL = one-tap timestamp logging (pills, etc).
    // TYPE_GLUCOSE = tapping "Log" opens the camera + OCR capture flow instead.
    val type: String = TYPE_NORMAL,
    // Cached alongside lastDoseTime (same recompute query) so the list/notification can show
    // "120 mg/dL — 45m ago" without an extra query.
    val lastGlucoseValue: Double? = null,
    val lastGlucoseUnit: String? = null
) {
    companion object {
        const val TYPE_NORMAL = "NORMAL"
        const val TYPE_GLUCOSE = "GLUCOSE"
    }
}
