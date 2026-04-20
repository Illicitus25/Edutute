package com.example.edutute.presentation.classes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.edutute.R
import com.example.edutute.databinding.ItemClassDetailSubjectBinding

class ClassDetailsSubjectsAdapter(
    private val onChangeTeacher: (ClassSubjectItem) -> Unit,
    private val onRemoveSubject: (ClassSubjectItem) -> Unit,
) : ListAdapter<ClassSubjectItem, ClassDetailsSubjectsAdapter.SubjectViewHolder>(SubjectDiffCallback()) {

    private var canManageSubjects: Boolean = true

    fun setCanManageSubjects(canManage: Boolean) {
        if (canManageSubjects == canManage) return
        canManageSubjects = canManage
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubjectViewHolder {
        val binding = ItemClassDetailSubjectBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SubjectViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SubjectViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SubjectViewHolder(
        private val binding: ItemClassDetailSubjectBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ClassSubjectItem) = with(binding) {
            subjectNameText.text = item.subjectName
            subjectCodeText.text = item.subjectCode.ifBlank {
                root.context.getString(R.string.label_subject_code_missing)
            }
            teacherNameText.text = item.teacherName.ifBlank {
                root.context.getString(R.string.label_not_assigned)
            }
            changeTeacherButton.isVisible = canManageSubjects
            removeSubjectButton.isVisible = canManageSubjects
            changeTeacherButton.setOnClickListener { onChangeTeacher(item) }
            removeSubjectButton.setOnClickListener { onRemoveSubject(item) }
        }
    }

    private class SubjectDiffCallback : DiffUtil.ItemCallback<ClassSubjectItem>() {
        override fun areItemsTheSame(oldItem: ClassSubjectItem, newItem: ClassSubjectItem): Boolean =
            oldItem.subjectId == newItem.subjectId

        override fun areContentsTheSame(oldItem: ClassSubjectItem, newItem: ClassSubjectItem): Boolean =
            oldItem == newItem
    }
}
