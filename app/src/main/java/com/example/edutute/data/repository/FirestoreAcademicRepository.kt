package com.example.edutute.data.repository

import com.example.edutute.core.util.ValidationUtils
import com.example.edutute.domain.access.RoleAccessManager
import com.example.edutute.data.auth.SessionStore
import com.example.edutute.data.firestore.FirestoreDataSource
import com.example.edutute.domain.model.ClassSection
import com.example.edutute.domain.model.ClassSectionDraft
import com.example.edutute.domain.model.FacultyMember
import com.example.edutute.domain.model.RecordStatus
import com.example.edutute.domain.model.SchoolClass
import com.example.edutute.domain.model.SchoolClassDraft
import com.example.edutute.domain.model.Section
import com.example.edutute.domain.model.SectionDraft
import com.example.edutute.domain.model.Subject
import com.example.edutute.domain.model.TeacherAssignment
import com.example.edutute.domain.model.TeacherAssignmentDraft
import com.example.edutute.domain.repository.AcademicRepository
import kotlinx.coroutines.tasks.await

class FirestoreAcademicRepository(
    private val dataSource: FirestoreDataSource,
    private val sessionStore: SessionStore,
    private val roleAccessManager: RoleAccessManager,
) : AcademicRepository {

    override suspend fun listClasses(): List<SchoolClass> {
        val institutionId = sessionStore.requireInstitutionId()
        val classes = dataSource.classes(institutionId)
            .whereEqualTo("status", RecordStatus.ACTIVE.name)
            .orderBy("displayOrder")
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(SchoolClass::class.java) }
        if (roleAccessManager.currentContext().isHeadmaster) return classes

        val allowedClassIds = listClassSections().map(ClassSection::classId).toSet()
        return classes.filter { it.id in allowedClassIds }
    }

    override suspend fun listSections(): List<Section> {
        val institutionId = sessionStore.requireInstitutionId()
        val sections = dataSource.sections(institutionId)
            .whereEqualTo("status", RecordStatus.ACTIVE.name)
            .orderBy("displayOrder")
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(Section::class.java) }
        if (roleAccessManager.currentContext().isHeadmaster) return sections

        val allowedSectionIds = listClassSections().map(ClassSection::sectionId).toSet()
        return sections.filter { it.id in allowedSectionIds }
    }

    override suspend fun listClassSections(): List<ClassSection> {
        val institutionId = sessionStore.requireInstitutionId()
        val academicSessionId = sessionStore.requireAcademicSessionId()
        val classSections = dataSource.classSections(institutionId, academicSessionId)
            .whereEqualTo("status", RecordStatus.ACTIVE.name)
            .orderBy("classOrder")
            .orderBy("sectionOrder")
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(ClassSection::class.java) }
        if (roleAccessManager.currentContext().isHeadmaster) return classSections

        val allowedIds = roleAccessManager.allowedClassSectionIds()
        return classSections.filter { it.id in allowedIds }
    }

    override suspend fun listTeacherAssignments(classSectionId: String): List<TeacherAssignment> {
        roleAccessManager.requireClassSectionAccess(classSectionId)
        val institutionId = sessionStore.requireInstitutionId()
        val academicSessionId = sessionStore.requireAcademicSessionId()
        return dataSource.teacherAssignments(institutionId, academicSessionId)
            .whereEqualTo("classSectionId", classSectionId)
            .whereEqualTo("status", RecordStatus.ACTIVE.name)
            .orderBy("subjectName")
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(TeacherAssignment::class.java) }
    }

    override suspend fun listTeacherAssignmentsForFaculty(facultyId: String): List<TeacherAssignment> {
        roleAccessManager.requireFacultyRecordAccess(facultyId)
        val institutionId = sessionStore.requireInstitutionId()
        val academicSessionId = sessionStore.requireAcademicSessionId()
        return dataSource.teacherAssignments(institutionId, academicSessionId)
            .whereEqualTo("facultyId", facultyId)
            .whereEqualTo("status", RecordStatus.ACTIVE.name)
            .orderBy("classSectionName")
            .orderBy("subjectName")
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(TeacherAssignment::class.java) }
    }

    override suspend fun saveClass(draft: SchoolClassDraft): SchoolClass {
        roleAccessManager.requireHeadmaster("create or edit classes")
        ValidationUtils.requireNotBlank(draft.name, "Class name")
        val institutionId = sessionStore.requireInstitutionId()
        val document = if (draft.id.isBlank()) {
            dataSource.classes(institutionId).document()
        } else {
            dataSource.classDocument(institutionId, draft.id)
        }
        val existing = if (draft.id.isBlank()) null else document.get().await().toObject(SchoolClass::class.java)
        val currentTime = System.currentTimeMillis()
        val resolvedOrder = draft.displayOrder.takeIf { it > 0 } ?: (listClasses().size + 1)
        val schoolClass = SchoolClass(
            id = document.id,
            institutionId = institutionId,
            name = draft.name.trim(),
            nameLower = ValidationUtils.normalize(draft.name),
            displayOrder = resolvedOrder,
            status = existing?.status ?: RecordStatus.ACTIVE.name,
            createdAt = existing?.createdAt ?: currentTime,
            updatedAt = currentTime,
        )
        document.set(schoolClass).await()
        return schoolClass
    }

    override suspend fun saveSection(draft: SectionDraft): Section {
        roleAccessManager.requireHeadmaster("create or edit sections")
        ValidationUtils.requireNotBlank(draft.name, "Section name")
        val institutionId = sessionStore.requireInstitutionId()
        val document = if (draft.id.isBlank()) {
            dataSource.sections(institutionId).document()
        } else {
            dataSource.sectionDocument(institutionId, draft.id)
        }
        val existing = if (draft.id.isBlank()) null else document.get().await().toObject(Section::class.java)
        val currentTime = System.currentTimeMillis()
        val resolvedOrder = draft.displayOrder.takeIf { it > 0 } ?: (listSections().size + 1)
        val section = Section(
            id = document.id,
            institutionId = institutionId,
            name = draft.name.trim(),
            nameLower = ValidationUtils.normalize(draft.name),
            displayOrder = resolvedOrder,
            status = existing?.status ?: RecordStatus.ACTIVE.name,
            createdAt = existing?.createdAt ?: currentTime,
            updatedAt = currentTime,
        )
        document.set(section).await()
        return section
    }

    override suspend fun saveClassSection(draft: ClassSectionDraft): ClassSection {
        roleAccessManager.requireHeadmaster("create or edit class sections")
        ValidationUtils.requireNotBlank(draft.classId, "Class")
        ValidationUtils.requireNotBlank(draft.sectionId, "Section")

        val institutionId = sessionStore.requireInstitutionId()
        val academicSessionId = sessionStore.requireAcademicSessionId()
        val classes = listClasses()
        val sections = listSections()
        val classEntity = classes.firstOrNull { it.id == draft.classId }
            ?: throw IllegalArgumentException("Selected class was not found.")
        val sectionEntity = sections.firstOrNull { it.id == draft.sectionId }
            ?: throw IllegalArgumentException("Selected section was not found.")

        val classTeacher = loadFaculty(institutionId, draft.classTeacherId)
        val coTeacher = loadFaculty(institutionId, draft.coClassTeacherId)
        val document = if (draft.id.isBlank()) {
            dataSource.classSections(institutionId, academicSessionId).document()
        } else {
            dataSource.classSectionDocument(institutionId, academicSessionId, draft.id)
        }
        val existing = if (draft.id.isBlank()) null else document.get().await().toObject(ClassSection::class.java)

        val duplicate = dataSource.classSections(institutionId, academicSessionId)
            .whereEqualTo("classId", draft.classId)
            .whereEqualTo("sectionId", draft.sectionId)
            .whereEqualTo("status", RecordStatus.ACTIVE.name)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(ClassSection::class.java) }
            .firstOrNull { it.id != draft.id }
        if (duplicate != null) {
            throw IllegalArgumentException("This class-section already exists for the active academic session.")
        }

        val currentTime = System.currentTimeMillis()
        val classSection = ClassSection(
            id = document.id,
            institutionId = institutionId,
            sessionId = academicSessionId,
            classId = classEntity.id,
            className = classEntity.name,
            classOrder = classEntity.displayOrder,
            sectionId = sectionEntity.id,
            sectionName = sectionEntity.name,
            sectionOrder = sectionEntity.displayOrder,
            displayName = "${classEntity.name} - ${sectionEntity.name}",
            displayNameLower = ValidationUtils.normalize("${classEntity.name} ${sectionEntity.name}"),
            classTeacherId = classTeacher?.id.orEmpty(),
            classTeacherName = classTeacher?.fullName.orEmpty(),
            coClassTeacherId = coTeacher?.id.orEmpty(),
            coClassTeacherName = coTeacher?.fullName.orEmpty(),
            status = existing?.status ?: RecordStatus.ACTIVE.name,
            createdAt = existing?.createdAt ?: currentTime,
            updatedAt = currentTime,
        )

        document.set(classSection).await()
        return classSection
    }

    override suspend fun saveTeacherAssignment(draft: TeacherAssignmentDraft): TeacherAssignment {
        roleAccessManager.requireHeadmaster("manage class subject assignments")
        ValidationUtils.requireNotBlank(draft.classSectionId, "Class")
        ValidationUtils.requireNotBlank(draft.subjectId, "Subject")

        val institutionId = sessionStore.requireInstitutionId()
        val academicSessionId = sessionStore.requireAcademicSessionId()
        val classSection = getClassSectionById(draft.classSectionId)
            ?: throw IllegalArgumentException("Selected class could not be found.")
        val subject = dataSource.subjectDocument(institutionId, draft.subjectId)
            .get()
            .await()
            .toObject(Subject::class.java)
            ?: throw IllegalArgumentException("Selected subject could not be found.")
        val faculty = loadFaculty(institutionId, draft.facultyId)

        val assignmentId = "${draft.classSectionId}_${draft.subjectId}"
        val document = dataSource.teacherAssignmentDocument(institutionId, academicSessionId, assignmentId)
        val existing = document.get().await().toObject(TeacherAssignment::class.java)
        val currentTime = System.currentTimeMillis()

        val assignment = TeacherAssignment(
            id = assignmentId,
            institutionId = institutionId,
            sessionId = academicSessionId,
            facultyId = faculty?.id.orEmpty(),
            facultyName = faculty?.fullName.orEmpty(),
            subjectId = subject.id,
            subjectName = subject.name,
            subjectCode = subject.code,
            classSectionId = classSection.id,
            classSectionName = classSection.displayName,
            status = RecordStatus.ACTIVE.name,
            createdAt = existing?.createdAt ?: currentTime,
            updatedAt = currentTime,
        )

        document.set(assignment).await()
        return assignment
    }

    override suspend fun removeTeacherAssignment(classSectionId: String, subjectId: String) {
        roleAccessManager.requireHeadmaster("remove class subject assignments")
        ValidationUtils.requireNotBlank(classSectionId, "Class")
        ValidationUtils.requireNotBlank(subjectId, "Subject")

        val institutionId = sessionStore.requireInstitutionId()
        val academicSessionId = sessionStore.requireAcademicSessionId()
        val assignmentId = "${classSectionId}_${subjectId}"
        dataSource.teacherAssignmentDocument(institutionId, academicSessionId, assignmentId)
            .update(
                mapOf(
                    "status" to RecordStatus.ARCHIVED.name,
                    "updatedAt" to System.currentTimeMillis(),
                ),
            )
            .await()
    }

    override suspend fun deleteClass(id: String) {
        roleAccessManager.requireHeadmaster("delete classes")
        val institutionId = sessionStore.requireInstitutionId()
        dataSource.classDocument(institutionId, id)
            .update(
                mapOf(
                    "status" to RecordStatus.ARCHIVED.name,
                    "updatedAt" to System.currentTimeMillis(),
                ),
            )
            .await()
    }

    override suspend fun deleteSection(id: String) {
        roleAccessManager.requireHeadmaster("delete sections")
        val institutionId = sessionStore.requireInstitutionId()
        dataSource.sectionDocument(institutionId, id)
            .update(
                mapOf(
                    "status" to RecordStatus.ARCHIVED.name,
                    "updatedAt" to System.currentTimeMillis(),
                ),
            )
            .await()
    }

    override suspend fun deleteClassSection(id: String) {
        roleAccessManager.requireHeadmaster("delete class sections")
        val institutionId = sessionStore.requireInstitutionId()
        val academicSessionId = sessionStore.requireAcademicSessionId()
        dataSource.classSectionDocument(institutionId, academicSessionId, id)
            .update(
                mapOf(
                    "status" to RecordStatus.ARCHIVED.name,
                    "updatedAt" to System.currentTimeMillis(),
                ),
            )
            .await()
    }

    override suspend fun getClassSectionById(id: String): ClassSection? {
        roleAccessManager.requireClassSectionAccess(id)
        val institutionId = sessionStore.requireInstitutionId()
        val academicSessionId = sessionStore.requireAcademicSessionId()
        return dataSource.classSectionDocument(institutionId, academicSessionId, id)
            .get()
            .await()
            .toObject(ClassSection::class.java)
    }

    private suspend fun loadFaculty(institutionId: String, facultyId: String): FacultyMember? {
        if (facultyId.isBlank()) return null
        return dataSource.facultyDocument(institutionId, facultyId)
            .get()
            .await()
            .toObject(FacultyMember::class.java)
    }
}
