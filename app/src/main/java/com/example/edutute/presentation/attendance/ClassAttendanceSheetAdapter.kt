package com.example.edutute.presentation.attendance

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.edutute.databinding.ItemClassAttendanceSheetBinding

data class ClassAttendanceSheetItem(
    val id: String,
    val rollNumber: String,
    val studentName: String,
    val isPresent: Boolean,
)

class ClassAttendanceSheetAdapter(
    private val onPresentChanged: (String, Boolean) -> Unit,
) : ListAdapter<ClassAttendanceSheetItem, ClassAttendanceSheetAdapter.ClassAttendanceSheetViewHolder>(
    ClassAttendanceSheetDiff(),
) {

    private var isEditable: Boolean = true

    fun setEditable(editable: Boolean) {
        if (isEditable == editable) return
        isEditable = editable
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClassAttendanceSheetViewHolder {
        val binding = ItemClassAttendanceSheetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ClassAttendanceSheetViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ClassAttendanceSheetViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ClassAttendanceSheetViewHolder(
        private val binding: ItemClassAttendanceSheetBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ClassAttendanceSheetItem) = with(binding) {
            rollNumberText.text = item.rollNumber.ifBlank { "--" }
            studentNameText.text = item.studentName

            presentCheckbox.setOnCheckedChangeListener(null)
            presentCheckbox.isChecked = item.isPresent
            presentCheckbox.isEnabled = isEditable
            presentCheckbox.isClickable = isEditable
            presentCheckbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != item.isPresent) {
                    onPresentChanged(item.id, isChecked)
                }
            }

            root.isEnabled = isEditable
            root.isClickable = isEditable
            root.setOnClickListener {
                if (!isEditable) return@setOnClickListener
                presentCheckbox.isChecked = !presentCheckbox.isChecked
            }
        }
    }

    private class ClassAttendanceSheetDiff : DiffUtil.ItemCallback<ClassAttendanceSheetItem>() {
        override fun areItemsTheSame(
            oldItem: ClassAttendanceSheetItem,
            newItem: ClassAttendanceSheetItem,
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: ClassAttendanceSheetItem,
            newItem: ClassAttendanceSheetItem,
        ): Boolean = oldItem == newItem
    }
}
