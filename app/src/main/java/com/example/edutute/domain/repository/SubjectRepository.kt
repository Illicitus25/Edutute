package com.example.edutute.domain.repository

import com.example.edutute.domain.model.Subject
import com.example.edutute.domain.model.SubjectDraft

interface SubjectRepository {
    suspend fun listSubjects(): List<Subject>

    suspend fun searchSubjects(query: String): List<Subject>

    suspend fun saveSubject(draft: SubjectDraft): Subject

    suspend fun deleteSubject(id: String)
}
