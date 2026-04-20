package com.example.edutute.presentation.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edutute.core.ui.UiState
import com.example.edutute.core.util.userMessage
import com.example.edutute.domain.model.Student
import com.example.edutute.domain.repository.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StudentsListViewModel(
    private val studentRepository: StudentRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow<UiState<List<Student>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Student>>> = mutableState.asStateFlow()

    private val mutableActionState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val actionState: StateFlow<UiState<Unit>> = mutableActionState.asStateFlow()

    private var sourceList: List<Student> = emptyList()
    private var currentQuery: String = ""

    fun load() {
        viewModelScope.launch {
            mutableState.value = UiState.Loading
            mutableState.value = try {
                sourceList = studentRepository.listStudents()
                UiState.Success(filter(sourceList, currentQuery))
            } catch (throwable: Throwable) {
                UiState.Error(throwable.userMessage("Unable to load students."))
            }
        }
    }

    fun updateQuery(query: String) {
        currentQuery = query
        mutableState.value = UiState.Success(filter(sourceList, currentQuery))
    }

    fun deleteStudent(id: String) {
        viewModelScope.launch {
            mutableActionState.value = UiState.Loading
            mutableActionState.value = try {
                studentRepository.deleteStudent(id)
                sourceList = studentRepository.listStudents()
                mutableState.value = UiState.Success(filter(sourceList, currentQuery))
                UiState.Success(Unit)
            } catch (throwable: Throwable) {
                UiState.Error(throwable.userMessage("Unable to delete student record."))
            }
        }
    }

    fun clearActionState() {
        mutableActionState.value = UiState.Idle
    }

    private fun filter(items: List<Student>, query: String): List<Student> {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return items
        return items.filter {
            it.fullNameLower.contains(normalized) ||
                it.admissionNumber.lowercase().contains(normalized) ||
                it.currentClassSectionName.lowercase().contains(normalized)
        }
    }
}
