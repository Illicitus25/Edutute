package com.example.edutute.presentation.faculty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edutute.core.ui.UiState
import com.example.edutute.core.util.userMessage
import com.example.edutute.domain.model.FacultyMember
import com.example.edutute.domain.repository.FacultyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FacultyListViewModel(
    private val facultyRepository: FacultyRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow<UiState<List<FacultyMember>>>(UiState.Loading)
    val state: StateFlow<UiState<List<FacultyMember>>> = mutableState.asStateFlow()

    private val mutableActionState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val actionState: StateFlow<UiState<Unit>> = mutableActionState.asStateFlow()

    private var sourceList: List<FacultyMember> = emptyList()
    private var currentQuery: String = ""

    fun load() {
        viewModelScope.launch {
            mutableState.value = UiState.Loading
            mutableState.value = try {
                sourceList = facultyRepository.listFaculty()
                UiState.Success(filter(sourceList, currentQuery))
            } catch (throwable: Throwable) {
                UiState.Error(throwable.userMessage("Unable to load faculty records."))
            }
        }
    }

    fun updateQuery(query: String) {
        currentQuery = query
        mutableState.value = UiState.Success(filter(sourceList, query))
    }

    fun deleteFaculty(id: String) {
        viewModelScope.launch {
            mutableActionState.value = UiState.Loading
            mutableActionState.value = try {
                facultyRepository.deleteFaculty(id)
                sourceList = facultyRepository.listFaculty()
                mutableState.value = UiState.Success(filter(sourceList, currentQuery))
                UiState.Success(Unit)
            } catch (throwable: Throwable) {
                UiState.Error(throwable.userMessage("Unable to delete faculty record."))
            }
        }
    }

    fun clearActionState() {
        mutableActionState.value = UiState.Idle
    }

    private fun filter(items: List<FacultyMember>, query: String): List<FacultyMember> {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return items
        return items.filter {
            it.fullNameLower.contains(normalized) || it.employeeCode.lowercase().contains(normalized)
        }
    }
}
