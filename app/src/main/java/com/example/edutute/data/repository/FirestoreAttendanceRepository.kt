package com.example.edutute.data.repository

import com.example.edutute.core.util.AttendanceDateUtils
import com.example.edutute.core.util.ValidationUtils
import com.example.edutute.domain.access.RoleAccessManager
import com.example.edutute.data.auth.SessionStore
import com.example.edutute.data.firestore.FirestoreDataSource
import com.example.edutute.domain.model.AttendanceStatus
import com.example.edutute.domain.model.ClassAttendanceDraft
import com.example.edutute.domain.model.ClassAttendanceEntry
import com.example.edutute.domain.model.ClassAttendanceRecord
import com.example.edutute.domain.model.ClassAttendanceRoster
import com.example.edutute.domain.model.ClassSection
import com.example.edutute.domain.model.FacultyAttendanceDraft
import com.example.edutute.domain.model.FacultyAttendanceEntry
import com.example.edutute.domain.model.FacultyAttendanceRecord
import com.example.edutute.domain.model.FacultyAttendanceSummary
import com.example.edutute.domain.model.FacultyMember
import com.example.edutute.domain.model.RecordStatus
import com.example.edutute.domain.model.StudentAttendanceSummary
import com.example.edutute.domain.model.Student
import com.example.edutute.domain.repository.AttendanceRepository
import kotlinx.coroutines.tasks.await

class FirestoreAttendanceRepository(
    private val dataSource: FirestoreDataSource,
    private val sessionStore: SessionStore,
    private val roleAccessManager: RoleAccessManager,
) : AttendanceRepository {

    override suspend fun loadClassRoster(classSectionId: String): ClassAttendanceRoster {
        ValidationUtils.requireNotBlank(classSectionId, "Class")
        roleAccessManager.requireClassSectionAccess(classSectionId)
        if (roleAccessManager.currentContext().isFaculty) {
            roleAccessManager.requireClassTeacherClassSectionAccess(classSectionId)
        }

        val institutionId = sessionStore.requireInstitutionId()
        val academicSessionId = sessionStore.requireAcademicSessionId()
        val classSection = dataSource.classSectionDocument(institutionId, academicSessionId, classSectionId)
            .get()
            .await()
            .toObject(ClassSection::class.java)
            ?: throw IllegalArgumentException("Selected class-section could not be found.")

        val students = dataSource.students(institutionId)
            .whereEqualTo("status", RecordStatus.ACTIVE.name)
            .whereEqualTo("currentClassSectionId", classSectionId)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(Student::class.java) }
            .sortedWith(
                compareBy<Student>({ it.currentRollNumber.toIntOrNull() ?: Int.MAX_VALUE }, { it.fullNameLower }),
            )

        return ClassAttendanceRoster(
            classSection = classSection,
            entries = students.map { student ->
                ClassAttendanceEntry(
                    studentId = student.id,
                    admissionNumber = student.admissionNumber,
                    fullName = student.fullName,
                    rollNumber = student.currentRollNumber,
                    status = AttendanceStatus.PRESENT.name,
                )
            },
        )
    }

    override suspend fun getClassAttendanceRecord(
        classSectionId: String,
        attendanceDate: String,
    ): ClassAttendanceRecord? {
        ValidationUtils.requireNotBlank(classSectionId, "Class")
        validateAttendanceDate(attendanceDate)
        roleAccessManager.requireClassSectionAccess(classSectionId)

        val institutionId = sessionStore.requireInstitutionId()
        val academicSessionId = sessionStore.requireAcademicSessionId()
        return dataSource.classAttendanceDocument(
            institutionId = institutionId,
            sessionId = academicSessionId,
            attendanceId = classAttendanceId(classSectionId, attendanceDate),
        ).get().await().toObject(ClassAttendanceRecord::class.java)
    }

    override suspend fun saveClassAttendance(draft: ClassAttendanceDraft): ClassAttendanceRecord {
        ValidationUtils.requireNotBlank(draft.classSectionId, "Class")
        validateAttendanceDate(draft.attendanceDate)
        requireEntries(draft.entries.map(ClassAttendanceEntry::status), "student attendance")
        roleAccessManager.requireClassSectionAccess(draft.classSectionId)
        if (roleAccessManager.currentContext().isFaculty) {
            roleAccessManager.requireClassTeacherClassSectionAccess(draft.classSectionId)
        }

        val institutionId = sessionStore.requireInstitutionId()
        val academicSessionId = sessionStore.requireAcademicSessionId()
        val classSection = dataSource.classSectionDocument(institutionId, academicSessionId, draft.classSectionId)
            .get()
            .await()
            .toObject(ClassSection::class.java)
            ?: throw IllegalArgumentException("Selected class-section could not be found.")

        val document = dataSource.classAttendanceDocument(
            institutionId = institutionId,
            sessionId = academicSessionId,
            attendanceId = classAttendanceId(draft.classSectionId, draft.attendanceDate),
        )
        val existing = document.get().await().toObject(ClassAttendanceRecord::class.java)
        if (existing != null && roleAccessManager.currentContext().isFaculty) {
            throw IllegalStateException("Faculty can submit class attendance only once per class per day.")
        }
        val currentTime = System.currentTimeMillis()
        val normalizedEntries = draft.entries
            .distinctBy(ClassAttendanceEntry::studentId)
            .sortedWith(
                compareBy<ClassAttendanceEntry>({ it.rollNumber.toIntOrNull() ?: Int.MAX_VALUE }, { it.fullName.lowercase() }),
            )
            .map { entry ->
                entry.copy(
                    status = entry.status.uppercase(),
                    fullName = entry.fullName.trim(),
                    admissionNumber = entry.admissionNumber.trim(),
                    rollNumber = entry.rollNumber.trim(),
                )
            }

        val record = ClassAttendanceRecord(
            id = document.id,
            institutionId = institutionId,
            sessionId = academicSessionId,
            classSectionId = classSection.id,
            classId = classSection.classId,
            className = classSection.className,
            sectionId = classSection.sectionId,
            sectionName = classSection.sectionName,
            classSectionName = classSection.displayName,
            attendanceDate = draft.attendanceDate,
            dateKey = dateKey(draft.attendanceDate),
            totalStudents = normalizedEntries.size,
            presentCount = normalizedEntries.count { it.status == AttendanceStatus.PRESENT.name },
            absentCount = normalizedEntries.count { it.status == AttendanceStatus.ABSENT.name },
            entries = normalizedEntries,
            createdAt = existing?.createdAt ?: currentTime,
            updatedAt = currentTime,
        )

        document.set(record).await()
        return record
    }

    override suspend fun getStudentAttendanceSummary(
        studentId: String,
        classSectionId: String,
    ): StudentAttendanceSummary {
        ValidationUtils.requireNotBlank(studentId, "Student")
        ValidationUtils.requireNotBlank(classSectionId, "Class")
        roleAccessManager.requireClassSectionAccess(classSectionId)

        val institutionId = sessionStore.requireInstitutionId()
        val academicSessionId = sessionStore.requireAcademicSessionId()
        val records = dataSource.classAttendance(institutionId, academicSessionId)
            .whereEqualTo("classSectionId", classSectionId)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(ClassAttendanceRecord::class.java) }

        val studentEntriesByRecord = records.mapNotNull { record ->
            record.entries.firstOrNull { entry -> entry.studentId == studentId }?.let { entry ->
                record to entry
            }
        }

        return StudentAttendanceSummary(
            daysAttended = studentEntriesByRecord.count { (_, entry) ->
                entry.status == AttendanceStatus.PRESENT.name
            },
            daysHeld = studentEntriesByRecord.size,
        )
    }

    override suspend fun rectifyStudentAttendance(
        studentId: String,
        classSectionId: String,
        attendanceDate: String,
        status: AttendanceStatus,
    ): ClassAttendanceRecord {
        roleAccessManager.requireHeadmaster("rectify saved class attendance")
        ValidationUtils.requireNotBlank(studentId, "Student")
        ValidationUtils.requireNotBlank(classSectionId, "Class")
        validateAttendanceDate(attendanceDate)

        val record = getClassAttendanceRecord(classSectionId, attendanceDate)
            ?: throw IllegalArgumentException("Attendance was not marked for this class on this date.")

        val institutionId = sessionStore.requireInstitutionId()
        val student = dataSource.studentDocument(institutionId, studentId)
            .get()
            .await()
            .toObject(Student::class.java)
            ?: throw IllegalArgumentException("Student record was not found.")

        if (student.currentClassSectionId != classSectionId) {
            throw IllegalArgumentException("This student is not assigned to the selected class-section.")
        }

        val updatedEntries = if (record.entries.any { it.studentId == studentId }) {
            record.entries.map { entry ->
                if (entry.studentId == studentId) {
                    entry.copy(status = status.name)
                } else {
                    entry
                }
            }
        } else {
            record.entries + ClassAttendanceEntry(
                studentId = student.id,
                admissionNumber = student.admissionNumber,
                fullName = student.fullName,
                rollNumber = student.currentRollNumber,
                status = status.name,
            )
        }

        return saveClassAttendance(
            ClassAttendanceDraft(
                classSectionId = classSectionId,
                attendanceDate = attendanceDate,
                entries = updatedEntries,
            ),
        )
    }

    override suspend fun loadFacultyRoster(): List<FacultyAttendanceEntry> {
        roleAccessManager.requireHeadmaster("mark faculty attendance")
        val institutionId = sessionStore.requireInstitutionId()
        return dataSource.faculty(institutionId)
            .whereEqualTo("status", RecordStatus.ACTIVE.name)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(FacultyMember::class.java) }
            .sortedBy(FacultyMember::fullNameLower)
            .map { faculty ->
                FacultyAttendanceEntry(
                    facultyId = faculty.id,
                    employeeCode = faculty.employeeCode,
                    fullName = faculty.fullName,
                    qualification = faculty.qualification,
                    status = AttendanceStatus.PRESENT.name,
                )
            }
    }

    override suspend fun getFacultyAttendanceRecord(attendanceDate: String): FacultyAttendanceRecord? {
        roleAccessManager.requireHeadmaster("view faculty attendance")
        validateAttendanceDate(attendanceDate)

        val institutionId = sessionStore.requireInstitutionId()
        return dataSource.facultyAttendanceDocument(
            institutionId = institutionId,
            attendanceId = facultyAttendanceId(attendanceDate),
        ).get().await().toObject(FacultyAttendanceRecord::class.java)
    }

    override suspend fun saveFacultyAttendance(draft: FacultyAttendanceDraft): FacultyAttendanceRecord {
        roleAccessManager.requireHeadmaster("save faculty attendance")
        validateAttendanceDate(draft.attendanceDate)
        requireEntries(draft.entries.map(FacultyAttendanceEntry::status), "faculty attendance")

        val institutionId = sessionStore.requireInstitutionId()
        val document = dataSource.facultyAttendanceDocument(
            institutionId = institutionId,
            attendanceId = facultyAttendanceId(draft.attendanceDate),
        )
        val existing = document.get().await().toObject(FacultyAttendanceRecord::class.java)
        val currentTime = System.currentTimeMillis()
        val normalizedEntries = draft.entries
            .distinctBy(FacultyAttendanceEntry::facultyId)
            .sortedBy { it.fullName.lowercase() }
            .map { entry ->
                entry.copy(
                    status = entry.status.uppercase(),
                    employeeCode = entry.employeeCode.trim(),
                    fullName = entry.fullName.trim(),
                    qualification = entry.qualification.trim(),
                )
            }

        val record = FacultyAttendanceRecord(
            id = document.id,
            institutionId = institutionId,
            attendanceDate = draft.attendanceDate,
            dateKey = dateKey(draft.attendanceDate),
            totalFaculty = normalizedEntries.size,
            presentCount = normalizedEntries.count { it.status == AttendanceStatus.PRESENT.name },
            absentCount = normalizedEntries.count { it.status == AttendanceStatus.ABSENT.name },
            entries = normalizedEntries,
            createdAt = existing?.createdAt ?: currentTime,
            updatedAt = currentTime,
        )

        document.set(record).await()
        return record
    }

    override suspend fun getFacultyAttendanceSummary(facultyId: String): FacultyAttendanceSummary {
        roleAccessManager.requireFacultyRecordAccess(facultyId)
        ValidationUtils.requireNotBlank(facultyId, "Faculty")

        val institutionId = sessionStore.requireInstitutionId()
        val records = dataSource.facultyAttendance(institutionId)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(FacultyAttendanceRecord::class.java) }

        val facultyEntriesByRecord = records.mapNotNull { record ->
            record.entries.firstOrNull { entry -> entry.facultyId == facultyId }?.let { entry ->
                record to entry
            }
        }

        return FacultyAttendanceSummary(
            daysAttended = facultyEntriesByRecord.count { (_, entry) ->
                entry.status == AttendanceStatus.PRESENT.name
            },
            daysHeld = facultyEntriesByRecord.size,
        )
    }

    override suspend fun rectifyFacultyAttendance(
        facultyId: String,
        attendanceDate: String,
        status: AttendanceStatus,
    ): FacultyAttendanceRecord {
        roleAccessManager.requireHeadmaster("rectify faculty attendance")
        ValidationUtils.requireNotBlank(facultyId, "Faculty")
        validateAttendanceDate(attendanceDate)

        val record = getFacultyAttendanceRecord(attendanceDate)
            ?: throw IllegalArgumentException("Attendance was not marked for this date.")

        val institutionId = sessionStore.requireInstitutionId()
        val faculty = dataSource.facultyDocument(institutionId, facultyId)
            .get()
            .await()
            .toObject(FacultyMember::class.java)
            ?: throw IllegalArgumentException("Faculty record was not found.")

        val updatedEntries = if (record.entries.any { it.facultyId == facultyId }) {
            record.entries.map { entry ->
                if (entry.facultyId == facultyId) {
                    entry.copy(status = status.name)
                } else {
                    entry
                }
            }
        } else {
            record.entries + FacultyAttendanceEntry(
                facultyId = faculty.id,
                employeeCode = faculty.employeeCode,
                fullName = faculty.fullName,
                qualification = faculty.qualification,
                status = status.name,
            )
        }

        return saveFacultyAttendance(
            FacultyAttendanceDraft(
                attendanceDate = attendanceDate,
                entries = updatedEntries,
            ),
        )
    }

    private fun validateAttendanceDate(value: String) {
        ValidationUtils.requireNotBlank(value, "Date")
        if (!AttendanceDateUtils.isValidStorageDate(value)) {
            throw IllegalArgumentException("Attendance date is invalid.")
        }
    }

    private fun requireEntries(
        statuses: List<String>,
        label: String,
    ) {
        if (statuses.isEmpty()) {
            throw IllegalArgumentException("No $label is available for saving.")
        }
        statuses.forEach { status ->
            if (status.uppercase() !in VALID_ATTENDANCE_STATUSES) {
                throw IllegalArgumentException("Attendance status is invalid.")
            }
        }
    }

    private fun classAttendanceId(
        classSectionId: String,
        attendanceDate: String,
    ): String = "class_${classSectionId}_${dateKey(attendanceDate)}"

    private fun facultyAttendanceId(attendanceDate: String): String = "faculty_${dateKey(attendanceDate)}"

    private fun dateKey(value: String): String = value.replace("-", "")

    companion object {
        private val VALID_ATTENDANCE_STATUSES = AttendanceStatus.entries.map { it.name }.toSet()
    }
}
