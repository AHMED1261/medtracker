package com.igh.medtracker.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface MedicationDao {

    @Query("SELECT * FROM medications ORDER BY sortOrder ASC")
    fun observeAll(): LiveData<List<Medication>>

    @Query("SELECT * FROM medications WHERE alwaysShow = 1 ORDER BY sortOrder ASC")
    suspend fun getAlwaysShow(): List<Medication>

    @Query("SELECT * FROM medications ORDER BY sortOrder ASC")
    suspend fun getAllSync(): List<Medication>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getById(id: Long): Medication?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM medications")
    suspend fun getMaxSortOrder(): Int

    @Insert
    suspend fun insert(medication: Medication): Long

    @Update
    suspend fun update(medication: Medication)

    @Delete
    suspend fun delete(medication: Medication)

    @Query("UPDATE medications SET sortOrder = :order WHERE id = :id")
    suspend fun updateSortOrder(id: Long, order: Int)

    @Query("UPDATE medications SET alwaysShow = :alwaysShow WHERE id = :id")
    suspend fun updateAlwaysShow(id: Long, alwaysShow: Boolean)

    @Query(
        """UPDATE medications SET
            lastDoseTime = (SELECT MAX(timestamp) FROM dose_logs WHERE medicationId = :id),
            lastGlucoseValue = (SELECT glucoseValue FROM dose_logs WHERE medicationId = :id ORDER BY timestamp DESC LIMIT 1),
            lastGlucoseUnit = (SELECT glucoseUnit FROM dose_logs WHERE medicationId = :id ORDER BY timestamp DESC LIMIT 1)
           WHERE id = :id"""
    )
    suspend fun recomputeLastDoseTime(id: Long)
}
