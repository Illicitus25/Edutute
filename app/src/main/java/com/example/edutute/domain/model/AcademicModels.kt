package com.example.edutute.domain.model

data class FacultyMember(
    val id: String = "",
    val institutionId: String = "",
    val authUid: String = "",
    val accountStatus: String = RecordStatus.INVITED.name,
    val fullName: String = "",
    val fullNameLower: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val employeeCode: String = "",
    val qualification: String = "",
    val joiningDate: String = "",
    val inviteSentAt: Long = 0L,
    val status: String = RecordStatus.ACTIVE.name,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class SchoolClass(
    val id: String = "",
    val institutionId: String = "",
    val name: String = "",
    val nameLower: String = "",
    val displayOrder: Int = 0,
    val status: String = RecordStatus.ACTIVE.name,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class Section(
    val id: String = "",
    val institutionId: String = "",
    val name: String = "",
    val nameLower: String = "",
    val displayOrder: Int = 0,
    val status: String = RecordStatus.ACTIVE.name,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class ClassSection(
    val id: String = "",
    val institutionId: String = "",
    val sessionId: String = "",
    val classId: String = "",
    val className: String = "",
    val classOrder: Int = 0,
    val sectionId: String = "",
    val sectionName: String = "",
    val sectionOrder: Int = 0,
    val displayName: String = "",
    val displayNameLower: String = "",
    val classTeacherId: String = "",
    val classTeacherName: String = "",
    val coClassTeacherId: String = "",
    val coClassTeacherName: String = "",
    val status: String = RecordStatus.ACTIVE.name,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class Subject(
    val id: String = "",
    val institutionId: String = "",
    val code: String = "",
    val normalizedCode: String = "",
    val name: String = "",
    val nameLower: String = "",
    val shortName: String = "",
    val subjectType: String = SubjectType.THEORETICAL.name,
    val status: String = RecordStatus.ACTIVE.name,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class Student(
    val id: String = "",
    val institutionId: String = "",
    val admissionNumber: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val fullName: String = "",
    val fullNameLower: String = "",
    val gender: String = Gender.UNSPECIFIED.name,
    val dateOfBirth: String = "",
    val guardianName: String = "",
    val guardianPhone: String = "",
    val email: String = "",
    val addressLine1: String = "",
    val addressLine2: String = "",
    val city: String = "",
    val state: String = "",
    val postalCode: String = "",
    val status: String = RecordStatus.ACTIVE.name,
    val currentSessionId: String = "",
    val currentClassSectionId: String = "",
    val currentClassSectionName: String = "",
    val currentRollNumber: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class StudentAssignment(
    val studentId: String = "",
    val institutionId: String = "",
    val sessionId: String = "",
    val classSectionId: String = "",
    val classSectionName: String = "",
    val classId: String = "",
    val sectionId: String = "",
    val rollNumber: String = "",
    val status: String = RecordStatus.ACTIVE.name,
    val assignedAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class TeacherAssignment(
    val id: String = "",
    val institutionId: String = "",
    val sessionId: String = "",
    val facultyId: String = "",
    val facultyName: String = "",
    val subjectId: String = "",
    val subjectName: String = "",
    val subjectCode: String = "",
    val classSectionId: String = "",
    val classSectionName: String = "",
    val status: String = RecordStatus.ACTIVE.name,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class FacultyDraft(
    val id: String = "",
    val fullName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val qualification: String = "",
    val joiningDate: String = "",
)

data class FacultySaveResult(
    val faculty: FacultyMember,
    val activationEmailSent: Boolean = false,
)

data class SchoolClassDraft(
    val id: String = "",
    val name: String = "",
    val displayOrder: Int = 0,
)

data class SectionDraft(
    val id: String = "",
    val name: String = "",
    val displayOrder: Int = 0,
)

data class ClassSectionDraft(
    val id: String = "",
    val classId: String = "",
    val sectionId: String = "",
    val classTeacherId: String = "",
    val coClassTeacherId: String = "",
)

data class TeacherAssignmentDraft(
    val classSectionId: String = "",
    val subjectId: String = "",
    val facultyId: String = "",
)

data class SubjectDraft(
    val id: String = "",
    val name: String = "",
    val subjectType: String = SubjectType.THEORETICAL.name,
)

data class StudentDraft(
    val id: String = "",
    val admissionNumber: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val gender: String = Gender.UNSPECIFIED.name,
    val dateOfBirth: String = "",
    val guardianName: String = "",
    val guardianPhone: String = "",
    val email: String = "",
    val addressLine1: String = "",
    val addressLine2: String = "",
    val city: String = "",
    val state: String = "",
    val postalCode: String = "",
    val classSectionId: String = "",
    val rollNumber: String = "",
)
