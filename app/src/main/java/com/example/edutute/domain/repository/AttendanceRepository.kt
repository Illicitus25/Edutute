package com.example.edutute.domain.repository

import com.example.edutute.domain.model.ClassAttendanceDraft
import com.example.edutute.domain.model.ClassAttendanceRecord
import com.example.edutute.domain.model.ClassAttendanceRoster
import com.example.edutute.domain.model.AttendanceStatus
import com.example.edutute.domain.model.FacultyAttendanceDraft
import com.example.edutute.domain.model.FacultyAttendanceEntry
import com.example.edutute.domain.model.FacultyAttendanceRecord
import com.example.edutute.domain.model.FacultyAttendanceSummary
import com.example.edutute.domain.model.StudentAttendanceSummary

interface AttendanceRepository {
    suspend fun loadClassRoster(classSectionId: String): ClassAttendanceRoster

    suspend fun getClassAttendanceRecord(
        classSectionId: String,
        attendanceDate: String,
    ): ClassAttendanceRecord?

    suspend fun saveClassAttendance(draft: ClassAttendanceDraft): ClassAttendanceRecord

    suspend fun getStudentAttendanceSummary(
        studentId: String,
        classSectionId: String,
    ): StudentAttendanceSummary

    suspend fun rectifyStudentAttendance(
        studentId: String,
        classSectionId: String,
        attendanceDate: String,
        status: AttendanceStatus,
    ): ClassAttendanceRecord

    suspend fun loadFacultyRoster(): List<FacultyAttendanceEntry>

    suspend fun getFacultyAttendanceRecord(attendanceDate: String): FacultyAttendanceRecord?

    suspend fun saveFacultyAttendance(draft: FacultyAttendanceDraft): FacultyAttendanceRecord

    suspend fun getFacultyAttendanceSummary(facultyId: String): FacultyAttendanceSummary

    suspend fun rectifyFacultyAttendance(
        facultyId: String,
        attendanceDate: String,
        status: AttendanceStatus,
    ): FacultyAttendanceRecord
}
