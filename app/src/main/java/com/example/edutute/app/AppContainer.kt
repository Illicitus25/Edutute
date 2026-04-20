package com.example.edutute.app

import android.content.Context
import com.example.edutute.data.access.FirestoreRoleAccessManager
import com.example.edutute.data.auth.SessionStore
import com.example.edutute.data.firestore.FirestoreDataSource
import com.example.edutute.data.repository.FirebaseAuthRepository
import com.example.edutute.data.repository.FirestoreAcademicRepository
import com.example.edutute.data.repository.FirestoreAttendanceRepository
import com.example.edutute.data.repository.FirestoreDashboardRepository
import com.example.edutute.data.repository.FirestoreFacultyRepository
import com.example.edutute.data.repository.FirestoreInstitutionRepository
import com.example.edutute.data.repository.FirestoreStudentRepository
import com.example.edutute.data.repository.FirestoreSubjectRepository
import com.example.edutute.data.repository.IndiaLocationValidationRepository
import com.example.edutute.domain.repository.AcademicRepository
import com.example.edutute.domain.repository.AttendanceRepository
import com.example.edutute.domain.repository.AuthRepository
import com.example.edutute.domain.repository.DashboardRepository
import com.example.edutute.domain.repository.FacultyRepository
import com.example.edutute.domain.repository.InstitutionRepository
import com.example.edutute.domain.repository.LocationValidationRepository
import com.example.edutute.domain.repository.StudentRepository
import com.example.edutute.domain.repository.SubjectRepository
import com.example.edutute.domain.access.RoleAccessManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AppContainer(
    private val appContext: Context,
) {
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firebaseFirestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val sessionStore = SessionStore()
    private val firestoreDataSource = FirestoreDataSource(firebaseFirestore)
    val locationValidationRepository: LocationValidationRepository = IndiaLocationValidationRepository()
    val roleAccessManager: RoleAccessManager = FirestoreRoleAccessManager(
        sessionStore = sessionStore,
        dataSource = firestoreDataSource,
    )

    val authRepository: AuthRepository = FirebaseAuthRepository(
        appContext = appContext,
        auth = firebaseAuth,
        dataSource = firestoreDataSource,
        sessionStore = sessionStore,
    )

    val institutionRepository: InstitutionRepository = FirestoreInstitutionRepository(
        auth = firebaseAuth,
        firestore = firebaseFirestore,
        dataSource = firestoreDataSource,
        sessionStore = sessionStore,
        roleAccessManager = roleAccessManager,
        locationValidationRepository = locationValidationRepository,
    )

    val dashboardRepository: DashboardRepository = FirestoreDashboardRepository(
        dataSource = firestoreDataSource,
        sessionStore = sessionStore,
        roleAccessManager = roleAccessManager,
    )

    val facultyRepository: FacultyRepository = FirestoreFacultyRepository(
        appContext = appContext,
        auth = firebaseAuth,
        firestore = firebaseFirestore,
        dataSource = firestoreDataSource,
        sessionStore = sessionStore,
        roleAccessManager = roleAccessManager,
    )

    val academicRepository: AcademicRepository = FirestoreAcademicRepository(
        dataSource = firestoreDataSource,
        sessionStore = sessionStore,
        roleAccessManager = roleAccessManager,
    )

    val subjectRepository: SubjectRepository = FirestoreSubjectRepository(
        dataSource = firestoreDataSource,
        sessionStore = sessionStore,
        roleAccessManager = roleAccessManager,
    )

    val attendanceRepository: AttendanceRepository = FirestoreAttendanceRepository(
        dataSource = firestoreDataSource,
        sessionStore = sessionStore,
        roleAccessManager = roleAccessManager,
    )

    val studentRepository: StudentRepository = FirestoreStudentRepository(
        firestore = firebaseFirestore,
        dataSource = firestoreDataSource,
        sessionStore = sessionStore,
        roleAccessManager = roleAccessManager,
        locationValidationRepository = locationValidationRepository,
    )
}
