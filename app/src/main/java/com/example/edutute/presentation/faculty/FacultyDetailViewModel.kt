package com.example.edutute.presentation.faculty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edutute.core.ui.UiState
import com.example.edutute.core.util.AttendanceDateUtils
import com.example.edutute.core.util.userMessage
import com.example.edutute.domain.model.AttendanceStatus
import com.example.edutute.domain.model.ClassSection
import com.example.edutute.domain.model.FacultyMember
import com.example.edutute.domain.model.FacultyAttendanceSummary
import com.example.edutute.domain.model.toAttendanceStatus
import com.example.edutute.domain.repository.AcademicRepository
import com.example.edutute.domain.repository.AttendanceRepository
import com.example.edutute.domain.repository.FacultyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FacultyTeachingAssignmentUiModel(
    val classSectionId: String,
    val classSectionName: String,
    val subjectNames: List<String>,
)

data class FacultyAttendanceDetailState(
    val scopeMessage: String = "",
    val summary: FacultyAttendanceSummary = FacultyAttendanceSummary(),
    val selectedDate: String = AttendanceDateUtils.todayStorageDate(),
    val hasSearchedDate: Boolean = false,
    val hasRecordForSelectedDate: Boolean = false,
    val isEditMode: Boolean = false,
    val selectedDateStatus: AttendanceStatus = AttendanceStatus.ABSENT,
    val resultMessage: String = "",
    val isBusy: Boolean = false,
)

data class FacultyDetailScreenData(
    val faculty: FacultyMember,
    val classTeacherOf: List<ClassSection>,
    val teachingAssignments: List<FacultyTeachingAssignmentUiModel>,
    val attendance: FacultyAttendanceDetailState = FacultyAttendanceDetailState(),
)

class FacultyDetailViewModel(
    private val facultyRepository: FacultyRepository,
    private val academicRepository: AcademicRepository,
    private val attendanceRepository: AttendanceRepository,
) : ViewModel() {

    private val mutableFacultyState = MutableStateFlow<UiState<FacultyDetailScreenData>>(UiState.Loading)
    val facultyState: StateFlow<UiState<FacultyDetailScreenData>> = mutableFacultyState.asStateFlow()

    private val mutableDeleteState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val deleteState: StateFlow<UiState<Unit>> = mutableDeleteState.asStateFlow()

    private val mutableAttendanceActionState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val attendanceActionState: StateFlow<UiState<String>> = mutableAttendanceActionState.asStateFlow()

    fun loadFaculty(id: String) {
        viewModelScope.launch {
            mutableFacultyState.value = UiState.Loading
            mutableFacultyState.value = try {
                UiState.Success(
                    loadFacultyDetail(id),
                )
            } catch (throwable: Throwable) {
                UiState.Error(throwable.userMessage("Unable to load faculty detail."))
            }
        }
    }

    fun deleteFaculty(id: String) {
        viewModelScope.launch {
            mutableDeleteState.value = UiState.Loading
            mutableDeleteState.value = try {
                facultyRepository.deleteFaculty(id)
                UiState.Success(Unit)
            } catch (throwable: Throwable) {
                UiState.Error(throwable.userMessage("Unable to delete faculty record."))
            }
        }
    }

    fun clearDeleteState() {
        mutableDeleteState.value = UiState.Idle
    }

    fun updateAttendanceDate(date: String) {
        updateScreenData { screen ->
            screen.copy(
                attendance = screen.attendance.copy(
                    selectedDate = date,
                    hasSearchedDate = false,
                    hasRecordForSelectedDate = false,
                    isEditMode = false,
                    selectedDateStatus = AttendanceStatus.ABSENT,
                    resultMessage = "",
                ),
            )
        }
    }

    fun searchAttendanceByDate() {
        val screen = currentScreenData() ?: return

        viewModelScope.launch {
            updateAttendanceState(screen.attendance.copy(isBusy = true, isEditMode = false, resultMessage = ""))
            try {
                val record = attendanceRepository.getFacultyAttendanceRecord(screen.attendance.selectedDate)
                val facultyEntry = record?.entries?.firstOrNull { entry -> entry.facultyId == screen.faculty.id }

                updateAttendanceState(
                    screen.attendance.copy(
                        hasSearchedDate = true,
                        hasRecordForSelectedDate = record != null,
                        isEditMode = false,
                        selectedDateStatus = facultyEntry?.status?.toAttendanceStatus() ?: AttendanceStatus.ABSENT,
                        resultMessage = when {
                            record == null -> "Attendance was not marked for this date."
                            facultyEntry == null -> "Attendance was marked for this date. Review and save to include this faculty member's status."
                            facultyEntry.status == AttendanceStatus.PRESENT.name -> "The faculty member was marked present on this date."
                            else -> "The faculty member was marked absent on this date."
                        },
                        isBusy = false,
                    ),
                )
            } catch (throwable: Throwable) {
                updateAttendanceState(
                    screen.attendance.copy(
                        hasSearchedDate = true,
                        hasRecordForSelectedDate = false,
                        isEditMode = false,
                        resultMessage = throwable.userMessage("Unable to check attendance for this date."),
                        isBusy = false,
                    ),
                )
            }
        }
    }

    fun enterAttendanceEditMode() {
        updateScreenData { screen ->
            if (!screen.attendance.hasRecordForSelectedDate) return@updateScreenData screen
            screen.copy(
                attendance = screen.attendance.copy(isEditMode = true),
            )
        }
    }

    fun updateAttendanceStatus(status: AttendanceStatus) {
        updateScreenData { screen ->
            if (!screen.attendance.isEditMode) return@updateScreenData screen
            screen.copy(
                attendance = screen.attendance.copy(selectedDateStatus = status),
            )
        }
    }

    fun saveAttendanceRectification() {
        val screen = currentScreenData() ?: return
        if (!screen.attendance.hasRecordForSelectedDate) {
            mutableAttendanceActionState.value = UiState.Error("Attendance was not marked for this date.")
            return
        }
        if (!screen.attendance.isEditMode) {
            mutableAttendanceActionState.value = UiState.Error("Tap Edit before changing attendance for this date.")
            return
        }

        viewModelScope.launch {
            updateAttendanceState(screen.attendance.copy(isBusy = true))
            mutableAttendanceActionState.value = UiState.Loading
            try {
                attendanceRepository.rectifyFacultyAttendance(
                    facultyId = screen.faculty.id,
                    attendanceDate = screen.attendance.selectedDate,
                    status = screen.attendance.selectedDateStatus,
                )
                val refreshedSummary = attendanceRepository.getFacultyAttendanceSummary(screen.faculty.id)
                updateAttendanceState(
                    screen.attendance.copy(
                        summary = refreshedSummary,
                        hasSearchedDate = true,
                        hasRecordForSelectedDate = true,
                        isEditMode = false,
                        resultMessage = "Attendance updated for ${AttendanceDateUtils.toDisplayDate(screen.attendance.selectedDate)}.",
                        isBusy = false,
                    ),
                )
                mutableAttendanceActionState.value = UiState.Success("Attendance rectified successfully.")
            } catch (throwable: Throwable) {
                updateAttendanceState(
                    screen.attendance.copy(
                        resultMessage = throwable.userMessage("Unable to update attendance for this faculty member."),
                        isBusy = false,
                    ),
                )
                mutableAttendanceActionState.value = UiState.Error(
                    throwable.userMessage("Unable to update attendance for this faculty member."),
                )
            }
        }
    }

    fun clearAttendanceActionState() {
        mutableAttendanceActionState.value = UiState.Idle
    }

    private suspend fun loadFacultyDetail(id: String): FacultyDetailScreenData {
        val faculty = facultyRepository.getFacultyById(id)
            ?: throw IllegalArgumentException("Faculty record was not found.")
        val classSections = academicRepository.listClassSections()
        val classTeacherOf = classSections
            .filter { it.classTeacherId == id }
            .sortedBy(ClassSection::displayName)
        val teachingAssignments = academicRepository.listTeacherAssignmentsForFaculty(id)
            .groupBy { it.classSectionId }
            .mapNotNull { (classSectionId, assignments) ->
                val classSection = classSections.firstOrNull { it.id == classSectionId }
                val classSectionName = classSection?.displayName ?: assignments.firstOrNull()?.classSectionName.orEmpty()
                if (classSectionName.isBlank()) {
                    null
                } else {
                    FacultyTeachingAssignmentUiModel(
                        classSectionId = classSectionId,
                        classSectionName = classSectionName,
                        subjectNames = assignments
                            .map { it.subjectName.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .sorted(),
                    )
                }
            }
            .sortedBy(FacultyTeachingAssignmentUiModel::classSectionName)

        return FacultyDetailScreenData(
            faculty = faculty,
            classTeacherOf = classTeacherOf,
            teachingAssignments = teachingAssignments,
            attendance = buildInitialAttendanceState(faculty),
        )
    }

    private suspend fun buildInitialAttendanceState(faculty: FacultyMember): FacultyAttendanceDetailState {
        val summary = try {
            attendanceRepository.getFacultyAttendanceSummary(faculty.id)
        } catch (_: Throwable) {
            FacultyAttendanceSummary()
        }

        return FacultyAttendanceDetailState(
            scopeMessage = "Summary is based only on saved faculty attendance dates for this institution.",
            summary = summary,
        )
    }

    private fun currentScreenData(): FacultyDetailScreenData? = (mutableFacultyState.value as? UiState.Success)?.data

    private fun updateAttendanceState(attendance: FacultyAttendanceDetailState) {
        updateScreenData { screen -> screen.copy(attendance = attendance) }
    }

    private inline fun updateScreenData(transform: (FacultyDetailScreenData) -> FacultyDetailScreenData) {
        val currentState = mutableFacultyState.value as? UiState.Success ?: return
        mutableFacultyState.value = UiState.Success(transform(currentState.data))
    }
}
