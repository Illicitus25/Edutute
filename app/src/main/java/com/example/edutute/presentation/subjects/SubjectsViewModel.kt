package com.example.edutute.presentation.subjects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edutute.core.ui.UiState
import com.example.edutute.core.util.userMessage
import com.example.edutute.domain.model.Subject
import com.example.edutute.domain.model.SubjectDraft
import com.example.edutute.domain.repository.SubjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SubjectsViewModel(
    private val subjectRepository: SubjectRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow<UiState<List<Subject>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Subject>>> = mutableState.asStateFlow()

    private val mutableActionState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val actionState: StateFlow<UiState<Unit>> = mutableActionState.asStateFlow()

    private var sourceList: List<Subject> = emptyList()
    private var currentQuery: String = ""

    fun load() {
        viewModelScope.launch {
            mutableState.value = UiState.Loading
            mutableState.value = try {
                sourceList = subjectRepository.listSubjects()
                UiState.Success(filter(sourceList, currentQuery))
            } catch (throwable: Throwable) {
                UiState.Error(throwable.userMessage("Unable to load subjects."))
            }
        }
    }

    fun updateQuery(query: String) {
        currentQuery = query
        mutableState.value = UiState.Success(filter(sourceList, currentQuery))
    }

    fun saveSubject(draft: SubjectDraft) = performAction { subjectRepository.saveSubject(draft) }

    fun deleteSubject(id: String) = performAction { subjectRepository.deleteSubject(id) }

    fun clearActionState() {
        mutableActionState.value = UiState.Idle
    }

    private fun filter(items: List<Subject>, query: String): List<Subject> {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return items
        return items.filter {
            it.nameLower.contains(normalized)
        }
    }

    private fun performAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            mutableActionState.value = UiState.Loading
            mutableActionState.value = try {
                action()
                sourceList = subjectRepository.listSubjects()
                mutableState.value = UiState.Success(filter(sourceList, currentQuery))
                UiState.Success(Unit)
            } catch (throwable: Throwable) {
                UiState.Error(throwable.userMessage("Unable to save subject."))
            }
        }
    }
}
