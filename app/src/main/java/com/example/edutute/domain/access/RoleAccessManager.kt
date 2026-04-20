package com.example.edutute.domain.access

import com.example.edutute.domain.model.UserRole

data class UserAccessContext(
    val userRole: UserRole = UserRole.HEADMASTER,
    val institutionId: String? = null,
    val currentSessionId: String? = null,
    val linkedFacultyId: String? = null,
) {
    val isHeadmaster: Boolean
        get() = userRole == UserRole.HEADMASTER

    val isFaculty: Boolean
        get() = userRole == UserRole.FACULTY
}

interface RoleAccessManager {
    fun currentContext(): UserAccessContext

    fun requireHeadmaster(action: String)

    fun requireFacultyId(): String

    fun requireFacultyRecordAccess(facultyId: String)

    suspend fun allowedClassSectionIds(): Set<String>

    suspend fun classTeacherClassSectionIds(): Set<String>

    suspend fun requireClassSectionAccess(classSectionId: String)

    suspend fun requireClassTeacherClassSectionAccess(classSectionId: String)
}
