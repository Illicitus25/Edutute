package com.example.edutute.data.repository

import android.content.Context
import com.example.edutute.core.util.ValidationUtils
import com.example.edutute.data.auth.SessionStore
import com.example.edutute.data.firestore.FirestoreDataSource
import com.example.edutute.domain.model.AppSession
import com.example.edutute.domain.model.AuthRegistrationRequest
import com.example.edutute.domain.model.FacultyMember
import com.example.edutute.domain.model.Institution
import com.example.edutute.domain.model.RecordStatus
import com.example.edutute.domain.model.SessionState
import com.example.edutute.domain.model.SetupStatus
import com.example.edutute.domain.model.UserProfile
import com.example.edutute.domain.model.UserRole
import com.example.edutute.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale

class FirebaseAuthRepository(
    private val appContext: Context,
    private val auth: FirebaseAuth,
    private val dataSource: FirestoreDataSource,
    private val sessionStore: SessionStore,
) : AuthRepository {

    override val currentSession: StateFlow<AppSession?> = sessionStore.session

    override suspend fun register(request: AuthRegistrationRequest) {
        when (request.userRole) {
            UserRole.HEADMASTER -> registerHeadmaster(request)
            UserRole.FACULTY -> registerFaculty(request)
        }
    }

    override suspend fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        val currentUser = auth.currentUser ?: return
        val currentTime = System.currentTimeMillis()
        val userRef = dataSource.user(currentUser.uid)
        val existingProfile = userRef.get().await().toObject(UserProfile::class.java)

        userRef.set(
            mapOf(
                "uid" to currentUser.uid,
                "email" to (currentUser.email ?: email.trim()),
                "accountStatus" to RecordStatus.ACTIVE.name,
                "lastLoginAt" to currentTime,
                "updatedAt" to currentTime,
            ),
            SetOptions.merge(),
        ).await()

        if (
            existingProfile != null &&
            resolveUserRole(existingProfile) == UserRole.FACULTY &&
            existingProfile.institutionId.isNotBlank() &&
            existingProfile.linkedFacultyId.isNotBlank()
        ) {
            dataSource.facultyDocument(existingProfile.institutionId, existingProfile.linkedFacultyId)
                .set(
                    mapOf(
                        "authUid" to currentUser.uid,
                        "accountStatus" to RecordStatus.ACTIVE.name,
                        "updatedAt" to currentTime,
                    ),
                    SetOptions.merge(),
                )
                .await()
        }
    }

    override suspend fun logout() {
        auth.signOut()
        sessionStore.clear()
    }

    override suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    override suspend fun resolveCurrentSession(): SessionState {
        val firebaseUser = auth.currentUser ?: run {
            sessionStore.clear()
            return SessionState.Unauthenticated
        }

        val userProfile = dataSource.user(firebaseUser.uid).get().await().toObject(UserProfile::class.java)
            ?: return unauthorized("User profile was not found. Please contact support.")

        if (userProfile.accountStatus != RecordStatus.ACTIVE.name) {
            return unauthorized("Your account is inactive.")
        }

        return when (resolveUserRole(userProfile)) {
            UserRole.HEADMASTER -> resolveHeadmasterSession(firebaseUser.uid, userProfile)
            UserRole.FACULTY -> resolveFacultySession(firebaseUser.uid, userProfile)
        }
    }

    private suspend fun registerHeadmaster(request: AuthRegistrationRequest) {
        val currentTime = System.currentTimeMillis()
        val email = request.email.trim()
        val fullName = request.fullName.trim()
        val phoneNumber = ValidationUtils.normalizeIndianPhoneDigits(request.phoneNumber)
        val authResult = auth.createUserWithEmailAndPassword(email, request.password).await()
        val currentUser = authResult.user ?: throw IllegalStateException("Account could not be created.")

        dataSource.user(currentUser.uid)
            .set(
                UserProfile(
                    uid = currentUser.uid,
                    email = currentUser.email ?: email,
                    fullName = fullName,
                    displayName = fullName,
                    phoneNumber = phoneNumber,
                    institutionId = "",
                    institutionalId = "",
                    userType = UserRole.HEADMASTER.name.lowercase(),
                    role = UserRole.HEADMASTER.name,
                    linkedFacultyId = "",
                    qualification = "",
                    joiningDate = "",
                    joiningDateTimestamp = 0L,
                    accountStatus = RecordStatus.ACTIVE.name,
                    lastLoginAt = currentTime,
                    createdAt = currentTime,
                    updatedAt = currentTime,
                ),
            )
            .await()
    }

    private suspend fun registerFaculty(request: AuthRegistrationRequest) {
        val institutionalId = request.institutionalId.trim().uppercase()
        val institution = dataSource.institution(institutionalId).get().await().toObject(Institution::class.java)
            ?: throw IllegalArgumentException("Institutional ID was not found.")

        if (institution.status != RecordStatus.ACTIVE.name) {
            throw IllegalArgumentException("This institution is not accepting faculty registrations.")
        }

        val currentTime = System.currentTimeMillis()
        val joiningDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(currentTime)
        val email = request.email.trim()
        val fullName = request.fullName.trim()
        val qualification = request.qualification.trim()
        val phoneNumber = ValidationUtils.normalizeIndianPhoneDigits(request.phoneNumber)
        val existingFaculty = findFacultyByEmail(
            institutionId = institutionalId,
            email = email,
        )
        if (existingFaculty != null && existingFaculty.authUid.isNotBlank()) {
            throw IllegalArgumentException(
                "An account for this faculty email already exists. Sign in instead, or use Forgot Password to activate it.",
            )
        }

        val authResult = auth.createUserWithEmailAndPassword(email, request.password).await()
        val currentUser = authResult.user ?: throw IllegalStateException("Account could not be created.")

        try {
            firestoreRegistrationForFaculty(
                existingFacultyId = existingFaculty?.id,
                currentUserUid = currentUser.uid,
                email = currentUser.email ?: email,
                fullName = fullName,
                phoneNumber = phoneNumber,
                qualification = qualification,
                institutionalId = institutionalId,
                joiningDate = existingFaculty?.joiningDate?.takeIf { it.isNotBlank() } ?: joiningDate,
                joiningDateTimestamp = currentTime,
            )
        } catch (throwable: Throwable) {
            currentUser.delete().await()
            throw throwable
        }
    }

    private suspend fun firestoreRegistrationForFaculty(
        existingFacultyId: String?,
        currentUserUid: String,
        email: String,
        fullName: String,
        phoneNumber: String,
        qualification: String,
        institutionalId: String,
        joiningDate: String,
        joiningDateTimestamp: Long,
    ) {
        dataSource.users().firestore.runTransaction { transaction ->
            val institutionRef = dataSource.institution(institutionalId)
            val institution = transaction.get(institutionRef).toObject(Institution::class.java)
                ?: throw IllegalArgumentException("Institutional ID was not found.")
            if (institution.status != RecordStatus.ACTIVE.name) {
                throw IllegalArgumentException("This institution is not accepting faculty registrations.")
            }

            val facultyDocument = existingFacultyId?.let { facultyId ->
                dataSource.facultyDocument(institutionalId, facultyId)
            } ?: dataSource.faculty(institutionalId).document()
            val existingFaculty = transaction.get(facultyDocument).toObject(FacultyMember::class.java)
            if (existingFaculty != null && existingFaculty.authUid.isNotBlank() && existingFaculty.authUid != currentUserUid) {
                throw IllegalArgumentException(
                    "An account for this faculty email already exists. Sign in instead, or use Forgot Password to activate it.",
                )
            }
            val facultyMember = FacultyMember(
                id = facultyDocument.id,
                institutionId = institutionalId,
                authUid = currentUserUid,
                accountStatus = RecordStatus.ACTIVE.name,
                fullName = existingFaculty?.fullName?.takeIf { it.isNotBlank() } ?: fullName,
                fullNameLower = ValidationUtils.normalize(existingFaculty?.fullName?.takeIf { it.isNotBlank() } ?: fullName),
                email = email,
                phoneNumber = existingFaculty?.phoneNumber?.takeIf { it.isNotBlank() } ?: phoneNumber,
                employeeCode = existingFaculty?.employeeCode?.takeIf { it.isNotBlank() }
                    ?: generateEmployeeCode(
                        transaction = transaction,
                        institutionId = institutionalId,
                        joiningDate = joiningDate,
                        currentTime = joiningDateTimestamp,
                    ),
                qualification = existingFaculty?.qualification?.takeIf { it.isNotBlank() } ?: qualification,
                joiningDate = joiningDate,
                inviteSentAt = existingFaculty?.inviteSentAt ?: 0L,
                status = RecordStatus.ACTIVE.name,
                createdAt = existingFaculty?.createdAt ?: joiningDateTimestamp,
                updatedAt = joiningDateTimestamp,
            )

            transaction.set(dataSource.facultyDocument(institutionalId, facultyDocument.id), facultyMember)
            transaction.set(
                dataSource.user(currentUserUid),
                UserProfile(
                    uid = currentUserUid,
                    email = email,
                    fullName = fullName,
                    displayName = fullName,
                    phoneNumber = phoneNumber,
                    institutionId = institutionalId,
                    institutionalId = institutionalId,
                    userType = UserRole.FACULTY.name.lowercase(),
                    role = UserRole.FACULTY.name,
                    linkedFacultyId = facultyDocument.id,
                    qualification = qualification,
                    joiningDate = joiningDate,
                    joiningDateTimestamp = joiningDateTimestamp,
                    accountStatus = RecordStatus.ACTIVE.name,
                    lastLoginAt = joiningDateTimestamp,
                    createdAt = joiningDateTimestamp,
                    updatedAt = joiningDateTimestamp,
                ),
            )
            Unit
        }.await()
    }

    private suspend fun resolveHeadmasterSession(firebaseUserUid: String, userProfile: UserProfile): SessionState {
        if (userProfile.institutionId.isBlank()) {
            val setupSession = buildSession(
                userProfile = userProfile,
                userRole = UserRole.HEADMASTER,
                institutionId = null,
                institutionName = "",
                linkedFacultyId = null,
                currentSessionId = null,
                requiresInstitutionSetup = true,
            )
            sessionStore.update(setupSession)
            return SessionState.SetupRequired(setupSession)
        }

        val institution = dataSource.institution(userProfile.institutionId).get().await()
            .toObject(com.example.edutute.domain.model.Institution::class.java)

        if (institution == null) {
            val setupSession = buildSession(
                userProfile = userProfile,
                userRole = UserRole.HEADMASTER,
                institutionId = userProfile.institutionId,
                institutionName = "",
                linkedFacultyId = null,
                currentSessionId = null,
                requiresInstitutionSetup = true,
            )
            sessionStore.update(setupSession)
            return SessionState.SetupRequired(setupSession)
        }

        if (institution.headmasterUid != firebaseUserUid) {
            return unauthorized("This account is not configured as the institution headmaster.")
        }

        val session = buildSession(
            userProfile = userProfile,
            userRole = UserRole.HEADMASTER,
            institutionId = institution.id,
            institutionName = institution.name,
            linkedFacultyId = null,
            currentSessionId = institution.currentSessionId.takeIf { it.isNotBlank() },
            requiresInstitutionSetup = institution.setupStatus != SetupStatus.COMPLETE.name ||
                institution.currentSessionId.isBlank(),
        )

        sessionStore.update(session)
        return if (session.requiresInstitutionSetup) {
            SessionState.SetupRequired(session)
        } else {
            SessionState.Authenticated(session)
        }
    }

    private suspend fun resolveFacultySession(firebaseUserUid: String, userProfile: UserProfile): SessionState {
        val institutionId = userProfile.institutionId.ifBlank { userProfile.institutionalId }
        if (institutionId.isBlank()) {
            return unauthorized("Faculty account is missing its institution link.")
        }

        val institution = dataSource.institution(institutionId).get().await().toObject(Institution::class.java)
            ?: return unauthorized("The linked institution could not be found.")

        if (institution.status != RecordStatus.ACTIVE.name) {
            return unauthorized("This institution is currently inactive.")
        }

        val linkedFacultyId = userProfile.linkedFacultyId
        if (linkedFacultyId.isBlank()) {
            return unauthorized("Faculty profile is not linked to this account.")
        }

        val faculty = dataSource.facultyDocument(institutionId, linkedFacultyId).get().await()
            .toObject(FacultyMember::class.java)
            ?: return unauthorized("Faculty profile was not found.")

        if (faculty.authUid != firebaseUserUid) {
            return unauthorized("Faculty profile linkage is invalid.")
        }

        if (faculty.status != RecordStatus.ACTIVE.name) {
            return unauthorized("Faculty profile is inactive.")
        }

        val session = buildSession(
            userProfile = userProfile,
            userRole = UserRole.FACULTY,
            institutionId = institution.id,
            institutionName = institution.name,
            linkedFacultyId = faculty.id,
            currentSessionId = institution.currentSessionId.takeIf { it.isNotBlank() },
            requiresInstitutionSetup = false,
        )
        sessionStore.update(session)
        return SessionState.Authenticated(session)
    }

    private fun buildSession(
        userProfile: UserProfile,
        userRole: UserRole,
        institutionId: String?,
        institutionName: String,
        linkedFacultyId: String?,
        currentSessionId: String?,
        requiresInstitutionSetup: Boolean,
    ): AppSession = AppSession(
        userId = userProfile.uid,
        displayName = userProfile.displayName.ifBlank { userProfile.email.substringBefore("@") },
        email = userProfile.email,
        userRole = userRole.name,
        institutionId = institutionId,
        linkedFacultyId = linkedFacultyId,
        institutionName = institutionName,
        currentSessionId = currentSessionId,
        requiresInstitutionSetup = requiresInstitutionSetup,
    )

    private fun resolveUserRole(userProfile: UserProfile): UserRole {
        val normalizedRole = userProfile.role.trim().uppercase()
        if (normalizedRole == UserRole.FACULTY.name) return UserRole.FACULTY
        if (normalizedRole == UserRole.HEADMASTER.name) return UserRole.HEADMASTER
        return if (userProfile.userType.trim().equals(UserRole.FACULTY.name, ignoreCase = true)) {
            UserRole.FACULTY
        } else {
            UserRole.HEADMASTER
        }
    }

    private fun generateEmployeeCode(
        transaction: Transaction,
        institutionId: String,
        joiningDate: String,
        currentTime: Long,
    ): String {
        val yearCode = joiningDate.takeLast(4).takeLast(2)
        val counterRef = dataSource.institutionCounterDocument(institutionId, "facultyEmployeeCodes_$yearCode")
        val nextSequence = (transaction.get(counterRef).getLong("lastSequence") ?: 0L) + 1L

        transaction.set(
            counterRef,
            mapOf(
                "counterId" to "facultyEmployeeCodes_$yearCode",
                "institutionId" to institutionId,
                "yearCode" to yearCode,
                "lastSequence" to nextSequence,
                "updatedAt" to currentTime,
            ),
        )

        return buildString {
            append('2')
            append(yearCode)
            append(nextSequence.toString().padStart(4, '0'))
        }
    }

    private fun unauthorized(message: String): SessionState {
        auth.signOut()
        sessionStore.clear()
        return SessionState.Unauthorized(message)
    }

    private suspend fun findFacultyByEmail(
        institutionId: String,
        email: String,
    ): FacultyMember? = dataSource.faculty(institutionId)
        .get()
        .await()
        .documents
        .mapNotNull { it.toObject(FacultyMember::class.java) }
        .firstOrNull { faculty ->
            faculty.status != RecordStatus.ARCHIVED.name &&
                faculty.email.equals(email.trim(), ignoreCase = true)
        }
}
