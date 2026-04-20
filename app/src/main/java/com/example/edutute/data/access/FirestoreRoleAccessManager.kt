package com.example.edutute.data.access

import com.example.edutute.data.auth.SessionStore
import com.example.edutute.data.firestore.FirestoreDataSource
import com.example.edutute.domain.access.RoleAccessManager
import com.example.edutute.domain.access.UserAccessContext
import com.example.edutute.domain.model.ClassSection
import com.example.edutute.domain.model.RecordStatus
import com.example.edutute.domain.model.TeacherAssignment
import com.example.edutute.domain.model.UserRole
import kotlinx.coroutines.tasks.await

class FirestoreRoleAccessManager(
    private val sessionStore: SessionStore,
    private val dataSource: FirestoreDataSource,
) : RoleAccessManager {

    override fun currentContext(): UserAccessContext {
        val session = sessionStore.session.value
        val userRole = when (session?.userRole?.trim()?.uppercase()) {
            UserRole.FACULTY.name -> UserRole.FACULTY
            else -> UserRole.HEADMASTER
        }
        return UserAccessContext(
            userRole = userRole,
            institutionId = session?.institutionId,
            currentSessionId = session?.currentSessionId,
            linkedFacultyId = session?.linkedFacultyId,
        )
    }

    override fun requireHeadmaster(action: String) {
        if (!currentContext().isHeadmaster) {
            throw IllegalStateException("Only headmaster accounts can $action.")
        }
    }

    override fun requireFacultyId(): String = currentContext().linkedFacultyId
        ?.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("Faculty account is missing its linked faculty profile.")

    override fun requireFacultyRecordAccess(facultyId: String) {
        val context = currentContext()
        if (context.isHeadmaster) return
        if (facultyId != requireFacultyId()) {
            throw IllegalStateException("You can access only your own faculty profile.")
        }
    }

    override suspend fun allowedClassSectionIds(): Set<String> {
        val context = currentContext()
        if (context.isHeadmaster) {
            return emptySet()
        }

        val institutionId = context.institutionId
            ?: throw IllegalStateException("Institution context is missing.")
        val sessionId = context.currentSessionId
            ?: throw IllegalStateException("Academic session is not configured.")
        val facultyId = requireFacultyId()

        val classTeacherIds = dataSource.classSections(institutionId, sessionId)
            .whereEqualTo("classTeacherId", facultyId)
            .whereEqualTo("status", RecordStatus.ACTIVE.name)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(ClassSection::class.java) }
            .map(ClassSection::id)

        val subjectTeacherIds = dataSource.teacherAssignments(institutionId, sessionId)
            .whereEqualTo("facultyId", facultyId)
            .whereEqualTo("status", RecordStatus.ACTIVE.name)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(TeacherAssignment::class.java) }
            .map(TeacherAssignment::classSectionId)

        return (classTeacherIds + subjectTeacherIds)
            .filter { it.isNotBlank() }
            .toSet()
    }

    override suspend fun classTeacherClassSectionIds(): Set<String> {
        val context = currentContext()
        if (context.isHeadmaster) {
            return emptySet()
        }

        val institutionId = context.institutionId
            ?: throw IllegalStateException("Institution context is missing.")
        val sessionId = context.currentSessionId
            ?: throw IllegalStateException("Academic session is not configured.")
        val facultyId = requireFacultyId()

        return dataSource.classSections(institutionId, sessionId)
            .whereEqualTo("classTeacherId", facultyId)
            .whereEqualTo("status", RecordStatus.ACTIVE.name)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(ClassSection::class.java) }
            .map(ClassSection::id)
            .filter { it.isNotBlank() }
            .toSet()
    }

    override suspend fun requireClassSectionAccess(classSectionId: String) {
        val context = currentContext()
        if (context.isHeadmaster) return
        if (classSectionId !in allowedClassSectionIds()) {
            throw IllegalStateException("You can access only classes assigned to you.")
        }
    }

    override suspend fun requireClassTeacherClassSectionAccess(classSectionId: String) {
        val context = currentContext()
        if (context.isHeadmaster) return
        if (classSectionId !in classTeacherClassSectionIds()) {
            throw IllegalStateException("Only the class teacher can mark attendance for this class.")
        }
    }
}
