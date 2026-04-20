package com.example.edutute.presentation.classes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.edutute.databinding.ItemClassDetailStudentBinding
import com.example.edutute.domain.model.Student

class ClassDetailsStudentsAdapter(
    private val onRemove: (Student) -> Unit,
) : ListAdapter<Student, ClassDetailsStudentsAdapter.StudentViewHolder>(StudentDiffCallback()) {

    private var canManageStudents: Boolean = true

    fun setCanManageStudents(canManage: Boolean) {
        if (canManageStudents == canManage) return
        canManageStudents = canManage
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val binding = ItemClassDetailStudentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StudentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class StudentViewHolder(
        private val binding: ItemClassDetailStudentBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(student: Student) = with(binding) {
            studentNameText.text = student.fullName
            rollNumberText.text = student.currentRollNumber
            removeStudentButton.isVisible = canManageStudents
            removeStudentButton.setOnClickListener { onRemove(student) }
        }
    }

    private class StudentDiffCallback : DiffUtil.ItemCallback<Student>() {
        override fun areItemsTheSame(oldItem: Student, newItem: Student): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Student, newItem: Student): Boolean = oldItem == newItem
    }
}
