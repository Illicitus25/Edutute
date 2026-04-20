package com.example.edutute.domain.repository

import com.example.edutute.domain.model.Student
import com.example.edutute.domain.model.StudentDraft

interface StudentRepository {
    suspend fun listStudents(): List<Student>

    suspend fun searchStudents(query: String): List<Student>

    suspend fun getStudentById(id: String): Student?

    suspend fun saveStudent(draft: StudentDraft): Student

    suspend fun assignStudentsToClassSection(
        studentIds: List<String>,
        classSectionId: String,
    )

    suspend fun removeStudentFromClassSection(
        studentId: String,
        classSectionId: String,
    )

    suspend fun deleteStudent(id: String)
}
