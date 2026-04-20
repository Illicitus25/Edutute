package com.example.edutute.data.repository

import android.content.Context
import com.example.edutute.core.util.ValidationUtils
import com.example.edutute.domain.access.RoleAccessManager
import com.example.edutute.data.auth.SessionStore
import com.example.edutute.data.firestore.FirestoreDataSource
import com.example.edutute.domain.model.FacultyDraft
import com.example.edutute.domain.model.FacultyMember
import com.example.edutute.domain.model.FacultySaveResult
import com.example.edutute.domain.model.RecordStatus
import com.example.edutute.domain.model.UserProfile
import com.example.edutute.domain.model.UserRole
import com.example.edutute.domain.repository.FacultyRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirestoreFacultyRepository(
    private val appContext: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val dataSource: FirestoreDataSource,
    private val sessionStore: SessionStore,
    private val roleAccessManager: RoleAccessManager,
) : FacultyRepository {

    override suspend fun listFaculty(): List<FacultyMember> {
        val institutionId = sessionStore.requireInstitutionId()
        val faculty = dataSource.faculty(institutionId)
            .whereEqualTo("status", RecordStatus.ACTIVE.name)
            .orderBy("fullNameLower")
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(FacultyMember::class.java) }
        if (roleAccessManager.currentContext().isHeadmaster) return faculty

        val ownFacultyId = roleAccessManager.requireFacultyId()
        return faculty.filter { it.id == ownFacultyId }
    }

    override suspend fun searchFaculty(query: String): List<FacultyMember> {
        val normalizedQuery = ValidationUtils.normalize(query)
        return listFaculty().filter { faculty ->
            normalizedQuery.isBlank() ||
                faculty.fullNameLower.contains(normalizedQuery) ||
                faculty.employeeCode.lowercase().contains(normalizedQuery)
        }
    }

    override suspend fun getFacultyById(id: String): FacultyMember? {
        roleAccessManager.requireFacultyRecordAccess(id)
        val institutionId = sessionStore.requireInstitutionId()
        return dataSource.facultyDocument(institutionId, id).get().await().toObject(FacultyMember::class.java)
    }

    override suspend fun saveFaculty(draft: FacultyDraft): FacultySaveResult {
        roleAccessManager.requireHeadmaster("create or edit faculty records")
        ValidationUtils.requireNotBlank(draft.fullName, "Faculty name")
        ValidationUtils.requireNotBlank(draft.joiningDate, "Joining date")
        if (!ValidationUtils.isValidDayMonthYear(draft.joiningDate)) {
            throw IllegalArgumentException("Joining date must be in dd/mm/yyyy format.")
        }
        if (!ValidationUtils.isValidEmail(draft.email)) {
            throw IllegalArgumentException("Faculty email is invalid.")
        }

        val institutionId = sessionStore.requireInstitutionId()
        ensureFacultyEmailAvailable(
            institutionId = institutionId,
            facultyId = draft.id,
            email = draft.email,
        )

        val existingFaculty = draft.id.takeIf { it.isNotBlank() }?.let { facultyId ->
            dataSource.facultyDocument(institutionId, facultyId).get().await().toObject(FacultyMember::class.java)
        }
        if (
            existingFaculty != null &&
            existingFaculty.authUid.isNotBlank() &&
            !existingFaculty.email.equals(draft.email.trim(), ignoreCase = true)
        ) {
            throw IllegalArgumentException("Faculty email cannot be changed after the account has been linked.")
        }

        val document = if (draft.id.isBlank()) {
            dataSource.faculty(institutionId).document()
        } else {
            dataSource.facultyDocument(institutionId, draft.id)
        }
        val currentTime = System.currentTimeMillis()

        firestore.runTransaction { transaction ->
            val existing = transaction.get(document).toObject(FacultyMember::class.java)
            val employeeCode = existing?.employeeCode?.takeIf { it.isNotBlank() }
                ?: generateEmployeeCode(
                    transaction = transaction,
                    institutionId = institutionId,
                    joiningDate = draft.joiningDate.trim(),
                    currentTime = currentTime,
                )

            val faculty = FacultyMember(
                id = document.id,
                institutionId = institutionId,
                authUid = existing?.authUid.orEmpty(),
                accountStatus = existing?.accountStatus
                    ?: if (existing?.authUid?.isNotBlank() == true) RecordStatus.ACTIVE.name else RecordStatus.INVITED.name,
                fullName = draft.fullName.trim(),
                fullNameLower = ValidationUtils.normalize(draft.fullName),
                email = draft.email.trim(),
                phoneNumber = draft.phoneNumber.trim(),
                employeeCode = employeeCode,
                qualification = draft.qualification.trim(),
                joiningDate = draft.joiningDate.trim(),
                inviteSentAt = existing?.inviteSentAt ?: 0L,
                status = existing?.status ?: RecordStatus.ACTIVE.name,
                createdAt = existing?.createdAt ?: currentTime,
                updatedAt = currentTime,
            )

            transaction.set(document, faculty)
            faculty
        }.await()

        val savedFaculty = dataSource.facultyDocument(institutionId, document.id).get().await().toObject(FacultyMember::class.java)
            ?: throw IllegalStateException("Failed to save faculty.")
        if (savedFaculty.authUid.isNotBlank()) {
            return FacultySaveResult(faculty = savedFaculty, activationEmailSent = false)
        }

        return ensureFacultyAccountProvisioned(savedFaculty)
    }

    override suspend fun deleteFaculty(id: String) {
        roleAccessManager.requireHeadmaster("delete faculty records")
        val institutionId = sessionStore.requireInstitutionId()
        dataSource.facultyDocument(institutionId, id)
            .update(
                mapOf(
                    "status" to RecordStatus.ARCHIVED.name,
                    "updatedAt" to System.currentTimeMillis(),
                ),
            )
            .await()
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

    private suspend fun ensureFacultyEmailAvailable(
        institutionId: String,
        facultyId: String,
        email: String,
    ) {
        val normalizedEmail = email.trim()
        val duplicateFaculty = dataSource.faculty(institutionId)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(FacultyMember::class.java) }
            .firstOrNull { faculty ->
                faculty.id != facultyId &&
                    faculty.status != RecordStatus.ARCHIVED.name &&
                    faculty.email.equals(normalizedEmail, ignoreCase = true)
            }
        if (duplicateFaculty != null) {
            throw IllegalArgumentException("A faculty record with this email already exists in this institution.")
        }
    }

    private suspend fun ensureFacultyAccountProvisioned(faculty: FacultyMember): FacultySaveResult {
        val matchingUserProfile = findFacultyUserProfileByEmail(
            institutionId = faculty.institutionId,
            email = faculty.email,
        )

        val authUid = when {
            matchingUserProfile != null && matchingUserProfile.linkedFacultyId.isNotBlank() &&
                matchingUserProfile.linkedFacultyId != faculty.id -> {
                throw IllegalArgumentException("This email is already linked to another faculty account.")
            }

            matchingUserProfile != null -> matchingUserProfile.uid
            else -> createSecondaryFacultyAuthAccount(faculty.email)
        }

        val invitationTime = System.currentTimeMillis()
        auth.sendPasswordResetEmail(faculty.email.trim()).await()
        dataSource.users().firestore.runTransaction { transaction ->
            val facultyRef = dataSource.facultyDocument(faculty.institutionId, faculty.id)
            val latestFaculty = transaction.get(facultyRef).toObject(FacultyMember::class.java)
                ?: throw IllegalStateException("Faculty record was not found after saving.")
            transaction.set(
                facultyRef,
                latestFaculty.copy(
                    authUid = authUid,
                    accountStatus = RecordStatus.INVITED.name,
                    inviteSentAt = invitationTime,
                    updatedAt = invitationTime,
                ),
            )

            val userRef = dataSource.user(authUid)
            val existingUser = transaction.get(userRef).toObject(UserProfile::class.java)
            val currentTime = existingUser?.createdAt?.takeIf { it > 0L } ?: invitationTime
            transaction.set(
                userRef,
                UserProfile(
                    uid = authUid,
                    email = faculty.email.trim(),
                    fullName = faculty.fullName,
                    displayName = faculty.fullName,
                    phoneNumber = faculty.phoneNumber,
                    institutionId = faculty.institutionId,
                    institutionalId = faculty.institutionId,
                    userType = UserRole.FACULTY.name.lowercase(),
                    role = UserRole.FACULTY.name,
                    linkedFacultyId = faculty.id,
                    qualification = faculty.qualification,
                    joiningDate = faculty.joiningDate,
                    joiningDateTimestamp = currentTime,
                    accountStatus = RecordStatus.INVITED.name,
                    lastLoginAt = existingUser?.lastLoginAt ?: 0L,
                    createdAt = currentTime,
                    updatedAt = invitationTime,
                ),
            )
            Unit
        }.await()

        val linkedFaculty = dataSource.facultyDocument(faculty.institutionId, faculty.id).get().await()
            .toObject(FacultyMember::class.java)
            ?: throw IllegalStateException("Faculty account could not be linked.")
        return FacultySaveResult(
            faculty = linkedFaculty,
            activationEmailSent = true,
        )
    }

    private suspend fun findFacultyUserProfileByEmail(
        institutionId: String,
        email: String,
    ): UserProfile? = dataSource.users()
        .whereEqualTo("email", email.trim())
        .get()
        .await()
        .documents
        .mapNotNull { it.toObject(UserProfile::class.java) }
        .firstOrNull { profile ->
            profile.institutionId == institutionId &&
                profile.role.equals(UserRole.FACULTY.name, ignoreCase = true)
        }

    private suspend fun createSecondaryFacultyAuthAccount(email: String): String {
        val appName = "faculty-invite-${UUID.randomUUID()}"
        val options = FirebaseOptions.fromResource(appContext)
            ?: throw IllegalStateException("Firebase configuration is unavailable for faculty invitations.")
        val secondaryApp = FirebaseApp.initializeApp(appContext, options, appName)
            ?: throw IllegalStateException("Unable to initialize Firebase for faculty invitations.")
        val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)

        return try {
            val tempPassword = UUID.randomUUID().toString() + "aA1!"
            secondaryAuth.createUserWithEmailAndPassword(email.trim(), tempPassword).await()
                .user?.uid
                ?: throw IllegalStateException("Faculty invitation account could not be created.")
        } catch (throwable: Throwable) {
            if (throwable is FirebaseAuthUserCollisionException) {
                throw IllegalArgumentException(
                    "An account with this email already exists. Ask the faculty member to sign in or reset their password.",
                )
            }
            throw throwable
        } finally {
            secondaryAuth.signOut()
            secondaryApp.delete()
        }
    }
}
