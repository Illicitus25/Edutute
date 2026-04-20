package com.example.edutute.domain.repository

import com.example.edutute.domain.model.ClassSection
import com.example.edutute.domain.model.ClassSectionDraft
import com.example.edutute.domain.model.SchoolClass
import com.example.edutute.domain.model.SchoolClassDraft
import com.example.edutute.domain.model.Section
import com.example.edutute.domain.model.SectionDraft
import com.example.edutute.domain.model.TeacherAssignment
import com.example.edutute.domain.model.TeacherAssignmentDraft

interface AcademicRepository {
    suspend fun listClasses(): List<SchoolClass>

    suspend fun listSections(): List<Section>

    suspend fun listClassSections(): List<ClassSection>

    suspend fun listTeacherAssignments(classSectionId: String): List<TeacherAssignment>

    suspend fun listTeacherAssignmentsForFaculty(facultyId: String): List<TeacherAssignment>

    suspend fun saveClass(draft: SchoolClassDraft): SchoolClass

    suspend fun saveSection(draft: SectionDraft): Section

    suspend fun saveClassSection(draft: ClassSectionDraft): ClassSection

    suspend fun saveTeacherAssignment(draft: TeacherAssignmentDraft): TeacherAssignment

    suspend fun removeTeacherAssignment(
        classSectionId: String,
        subjectId: String,
    )

    suspend fun deleteClass(id: String)

    suspend fun deleteSection(id: String)

    suspend fun deleteClassSection(id: String)

    suspend fun getClassSectionById(id: String): ClassSection?
}
