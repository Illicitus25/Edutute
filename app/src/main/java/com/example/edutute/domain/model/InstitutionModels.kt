package com.example.edutute.domain.model

import com.example.edutute.core.util.InstitutionSetupUtils

data class Institution(
    val id: String = "",
    val name: String = "",
    val headmasterUid: String = "",
    val addressLine1: String = "",
    val addressLine2: String = "",
    val city: String = "",
    val state: String = "",
    val postalCode: String = "",
    val contactEmail: String = "",
    val contactPhone: String = "",
    val currentSessionId: String = "",
    val setupStatus: String = SetupStatus.NOT_STARTED.name,
    val status: String = RecordStatus.ACTIVE.name,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class AcademicSession(
    val id: String = "",
    val name: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val status: String = SessionStatus.ACTIVE.name,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class DashboardSummary(
    val totalStudents: Int = 0,
    val totalFaculty: Int = 0,
    val totalClassSections: Int = 0,
    val totalSubjects: Int = 0,
    val recentActivity: List<String> = emptyList(),
    val upcomingExams: List<String> = emptyList(),
)

data class InstitutionDraft(
    val name: String = "",
    val addressLine1: String = "",
    val addressLine2: String = "",
    val city: String = "",
    val state: String = "",
    val postalCode: String = "",
    val contactEmail: String = "",
    val contactPhone: String = "",
    val currentAcademicSessionName: String = InstitutionSetupUtils.currentAcademicSessionLabel(),
)
