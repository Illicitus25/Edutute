package com.example.edutute.data.repository

import com.example.edutute.core.util.awaitCount
import com.example.edutute.domain.access.RoleAccessManager
import com.example.edutute.data.auth.SessionStore
import com.example.edutute.data.firestore.FirestoreDataSource
import com.example.edutute.domain.model.DashboardSummary
import com.example.edutute.domain.model.RecordStatus
import com.example.edutute.domain.repository.DashboardRepository

class FirestoreDashboardRepository(
    private val dataSource: FirestoreDataSource,
    private val sessionStore: SessionStore,
    private val roleAccessManager: RoleAccessManager,
) : DashboardRepository {

    override suspend fun getDashboardSummary(): DashboardSummary {
        roleAccessManager.requireHeadmaster("view the institution-wide dashboard")
        val institutionId = sessionStore.requireInstitutionId()
        val academicSessionId = sessionStore.requireAcademicSessionId()

        val facultyCount = dataSource.faculty(institutionId)
            .whereEqualTo("status", RecordStatus.ACTIVE.name)
            .awaitCount()
        val studentCount = dataSource.students(institutionId)
            .whereEqualTo("status", RecordStatus.ACTIVE.name)
            .whereEqualTo("currentSessionId", academicSessionId)
            .awaitCount()
        val classSectionCount = dataSource.classSections(institutionId, academicSessionId)
            .whereEqualTo("status", RecordStatus.ACTIVE.name)
            .awaitCount()
        val subjectCount = dataSource.subjects(institutionId)
            .whereEqualTo("status", RecordStatus.ACTIVE.name)
            .awaitCount()

        return DashboardSummary(
            totalStudents = studentCount,
            totalFaculty = facultyCount,
            totalClassSections = classSectionCount,
            totalSubjects = subjectCount,
            recentActivity = listOf(
                "Faculty, class, and student updates will appear here next.",
                "Audit log support is reserved for the next phase.",
            ),
            upcomingExams = listOf(
                "Exam scheduling module is prepared in the schema but not yet enabled.",
                "Use this space as a placeholder for upcoming exam dates.",
            ),
        )
    }
}
