package com.example.edutute.domain.model

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val fullName: String = "",
    val displayName: String = "",
    val phoneNumber: String = "",
    val institutionId: String = "",
    val institutionalId: String = "",
    val userType: String = UserRole.HEADMASTER.name.lowercase(),
    val role: String = UserRole.HEADMASTER.name,
    val linkedFacultyId: String = "",
    val qualification: String = "",
    val joiningDate: String = "",
    val joiningDateTimestamp: Long = 0L,
    val accountStatus: String = RecordStatus.ACTIVE.name,
    val lastLoginAt: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class AuthRegistrationRequest(
    val userRole: UserRole = UserRole.HEADMASTER,
    val institutionalId: String = "",
    val fullName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val qualification: String = "",
    val password: String = "",
)

data class AppSession(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val userRole: String = UserRole.HEADMASTER.name,
    val institutionId: String? = null,
    val linkedFacultyId: String? = null,
    val institutionName: String = "",
    val currentSessionId: String? = null,
    val requiresInstitutionSetup: Boolean = false,
)

sealed interface SessionState {
    data object Loading : SessionState
    data object Unauthenticated : SessionState
    data class Authenticated(val session: AppSession) : SessionState
    data class SetupRequired(val session: AppSession) : SessionState
    data class Unauthorized(val message: String) : SessionState
}
