package com.igh.medtracker.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface DoseLogDao {

    @Query("SELECT * FROM dose_logs ORDER BY timestamp DESC")
    fun observeAll(): LiveData<List<DoseLog>>

    @Query("SELECT * FROM dose_logs WHERE medicationId = :medicationId ORDER BY timestamp DESC")
    fun observeForMedication(medicationId: Long): LiveData<List<DoseLog>>

    @Query("SELECT * FROM dose_logs WHERE medicationId = :medicationId ORDER BY timestamp DESC")
    suspend fun getForMedicationSync(medicationId: Long): List<DoseLog>

    @Insert
    suspend fun insert(log: DoseLog): Long

    @Update
    suspend fun update(log: DoseLog)

    @Delete
    suspend fun delete(log: DoseLog)

    @Query("DELETE FROM dose_logs WHERE medicationId = :medicationId")
    suspend fun clearForMedication(medicationId: Long)

    @Query("DELETE FROM dose_logs")
    suspend fun clearAll()
}
