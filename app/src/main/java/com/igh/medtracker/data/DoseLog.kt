package com.igh.medtracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dose_logs",
    foreignKeys = [ForeignKey(
        entity = Medication::class,
        parentColumns = ["id"],
        childColumns = ["medicationId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("medicationId")]
)
data class DoseLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val timestamp: Long,
    val doseAmount: String? = null,
    // Populated only for TYPE_GLUCOSE medications (via the camera + OCR capture flow).
    val glucoseValue: Double? = null,
    val glucoseUnit: String? = null,
    // Absolute path to the captured meter photo (app-private storage), kept for reference/verification.
    val photoPath: String? = null
)
