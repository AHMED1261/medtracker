package com.igh.medtracker.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.igh.medtracker.R
import com.igh.medtracker.data.MedRepository
import com.igh.medtracker.data.Medication
import com.igh.medtracker.databinding.ActivityMainBinding
import com.igh.medtracker.databinding.DialogAddMedicationBinding
import com.igh.medtracker.service.NotificationHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: MedRepository
    private lateinit var adapter: MedicationAdapter

    private val tickHandler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            if (::adapter.isInitialized) adapter.refreshTimestamps()
            tickHandler.postDelayed(this, 60_000L)
        }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        repo = MedRepository.getInstance(this)

        adapter = MedicationAdapter(
            onLogClick = ::handleLogClick,
            onDeleteClick = ::handleDeleteClick,
            onAlwaysShowChanged = ::handleAlwaysShowChanged,
            onCardClick = { medication ->
                startActivity(Intent(this, LogActivity::class.java).apply {
                    putExtra(LogActivity.EXTRA_MEDICATION_ID, medication.id)
                })
            },
            onStartDrag = { holder -> itemTouchHelper.startDrag(holder) }
        )

        binding.recyclerMedications.layoutManager = LinearLayoutManager(this)
        binding.recyclerMedications.adapter = adapter
        itemTouchHelper.attachToRecyclerView(binding.recyclerMedications)

        binding.fabAdd.setOnClickListener { showAddMedicationDialog() }

        repo.observeMedications().observe(this) { medications ->
            adapter.submitList(medications)
            binding.emptyState.visibility = if (medications.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }

        requestNotificationPermissionIfNeeded()
        maybePromptBatteryOptimization()
    }

    private val itemTouchHelper by lazy {
        ItemTouchHelper(
            MedicationTouchCallback(
                adapter = adapter,
                getCurrentList = { adapter.currentList },
                onDragFinished = { orderedIds ->
                    lifecycleScope.launch {
                        repo.persistOrder(orderedIds)
                        NotificationHelper.refreshAll(applicationContext)
                    }
                }
            )
        )
    }

    override fun onResume() {
        super.onResume()
        tickHandler.post(tickRunnable)
    }

    override fun onPause() {
        super.onPause()
        tickHandler.removeCallbacks(tickRunnable)
    }

    private fun handleLogClick(medication: Medication) {
        if (medication.type == Medication.TYPE_GLUCOSE) {
            startActivity(Intent(this, GlucoseCaptureActivity::class.java).apply {
                putExtra(GlucoseCaptureActivity.EXTRA_MEDICATION_ID, medication.id)
            })
            return
        }
        lifecycleScope.launch {
            val result = repo.logDoseNow(medication.id)
            if (result.success) {
                Snackbar.make(binding.root, R.string.dose_logged, Snackbar.LENGTH_SHORT).show()
                NotificationHelper.refreshOne(applicationContext, medication.id)
            } else {
                Snackbar.make(
                    binding.root,
                    getString(R.string.cooldown_active, result.cooldownRemainingMinutes),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun handleDeleteClick(medication: Medication) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_medication_confirm_title)
            .setMessage(R.string.delete_medication_confirm_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    repo.deleteMedication(medication)
                    NotificationHelper.cancelForMedication(applicationContext, medication.id)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun handleAlwaysShowChanged(medication: Medication, isChecked: Boolean) {
        lifecycleScope.launch {
            repo.setAlwaysShow(medication.id, isChecked)
            if (isChecked) {
                NotificationHelper.refreshAll(applicationContext)
            } else {
                NotificationHelper.cancelForMedication(applicationContext, medication.id)
            }
        }
    }

    private fun showAddMedicationDialog() {
        val dialogBinding = DialogAddMedicationBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.add_medication)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.add, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val addButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            addButton.isEnabled = false
            dialogBinding.editName.doOnTextChanged { text, _, _, _ ->
                addButton.isEnabled = !text.isNullOrBlank()
            }
            addButton.setOnClickListener {
                val name = dialogBinding.editName.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    val type = if (dialogBinding.checkCameraType.isChecked) {
                        Medication.TYPE_GLUCOSE
                    } else {
                        Medication.TYPE_NORMAL
                    }
                    lifecycleScope.launch {
                        repo.addMedication(name, type)
                        NotificationHelper.refreshAll(applicationContext)
                    }
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun maybePromptBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) return

        AlertDialog.Builder(this)
            .setTitle(R.string.battery_optimization_title)
            .setMessage(R.string.battery_optimization_message)
            .setPositiveButton(R.string.battery_optimization_action) { _, _ ->
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
            .setNegativeButton(R.string.later, null)
            .show()
    }
}
