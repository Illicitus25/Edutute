package com.example.edutute.presentation.faculty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edutute.core.ui.UiState
import com.example.edutute.core.util.userMessage
import com.example.edutute.domain.model.FacultyDraft
import com.example.edutute.domain.model.FacultyMember
import com.example.edutute.domain.model.FacultySaveResult
import com.example.edutute.domain.repository.FacultyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FacultyFormViewModel(
    private val facultyRepository: FacultyRepository,
) : ViewModel() {

    private val mutableFacultyState = MutableStateFlow<UiState<FacultyMember?>>(UiState.Success(null))
    val facultyState: StateFlow<UiState<FacultyMember?>> = mutableFacultyState.asStateFlow()

    private val mutableSaveState = MutableStateFlow<UiState<FacultySaveResult>>(UiState.Idle)
    val saveState: StateFlow<UiState<FacultySaveResult>> = mutableSaveState.asStateFlow()

    fun loadFaculty(id: String?) {
        if (id.isNullOrBlank()) {
            mutableFacultyState.value = UiState.Success(null)
            return
        }
        viewModelScope.launch {
            mutableFacultyState.value = UiState.Loading
            mutableFacultyState.value = try {
                UiState.Success(facultyRepository.getFacultyById(id))
            } catch (throwable: Throwable) {
                UiState.Error(throwable.userMessage("Unable to load faculty detail."))
            }
        }
    }

    fun saveFaculty(draft: FacultyDraft) {
        viewModelScope.launch {
            mutableSaveState.value = UiState.Loading
            mutableSaveState.value = try {
                UiState.Success(facultyRepository.saveFaculty(draft))
            } catch (throwable: Throwable) {
                UiState.Error(throwable.userMessage("Unable to save faculty record."))
            }
        }
    }

    fun clearSaveState() {
        mutableSaveState.value = UiState.Idle
    }
}
