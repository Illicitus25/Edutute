package com.example.edutute.domain.model

enum class UserRole {
    HEADMASTER,
    FACULTY,
}

enum class RecordStatus {
    ACTIVE,
    INVITED,
    INACTIVE,
    ARCHIVED,
}

enum class SetupStatus {
    NOT_STARTED,
    COMPLETE,
}

enum class SessionStatus {
    ACTIVE,
    ARCHIVED,
}

enum class SubjectType {
    THEORETICAL,
    PRACTICAL,
}

enum class Gender {
    MALE,
    FEMALE,
    OTHER,
    UNSPECIFIED,
}

enum class AttendanceStatus {
    PRESENT,
    ABSENT,
}
