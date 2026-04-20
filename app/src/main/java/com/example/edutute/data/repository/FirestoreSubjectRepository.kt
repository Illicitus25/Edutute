package com.example.edutute.data.repository

import com.example.edutute.core.util.ValidationUtils
import com.example.edutute.domain.access.RoleAccessManager
import com.example.edutute.data.auth.SessionStore
import com.example.edutute.data.firestore.FirestoreDataSource
import com.example.edutute.domain.model.RecordStatus
import com.example.edutute.domain.model.Subject
import com.example.edutute.domain.model.SubjectDraft
import com.example.edutute.domain.model.SubjectType
import com.example.edutute.domain.repository.SubjectRepository
import kotlinx.coroutines.tasks.await

class FirestoreSubjectRepository(
    private val dataSource: FirestoreDataSource,
    private val sessionStore: SessionStore,
    private val roleAccessManager: RoleAccessManager,
) : SubjectRepository {

    override suspend fun listSubjects(): List<Subject> {
        val institutionId = sessionStore.requireInstitutionId()
        return dataSource.subjects(institutionId)
            .whereEqualTo("status", RecordStatus.ACTIVE.name)
            .orderBy("nameLower")
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(Subject::class.java) }
    }

    override suspend fun searchSubjects(query: String): List<Subject> {
        val normalizedQuery = ValidationUtils.normalize(query)
        return listSubjects().filter { subject ->
            normalizedQuery.isBlank() ||
                subject.nameLower.contains(normalizedQuery)
        }
    }

    override suspend fun saveSubject(draft: SubjectDraft): Subject {
        roleAccessManager.requireHeadmaster("create or edit subjects")
        ValidationUtils.requireNotBlank(draft.name, "Subject name")

        val institutionId = sessionStore.requireInstitutionId()
        val normalizedName = ValidationUtils.normalize(draft.name)
        val duplicate = dataSource.subjects(institutionId)
            .whereEqualTo("nameLower", normalizedName)
            .whereEqualTo("status", RecordStatus.ACTIVE.name)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(Subject::class.java) }
            .firstOrNull { it.id != draft.id }
        if (duplicate != null) {
            throw IllegalArgumentException("Subject name is already in use.")
        }

        val document = if (draft.id.isBlank()) {
            dataSource.subjects(institutionId).document()
        } else {
            dataSource.subjectDocument(institutionId, draft.id)
        }
        val existing = if (draft.id.isBlank()) null else document.get().await().toObject(Subject::class.java)
        val currentTime = System.currentTimeMillis()
        val normalizedSubjectType = draft.subjectType
            .trim()
            .uppercase()
            .takeIf { candidate -> SubjectType.entries.any { it.name == candidate } }
            ?: SubjectType.THEORETICAL.name
        val generatedCode = generateSubjectCode(draft.name)
        val subject = Subject(
            id = document.id,
            institutionId = institutionId,
            code = existing?.code?.takeIf { it.isNotBlank() } ?: generatedCode,
            normalizedCode = existing?.normalizedCode?.takeIf { it.isNotBlank() } ?: ValidationUtils.normalizeCode(generatedCode),
            name = draft.name.trim(),
            nameLower = normalizedName,
            shortName = existing?.shortName.orEmpty(),
            subjectType = normalizedSubjectType,
            status = existing?.status ?: RecordStatus.ACTIVE.name,
            createdAt = existing?.createdAt ?: currentTime,
            updatedAt = currentTime,
        )
        document.set(subject).await()
        return subject
    }

    override suspend fun deleteSubject(id: String) {
        roleAccessManager.requireHeadmaster("delete subjects")
        val institutionId = sessionStore.requireInstitutionId()
        dataSource.subjectDocument(institutionId, id)
            .update(
                mapOf(
                    "status" to RecordStatus.ARCHIVED.name,
                    "updatedAt" to System.currentTimeMillis(),
                ),
            )
            .await()
    }

    private fun generateSubjectCode(name: String): String {
        val tokens = name.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        val initials = tokens
            .take(4)
            .joinToString(separator = "") { token -> token.first().uppercaseChar().toString() }
        return initials.ifBlank {
            name.trim()
                .filter(Char::isLetterOrDigit)
                .take(4)
                .uppercase()
                .ifBlank { "SUBJ" }
        }
    }
}
