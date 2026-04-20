package com.example.edutute.presentation.attendance

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
import com.example.edutute.domain.model.FacultyAttendanceDraft
import com.example.edutute.domain.model.FacultyAttendanceEntry
import com.example.edutute.domain.model.SchoolClass
import com.example.edutute.domain.model.Section
import com.example.edutute.domain.model.toAttendanceStatus
import com.example.edutute.domain.repository.AcademicRepository
import com.example.edutute.domain.repository.AttendanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AttendanceMode {
    MARK,
    VIEW,
}

enum class AttendanceTarget {
    CLASS,
    FACULTY,
}

enum class AttendanceMessageTone {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
}

data class AttendanceMessage(
    val text: String = "",
    val tone: AttendanceMessageTone = AttendanceMessageTone.INFO,
)

data class ClassAttendanceEntryUi(
    val studentId: String,
    val admissionNumber: String,
    val fullName: String,
    val rollNumber: String,
    val status: AttendanceStatus,
)

data class FacultyAttendanceEntryUi(
    val facultyId: String,
    val employeeCode: String,
    val fullName: String,
    val qualification: String,
    val status: AttendanceStatus,
)

data class ClassAttendancePanelState(
    val selectedClassId: String = "",
    val selectedSectionId: String = "",
    val selectedDate: String = AttendanceDateUtils.todayStorageDate(),
    val selectedClassSectionName: String = "",
    val entries: List<ClassAttendanceEntryUi> = emptyList(),
    val hasLoadedData: Boolean = false,
    val isExistingRecord: Boolean = false,
    val isEditMode: Boolean = false,
    val isWorking: Boolean = false,
    val message: AttendanceMessage = AttendanceMessage(),
)

data class FacultyAttendancePanelState(
    val selectedDate: String = AttendanceDateUtils.todayStorageDate(),
    val entries: List<FacultyAttendanceEntryUi> = emptyList(),
    val hasLoadedData: Boolean = false,
    val isExistingRecord: Boolean = false,
    val isEditMode: Boolean = false,
    val isWorking: Boolean = false,
    val message: AttendanceMessage = AttendanceMessage(),
)

data class AttendanceScreenData(
    val classes: List<SchoolClass> = emptyList(),
    val sections: List<Section> = emptyList(),
    val classSections: List<ClassSection> = emptyList(),
    val markableClassSectionIds: Set<String> = emptySet(),
    val mode: AttendanceMode = AttendanceMode.MARK,
    val target: AttendanceTarget = AttendanceTarget.CLASS,
    val isFacultyUser: Boolean = false,
    val canAccessFacultyAttendance: Boolean = true,
    val canRectifySavedRecords: Boolean = true,
    val classPanel: ClassAttendancePanelState = ClassAttendancePanelState(),
    val facultyPanel: FacultyAttendancePanelState = FacultyAttendancePanelState(),
)

class AttendanceViewModel(
    private val attendanceRepository: AttendanceRepository,
    private val academicRepository: AcademicRepository,
    private val roleAccessManager: RoleAccessManager,
) : ViewModel() {

    private val mutableState = MutableStateFlow<UiState<AttendanceScreenData>>(UiState.Loading)
    val state: StateFlow<UiState<AttendanceScreenData>> = mutableState.asStateFlow()

    private val mutableActionState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val actionState: StateFlow<UiState<String>> = mutableActionState.asStateFlow()

    fun ensureLoaded() {
        if (mutableState.value is UiState.Success) return
        load()
    }

    fun load() {
        viewModelScope.launch {
            mutableState.value = UiState.Loading
            mutableState.value = try {
                UiState.Success(
                    AttendanceScreenData(
                        classes = academicRepository.listClasses(),
                        sections = academicRepository.listSections(),
                        classSections = academicRepository.listClassSections(),
                        markableClassSectionIds = if (roleAccessManager.currentContext().isFaculty) {
                            roleAccessManager.classTeacherClassSectionIds()
                        } else {
                            emptySet()
                        },
                        isFacultyUser = roleAccessManager.currentContext().isFaculty,
                        canAccessFacultyAttendance = roleAccessManager.currentContext().isHeadmaster,
                        canRectifySavedRecords = roleAccessManager.currentContext().isHeadmaster,
                    ),
                )
            } catch (throwable: Throwable) {
                UiState.Error(throwable.userMessage("Unable to load attendance references."))
            }
        }
    }

    fun updateMode(mode: AttendanceMode) {
        updateSuccessState { data ->
            val updatedData = data.copy(
                mode = mode,
                classPanel = data.classPanel.copy(isEditMode = false),
                facultyPanel = data.facultyPanel.copy(isEditMode = false),
            )
            if (updatedData.isFacultyUser && mode == AttendanceMode.MARK) {
                updatedData.withValidFacultyMarkSelection()
            } else {
                updatedData
            }
        }
    }

    fun updateTarget(target: AttendanceTarget) {
        updateSuccessState { data ->
            data.copy(
                target = if (data.isFacultyUser) AttendanceTarget.CLASS else target,
                classPanel = data.classPanel.copy(isEditMode = false),
                facultyPanel = data.facultyPanel.copy(isEditMode = false),
            )
        }
    }

    fun updateClassSelection(classId: String) {
        updateSuccessState { data ->
            val retainedSectionId = data.classPanel.selectedSectionId.takeIf {
                data.classSections.any { classSection ->
                    classSection.classId == classId && classSection.sectionId == it
                }
            }.orEmpty()
            data.copy(
                classPanel = data.classPanel.reset(
                    selectedClassId = classId,
                    selectedSectionId = retainedSectionId,
                ),
            )
        }
    }

    fun updateSectionSelection(sectionId: String) {
        updateSuccessState { data ->
            data.copy(
                classPanel = data.classPanel.reset(selectedSectionId = sectionId),
            )
        }
    }

    fun updateClassDate(date: String) {
        updateSuccessState { data ->
            data.copy(
                classPanel = data.classPanel.reset(selectedDate = date),
            )
        }
    }

    fun updateFacultyDate(date: String) {
        updateSuccessState { data ->
            data.copy(
                facultyPanel = data.facultyPanel.reset(selectedDate = date),
            )
        }
    }

    fun loadCurrentSelection() {
        when (currentScreenData()?.target) {
            AttendanceTarget.CLASS -> loadClassAttendance()
            AttendanceTarget.FACULTY -> loadFacultyAttendance()
            null -> Unit
        }
    }

    fun updateClassAttendanceStatus(
        studentId: String,
        status: AttendanceStatus,
    ) {
        updateSuccessState { data ->
            if (!canEditClassAttendance(data)) return@updateSuccessState data
            data.copy(
                classPanel = data.classPanel.copy(
                    entries = data.classPanel.entries.map { entry ->
                        if (entry.studentId == studentId) entry.copy(status = status) else entry
                    },
                ),
            )
        }
    }

    fun updateFacultyAttendanceStatus(
        facultyId: String,
        status: AttendanceStatus,
    ) {
        updateSuccessState { data ->
            if (!canEditFacultyAttendance(data)) return@updateSuccessState data
            data.copy(
                facultyPanel = data.facultyPanel.copy(
                    entries = data.facultyPanel.entries.map { entry ->
                        if (entry.facultyId == facultyId) entry.copy(status = status) else entry
                    },
                ),
            )
        }
    }

    fun enterEditMode() {
        updateSuccessState { data ->
            if (!data.canRectifySavedRecords) {
                mutableActionState.value = UiState.Error("Faculty users can view saved class attendance but cannot edit past records.")
                return@updateSuccessState data
            }
            when (data.target) {
                AttendanceTarget.CLASS -> {
                    if (data.mode != AttendanceMode.VIEW || data.classPanel.entries.isEmpty()) return@updateSuccessState data
                    data.copy(
                        classPanel = data.classPanel.copy(
                            isEditMode = true,
                            message = AttendanceMessage(
                                text = "Edit mode enabled. Review the register carefully before saving.",
                                tone = AttendanceMessageTone.INFO,
                            ),
                        ),
                    )
                }

                AttendanceTarget.FACULTY -> {
                    if (data.mode != AttendanceMode.VIEW || data.facultyPanel.entries.isEmpty()) return@updateSuccessState data
                    data.copy(
                        facultyPanel = data.facultyPanel.copy(
                            isEditMode = true,
                            message = AttendanceMessage(
                                text = "Edit mode enabled. Review the roster carefully before saving.",
                                tone = AttendanceMessageTone.INFO,
                            ),
                        ),
                    )
                }
            }
        }
    }

    fun markAllPresent() {
        markAll(AttendanceStatus.PRESENT)
    }

    fun markAllAbsent() {
        markAll(AttendanceStatus.ABSENT)
    }

    fun saveCurrentSelection() {
        when (currentScreenData()?.target) {
            AttendanceTarget.CLASS -> saveClassAttendance()
            AttendanceTarget.FACULTY -> saveFacultyAttendance()
            null -> Unit
        }
    }

    fun clearActionState() {
        mutableActionState.value = UiState.Idle
    }

    private fun loadClassAttendance() {
        val snapshot = currentScreenData() ?: return
        val panel = snapshot.classPanel
        if (panel.selectedClassId.isBlank()) {
            updateClassPanelMessage("Select a class before loading attendance.", AttendanceMessageTone.WARNING)
            return
        }
        if (panel.selectedSectionId.isBlank()) {
            updateClassPanelMessage("Select a section before loading attendance.", AttendanceMessageTone.WARNING)
            return
        }
        if (!AttendanceDateUtils.isValidStorageDate(panel.selectedDate)) {
            updateClassPanelMessage("Choose a valid attendance date.", AttendanceMessageTone.WARNING)
            return
        }

        val classSection = resolveClassSection(snapshot, panel.selectedClassId, panel.selectedSectionId)
        if (classSection == null) {
            updateClassPanelMessage(
                "The selected class and section are not linked in the active academic session.",
                AttendanceMessageTone.WARNING,
            )
            return
        }
        if (snapshot.isFacultyUser && snapshot.mode == AttendanceMode.MARK &&
            classSection.id !in snapshot.markableClassSectionIds
        ) {
            updateClassPanelMessage(
                "Only the class teacher can mark attendance for this class-section.",
                AttendanceMessageTone.WARNING,
            )
            return
        }

        viewModelScope.launch {
            updateSuccessState { data ->
                data.copy(classPanel = data.classPanel.copy(isWorking = true, message = AttendanceMessage()))
            }

            try {
                val existingRecord = attendanceRepository.getClassAttendanceRecord(classSection.id, panel.selectedDate)
                val nextPanel = when {
                    existingRecord != null && snapshot.mode == AttendanceMode.MARK && snapshot.isFacultyUser -> panel.copy(
                        selectedClassSectionName = classSection.displayName,
                        entries = emptyList(),
                        hasLoadedData = false,
                        isExistingRecord = true,
                        isEditMode = false,
                        isWorking = false,
                        message = AttendanceMessage(
                            text = "Attendance has already been submitted for this class-section on ${AttendanceDateUtils.toDisplayDate(panel.selectedDate)}. Faculty can submit only once per day.",
                            tone = AttendanceMessageTone.WARNING,
                        ),
                    )

                    existingRecord != null -> panel.copy(
                        selectedClassSectionName = classSection.displayName,
                        entries = existingRecord.entries.map(::toClassEntryUi),
                        hasLoadedData = true,
                        isExistingRecord = true,
                        isEditMode = false,
                        isWorking = false,
                        message = AttendanceMessage(
                            text = if (snapshot.mode == AttendanceMode.MARK && snapshot.canRectifySavedRecords) {
                                "Class attendance already exists for ${AttendanceDateUtils.toDisplayDate(panel.selectedDate)}. It is now ready for rectification."
                            } else if (snapshot.mode == AttendanceMode.MARK) {
                                "Class attendance already exists for ${AttendanceDateUtils.toDisplayDate(panel.selectedDate)} and is view-only for faculty users."
                            } else {
                                if (snapshot.canRectifySavedRecords) {
                                    "Class attendance record loaded. Results are read-only until you tap Edit."
                                } else {
                                    "Class attendance record loaded in view-only mode."
                                }
                            },
                            tone = AttendanceMessageTone.INFO,
                        ),
                    )

                    snapshot.mode == AttendanceMode.VIEW -> panel.copy(
                        selectedClassSectionName = classSection.displayName,
                        entries = emptyList(),
                        hasLoadedData = true,
                        isExistingRecord = false,
                        isEditMode = false,
                        isWorking = false,
                        message = AttendanceMessage(
                            text = "No class attendance record was found for the selected filters.",
                            tone = AttendanceMessageTone.WARNING,
                        ),
                    )

                    else -> {
                        val roster = attendanceRepository.loadClassRoster(classSection.id)
                        panel.copy(
                            selectedClassSectionName = roster.classSection.displayName,
                            entries = roster.entries.map(::toClassEntryUi),
                            hasLoadedData = true,
                            isExistingRecord = false,
                            isEditMode = false,
                            isWorking = false,
                            message = AttendanceMessage(
                                text = if (roster.entries.isEmpty()) {
                                    "No students found in this class and section."
                                } else {
                                    ""
                                },
                                tone = if (roster.entries.isEmpty()) {
                                    AttendanceMessageTone.WARNING
                                } else {
                                    AttendanceMessageTone.INFO
                                },
                            ),
                        )
                    }
                }

                updateSuccessState { data ->
                    data.copy(classPanel = nextPanel)
                }
            } catch (throwable: Throwable) {
                updateSuccessState { data ->
                    data.copy(
                        classPanel = data.classPanel.copy(
                            isWorking = false,
                            message = AttendanceMessage(
                                throwable.userMessage("Unable to load class attendance."),
                                AttendanceMessageTone.ERROR,
                            ),
                        ),
                    )
                }
            }
        }
    }

    private fun loadFacultyAttendance() {
        val snapshot = currentScreenData() ?: return
        if (!snapshot.canAccessFacultyAttendance) {
            mutableActionState.value = UiState.Error("Faculty users cannot access faculty attendance.")
            return
        }
        val panel = snapshot.facultyPanel
        if (!AttendanceDateUtils.isValidStorageDate(panel.selectedDate)) {
            updateFacultyPanelMessage("Choose a valid attendance date.", AttendanceMessageTone.WARNING)
            return
        }

        viewModelScope.launch {
            updateSuccessState { data ->
                data.copy(facultyPanel = data.facultyPanel.copy(isWorking = true, message = AttendanceMessage()))
            }

            try {
                val existingRecord = attendanceRepository.getFacultyAttendanceRecord(panel.selectedDate)
                val nextPanel = when {
                    existingRecord != null -> panel.copy(
                        entries = existingRecord.entries.map(::toFacultyEntryUi),
                        hasLoadedData = true,
                        isExistingRecord = true,
                        isEditMode = false,
                        isWorking = false,
                        message = AttendanceMessage(
                            text = if (snapshot.mode == AttendanceMode.MARK) {
                                "Faculty attendance already exists for ${AttendanceDateUtils.toDisplayDate(panel.selectedDate)}. It is now ready for rectification."
                            } else {
                                "Faculty attendance record loaded. Results are read-only until you tap Edit."
                            },
                            tone = AttendanceMessageTone.INFO,
                        ),
                    )

                    snapshot.mode == AttendanceMode.VIEW -> panel.copy(
                        entries = emptyList(),
                        hasLoadedData = true,
                        isExistingRecord = false,
                        isEditMode = false,
                        isWorking = false,
                        message = AttendanceMessage(
                            text = "No faculty attendance record was found for the selected date.",
                            tone = AttendanceMessageTone.WARNING,
                        ),
                    )

                    else -> {
                        val roster = attendanceRepository.loadFacultyRoster()
                        panel.copy(
                            entries = roster.map(::toFacultyEntryUi),
                            hasLoadedData = true,
                            isExistingRecord = false,
                            isEditMode = false,
                            isWorking = false,
                            message = AttendanceMessage(
                                text = if (roster.isEmpty()) {
                                    "No active faculty records are available for attendance."
                                } else {
                                    "Faculty roster loaded for ${AttendanceDateUtils.toDisplayDate(panel.selectedDate)}."
                                },
                                tone = if (roster.isEmpty()) {
                                    AttendanceMessageTone.WARNING
                                } else {
                                    AttendanceMessageTone.INFO
                                },
                            ),
                        )
                    }
                }

                updateSuccessState { data ->
                    data.copy(facultyPanel = nextPanel)
                }
            } catch (throwable: Throwable) {
                updateSuccessState { data ->
                    data.copy(
                        facultyPanel = data.facultyPanel.copy(
                            isWorking = false,
                            message = AttendanceMessage(
                                throwable.userMessage("Unable to load faculty attendance."),
                                AttendanceMessageTone.ERROR,
                            ),
                        ),
                    )
                }
            }
        }
    }

    private fun saveClassAttendance() {
        val snapshot = currentScreenData() ?: return
        val panel = snapshot.classPanel
        if (panel.entries.isEmpty()) {
            mutableActionState.value = UiState.Error("Load a class roster before saving attendance.")
            return
        }
        if (!canEditClassAttendance(snapshot)) {
            mutableActionState.value = UiState.Error(
                if (snapshot.isFacultyUser && panel.isExistingRecord) {
                    "Attendance already exists for this date and faculty users cannot modify past records."
                } else {
                    "Tap Edit before changing this class attendance record."
                },
            )
            return
        }
        if (!AttendanceDateUtils.isValidStorageDate(panel.selectedDate)) {
            mutableActionState.value = UiState.Error("Choose a valid attendance date.")
            return
        }

        val classSection = resolveClassSection(snapshot, panel.selectedClassId, panel.selectedSectionId)
        if (classSection == null) {
            mutableActionState.value = UiState.Error("Selected class-section could not be resolved.")
            return
        }

        viewModelScope.launch {
            val wasExisting = panel.isExistingRecord
            updateSuccessState { data ->
                data.copy(classPanel = data.classPanel.copy(isWorking = true))
            }

            try {
                val savedRecord = attendanceRepository.saveClassAttendance(
                    ClassAttendanceDraft(
                        classSectionId = classSection.id,
                        attendanceDate = panel.selectedDate,
                        entries = panel.entries.map { entry ->
                            ClassAttendanceEntry(
                                studentId = entry.studentId,
                                admissionNumber = entry.admissionNumber,
                                fullName = entry.fullName,
                                rollNumber = entry.rollNumber,
                                status = entry.status.name,
                            )
                        },
                    ),
                )

                updateSuccessState { data ->
                    data.copy(
                        classPanel = if (snapshot.mode == AttendanceMode.MARK) {
                            data.classPanel.reset(
                                selectedClassId = data.classPanel.selectedClassId,
                                selectedSectionId = data.classPanel.selectedSectionId,
                                selectedDate = data.classPanel.selectedDate,
                            )
                        } else {
                            data.classPanel.copy(
                                selectedClassSectionName = savedRecord.classSectionName,
                                entries = savedRecord.entries.map(::toClassEntryUi),
                                hasLoadedData = true,
                                isExistingRecord = true,
                                isEditMode = false,
                                isWorking = false,
                                message = AttendanceMessage(
                                    text = "Class attendance updated. Results are read-only again until you tap Edit.",
                                    tone = AttendanceMessageTone.SUCCESS,
                                ),
                            )
                        },
                    )
                }
                mutableActionState.value = UiState.Success(
                    if (wasExisting) "Class attendance updated." else "Class attendance saved.",
                )
            } catch (throwable: Throwable) {
                updateSuccessState { data ->
                    data.copy(
                        classPanel = data.classPanel.copy(
                            isWorking = false,
                            message = AttendanceMessage(
                                throwable.userMessage("Unable to save class attendance."),
                                AttendanceMessageTone.ERROR,
                            ),
                        ),
                    )
                }
                mutableActionState.value = UiState.Error(throwable.userMessage("Unable to save class attendance."))
            }
        }
    }

    private fun saveFacultyAttendance() {
        val snapshot = currentScreenData() ?: return
        val panel = snapshot.facultyPanel
        if (panel.entries.isEmpty()) {
            mutableActionState.value = UiState.Error("Load the faculty roster before saving attendance.")
            return
        }
        if (!canEditFacultyAttendance(snapshot)) {
            mutableActionState.value = UiState.Error("Tap Edit before changing this faculty attendance record.")
            return
        }
        if (!AttendanceDateUtils.isValidStorageDate(panel.selectedDate)) {
            mutableActionState.value = UiState.Error("Choose a valid attendance date.")
            return
        }

        viewModelScope.launch {
            val wasExisting = panel.isExistingRecord
            updateSuccessState { data ->
                data.copy(facultyPanel = data.facultyPanel.copy(isWorking = true))
            }

            try {
                val savedRecord = attendanceRepository.saveFacultyAttendance(
                    FacultyAttendanceDraft(
                        attendanceDate = panel.selectedDate,
                        entries = panel.entries.map { entry ->
                            FacultyAttendanceEntry(
                                facultyId = entry.facultyId,
                                employeeCode = entry.employeeCode,
                                fullName = entry.fullName,
                                qualification = entry.qualification,
                                status = entry.status.name,
                            )
                        },
                    ),
                )

                updateSuccessState { data ->
                    data.copy(
                        facultyPanel = if (snapshot.mode == AttendanceMode.MARK) {
                            data.facultyPanel.reset(selectedDate = data.facultyPanel.selectedDate)
                        } else {
                            data.facultyPanel.copy(
                                entries = savedRecord.entries.map(::toFacultyEntryUi),
                                hasLoadedData = true,
                                isExistingRecord = true,
                                isEditMode = false,
                                isWorking = false,
                                message = AttendanceMessage(
                                    text = "Faculty attendance updated for ${AttendanceDateUtils.toDisplayDate(savedRecord.attendanceDate)}. Results are read-only again until you tap Edit.",
                                    tone = AttendanceMessageTone.SUCCESS,
                                ),
                            )
                        },
                    )
                }
                mutableActionState.value = UiState.Success(
                    if (wasExisting) "Faculty attendance updated." else "Faculty attendance saved.",
                )
            } catch (throwable: Throwable) {
                updateSuccessState { data ->
                    data.copy(
                        facultyPanel = data.facultyPanel.copy(
                            isWorking = false,
                            message = AttendanceMessage(
                                throwable.userMessage("Unable to save faculty attendance."),
                                AttendanceMessageTone.ERROR,
                            ),
                        ),
                    )
                }
                mutableActionState.value = UiState.Error(throwable.userMessage("Unable to save faculty attendance."))
            }
        }
    }

    private fun markAll(status: AttendanceStatus) {
        updateSuccessState { data ->
            when (data.target) {
                AttendanceTarget.CLASS -> {
                    if (!canEditClassAttendance(data)) return@updateSuccessState data
                    data.copy(
                        classPanel = data.classPanel.copy(
                            entries = data.classPanel.entries.map { it.copy(status = status) },
                        ),
                    )
                }

                AttendanceTarget.FACULTY -> {
                    if (!canEditFacultyAttendance(data)) return@updateSuccessState data
                    data.copy(
                        facultyPanel = data.facultyPanel.copy(
                            entries = data.facultyPanel.entries.map { it.copy(status = status) },
                        ),
                    )
                }
            }
        }
    }

    private fun canEditClassAttendance(data: AttendanceScreenData): Boolean =
        when {
            data.mode == AttendanceMode.MARK && data.isFacultyUser -> !data.classPanel.isExistingRecord
            data.mode == AttendanceMode.MARK -> true
            else -> data.classPanel.isEditMode && data.canRectifySavedRecords
        }

    private fun canEditFacultyAttendance(data: AttendanceScreenData): Boolean =
        data.canAccessFacultyAttendance && (data.mode == AttendanceMode.MARK || data.facultyPanel.isEditMode)

    private fun updateClassPanelMessage(
        message: String,
        tone: AttendanceMessageTone,
    ) {
        updateSuccessState { data ->
            data.copy(classPanel = data.classPanel.copy(message = AttendanceMessage(message, tone)))
        }
    }

    private fun updateFacultyPanelMessage(
        message: String,
        tone: AttendanceMessageTone,
    ) {
        updateSuccessState { data ->
            data.copy(facultyPanel = data.facultyPanel.copy(message = AttendanceMessage(message, tone)))
        }
    }

    private fun resolveClassSection(
        data: AttendanceScreenData,
        classId: String,
        sectionId: String,
    ): ClassSection? = data.classSections.firstOrNull { classSection ->
        classSection.classId == classId && classSection.sectionId == sectionId
    }

    private fun toClassEntryUi(entry: ClassAttendanceEntry): ClassAttendanceEntryUi = ClassAttendanceEntryUi(
        studentId = entry.studentId,
        admissionNumber = entry.admissionNumber,
        fullName = entry.fullName,
        rollNumber = entry.rollNumber,
        status = entry.status.toAttendanceStatus(),
    )

    private fun toFacultyEntryUi(entry: FacultyAttendanceEntry): FacultyAttendanceEntryUi = FacultyAttendanceEntryUi(
        facultyId = entry.facultyId,
        employeeCode = entry.employeeCode,
        fullName = entry.fullName,
        qualification = entry.qualification,
        status = entry.status.toAttendanceStatus(),
    )

    private fun currentScreenData(): AttendanceScreenData? = (mutableState.value as? UiState.Success)?.data

    private inline fun updateSuccessState(transform: (AttendanceScreenData) -> AttendanceScreenData) {
        val currentState = mutableState.value as? UiState.Success ?: return
        mutableState.value = UiState.Success(transform(currentState.data))
    }
}

private fun AttendanceScreenData.withValidFacultyMarkSelection(): AttendanceScreenData {
    val selectedClassSection = classSections.firstOrNull { classSection ->
        classSection.classId == classPanel.selectedClassId &&
            classSection.sectionId == classPanel.selectedSectionId
    }
    return if (selectedClassSection != null && selectedClassSection.id in markableClassSectionIds) {
        this
    } else {
        copy(
            classPanel = classPanel.reset(
                selectedClassId = "",
                selectedSectionId = "",
                selectedDate = classPanel.selectedDate,
            ),
        )
    }
}

private fun ClassAttendancePanelState.reset(
    selectedClassId: String = this.selectedClassId,
    selectedSectionId: String = this.selectedSectionId,
    selectedDate: String = this.selectedDate,
): ClassAttendancePanelState = copy(
    selectedClassId = selectedClassId,
    selectedSectionId = selectedSectionId,
    selectedDate = selectedDate,
    selectedClassSectionName = "",
    entries = emptyList(),
    hasLoadedData = false,
    isExistingRecord = false,
    isEditMode = false,
    isWorking = false,
    message = AttendanceMessage(),
)

private fun FacultyAttendancePanelState.reset(
    selectedDate: String = this.selectedDate,
): FacultyAttendancePanelState = copy(
    selectedDate = selectedDate,
    entries = emptyList(),
    hasLoadedData = false,
    isExistingRecord = false,
    isEditMode = false,
    isWorking = false,
    message = AttendanceMessage(),
)
