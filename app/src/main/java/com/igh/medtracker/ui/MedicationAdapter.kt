package com.igh.medtracker.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.igh.medtracker.R
import com.igh.medtracker.data.Medication
import com.igh.medtracker.databinding.ItemMedicationBinding
import com.igh.medtracker.util.TimeUtils

class MedicationAdapter(
    private val onLogClick: (Medication) -> Unit,
    private val onDeleteClick: (Medication) -> Unit,
    private val onAlwaysShowChanged: (Medication, Boolean) -> Unit,
    private val onCardClick: (Medication) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : ListAdapter<Medication, MedicationAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMedicationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /** Called by the 60-second ticker to refresh relative-time text without a full DB reload. */
    fun refreshTimestamps() {
        notifyItemRangeChanged(0, itemCount, PAYLOAD_TIME_ONLY)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_TIME_ONLY)) {
            holder.bindTimeOnly(getItem(position))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class ViewHolder(private val binding: ItemMedicationBinding) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility")
        fun bind(medication: Medication) {
            binding.textName.text = medication.name
            bindTimeOnly(medication)

            val isGlucose = medication.type == Medication.TYPE_GLUCOSE
            binding.buttonLog.text = binding.root.context.getString(
                if (isGlucose) R.string.capture_button else R.string.log_button
            )
            binding.buttonLog.setIconResource(if (isGlucose) R.drawable.ic_camera else R.drawable.ic_check)

            binding.switchAlwaysShow.setOnCheckedChangeListener(null)
            binding.switchAlwaysShow.isChecked = medication.alwaysShow
            binding.switchAlwaysShow.setOnCheckedChangeListener { _, isChecked ->
                onAlwaysShowChanged(medication, isChecked)
            }

            binding.buttonLog.setOnClickListener { onLogClick(medication) }
            binding.buttonDelete.setOnClickListener { onDeleteClick(medication) }

            binding.root.setOnClickListener { onCardClick(medication) }

            binding.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(this)
                }
                false
            }
        }

        fun bindTimeOnly(medication: Medication) {
            binding.textLastDose.text = TimeUtils.medicationSubtitle(binding.root.context, medication)
        }
    }

    companion object {
        private const val PAYLOAD_TIME_ONLY = "time_only"

        private val DIFF = object : DiffUtil.ItemCallback<Medication>() {
            override fun areItemsTheSame(oldItem: Medication, newItem: Medication) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Medication, newItem: Medication) = oldItem == newItem
        }
    }
}
