package com.igh.medtracker.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.igh.medtracker.R
import com.igh.medtracker.data.DoseLog
import com.igh.medtracker.data.MedRepository
import com.igh.medtracker.databinding.ActivityLogBinding
import com.igh.medtracker.databinding.DialogEditLogBinding
import com.igh.medtracker.service.NotificationHelper
import com.igh.medtracker.util.ExportUtils
import com.igh.medtracker.util.TimeUtils
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

class LogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogBinding
    private lateinit var repo: MedRepository
    private lateinit var adapter: LogAdapter

    private var medicationId: Long = -1L
    private var medicationNames: Map<Long, String> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        repo = MedRepository.getInstance(this)
        medicationId = intent.getLongExtra(EXTRA_MEDICATION_ID, -1L)
        val filtered = medicationId != -1L

        adapter = LogAdapter(
            showMedicationName = !filtered,
            medicationNamesProvider = { medicationNames },
            onEditClick = ::showEditDialog,
            onDeleteClick = ::confirmDeleteLog,
            onPhotoClick = ::viewPhoto
        )
        binding.recyclerLogs.layoutManager = LinearLayoutManager(this)
        binding.recyclerLogs.adapter = adapter

        lifecycleScope.launch {
            medicationNames = repo.getAllMedications().associate { it.id to it.name }
            if (filtered) {
                supportActionBar?.title = getString(
                    R.string.dose_log_title_filtered,
                    medicationNames[medicationId].orEmpty()
                )
            } else {
                supportActionBar?.title = getString(R.string.dose_log_title)
            }
            adapter.notifyDataSetChanged()
        }

        val logsLiveData = if (filtered) repo.observeLogsForMedication(medicationId) else repo.observeAllLogs()
        logsLiveData.observe(this) { logs ->
            adapter.submitList(logs)
            binding.emptyState.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_log, menu)
        val filtered = medicationId != -1L
        menu.findItem(R.id.action_export_csv)?.isVisible = filtered
        menu.findItem(R.id.action_export_pdf)?.isVisible = filtered
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_share -> {
                shareCurrentLogs()
                true
            }
            R.id.action_clear -> {
                confirmClearLogs()
                true
            }
            R.id.action_export_csv -> {
                exportLogs(isCsv = true)
                true
            }
            R.id.action_export_pdf -> {
                exportLogs(isCsv = false)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun exportLogs(isCsv: Boolean) {
        val medId = medicationId
        if (medId == -1L) return
        lifecycleScope.launch {
            try {
                val medication = repo.getMedication(medId) ?: return@launch
                val logsForExport = repo.getLogsForMedicationSync(medId)
                val file = if (isCsv) {
                    ExportUtils.exportCsv(this@LogActivity, medication, logsForExport)
                } else {
                    ExportUtils.exportPdf(this@LogActivity, medication, logsForExport)
                }
                val mime = if (isCsv) "text/csv" else "application/pdf"
                ExportUtils.shareFile(this@LogActivity, file, mime)
            } catch (e: Exception) {
                Toast.makeText(this@LogActivity, R.string.export_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun viewPhoto(log: DoseLog) {
        val path = log.photoPath ?: return
        val file = File(path)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, null))
    }

    private fun shareCurrentLogs() {
        val current = adapter.currentList
        val builder = StringBuilder()
        current.forEach { log ->
            val name = medicationNames[log.medicationId].orEmpty()
            val time = TimeUtils.formatAbsolute(log.timestamp)
            val dose = if (!log.doseAmount.isNullOrBlank()) " - ${log.doseAmount}" else ""
            builder.append(if (name.isNotEmpty()) "$name: $time$dose\n" else "$time$dose\n")
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, builder.toString().trim())
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
    }

    private fun confirmClearLogs() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_log_confirm_title)
            .setMessage(R.string.clear_log_confirm_message)
            .setPositiveButton(R.string.clear_log) { _, _ ->
                lifecycleScope.launch {
                    if (medicationId != -1L) {
                        repo.clearLogsForMedication(medicationId)
                        NotificationHelper.refreshOne(applicationContext, medicationId)
                    } else {
                        repo.clearAllLogs()
                        NotificationHelper.refreshAll(applicationContext)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteLog(log: DoseLog) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_log_confirm_title)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    repo.deleteLog(log)
                    NotificationHelper.refreshOne(applicationContext, log.medicationId)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditDialog(log: DoseLog) {
        val dialogBinding = DialogEditLogBinding.inflate(layoutInflater)
        val calendar = TimeUtils.calendarFor(log.timestamp)
        val isGlucose = log.glucoseValue != null

        if (isGlucose) {
            dialogBinding.inputLayoutDose.hint = getString(R.string.glucose_value_hint)
            dialogBinding.editDose.setText(TimeUtils.formatNumber(log.glucoseValue!!))
            dialogBinding.editDose.inputType =
                android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        } else {
            dialogBinding.editDose.setText(log.doseAmount.orEmpty())
        }

        fun updateButtons() {
            dialogBinding.buttonPickDate.text = String.format(
                "%04d/%02d/%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            dialogBinding.buttonPickTime.text = String.format(
                "%02d:%02d",
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE)
            )
        }
        updateButtons()

        dialogBinding.buttonPickDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    calendar.set(Calendar.YEAR, year)
                    calendar.set(Calendar.MONTH, month)
                    calendar.set(Calendar.DAY_OF_MONTH, day)
                    updateButtons()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        dialogBinding.buttonPickTime.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hour)
                    calendar.set(Calendar.MINUTE, minute)
                    updateButtons()
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.edit_log_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save, null)
            .setNeutralButton(R.string.delete) { _, _ -> confirmDeleteLog(log) }
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = dialogBinding.editDose.text?.toString()?.trim()
                val updated = if (isGlucose) {
                    val value = text?.toDoubleOrNull()
                    if (value == null) {
                        dialogBinding.inputLayoutDose.error = getString(R.string.glucose_invalid_value)
                        return@setOnClickListener
                    }
                    log.copy(timestamp = calendar.timeInMillis, glucoseValue = value)
                } else {
                    log.copy(timestamp = calendar.timeInMillis, doseAmount = text.takeUnless { it.isNullOrEmpty() })
                }
                lifecycleScope.launch {
                    repo.updateLog(updated)
                    NotificationHelper.refreshOne(applicationContext, updated.medicationId)
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    companion object {
        const val EXTRA_MEDICATION_ID = "medication_id"
    }
}
