package com.igh.medtracker.data

import android.content.Context
import androidx.lifecycle.LiveData

/**
 * Single source of truth for all reads/writes. Both the in-app "تسجيل" button and the
 * notification action button go through [logDoseNow], so the 5-minute cooldown is enforced
 * identically regardless of entry point.
 */
class MedRepository private constructor(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val medicationDao = db.medicationDao()
    private val doseLogDao = db.doseLogDao()

    companion object {
        const val COOLDOWN_MILLIS = 5 * 60 * 1000L

        @Volatile
        private var INSTANCE: MedRepository? = null

        fun getInstance(context: Context): MedRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MedRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun observeMedications(): LiveData<List<Medication>> = medicationDao.observeAll()

    fun observeAllLogs(): LiveData<List<DoseLog>> = doseLogDao.observeAll()

    fun observeLogsForMedication(medicationId: Long): LiveData<List<DoseLog>> =
        doseLogDao.observeForMedication(medicationId)

    suspend fun getAlwaysShowMedications(): List<Medication> = medicationDao.getAlwaysShow()

    suspend fun getAllMedications(): List<Medication> = medicationDao.getAllSync()

    suspend fun getMedication(id: Long): Medication? = medicationDao.getById(id)

    suspend fun addMedication(name: String, type: String = Medication.TYPE_NORMAL) {
        val nextOrder = medicationDao.getMaxSortOrder() + 1
        medicationDao.insert(Medication(name = name, sortOrder = nextOrder, alwaysShow = true, type = type))
    }

    suspend fun deleteMedication(medication: Medication) {
        medicationDao.delete(medication) // dose_logs cascade-delete via foreign key
    }

    suspend fun setAlwaysShow(medicationId: Long, alwaysShow: Boolean) {
        medicationDao.updateAlwaysShow(medicationId, alwaysShow)
    }

    suspend fun persistOrder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> medicationDao.updateSortOrder(id, index) }
    }

    /**
     * Result of a log attempt.
     * @param success false when the 5-minute cooldown blocked the write.
     * @param cooldownRemainingMinutes minutes left before the medication can be logged again
     *   (only meaningful when success == false).
     */
    data class LogResult(val success: Boolean, val cooldownRemainingMinutes: Int = 0)

    suspend fun logDoseNow(medicationId: Long): LogResult {
        val medication = medicationDao.getById(medicationId) ?: return LogResult(false)
        val now = System.currentTimeMillis()
        val last = medication.lastDoseTime
        val cooldownApplies = medication.type != Medication.TYPE_GLUCOSE
        if (cooldownApplies && last != null && now - last < COOLDOWN_MILLIS) {
            val remainingMs = COOLDOWN_MILLIS - (now - last)
            val remainingMin = ((remainingMs + 59_999) / 60_000).toInt() // round up
            return LogResult(false, remainingMin.coerceAtLeast(1))
        }
        doseLogDao.insert(DoseLog(medicationId = medicationId, timestamp = now))
        medicationDao.recomputeLastDoseTime(medicationId)
        return LogResult(true)
    }

    /** No cooldown gate — the camera + confirm-screen flow that leads here is already a
     * deliberate, multi-step action, not something a double-tap can trigger by accident. */
    suspend fun saveGlucoseReading(medicationId: Long, value: Double, unit: String, photoPath: String?): LogResult {
        doseLogDao.insert(
            DoseLog(
                medicationId = medicationId,
                timestamp = System.currentTimeMillis(),
                glucoseValue = value,
                glucoseUnit = unit,
                photoPath = photoPath
            )
        )
        medicationDao.recomputeLastDoseTime(medicationId)
        return LogResult(true)
    }

    suspend fun getLogsForMedicationSync(medicationId: Long): List<DoseLog> =
        doseLogDao.getForMedicationSync(medicationId)

    suspend fun updateLog(log: DoseLog) {
        doseLogDao.update(log)
        medicationDao.recomputeLastDoseTime(log.medicationId)
    }

    suspend fun deleteLog(log: DoseLog) {
        doseLogDao.delete(log)
        medicationDao.recomputeLastDoseTime(log.medicationId)
    }

    suspend fun clearLogsForMedication(medicationId: Long) {
        doseLogDao.clearForMedication(medicationId)
        medicationDao.recomputeLastDoseTime(medicationId)
    }

    suspend fun clearAllLogs() {
        doseLogDao.clearAll()
        getAllMedications().forEach { medicationDao.recomputeLastDoseTime(it.id) }
    }
}
