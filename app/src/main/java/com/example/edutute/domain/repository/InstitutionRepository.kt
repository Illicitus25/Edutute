package com.example.edutute.domain.repository

import com.example.edutute.domain.model.AcademicSession
import com.example.edutute.domain.model.Institution
import com.example.edutute.domain.model.InstitutionDraft

interface InstitutionRepository {
    suspend fun getInstitution(): Institution?

    suspend fun getCurrentAcademicSession(): AcademicSession?

    suspend fun listAcademicSessions(): List<AcademicSession>

    suspend fun saveInstitution(draft: InstitutionDraft): Institution
}
