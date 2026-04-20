package com.example.edutute.presentation.classes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edutute.core.ui.UiState
import com.example.edutute.core.util.ValidationUtils
import com.example.edutute.core.util.userMessage
import com.example.edutute.domain.access.RoleAccessManager
import com.example.edutute.domain.model.ClassSection
import com.example.edutute.domain.model.ClassSectionDraft
import com.example.edutute.domain.model.SchoolClassDraft
import com.example.edutute.domain.model.SectionDraft
import com.example.edutute.domain.model.Student
import com.example.edutute.domain.repository.AcademicRepository
import com.example.edutute.domain.repository.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ClassesScreenData(
    val classSections: List<ClassSection> = emptyList(),
    val students: List<Student> = emptyList(),
    val isFacultyUser: Boolean = false,
)

class ClassesViewModel(
    private val academicRepository: AcademicRepository,
    private val studentRepository: StudentRepository,
    private val roleAccessManager: RoleAccessManager,
) : ViewModel() {

    private val mutableState = MutableStateFlow<UiState<ClassesScreenData>>(UiState.Loading)
    val state: StateFlow<UiState<ClassesScreenData>> = mutableState.asStateFlow()

    private val mutableActionState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val actionState: StateFlow<UiState<Unit>> = mutableActionState.asStateFlow()

    private var currentQuery: String = ""
    private var sourceData: ClassesScreenData = ClassesScreenData()

    fun load() {
        viewModelScope.launch {
            mutableState.value = UiState.Loading
            mutableState.value = try {
                sourceData = ClassesScreenData(
                    classSections = academicRepository.listClassSections(),
                    students = studentRepository.listStudents(),
                    isFacultyUser = roleAccessManager.currentContext().isFaculty,
                )
                UiState.Success(filteredData())
            } catch (throwable: Throwable) {
                UiState.Error(throwable.userMessage("Unable to load class and section data."))
            }
        }
    }

    fun updateQuery(query: String) {
        currentQuery = query
        mutableState.value = UiState.Success(filteredData())
    }

    fun saveClassWithSection(
        className: String,
        sectionName: String,
    ) = performAction {
        val normalizedClass = ValidationUtils.normalize(className)
        val normalizedSection = ValidationUtils.normalize(sectionName)
        val existingClasses = academicRepository.listClasses()
        val existingSections = academicRepository.listSections()

        val schoolClass = existingClasses.firstOrNull { it.nameLower == normalizedClass }
            ?: academicRepository.saveClass(
                SchoolClassDraft(
                    name = className,
                ),
            )

        val section = existingSections.firstOrNull { it.nameLower == normalizedSection }
            ?: academicRepository.saveSection(
                SectionDraft(
                    name = sectionName,
                ),
            )

        academicRepository.saveClassSection(
            ClassSectionDraft(
                classId = schoolClass.id,
                sectionId = section.id,
            ),
        )
    }

    fun deleteClassSection(id: String) = performAction { academicRepository.deleteClassSection(id) }

    fun clearActionState() {
        mutableActionState.value = UiState.Idle
    }

    private fun performAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            mutableActionState.value = UiState.Loading
            mutableActionState.value = try {
                action()
                load()
                UiState.Success(Unit)
            } catch (throwable: Throwable) {
                UiState.Error(throwable.userMessage("Unable to save changes."))
            }
        }
    }

    private fun filteredData(): ClassesScreenData = sourceData.copy(
        classSections = filter(sourceData.classSections, currentQuery),
    )

    private fun filter(items: List<ClassSection>, query: String): List<ClassSection> {
        val normalized = ValidationUtils.normalize(query)
        if (normalized.isBlank()) return items
        return items.filter { item ->
            item.className.lowercase().contains(normalized) ||
                item.sectionName.lowercase().contains(normalized) ||
                item.displayNameLower.contains(normalized)
        }
    }
}
