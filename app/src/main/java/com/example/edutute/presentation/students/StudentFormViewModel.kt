package com.example.edutute.presentation.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edutute.core.ui.UiState
import com.example.edutute.core.util.userMessage
import com.example.edutute.domain.model.ClassSection
import com.example.edutute.domain.model.IndiaAddressValidation
import com.example.edutute.domain.model.Student
import com.example.edutute.domain.model.StudentDraft
import com.example.edutute.domain.repository.AcademicRepository
import com.example.edutute.domain.repository.LocationValidationRepository
import com.example.edutute.domain.repository.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StudentFormData(
    val student: Student?,
    val classSections: List<ClassSection>,
)

class StudentFormViewModel(
    private val studentRepository: StudentRepository,
    private val academicRepository: AcademicRepository,
    private val locationValidationRepository: LocationValidationRepository,
) : ViewModel() {

    private val mutableFormState = MutableStateFlow<UiState<StudentFormData>>(UiState.Loading)
    val formState: StateFlow<UiState<StudentFormData>> = mutableFormState.asStateFlow()

    private val mutableSaveState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val saveState: StateFlow<UiState<Unit>> = mutableSaveState.asStateFlow()

    private val mutableAddressLookupState = MutableStateFlow<UiState<IndiaAddressValidation>>(UiState.Idle)
    val addressLookupState: StateFlow<UiState<IndiaAddressValidation>> = mutableAddressLookupState.asStateFlow()

    fun load(studentId: String?) {
        viewModelScope.launch {
            mutableFormState.value = UiState.Loading
            mutableFormState.value = try {
                UiState.Success(
                    StudentFormData(
                        student = studentId?.takeIf { it.isNotBlank() }?.let { studentRepository.getStudentById(it) },
                        classSections = academicRepository.listClassSections(),
                    ),
                )
            } catch (throwable: Throwable) {
                UiState.Error(throwable.userMessage("Unable to load student form."))
            }
        }
    }

    fun save(draft: StudentDraft) {
        viewModelScope.launch {
            mutableSaveState.value = UiState.Loading
            mutableSaveState.value = try {
                studentRepository.saveStudent(draft)
                UiState.Success(Unit)
            } catch (throwable: Throwable) {
                UiState.Error(throwable.userMessage("Unable to save student record."))
            }
        }
    }

    fun clearSaveState() {
        mutableSaveState.value = UiState.Idle
    }

    fun lookupPostalCode(postalCode: String) {
        viewModelScope.launch {
            mutableAddressLookupState.value = UiState.Loading
            mutableAddressLookupState.value = try {
                UiState.Success(locationValidationRepository.lookupIndianAddress(postalCode))
            } catch (throwable: Throwable) {
                UiState.Error(throwable.userMessage("Unable to verify postal code."))
            }
        }
    }

    fun clearAddressLookupState() {
        mutableAddressLookupState.value = UiState.Idle
    }
}
