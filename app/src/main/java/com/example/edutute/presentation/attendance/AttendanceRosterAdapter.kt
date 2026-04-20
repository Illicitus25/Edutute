package com.example.edutute.presentation.attendance

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.edutute.R
import com.example.edutute.databinding.ItemAttendanceEntryBinding
import com.example.edutute.domain.model.AttendanceStatus

data class AttendanceRosterListItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val meta: String,
    val status: AttendanceStatus,
)

class AttendanceRosterAdapter(
    private val onStatusChanged: (String, AttendanceStatus) -> Unit,
) : ListAdapter<AttendanceRosterListItem, AttendanceRosterAdapter.AttendanceRosterViewHolder>(AttendanceRosterDiff()) {

    private var isEditable: Boolean = true

    fun setEditable(editable: Boolean) {
        if (isEditable == editable) return
        isEditable = editable
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceRosterViewHolder {
        val binding = ItemAttendanceEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AttendanceRosterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AttendanceRosterViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AttendanceRosterViewHolder(
        private val binding: ItemAttendanceEntryBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AttendanceRosterListItem) = with(binding) {
            val context = root.context
            titleText.text = item.title
            subtitleText.text = item.subtitle
            metaText.text = item.meta
            metaText.isVisible = item.meta.isNotBlank()
            avatarText.text = item.title
                .split(' ')
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString(separator = "") { token -> token.first().uppercase() }
                .ifBlank { "#" }

            val isPresent = item.status == AttendanceStatus.PRESENT
            presentButton.isChecked = isPresent
            absentButton.isChecked = !isPresent
            statusToggleGroup.isVisible = isEditable
            presentButton.isEnabled = isEditable
            absentButton.isEnabled = isEditable
            statusChipText.text = context.getString(
                if (isPresent) R.string.label_present else R.string.label_absent,
            )
            statusChipText.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (isPresent) R.color.brand_success else R.color.brand_error,
                ),
            )
            root.strokeColor = ContextCompat.getColor(
                context,
                if (isPresent) R.color.brand_outline else R.color.brand_error,
            )

            presentButton.setOnClickListener {
                if (!isEditable) return@setOnClickListener
                if (!isPresent) {
                    onStatusChanged(item.id, AttendanceStatus.PRESENT)
                }
            }
            absentButton.setOnClickListener {
                if (!isEditable) return@setOnClickListener
                if (isPresent) {
                    onStatusChanged(item.id, AttendanceStatus.ABSENT)
                }
            }
        }
    }

    private class AttendanceRosterDiff : DiffUtil.ItemCallback<AttendanceRosterListItem>() {
        override fun areItemsTheSame(
            oldItem: AttendanceRosterListItem,
            newItem: AttendanceRosterListItem,
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: AttendanceRosterListItem,
            newItem: AttendanceRosterListItem,
        ): Boolean = oldItem == newItem
    }
}
