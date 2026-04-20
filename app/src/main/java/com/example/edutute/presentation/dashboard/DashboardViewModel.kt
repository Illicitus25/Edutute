package com.example.edutute.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edutute.core.ui.UiState
import com.example.edutute.core.util.userMessage
import com.example.edutute.domain.access.RoleAccessManager
import com.example.edutute.domain.model.UserRole
import com.example.edutute.domain.repository.DashboardRepository
import com.example.edutute.domain.repository.AcademicRepository
import com.example.edutute.domain.repository.AttendanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardMetricCard(
    val label: String,
    val value: String,
)

data class DashboardScreenData(
    val isFacultyUser: Boolean = false,
    val heroBadge: String = "",
    val heroTitle: String = "",
    val heroSubtitle: String = "",
    val cards: List<DashboardMetricCard> = emptyList(),
    val summaryTitle: String = "",
    val summaryBody: String = "",
    val showFacultyAction: Boolean = true,
    val showStudentsAction: Boolean = true,
    val showClassesAction: Boolean = true,
    val showSubjectsAction: Boolean = true,
    val showInstitutionAction: Boolean = true,
    val showAttendanceAction: Boolean = true,
)

class DashboardViewModel(
    private val dashboardRepository: DashboardRepository,
    private val attendanceRepository: AttendanceRepository,
    private val academicRepository: AcademicRepository,
    private val roleAccessManager: RoleAccessManager,
) : ViewModel() {

    private val mutableState = MutableStateFlow<UiState<DashboardScreenData>>(UiState.Loading)
    val state: StateFlow<UiState<DashboardScreenData>> = mutableState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            mutableState.value = UiState.Loading
            mutableState.value = try {
                UiState.Success(
                    if (roleAccessManager.currentContext().userRole == UserRole.FACULTY) {
                        loadFacultyDashboard()
                    } else {
                        loadHeadmasterDashboard()
                    },
                )
            } catch (throwable: Throwable) {
                UiState.Error(throwable.userMessage("Unable to load dashboard."))
            }
        }
    }

    private suspend fun loadHeadmasterDashboard(): DashboardScreenData {
        val summary = dashboardRepository.getDashboardSummary()
        val missingSteps = buildList {
            if (summary.totalFaculty == 0) add("Add your first faculty member.")
            if (summary.totalClassSections == 0) add("Create your first class and section.")
            if (summary.totalSubjects == 0) add("Add the subjects taught in your institution.")
            if (summary.totalStudents == 0) add("Enroll students after class-sections are ready.")
        }

        return DashboardScreenData(
            isFacultyUser = false,
            heroBadge = "Live Overview",
            heroTitle = "Dashboard",
            heroSubtitle = "A high-contrast operational view of your institution, built for faster daily decisions.",
            cards = listOf(
                DashboardMetricCard(label = "Total Students", value = summary.totalStudents.toString()),
                DashboardMetricCard(label = "Total Faculty", value = summary.totalFaculty.toString()),
                DashboardMetricCard(label = "Class Sections", value = summary.totalClassSections.toString()),
                DashboardMetricCard(label = "Subjects", value = summary.totalSubjects.toString()),
            ),
            summaryTitle = if (missingSteps.isEmpty()) "Ready for daily use" else "Finish your core setup",
            summaryBody = if (missingSteps.isEmpty()) {
                "Your core records are in place. Use the shortcuts below to keep the institution profile current and run attendance."
            } else {
                missingSteps.joinToString("\n• ", prefix = "• ")
            },
        )
    }

    private suspend fun loadFacultyDashboard(): DashboardScreenData {
        val facultyId = roleAccessManager.requireFacultyId()
        val attendanceSummary = attendanceRepository.getFacultyAttendanceSummary(facultyId)
        val assignedClasses = academicRepository.listClassSections()
        val assignedSubjects = academicRepository.listTeacherAssignmentsForFaculty(facultyId)
            .map { it.subjectId }
            .distinct()
            .count()
        val attendancePercentage = if (attendanceSummary.daysHeld == 0) {
            0
        } else {
            ((attendanceSummary.daysAttended * 100.0) / attendanceSummary.daysHeld).toInt()
        }

        return DashboardScreenData(
            isFacultyUser = true,
            heroBadge = "Personal Overview",
            heroTitle = "Faculty Dashboard",
            heroSubtitle = "A focused teaching workspace with only the classes, attendance, and institution details relevant to you.",
            cards = listOf(
                DashboardMetricCard(label = "Overall Attendance", value = "$attendancePercentage%"),
                DashboardMetricCard(label = "Days Attended", value = attendanceSummary.daysAttended.toString()),
                DashboardMetricCard(label = "Days Held", value = attendanceSummary.daysHeld.toString()),
                DashboardMetricCard(label = "Classes Assigned", value = assignedClasses.size.toString()),
            ),
            summaryTitle = "Your teaching scope",
            summaryBody = buildString {
                append("Subjects assigned: ")
                append(assignedSubjects)
                append("\n")
                append("Faculty attendance on this dashboard is view-only.")
            },
            showFacultyAction = false,
            showStudentsAction = false,
            showClassesAction = true,
            showSubjectsAction = false,
            showInstitutionAction = true,
            showAttendanceAction = true,
        )
    }
}
