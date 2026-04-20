package com.example.edutute.presentation.classes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.edutute.databinding.ItemSelectableStudentBinding
import com.example.edutute.domain.model.Student

class ClassDetailsStudentSelectionAdapter : RecyclerView.Adapter<ClassDetailsStudentSelectionAdapter.SelectionViewHolder>() {

    private var items: List<Student> = emptyList()
    private val selectedIds = linkedSetOf<String>()

    fun submitList(students: List<Student>) {
        items = students
        selectedIds.removeAll { selectedId -> students.none { it.id == selectedId } }
        notifyDataSetChanged()
    }

    fun selectedStudentIds(): List<String> = selectedIds.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectionViewHolder {
        val binding = ItemSelectableStudentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SelectionViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: SelectionViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class SelectionViewHolder(
        private val binding: ItemSelectableStudentBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(student: Student) = with(binding) {
            studentCheckBox.text = student.fullName
            studentMetaText.text = student.admissionNumber
            studentCheckBox.setOnCheckedChangeListener(null)
            studentCheckBox.isChecked = selectedIds.contains(student.id)
            studentCheckBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedIds.add(student.id)
                } else {
                    selectedIds.remove(student.id)
                }
            }
            root.setOnClickListener {
                studentCheckBox.isChecked = !studentCheckBox.isChecked
            }
        }
    }
}
