package com.example.edutute.presentation.classes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edutute.core.ui.UiState
import com.example.edutute.core.util.AttendanceDateUtils
import com.example.edutute.core.util.userMessage
import com.example.edutute.domain.access.RoleAccessManager
import com.example.edutute.domain.model.AttendanceStatus
import com.example.edutute.domain.model.ClassAttendanceDraft
import com.example.edutute.domain.model.ClassAttendanceEntry
import com.example.edutute.domain.model.ClassSection
import com.example.edutute.domain.model.ClassSectionDraft
import com.example.edutute.domain.model.FacultyMember
import com.example.edutute.domain.model.Student
import com.example.edutute.domain.model.Subject
import com.example.edutute.domain.model.TeacherAssignmentDraft
import com.example.edutute.domain.repository.AcademicRepository
import com.example.edutute.domain.repository.AttendanceRepository
import com.example.edutute.domain.repository.FacultyRepository
import com.example.edutute.domain.repository.StudentRepository
import com.example.edutute.domain.repository.SubjectRepository
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ClassSubjectItem(
    val subjectId: String,
    val subjectName: String,
    val subjectCode: String,
    val teacherId: String = "",
    val teacherName: String = "",
)

data class ClassAttendanceDetailState(
    val selectedDate: String = AttendanceDateUtils.todayStorageDate(),
    val hasSearchedDate: Boolean = false,
    val hasRecordForSelectedDate: Boolean = false,
    val isEditMode: Boolean = false,
    val entries: List<ClassAttendanceEntry> = emptyList(),
    val resultMessage: String = "",
    val isBusy: Boolean = false,
) {
    val totalStudents: Int
        get() = entries.size

    val presentCount: Int
        get() = entries.count { it.status == AttendanceStatus.PRESENT.name }

    val absentCount: Int
        get() = totalStudents - presentCount

    val attendancePercentage: Int
        get() = if (totalStudents == 0) {
            0
        } else {
            ((presentCount * 100.0) / totalStudents).roundToInt()
        }
}

data class ClassDetailsScreenData(
    val classSection: ClassSection,
    val faculty: List<FacultyMember>,
    val assignedStudents: List<Student>,
    val availableStudents: List<Student>,
    val assignedSubjects: List<ClassSubjectItem>,
    val availableSubjects: List<Subject>,
    val canManageClass: Boolean = true,
    val canRectifyAttendance: Boolean = true,
    val attendance: ClassAttendanceDetailState = ClassAttendanceDetailState(),
)

class ClassDetailsViewModel(
    private val academicRepository: AcademicRepository,
    private val facultyRepository: FacultyRepository,
    private val studentRepository: StudentRepository,
    private val subjectRepository: SubjectRepository,
    private val attendanceRepository: AttendanceRepository,
    private val roleAccessManager: RoleAccessManager,
) : ViewModel() {

    private val mutableState = MutableStateFlow<UiState<ClassDetailsScreenData>>(UiState.Loading)
    val state: StateFlow<UiState<ClassDetailsScreenData>> = mutableState.asStateFlow()

    private val mutableActionState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val actionState: StateFlow<UiState<Unit>> = mutableActionState.asStateFlow()

    private val mutableAttendanceActionState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val attendanceActionState: StateFlow<UiState<String>> = mutableAttendanceActionState.asStateFlow()

    private var classSectionId: String = ""
    private var loadedData: ClassDetailsScreenData? = null

    fun load(classSectionId: String) {
        if (classSectionId.isBlank()) {
            mutableState.value = UiState.Error("Class could not be found.")
            return
        }
        this.classSectionId = classSectionId
        viewModelScope.launch {
            mutableState.value = UiState.Loading
            mutableState.value = try {
                val data = loadData(classSectionId)
                loadedData = data
                UiState.Success(data)
            } catch (throwable: Throwable) {
                UiState.Error(throwable.userMessage("Unable to load class details."))
            }
        }
    }

    fun saveClassTeacher(facultyId: String) = performAction {
        val current = requireLoadedData()
        academicRepository.saveClassSection(
            ClassSectionDraft(
                id = current.classSection.id,
                classId = current.classSection.classId,
                sectionId = current.classSection.sectionId,
                classTeacherId = facultyId,
                coClassTeacherId = current.classSection.coClassTeacherId,
            ),
        )
    }

    fun addStudents(studentIds: List<String>) = performAction {
        val current = requireLoadedData()
        studentRepository.assignStudentsToClassSection(
            studentIds = studentIds,
            classSectionId = current.classSection.id,
        )
    }

    fun removeStudent(studentId: String) = performAction {
        val current = requireLoadedData()
        studentRepository.removeStudentFromClassSection(
            studentId = studentId,
            classSectionId = current.classSection.id,
        )
    }

    fun addSubject(subjectId: String) = performAction {
        val current = requireLoadedData()
        academicRepository.saveTeacherAssignment(
            TeacherAssignmentDraft(
                classSectionId = current.classSection.id,
                subjectId = subjectId,
                facultyId = "",
            ),
        )
    }

    fun updateSubjectTeacher(subjectId: String, facultyId: String) = performAction {
        val current = requireLoadedData()
        academicRepository.saveTeacherAssignment(
            TeacherAssignmentDraft(
                classSectionId = current.classSection.id,
                subjectId = subjectId,
                facultyId = facultyId,
            ),
        )
    }

    fun removeSubject(subjectId: String) = performAction {
        val current = requireLoadedData()
        academicRepository.removeTeacherAssignment(
            classSectionId = current.classSection.id,
            subjectId = subjectId,
        )
    }

    fun deleteClassSection() {
        val current = requireLoadedData()
        viewModelScope.launch {
            mutableActionState.value = UiState.Loading
            mutableActionState.value = try {
                academicRepository.deleteClassSection(current.classSection.id)
                UiState.Success(Unit)
            } catch (throwable: Throwable) {
                UiState.Error(throwable.userMessage("Unable to delete class."))
            }
        }
    }

    fun clearActionState() {
        mutableActionState.value = UiState.Idle
    }

    fun updateAttendanceDate(date: String) {
        updateScreenData { screen ->
            screen.copy(
                attendance = screen.attendance.copy(
                    selectedDate = date,
                    hasSearchedDate = false,
                    hasRecordForSelectedDate = false,
                    isEditMode = false,
                    entries = emptyList(),
                    resultMessage = "",
                ),
            )
        }
    }

    fun searchAttendanceByDate() {
        val current = requireLoadedData()
        viewModelScope.launch {
            updateAttendanceState(current.attendance.copy(isBusy = true, isEditMode = false, resultMessage = ""))
            try {
                val record = attendanceRepository.getClassAttendanceRecord(
                    classSectionId = current.classSection.id,
                    attendanceDate = current.attendance.selectedDate,
                )
                updateAttendanceState(
                    current.attendance.copy(
                        hasSearchedDate = true,
                        hasRecordForSelectedDate = record != null,
                        isEditMode = false,
                        entries = record?.entries?.sortedForAttendanceSheet().orEmpty(),
                        resultMessage = if (record == null) {
                            "Attendance was not marked for this class on this date."
                        } else {
                            if (current.canRectifyAttendance) {
                                "Class attendance record loaded. Results are read-only until you tap Edit."
                            } else {
                                "Class attendance record loaded in view-only mode."
                            }
                        },
                        isBusy = false,
                    ),
                )
            } catch (throwable: Throwable) {
                updateAttendanceState(
                    current.attendance.copy(
                        hasSearchedDate = true,
                        hasRecordForSelectedDate = false,
                        isEditMode = false,
                        entries = emptyList(),
                        resultMessage = throwable.userMessage("Unable to load class attendance for this date."),
                        isBusy = false,
                    ),
                )
            }
        }
    }

    fun enterAttendanceEditMode() {
        if (!roleAccessManager.currentContext().isHeadmaster) {
            mutableAttendanceActionState.value = UiState.Error("Faculty users can view class attendance but cannot edit past records.")
            return
        }
        updateScreenData { screen ->
            if (!screen.attendance.hasRecordForSelectedDate || screen.attendance.entries.isEmpty()) return@updateScreenData screen
            screen.copy(
                attendance = screen.attendance.copy(
                    isEditMode = true,
                    resultMessage = "Edit mode enabled. Review the register carefully before saving.",
                ),
            )
        }
    }

    fun updateAttendanceStatus(
        studentId: String,
        status: AttendanceStatus,
    ) {
        updateScreenData { screen ->
            if (!screen.attendance.isEditMode) return@updateScreenData screen
            screen.copy(
                attendance = screen.attendance.copy(
                    entries = screen.attendance.entries.map { entry ->
                        if (entry.studentId == studentId) {
                            entry.copy(status = status.name)
                        } else {
                            entry
                        }
                    },
                ),
            )
        }
    }

    fun saveAttendanceRectification() {
        val current = requireLoadedData()
        if (!current.canRectifyAttendance) {
            mutableAttendanceActionState.value = UiState.Error("Faculty users can view class attendance but cannot edit past records.")
            return
        }
        if (!current.attendance.hasRecordForSelectedDate) {
            mutableAttendanceActionState.value = UiState.Error("Attendance was not marked for this class on this date.")
            return
        }
        if (!current.attendance.isEditMode) {
            mutableAttendanceActionState.value = UiState.Error("Tap Edit before changing class attendance for this date.")
            return
        }
        if (current.attendance.entries.isEmpty()) {
            mutableAttendanceActionState.value = UiState.Error("No student attendance is available for this date.")
            return
        }

        viewModelScope.launch {
            updateAttendanceState(current.attendance.copy(isBusy = true))
            mutableAttendanceActionState.value = UiState.Loading
            try {
                val savedRecord = attendanceRepository.saveClassAttendance(
                    ClassAttendanceDraft(
                        classSectionId = current.classSection.id,
                        attendanceDate = current.attendance.selectedDate,
                        entries = current.attendance.entries,
                    ),
                )
                updateAttendanceState(
                    current.attendance.copy(
                        hasSearchedDate = true,
                        hasRecordForSelectedDate = true,
                        isEditMode = false,
                        entries = savedRecord.entries.sortedForAttendanceSheet(),
                        resultMessage = "Attendance updated for ${AttendanceDateUtils.toDisplayDate(current.attendance.selectedDate)}. Results are read-only again until you tap Edit.",
                        isBusy = false,
                    ),
                )
                mutableAttendanceActionState.value = UiState.Success("Class attendance updated.")
            } catch (throwable: Throwable) {
                updateAttendanceState(
                    current.attendance.copy(
                        resultMessage = throwable.userMessage("Unable to update class attendance."),
                        isBusy = false,
                    ),
                )
                mutableAttendanceActionState.value = UiState.Error(
                    throwable.userMessage("Unable to update class attendance."),
                )
            }
        }
    }

    fun clearAttendanceActionState() {
        mutableAttendanceActionState.value = UiState.Idle
    }

    private fun performAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            mutableActionState.value = UiState.Loading
            mutableActionState.value = try {
                action()
                val refreshed = loadData(classSectionId, loadedData?.attendance)
                loadedData = refreshed
                mutableState.value = UiState.Success(refreshed)
                UiState.Success(Unit)
            } catch (throwable: Throwable) {
                UiState.Error(throwable.userMessage("Unable to update class details."))
            }
        }
    }

    private suspend fun loadData(
        classSectionId: String,
        attendanceState: ClassAttendanceDetailState? = null,
    ): ClassDetailsScreenData {
        val isFacultyUser = roleAccessManager.currentContext().isFaculty
        val classSection = academicRepository.getClassSectionById(classSectionId)
            ?: throw IllegalArgumentException("Class could not be found.")
        val faculty = if (isFacultyUser) {
            emptyList()
        } else {
            facultyRepository.listFaculty().sortedBy(FacultyMember::fullNameLower)
        }
        val students = studentRepository.listStudents()
        val allSubjects = if (isFacultyUser) emptyList() else subjectRepository.listSubjects()
        val teacherAssignments = academicRepository.listTeacherAssignments(classSectionId)
        val assignedStudents = students
            .filter { it.currentClassSectionId == classSectionId }
            .sortedBy(Student::fullNameLower)
        val availableStudents = if (isFacultyUser) {
            emptyList()
        } else {
            students
                .filter { it.currentClassSectionId.isBlank() }
                .sortedBy(Student::fullNameLower)
        }
        val assignedSubjects = teacherAssignments
            .map {
                ClassSubjectItem(
                    subjectId = it.subjectId,
                    subjectName = it.subjectName,
                    subjectCode = it.subjectCode,
                    teacherId = it.facultyId,
                    teacherName = it.facultyName,
                )
            }
            .sortedBy(ClassSubjectItem::subjectName)
        val availableSubjects = allSubjects
            .filter { subject -> assignedSubjects.none { it.subjectId == subject.id } }
            .sortedBy(Subject::nameLower)

        return ClassDetailsScreenData(
            classSection = classSection,
            faculty = faculty,
            assignedStudents = assignedStudents,
            availableStudents = availableStudents,
            assignedSubjects = assignedSubjects,
            availableSubjects = availableSubjects,
            canManageClass = !isFacultyUser,
            canRectifyAttendance = !isFacultyUser,
            attendance = attendanceState ?: ClassAttendanceDetailState(),
        )
    }

    private fun requireLoadedData(): ClassDetailsScreenData =
        loadedData ?: throw IllegalStateException("Class details are not loaded yet.")

    private fun updateAttendanceState(attendance: ClassAttendanceDetailState) {
        updateScreenData { screen -> screen.copy(attendance = attendance) }
    }

    private inline fun updateScreenData(transform: (ClassDetailsScreenData) -> ClassDetailsScreenData) {
        val current = mutableState.value as? UiState.Success ?: return
        val updated = transform(current.data)
        loadedData = updated
        mutableState.value = UiState.Success(updated)
    }
}

private fun List<ClassAttendanceEntry>.sortedForAttendanceSheet(): List<ClassAttendanceEntry> = sortedWith(
    compareBy<ClassAttendanceEntry>({ it.rollNumber.toIntOrNull() ?: Int.MAX_VALUE }, { it.fullName.lowercase() }),
)
