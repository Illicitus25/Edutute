package com.example.edutute.presentation.faculty

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.edutute.R
import com.example.edutute.databinding.ItemFacultyTeachingAssignmentBinding

class FacultyTeachingAssignmentsAdapter :
    ListAdapter<FacultyTeachingAssignmentUiModel, FacultyTeachingAssignmentsAdapter.TeachingViewHolder>(DiffCallback) {

    private val expandedClassSectionIds = linkedSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeachingViewHolder {
        val binding = ItemFacultyTeachingAssignmentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return TeachingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TeachingViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, expandedClassSectionIds.contains(item.classSectionId))
        holder.itemView.setOnClickListener {
            toggle(item.classSectionId)
        }
    }

    override fun submitList(list: List<FacultyTeachingAssignmentUiModel>?) {
        expandedClassSectionIds.retainAll(list.orEmpty().map { it.classSectionId }.toSet())
        super.submitList(list)
    }

    private fun toggle(classSectionId: String) {
        if (!expandedClassSectionIds.add(classSectionId)) {
            expandedClassSectionIds.remove(classSectionId)
        }
        currentList.indexOfFirst { it.classSectionId == classSectionId }
            .takeIf { it >= 0 }
            ?.let(::notifyItemChanged)
    }

    class TeachingViewHolder(
        private val binding: ItemFacultyTeachingAssignmentBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FacultyTeachingAssignmentUiModel, isExpanded: Boolean) = with(binding) {
            classNameText.text = item.classSectionName
            teachingHintText.text = root.context.getString(
                if (isExpanded) R.string.label_hide_subjects else R.string.label_view_subjects,
            )
            expandIndicatorText.text = root.context.getString(
                if (isExpanded) R.string.symbol_collapse else R.string.symbol_expand,
            )
            subjectsContainer.isVisible = isExpanded

            subjectsChipGroup.removeAllViews()
            if (item.subjectNames.isEmpty()) {
                emptySubjectsText.isVisible = true
                subjectsChipGroup.isVisible = false
            } else {
                emptySubjectsText.isVisible = false
                subjectsChipGroup.isVisible = true
                item.subjectNames.forEach { subjectName ->
                    val chip = com.google.android.material.chip.Chip(root.context).apply {
                        text = subjectName
                        isCheckable = false
                        isClickable = false
                        setEnsureMinTouchTargetSize(false)
                    }
                    subjectsChipGroup.addView(chip)
                }
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<FacultyTeachingAssignmentUiModel>() {
        override fun areItemsTheSame(
            oldItem: FacultyTeachingAssignmentUiModel,
            newItem: FacultyTeachingAssignmentUiModel,
        ): Boolean = oldItem.classSectionId == newItem.classSectionId

        override fun areContentsTheSame(
            oldItem: FacultyTeachingAssignmentUiModel,
            newItem: FacultyTeachingAssignmentUiModel,
        ): Boolean = oldItem == newItem
    }
}
