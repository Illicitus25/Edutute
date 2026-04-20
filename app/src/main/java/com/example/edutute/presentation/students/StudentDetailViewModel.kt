package com.example.edutute.presentation.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edutute.core.ui.UiState
import com.example.edutute.core.util.AttendanceDateUtils
import com.example.edutute.core.util.userMessage
import com.example.edutute.domain.model.AttendanceStatus
import com.example.edutute.domain.model.Student
import com.example.edutute.domain.model.StudentAttendanceSummary
import com.example.edutute.domain.model.toAttendanceStatus
import com.example.edutute.domain.repository.AttendanceRepository
import com.example.edutute.domain.repository.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StudentAttendanceDetailState(
    val scopeMessage: String = "",
    val summary: StudentAttendanceSummary = StudentAttendanceSummary(),
    val selectedDate: String = AttendanceDateUtils.todayStorageDate(),
    val hasSearchedDate: Boolean = false,
    val hasRecordForSelectedDate: Boolean = false,
    val isEditMode: Boolean = false,
    val selectedDateStatus: AttendanceStatus = AttendanceStatus.ABSENT,
    val resultMessage: String = "",
    val isBusy: Boolean = false,
)

data class StudentDetailScreenData(
    val student: Student,
    val attendance: StudentAttendanceDetailState = StudentAttendanceDetailState(),
)

class StudentDetailViewModel(
    private val studentRepository: StudentRepository,
    private val attendanceRepository: AttendanceRepository,
) : ViewModel() {

    private val mutableStudentState = MutableStateFlow<UiState<StudentDetailScreenData>>(UiState.Loading)
    val studentState: StateFlow<UiState<StudentDetailScreenData>> = mutableStudentState.asStateFlow()

    private val mutableDeleteState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val deleteState: StateFlow<UiState<Unit>> = mutableDeleteState.asStateFlow()

    private val mutableAttendanceActionState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val attendanceActionState: StateFlow<UiState<String>> = mutableAttendanceActionState.asStateFlow()

    fun loadStudent(id: String) {
        viewModelScope.launch {
            mutableStudentState.value = UiState.Loading
            mutableStudentState.value = try {
                val student = studentRepository.getStudentById(id)
                    ?: throw IllegalArgumentException("Student record was not found.")
                UiState.Success(
                    StudentDetailScreenData(
                        student = student,
                        attendance = buildInitialAttendanceState(student),
                    ),
                )
            } catch (throwable: Throwable) {
                UiState.Error(throwable.userMessage("Unable to load student detail."))
            }
        }
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
        if (screen.student.currentClassSectionId.isBlank()) {
            updateAttendanceState(
                screen.attendance.copy(
                    hasSearchedDate = true,
                    hasRecordForSelectedDate = false,
                    resultMessage = "This student is not currently assigned to a class-section.",
                ),
            )
            return
        }

        viewModelScope.launch {
            updateAttendanceState(screen.attendance.copy(isBusy = true, isEditMode = false, resultMessage = ""))
            try {
                val record = attendanceRepository.getClassAttendanceRecord(
                    classSectionId = screen.student.currentClassSectionId,
                    attendanceDate = screen.attendance.selectedDate,
                )
                val studentEntry = record?.entries?.firstOrNull { entry -> entry.studentId == screen.student.id }

                updateAttendanceState(
                    screen.attendance.copy(
                        hasSearchedDate = true,
                        hasRecordForSelectedDate = record != null,
                        isEditMode = false,
                        selectedDateStatus = studentEntry?.status?.toAttendanceStatus() ?: AttendanceStatus.ABSENT,
                        resultMessage = when {
                            record == null -> "Attendance was not marked for this class on this date."
                            studentEntry == null -> "Attendance was marked for this class on this date. Review and save to include this student's status."
                            studentEntry.status == AttendanceStatus.PRESENT.name -> "The student was marked present on this date."
                            else -> "The student was marked absent on this date."
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
            mutableAttendanceActionState.value = UiState.Error("Attendance was not marked for this class on this date.")
            return
        }
        if (!screen.attendance.isEditMode) {
            mutableAttendanceActionState.value = UiState.Error("Tap Edit before changing attendance for this date.")
            return
        }
        if (screen.student.currentClassSectionId.isBlank()) {
            mutableAttendanceActionState.value = UiState.Error("This student is not currently assigned to a class-section.")
            return
        }

        viewModelScope.launch {
            updateAttendanceState(screen.attendance.copy(isBusy = true))
            mutableAttendanceActionState.value = UiState.Loading
            try {
                attendanceRepository.rectifyStudentAttendance(
                    studentId = screen.student.id,
                    classSectionId = screen.student.currentClassSectionId,
                    attendanceDate = screen.attendance.selectedDate,
                    status = screen.attendance.selectedDateStatus,
                )
                val refreshedSummary = attendanceRepository.getStudentAttendanceSummary(
                    studentId = screen.student.id,
                    classSectionId = screen.student.currentClassSectionId,
                )
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
                        resultMessage = throwable.userMessage("Unable to update attendance for this student."),
                        isBusy = false,
                    ),
                )
                mutableAttendanceActionState.value = UiState.Error(
                    throwable.userMessage("Unable to update attendance for this student."),
                )
            }
        }
    }

    fun deleteStudent(id: String) {
        viewModelScope.launch {
            mutableDeleteState.value = UiState.Loading
            mutableDeleteState.value = try {
                studentRepository.deleteStudent(id)
                UiState.Success(Unit)
            } catch (throwable: Throwable) {
                UiState.Error(throwable.userMessage("Unable to delete student record."))
            }
        }
    }

    fun clearDeleteState() {
        mutableDeleteState.value = UiState.Idle
    }

    fun clearAttendanceActionState() {
        mutableAttendanceActionState.value = UiState.Idle
    }

    private suspend fun buildInitialAttendanceState(student: Student): StudentAttendanceDetailState {
        if (student.currentClassSectionId.isBlank()) {
            return StudentAttendanceDetailState(
                scopeMessage = "Attendance summary is unavailable because this student is not assigned to a class-section.",
            )
        }

        val summary = try {
            attendanceRepository.getStudentAttendanceSummary(
                studentId = student.id,
                classSectionId = student.currentClassSectionId,
            )
        } catch (_: Throwable) {
            StudentAttendanceSummary()
        }

        return StudentAttendanceDetailState(
            scopeMessage = "Summary is based only on days where attendance was marked for ${student.currentClassSectionName.ifBlank { "the current class-section" }}.",
            summary = summary,
        )
    }

    private fun currentScreenData(): StudentDetailScreenData? = (mutableStudentState.value as? UiState.Success)?.data

    private fun updateAttendanceState(attendance: StudentAttendanceDetailState) {
        updateScreenData { screen -> screen.copy(attendance = attendance) }
    }

    private inline fun updateScreenData(transform: (StudentDetailScreenData) -> StudentDetailScreenData) {
        val currentState = mutableStudentState.value as? UiState.Success ?: return
        mutableStudentState.value = UiState.Success(transform(currentState.data))
    }
}
