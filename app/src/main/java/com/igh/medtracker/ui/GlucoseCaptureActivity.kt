package com.igh.medtracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.igh.medtracker.R
import com.igh.medtracker.data.MedRepository
import com.igh.medtracker.databinding.ActivityGlucoseCaptureBinding
import com.igh.medtracker.service.NotificationHelper
import kotlinx.coroutines.launch
import java.io.File

/**
 * The OCR reading is a *suggestion*, never an auto-save: this is health-adjacent data and meter
 * photos can have glare/blur/misreads, so the detected number always lands in an editable field
 * next to the photo, and nothing is written to the database until the person taps "حفظ".
 */
class GlucoseCaptureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGlucoseCaptureBinding
    private lateinit var repo: MedRepository
    private var medicationId: Long = -1L
    private var photoFile: File? = null

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = photoFile
        if (success && file != null && file.exists()) {
            showCapturedPhoto(file)
            runOcr(file)
        } else {
            finish()
        }
    }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchCamera()
            } else {
                Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGlucoseCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        medicationId = intent.getLongExtra(EXTRA_MEDICATION_ID, -1L)
        if (medicationId == -1L) {
            finish()
            return
        }
        repo = MedRepository.getInstance(this)

        binding.buttonRetake.setOnClickListener { launchCameraFlow() }
        binding.buttonSave.setOnClickListener { saveReading() }
        binding.buttonCancel.setOnClickListener { finish() }

        if (savedInstanceState == null) {
            launchCameraFlow()
        }
    }

    private fun launchCameraFlow() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            launchCamera()
        }
    }

    private fun launchCamera() {
        val dir = File(filesDir, "glucose_photos").apply { mkdirs() }
        val file = File(dir, "glucose_${System.currentTimeMillis()}.jpg")
        photoFile = file
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        takePicture.launch(uri)
    }

    private fun showCapturedPhoto(file: File) {
        Glide.with(this).load(file).centerCrop().into(binding.imagePreview)
    }

    private fun runOcr(file: File) {
        binding.textDetecting.visibility = View.VISIBLE
        val image = try {
            InputImage.fromFilePath(this, android.net.Uri.fromFile(file))
        } catch (e: Exception) {
            binding.textDetecting.visibility = View.GONE
            return
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                binding.textDetecting.visibility = View.GONE
                extractBestNumber(visionText)?.let { value ->
                    binding.editValue.setText(formatForInput(value))
                }
            }
            .addOnFailureListener {
                binding.textDetecting.visibility = View.GONE
                // Leave the field empty — the person can still type the reading manually.
            }
    }

    /** Picks the numeric token with the tallest bounding box within a plausible glucose range —
     * meter displays show the reading in the largest digits on screen, so text height is a
     * reasonable proxy for "this is the reading" versus a smaller unit label or model number. */
    private fun extractBestNumber(visionText: Text): Double? {
        val regex = Regex("^\\d{1,3}([.,]\\d)?$")
        var best: Double? = null
        var bestHeight = 0
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val raw = element.text.trim()
                    if (!regex.matches(raw)) continue
                    val value = raw.replace(",", ".").toDoubleOrNull() ?: continue
                    if (value < 20 || value > 600) continue // plausible mg/dL glucose range guard
                    val height = element.boundingBox?.height() ?: 0
                    if (height > bestHeight) {
                        bestHeight = height
                        best = value
                    }
                }
            }
        }
        return best
    }

    private fun formatForInput(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private fun saveReading() {
        val text = binding.editValue.text?.toString()?.trim()
        val value = text?.toDoubleOrNull()
        if (value == null) {
            binding.inputLayoutValue.error = getString(R.string.glucose_invalid_value)
            return
        }
        binding.inputLayoutValue.error = null
        val unit = if (binding.radioMmoll.isChecked) UNIT_MMOLL else UNIT_MGDL

        lifecycleScope.launch {
            repo.saveGlucoseReading(medicationId, value, unit, photoFile?.absolutePath)
            NotificationHelper.refreshOne(applicationContext, medicationId)
            finish()
        }
    }

    companion object {
        const val EXTRA_MEDICATION_ID = "medication_id"
        const val UNIT_MGDL = "mg/dL"
        const val UNIT_MMOLL = "mmol/L"
    }
}
