package com.igh.medtracker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.igh.medtracker.R
import com.igh.medtracker.data.DoseLog
import com.igh.medtracker.databinding.ItemLogBinding
import com.igh.medtracker.util.TimeUtils
import java.io.File

class LogAdapter(
    private val showMedicationName: Boolean,
    private val medicationNamesProvider: () -> Map<Long, String>,
    private val onEditClick: (DoseLog) -> Unit,
    private val onDeleteClick: (DoseLog) -> Unit,
    private val onPhotoClick: (DoseLog) -> Unit
) : ListAdapter<DoseLog, LogAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(log: DoseLog) {
            val context = binding.root.context
            if (showMedicationName) {
                binding.textMedicationName.visibility = View.VISIBLE
                binding.textMedicationName.text = medicationNamesProvider()[log.medicationId].orEmpty()
            } else {
                binding.textMedicationName.visibility = View.GONE
            }

            val absolute = TimeUtils.formatAbsolute(log.timestamp)
            val relative = TimeUtils.relativeTime(context, log.timestamp)
            binding.textTimestamp.text = "$absolute  ($relative)"

            if (log.glucoseValue != null) {
                binding.textDose.visibility = View.VISIBLE
                binding.textDose.text = context.getString(
                    R.string.glucose_reading_label,
                    TimeUtils.formatNumber(log.glucoseValue),
                    log.glucoseUnit.orEmpty()
                )
            } else if (!log.doseAmount.isNullOrBlank()) {
                binding.textDose.visibility = View.VISIBLE
                binding.textDose.text = log.doseAmount
            } else {
                binding.textDose.visibility = View.GONE
            }

            val photo = log.photoPath?.let { File(it) }
            if (photo != null && photo.exists()) {
                binding.imagePhoto.visibility = View.VISIBLE
                Glide.with(context).load(photo).centerCrop().into(binding.imagePhoto)
                binding.imagePhoto.setOnClickListener { onPhotoClick(log) }
            } else {
                binding.imagePhoto.visibility = View.GONE
                binding.imagePhoto.setOnClickListener(null)
            }

            binding.buttonEdit.setOnClickListener { onEditClick(log) }
            binding.buttonDelete.setOnClickListener { onDeleteClick(log) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DoseLog>() {
            override fun areItemsTheSame(oldItem: DoseLog, newItem: DoseLog) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: DoseLog, newItem: DoseLog) = oldItem == newItem
        }
    }
}
