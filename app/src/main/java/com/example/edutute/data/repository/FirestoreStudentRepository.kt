package com.example.edutute.data.repository

import com.example.edutute.core.util.ValidationUtils
import com.example.edutute.domain.access.RoleAccessManager
import com.example.edutute.data.auth.SessionStore
import com.example.edutute.data.firestore.FirestoreDataSource
import com.example.edutute.domain.model.ClassSection
import com.example.edutute.domain.model.RecordStatus
import com.example.edutute.domain.model.Student
import com.example.edutute.domain.model.StudentAssignment
import com.example.edutute.domain.model.StudentDraft
import com.example.edutute.domain.repository.LocationValidationRepository
import com.example.edutute.domain.repository.StudentRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.TimeZone

class FirestoreStudentRepository(
    private val firestore: FirebaseFirestore,
    private val dataSource: FirestoreDataSource,
    private val sessionStore: SessionStore,
    private val roleAccessManager: RoleAccessManager,
    private val locationValidationRepository: LocationValidationRepository,
) : StudentRepository {

    override suspend fun listStudents(): List<Student> {
        val institutionId = sessionStore.requireInstitutionId()
        val students = dataSource.students(institutionId)
            .whereEqualTo("status", RecordStatus.ACTIVE.name)
            .orderBy("fullNameLower")
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(Student::class.java) }
        if (roleAccessManager.currentContext().isHeadmaster) return students

        val allowedClassSectionIds = roleAccessManager.allowedClassSectionIds()
        return students.filter { it.currentClassSectionId in allowedClassSectionIds }
    }

    override suspend fun searchStudents(query: String): List<Student> {
        val normalizedQuery = ValidationUtils.normalize(query)
        return listStudents().filter { student ->
            normalizedQuery.isBlank() ||
                student.fullNameLower.contains(normalizedQuery) ||
                student.admissionNumber.lowercase().contains(normalizedQuery) ||
                student.currentClassSectionName.lowercase().contains(normalizedQuery)
        }
    }

    override suspend fun getStudentById(id: String): Student? {
        val institutionId = sessionStore.requireInstitutionId()
        val student = dataSource.studentDocument(institutionId, id).get().await().toObject(Student::class.java)
        if (student == null || roleAccessManager.currentContext().isHeadmaster) {
            return student
        }
        val allowedClassSectionIds = roleAccessManager.allowedClassSectionIds()
        return student.takeIf { it.currentClassSectionId in allowedClassSectionIds }
    }

    override suspend fun saveStudent(draft: StudentDraft): Student {
        roleAccessManager.requireHeadmaster("create or edit students")
        ValidationUtils.requireNotBlank(draft.firstName, "First name")
        ValidationUtils.requireNotBlank(draft.guardianName, "Guardian name")
        ValidationUtils.requireNotBlank(draft.guardianPhone, "Guardian phone")
        if (!ValidationUtils.isValidDayMonthYear(draft.dateOfBirth)) {
            throw IllegalArgumentException("Date of birth must be in dd/mm/yyyy format.")
        }
        if (!ValidationUtils.isValidEmail(draft.email)) {
            throw IllegalArgumentException("Student email is invalid.")
        }

        val institutionId = sessionStore.requireInstitutionId()
        val academicSessionId = sessionStore.requireAcademicSessionId()
        val verifiedAddress = if (
            draft.city.isNotBlank() ||
            draft.state.isNotBlank() ||
            draft.postalCode.isNotBlank()
        ) {
            locationValidationRepository.validateIndianAddress(
                city = draft.city,
                state = draft.state,
                postalCode = draft.postalCode,
            )
        } else {
            null
        }
        val document = if (draft.id.isBlank()) {
            dataSource.students(institutionId).document()
        } else {
            dataSource.studentDocument(institutionId, draft.id)
        }

        val studentAssignmentRef = dataSource.studentAssignmentDocument(institutionId, academicSessionId, document.id)
        val classSectionRef = draft.classSectionId.takeIf { it.isNotBlank() }
            ?.let { dataSource.classSectionDocument(institutionId, academicSessionId, it) }
        val currentTime = System.currentTimeMillis()

        firestore.runTransaction { transaction ->
            val existingStudent = transaction.get(document).toObject(Student::class.java)
            val existingAssignment = transaction.get(studentAssignmentRef).toObject(StudentAssignment::class.java)
            val selectedClassSection = classSectionRef?.let { transaction.get(it).toObject(ClassSection::class.java) }

            if (draft.classSectionId.isNotBlank() && selectedClassSection == null) {
                throw IllegalArgumentException("Selected class-section could not be found.")
            }

            val newRollKey = if (draft.classSectionId.isNotBlank() && draft.rollNumber.isNotBlank()) {
                ValidationUtils.normalizeCode(draft.rollNumber)
            } else {
                ""
            }
            val oldRollKey = existingAssignment?.rollNumber?.takeIf { it.isNotBlank() }?.let(ValidationUtils::normalizeCode).orEmpty()

            if (draft.classSectionId.isNotBlank() && draft.rollNumber.isBlank()) {
                throw IllegalArgumentException("Roll number is required when assigning a student to a class-section.")
            }

            if (draft.classSectionId.isNotBlank()) {
                val newRollRef = dataSource.rollIndex(institutionId, academicSessionId, draft.classSectionId, newRollKey)
                val newRollHolder = transaction.get(newRollRef)
                if (newRollHolder.exists() && newRollHolder.getString("studentId") != document.id) {
                    throw IllegalArgumentException("Roll number is already assigned in this class-section.")
                }
                transaction.set(
                    newRollRef,
                    mapOf(
                        "studentId" to document.id,
                        "rollNumber" to draft.rollNumber.trim(),
                        "updatedAt" to currentTime,
                    ),
                )
            }

            if (
                existingAssignment != null &&
                (existingAssignment.classSectionId != draft.classSectionId || oldRollKey != newRollKey)
            ) {
                val oldRollRef = dataSource.rollIndex(
                    institutionId = institutionId,
                    sessionId = academicSessionId,
                    classSectionId = existingAssignment.classSectionId,
                    rollKey = oldRollKey,
                )
                transaction.delete(oldRollRef)
            }

            val fullName = listOf(draft.firstName.trim(), draft.lastName.trim())
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .trim()
            val admissionNumber = existingStudent?.admissionNumber?.takeIf { it.isNotBlank() }
                ?: generateAdmissionNumber(transaction, institutionId, currentTime)

            val student = Student(
                id = document.id,
                institutionId = institutionId,
                admissionNumber = admissionNumber,
                firstName = draft.firstName.trim(),
                lastName = draft.lastName.trim(),
                fullName = fullName,
                fullNameLower = ValidationUtils.normalize(fullName),
                gender = draft.gender,
                dateOfBirth = draft.dateOfBirth.trim(),
                guardianName = draft.guardianName.trim(),
                guardianPhone = draft.guardianPhone.trim(),
                email = draft.email.trim(),
                addressLine1 = draft.addressLine1.trim(),
                addressLine2 = draft.addressLine2.trim(),
                city = verifiedAddress?.city.orEmpty().ifBlank { draft.city.trim() },
                state = verifiedAddress?.state.orEmpty().ifBlank { draft.state.trim() },
                postalCode = verifiedAddress?.postalCode.orEmpty().ifBlank { draft.postalCode.trim() },
                status = existingStudent?.status ?: RecordStatus.ACTIVE.name,
                currentSessionId = if (draft.classSectionId.isNotBlank()) academicSessionId else "",
                currentClassSectionId = draft.classSectionId,
                currentClassSectionName = selectedClassSection?.displayName.orEmpty(),
                currentRollNumber = draft.rollNumber.trim(),
                createdAt = existingStudent?.createdAt ?: currentTime,
                updatedAt = currentTime,
            )

            transaction.set(document, student)

            if (draft.classSectionId.isBlank()) {
                transaction.delete(studentAssignmentRef)
            } else {
                val assignment = StudentAssignment(
                    studentId = document.id,
                    institutionId = institutionId,
                    sessionId = academicSessionId,
                    classSectionId = draft.classSectionId,
                    classSectionName = selectedClassSection?.displayName.orEmpty(),
                    classId = selectedClassSection?.classId.orEmpty(),
                    sectionId = selectedClassSection?.sectionId.orEmpty(),
                    rollNumber = draft.rollNumber.trim(),
                    status = RecordStatus.ACTIVE.name,
                    assignedAt = existingAssignment?.assignedAt ?: currentTime,
                    updatedAt = currentTime,
                )
                transaction.set(studentAssignmentRef, assignment)
            }
            student
        }.await()

        return dataSource.studentDocument(institutionId, document.id).get().await().toObject(Student::class.java)
            ?: throw IllegalStateException("Failed to save student.")
    }

    override suspend fun assignStudentsToClassSection(
        studentIds: List<String>,
        classSectionId: String,
    ) {
        roleAccessManager.requireHeadmaster("assign students to classes")
        ValidationUtils.requireNotBlank(classSectionId, "Class")
        if (studentIds.isEmpty()) {
            throw IllegalArgumentException("Select at least one student.")
        }

        val institutionId = sessionStore.requireInstitutionId()
        val academicSessionId = sessionStore.requireAcademicSessionId()
        val classSection = dataSource.classSectionDocument(institutionId, academicSessionId, classSectionId)
            .get()
            .await()
            .toObject(ClassSection::class.java)
            ?: throw IllegalArgumentException("Selected class-section could not be found.")

        val currentStudents = listStudents().filter { it.currentClassSectionId == classSectionId }
        val selectedStudents = studentIds
            .distinct()
            .map { studentId ->
                getStudentById(studentId) ?: throw IllegalArgumentException("Student was not found.")
            }
            .filter { it.currentClassSectionId.isBlank() || it.currentClassSectionId == classSectionId }

        if (selectedStudents.size != studentIds.distinct().size) {
            throw IllegalArgumentException("Only unassigned students can be added to this class.")
        }

        val finalRoster = (currentStudents + selectedStudents)
            .associateBy(Student::id)
            .values
            .sortedBy(Student::fullNameLower)

        persistRoster(
            institutionId = institutionId,
            academicSessionId = academicSessionId,
            classSection = classSection,
            previousRoster = currentStudents,
            finalRoster = finalRoster,
            removedStudent = null,
        )
    }

    override suspend fun removeStudentFromClassSection(
        studentId: String,
        classSectionId: String,
    ) {
        roleAccessManager.requireHeadmaster("remove students from classes")
        ValidationUtils.requireNotBlank(classSectionId, "Class")
        ValidationUtils.requireNotBlank(studentId, "Student")

        val institutionId = sessionStore.requireInstitutionId()
        val academicSessionId = sessionStore.requireAcademicSessionId()
        val classSection = dataSource.classSectionDocument(institutionId, academicSessionId, classSectionId)
            .get()
            .await()
            .toObject(ClassSection::class.java)
            ?: throw IllegalArgumentException("Selected class-section could not be found.")
        val currentStudents = listStudents().filter { it.currentClassSectionId == classSectionId }
        val removedStudent = currentStudents.firstOrNull { it.id == studentId }
            ?: throw IllegalArgumentException("Student is not assigned to this class.")
        val finalRoster = currentStudents
            .filterNot { it.id == studentId }
            .sortedBy(Student::fullNameLower)

        persistRoster(
            institutionId = institutionId,
            academicSessionId = academicSessionId,
            classSection = classSection,
            previousRoster = currentStudents,
            finalRoster = finalRoster,
            removedStudent = removedStudent,
        )
    }

    private fun generateAdmissionNumber(
        transaction: Transaction,
        institutionId: String,
        currentTime: Long,
    ): String {
        val yearCode = admissionYearCode(currentTime)
        val counterRef = dataSource.institutionCounterDocument(institutionId, "studentAdmissions_$yearCode")
        val nextSequence = (transaction.get(counterRef).getLong("lastSequence") ?: 0L) + 1L

        transaction.set(
            counterRef,
            mapOf(
                "counterId" to "studentAdmissions_$yearCode",
                "institutionId" to institutionId,
                "yearCode" to yearCode,
                "lastSequence" to nextSequence,
                "updatedAt" to currentTime,
            ),
        )

        return buildString {
            append('1')
            append(yearCode)
            append(nextSequence.toString().padStart(5, '0'))
        }
    }

    private fun admissionYearCode(currentTime: Long): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
            timeInMillis = currentTime
        }
        return (calendar.get(Calendar.YEAR) % 100).toString().padStart(2, '0')
    }

    override suspend fun deleteStudent(id: String) {
        roleAccessManager.requireHeadmaster("delete students")
        val institutionId = sessionStore.requireInstitutionId()
        val academicSessionId = sessionStore.requireAcademicSessionId()
        val studentRef = dataSource.studentDocument(institutionId, id)
        val assignmentRef = dataSource.studentAssignmentDocument(institutionId, academicSessionId, id)

        firestore.runTransaction { transaction ->
            val student = transaction.get(studentRef).toObject(Student::class.java)
                ?: throw IllegalArgumentException("Student was not found.")
            val assignment = transaction.get(assignmentRef).toObject(StudentAssignment::class.java)
            if (assignment != null && assignment.rollNumber.isNotBlank()) {
                val rollRef = dataSource.rollIndex(
                    institutionId = institutionId,
                    sessionId = academicSessionId,
                    classSectionId = assignment.classSectionId,
                    rollKey = ValidationUtils.normalizeCode(assignment.rollNumber),
                )
                transaction.delete(rollRef)
                transaction.delete(assignmentRef)
            }

            transaction.set(
                studentRef,
                student.copy(
                    status = RecordStatus.ARCHIVED.name,
                    currentSessionId = "",
                    currentClassSectionId = "",
                    currentClassSectionName = "",
                    currentRollNumber = "",
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            student
        }.await()
    }

    private suspend fun persistRoster(
        institutionId: String,
        academicSessionId: String,
        classSection: ClassSection,
        previousRoster: List<Student>,
        finalRoster: List<Student>,
        removedStudent: Student?,
    ) {
        val batch = firestore.batch()
        val currentTime = System.currentTimeMillis()

        previousRoster.forEach { student ->
            val currentRollKey = student.currentRollNumber.takeIf { it.isNotBlank() }?.let(ValidationUtils::normalizeCode)
            if (!currentRollKey.isNullOrBlank()) {
                batch.delete(
                    dataSource.rollIndex(
                        institutionId = institutionId,
                        sessionId = academicSessionId,
                        classSectionId = classSection.id,
                        rollKey = currentRollKey,
                    ),
                )
            }
        }

        removedStudent?.let { student ->
            batch.set(
                dataSource.studentDocument(institutionId, student.id),
                student.copy(
                    currentSessionId = "",
                    currentClassSectionId = "",
                    currentClassSectionName = "",
                    currentRollNumber = "",
                    updatedAt = currentTime,
                ),
            )
            batch.delete(dataSource.studentAssignmentDocument(institutionId, academicSessionId, student.id))
        }

        finalRoster.forEachIndexed { index, student ->
            val rollNumber = (index + 1).toString()
            batch.set(
                dataSource.studentDocument(institutionId, student.id),
                student.copy(
                    currentSessionId = academicSessionId,
                    currentClassSectionId = classSection.id,
                    currentClassSectionName = classSection.displayName,
                    currentRollNumber = rollNumber,
                    updatedAt = currentTime,
                ),
            )
            batch.set(
                dataSource.studentAssignmentDocument(institutionId, academicSessionId, student.id),
                StudentAssignment(
                    studentId = student.id,
                    institutionId = institutionId,
                    sessionId = academicSessionId,
                    classSectionId = classSection.id,
                    classSectionName = classSection.displayName,
                    classId = classSection.classId,
                    sectionId = classSection.sectionId,
                    rollNumber = rollNumber,
                    status = RecordStatus.ACTIVE.name,
                    assignedAt = currentTime,
                    updatedAt = currentTime,
                ),
            )
            batch.set(
                dataSource.rollIndex(
                    institutionId = institutionId,
                    sessionId = academicSessionId,
                    classSectionId = classSection.id,
                    rollKey = ValidationUtils.normalizeCode(rollNumber),
                ),
                mapOf(
                    "studentId" to student.id,
                    "rollNumber" to rollNumber,
                    "updatedAt" to currentTime,
                ),
            )
        }

        batch.commit().await()
    }
}
