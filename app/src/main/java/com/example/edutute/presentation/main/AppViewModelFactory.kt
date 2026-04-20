package com.example.edutute.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.edutute.app.AppContainer
import com.example.edutute.presentation.auth.LoginViewModel
import com.example.edutute.presentation.auth.SignUpViewModel
import com.example.edutute.presentation.attendance.AttendanceViewModel
import com.example.edutute.presentation.classes.ClassDetailsViewModel
import com.example.edutute.presentation.classes.ClassesViewModel
import com.example.edutute.presentation.dashboard.DashboardViewModel
import com.example.edutute.presentation.faculty.FacultyDetailViewModel
import com.example.edutute.presentation.faculty.FacultyFormViewModel
import com.example.edutute.presentation.faculty.FacultyListViewModel
import com.example.edutute.presentation.institution.InstitutionProfileViewModel
import com.example.edutute.presentation.students.StudentDetailViewModel
import com.example.edutute.presentation.students.StudentFormViewModel
import com.example.edutute.presentation.students.StudentsListViewModel
import com.example.edutute.presentation.subjects.SubjectsViewModel

class AppViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) ->
                MainViewModel(container.authRepository) as T
            modelClass.isAssignableFrom(LoginViewModel::class.java) ->
                LoginViewModel(container.authRepository) as T
            modelClass.isAssignableFrom(SignUpViewModel::class.java) ->
                SignUpViewModel(container.authRepository) as T
            modelClass.isAssignableFrom(DashboardViewModel::class.java) ->
                DashboardViewModel(
                    container.dashboardRepository,
                    container.attendanceRepository,
                    container.academicRepository,
                    container.roleAccessManager,
                ) as T
            modelClass.isAssignableFrom(InstitutionProfileViewModel::class.java) ->
                InstitutionProfileViewModel(
                    container.institutionRepository,
                    container.locationValidationRepository,
                    container.roleAccessManager,
                ) as T
            modelClass.isAssignableFrom(FacultyListViewModel::class.java) ->
                FacultyListViewModel(container.facultyRepository) as T
            modelClass.isAssignableFrom(FacultyFormViewModel::class.java) ->
                FacultyFormViewModel(container.facultyRepository) as T
            modelClass.isAssignableFrom(FacultyDetailViewModel::class.java) ->
                FacultyDetailViewModel(
                    container.facultyRepository,
                    container.academicRepository,
                    container.attendanceRepository,
                ) as T
            modelClass.isAssignableFrom(ClassesViewModel::class.java) ->
                ClassesViewModel(
                    container.academicRepository,
                    container.studentRepository,
                    container.roleAccessManager,
                ) as T
            modelClass.isAssignableFrom(AttendanceViewModel::class.java) ->
                AttendanceViewModel(
                    container.attendanceRepository,
                    container.academicRepository,
                    container.roleAccessManager,
                ) as T
            modelClass.isAssignableFrom(ClassDetailsViewModel::class.java) ->
                ClassDetailsViewModel(
                    container.academicRepository,
                    container.facultyRepository,
                    container.studentRepository,
                    container.subjectRepository,
                    container.attendanceRepository,
                    container.roleAccessManager,
                ) as T
            modelClass.isAssignableFrom(SubjectsViewModel::class.java) ->
                SubjectsViewModel(container.subjectRepository) as T
            modelClass.isAssignableFrom(StudentsListViewModel::class.java) ->
                StudentsListViewModel(container.studentRepository) as T
            modelClass.isAssignableFrom(StudentFormViewModel::class.java) ->
                StudentFormViewModel(
                    container.studentRepository,
                    container.academicRepository,
                    container.locationValidationRepository,
                ) as T
            modelClass.isAssignableFrom(StudentDetailViewModel::class.java) ->
                StudentDetailViewModel(
                    container.studentRepository,
                    container.attendanceRepository,
                ) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
