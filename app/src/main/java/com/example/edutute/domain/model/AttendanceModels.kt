package com.example.edutute.domain.model

data class ClassAttendanceEntry(
    val studentId: String = "",
    val admissionNumber: String = "",
    val fullName: String = "",
    val rollNumber: String = "",
    val status: String = AttendanceStatus.PRESENT.name,
)

data class ClassAttendanceRecord(
    val id: String = "",
    val institutionId: String = "",
    val sessionId: String = "",
    val classSectionId: String = "",
    val classId: String = "",
    val className: String = "",
    val sectionId: String = "",
    val sectionName: String = "",
    val classSectionName: String = "",
    val attendanceDate: String = "",
    val dateKey: String = "",
    val totalStudents: Int = 0,
    val presentCount: Int = 0,
    val absentCount: Int = 0,
    val entries: List<ClassAttendanceEntry> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class FacultyAttendanceEntry(
    val facultyId: String = "",
    val employeeCode: String = "",
    val fullName: String = "",
    val qualification: String = "",
    val status: String = AttendanceStatus.PRESENT.name,
)

data class FacultyAttendanceRecord(
    val id: String = "",
    val institutionId: String = "",
    val attendanceDate: String = "",
    val dateKey: String = "",
    val totalFaculty: Int = 0,
    val presentCount: Int = 0,
    val absentCount: Int = 0,
    val entries: List<FacultyAttendanceEntry> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class ClassAttendanceRoster(
    val classSection: ClassSection,
    val entries: List<ClassAttendanceEntry> = emptyList(),
)

data class ClassAttendanceDraft(
    val classSectionId: String = "",
    val attendanceDate: String = "",
    val entries: List<ClassAttendanceEntry> = emptyList(),
)

data class FacultyAttendanceDraft(
    val attendanceDate: String = "",
    val entries: List<FacultyAttendanceEntry> = emptyList(),
)

data class StudentAttendanceSummary(
    val daysAttended: Int = 0,
    val daysHeld: Int = 0,
) {
    val attendancePercentage: Int
        get() = if (daysHeld == 0) {
            0
        } else {
            ((daysAttended * 100.0) / daysHeld).toInt()
        }
}

data class FacultyAttendanceSummary(
    val daysAttended: Int = 0,
    val daysHeld: Int = 0,
) {
    val attendancePercentage: Int
        get() = if (daysHeld == 0) {
            0
        } else {
            ((daysAttended * 100.0) / daysHeld).toInt()
        }
}

fun String.toAttendanceStatus(): AttendanceStatus =
    AttendanceStatus.entries.firstOrNull { it.name == this } ?: AttendanceStatus.PRESENT
