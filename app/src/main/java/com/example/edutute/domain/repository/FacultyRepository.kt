package com.example.edutute.domain.repository

import com.example.edutute.domain.model.FacultyDraft
import com.example.edutute.domain.model.FacultyMember
import com.example.edutute.domain.model.FacultySaveResult

interface FacultyRepository {
    suspend fun listFaculty(): List<FacultyMember>

    suspend fun searchFaculty(query: String): List<FacultyMember>

    suspend fun getFacultyById(id: String): FacultyMember?

    suspend fun saveFaculty(draft: FacultyDraft): FacultySaveResult

    suspend fun deleteFaculty(id: String)
}
