package com.example.edutute.data.repository

import com.example.edutute.core.util.InstitutionSetupUtils
import com.example.edutute.core.util.ValidationUtils
import com.example.edutute.data.auth.SessionStore
import com.example.edutute.data.firestore.FirestoreDataSource
import com.example.edutute.domain.access.RoleAccessManager
import com.example.edutute.domain.model.AcademicSession
import com.example.edutute.domain.model.Institution
import com.example.edutute.domain.model.InstitutionDraft
import com.example.edutute.domain.model.RecordStatus
import com.example.edutute.domain.model.SessionStatus
import com.example.edutute.domain.model.SetupStatus
import com.example.edutute.domain.model.UserRole
import com.example.edutute.domain.repository.InstitutionRepository
import com.example.edutute.domain.repository.LocationValidationRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.tasks.await

class FirestoreInstitutionRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val dataSource: FirestoreDataSource,
    private val sessionStore: SessionStore,
    private val roleAccessManager: RoleAccessManager,
    private val locationValidationRepository: LocationValidationRepository,
) : InstitutionRepository {

    private companion object {
        const val INSTITUTION_ID_COUNTER = "institutionIds"
    }

    override suspend fun getInstitution(): Institution? {
        val institutionId = sessionStore.session.value?.institutionId ?: return null
        return dataSource.institution(institutionId).get().await().toObject(Institution::class.java)
    }

    override suspend fun getCurrentAcademicSession(): AcademicSession? {
        val institutionId = sessionStore.session.value?.institutionId ?: return null
        val sessionId = sessionStore.session.value?.currentSessionId ?: return null
        return dataSource.session(institutionId, sessionId).get().await().toObject(AcademicSession::class.java)
    }

    override suspend fun listAcademicSessions(): List<AcademicSession> {
        val institutionId = sessionStore.session.value?.institutionId ?: return emptyList()
        return dataSource.sessions(institutionId)
            .orderBy("createdAt")
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(AcademicSession::class.java) }
    }

    override suspend fun saveInstitution(draft: InstitutionDraft): Institution {
        roleAccessManager.requireHeadmaster("edit institution details")
        ValidationUtils.requireNotBlank(draft.name, "Institution name")
        ValidationUtils.requireNotBlank(draft.addressLine1, "Address")
        ValidationUtils.requireNotBlank(draft.city, "City")
        ValidationUtils.requireNotBlank(draft.state, "State")
        ValidationUtils.requireNotBlank(draft.postalCode, "Postal code")
        ValidationUtils.requireNotBlank(draft.contactEmail, "Contact email")
        ValidationUtils.requireNotBlank(draft.contactPhone, "Contact phone")
        if (!ValidationUtils.isValidEmail(draft.contactEmail)) {
            throw IllegalArgumentException("Contact email is invalid.")
        }
        if (!ValidationUtils.isValidIndianPhone(draft.contactPhone)) {
            throw IllegalArgumentException("Enter a valid Indian mobile number.")
        }

        val verifiedAddress = locationValidationRepository.validateIndianAddress(
            city = draft.city,
            state = draft.state,
            postalCode = draft.postalCode,
        )

        val currentUser = auth.currentUser ?: throw IllegalStateException("No authenticated user found.")
        val currentTime = System.currentTimeMillis()
        val sessionName = draft.currentAcademicSessionName.trim()
            .ifBlank { InstitutionSetupUtils.currentAcademicSessionLabel() }
        val userRef = dataSource.user(currentUser.uid)

        val existingInstitutionId = sessionStore.session.value?.institutionId
        val existingInstitution = existingInstitutionId
            ?.let { dataSource.institution(it).get().await().toObject(Institution::class.java) }

        return if (existingInstitution == null) {
            createInstitution(
                currentUserUid = currentUser.uid,
                currentUserEmail = currentUser.email ?: draft.contactEmail.trim(),
                userRef = userRef,
                sessionName = sessionName,
                currentTime = currentTime,
                verifiedAddressCity = verifiedAddress.city,
                verifiedAddressState = verifiedAddress.state,
                verifiedAddressPostalCode = verifiedAddress.postalCode,
                draft = draft,
            )
        } else {
            updateInstitution(
                currentUserUid = currentUser.uid,
                currentUserEmail = currentUser.email ?: draft.contactEmail.trim(),
                institution = existingInstitution,
                userRef = userRef,
                sessionName = sessionName,
                currentTime = currentTime,
                verifiedAddressCity = verifiedAddress.city,
                verifiedAddressState = verifiedAddress.state,
                verifiedAddressPostalCode = verifiedAddress.postalCode,
                draft = draft,
            )
        }
    }

    private suspend fun createInstitution(
        currentUserUid: String,
        currentUserEmail: String,
        userRef: DocumentReference,
        sessionName: String,
        currentTime: Long,
        verifiedAddressCity: String,
        verifiedAddressState: String,
        verifiedAddressPostalCode: String,
        draft: InstitutionDraft,
    ): Institution = firestore.runTransaction { transaction ->
        val institutionId = nextInstitutionId(transaction, currentTime)
        val institutionRef = dataSource.institution(institutionId)
        val sessionRef = dataSource.sessions(institutionId).document()

        val institution = Institution(
            id = institutionId,
            name = draft.name.trim(),
            headmasterUid = currentUserUid,
            addressLine1 = draft.addressLine1.trim(),
            addressLine2 = draft.addressLine2.trim(),
            city = verifiedAddressCity,
            state = verifiedAddressState,
            postalCode = verifiedAddressPostalCode,
            contactEmail = draft.contactEmail.trim(),
            contactPhone = ValidationUtils.normalizeIndianPhoneDigits(draft.contactPhone),
            currentSessionId = sessionRef.id,
            setupStatus = SetupStatus.COMPLETE.name,
            status = RecordStatus.ACTIVE.name,
            createdAt = currentTime,
            updatedAt = currentTime,
        )

        val academicSession = AcademicSession(
            id = sessionRef.id,
            name = sessionName,
            status = SessionStatus.ACTIVE.name,
            createdAt = currentTime,
            updatedAt = currentTime,
        )

        transaction.set(institutionRef, institution)
        transaction.set(sessionRef, academicSession)
        transaction.set(
            userRef,
            mapOf(
                "uid" to currentUserUid,
                "email" to currentUserEmail,
                "institutionId" to institutionId,
                "institutionalId" to institutionId,
                "role" to UserRole.HEADMASTER.name,
                "userType" to UserRole.HEADMASTER.name.lowercase(),
                "accountStatus" to RecordStatus.ACTIVE.name,
                "updatedAt" to currentTime,
            ),
            SetOptions.merge(),
        )

        institution
    }.await()

    private suspend fun updateInstitution(
        currentUserUid: String,
        currentUserEmail: String,
        institution: Institution,
        userRef: DocumentReference,
        sessionName: String,
        currentTime: Long,
        verifiedAddressCity: String,
        verifiedAddressState: String,
        verifiedAddressPostalCode: String,
        draft: InstitutionDraft,
    ): Institution {
        val institutionRef = dataSource.institution(institution.id)
        val currentSession = institution.currentSessionId
            .takeIf { it.isNotBlank() }
            ?.let { dataSource.session(institution.id, it).get().await().toObject(AcademicSession::class.java) }
        val existingSessionForName = dataSource.sessions(institution.id)
            .whereEqualTo("name", sessionName)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()

        val sessionRef = when {
            currentSession?.name == sessionName -> dataSource.session(institution.id, currentSession.id)
            existingSessionForName != null -> dataSource.session(institution.id, existingSessionForName.id)
            else -> dataSource.sessions(institution.id).document()
        }
        val shouldReadSessionInTransaction = currentSession?.name == sessionName || existingSessionForName != null

        return firestore.runTransaction { transaction ->
            val institutionBeforeSave = transaction.get(institutionRef).toObject(Institution::class.java)
                ?: throw IllegalStateException("Institution was not found.")
            val sessionBeforeSave = if (shouldReadSessionInTransaction) {
                transaction.get(sessionRef).toObject(AcademicSession::class.java)
            } else {
                null
            }

            val updatedInstitution = Institution(
                id = institutionRef.id,
                name = draft.name.trim(),
                headmasterUid = currentUserUid,
                addressLine1 = draft.addressLine1.trim(),
                addressLine2 = draft.addressLine2.trim(),
                city = verifiedAddressCity,
                state = verifiedAddressState,
                postalCode = verifiedAddressPostalCode,
                contactEmail = draft.contactEmail.trim(),
                contactPhone = ValidationUtils.normalizeIndianPhoneDigits(draft.contactPhone),
                currentSessionId = sessionRef.id,
                setupStatus = SetupStatus.COMPLETE.name,
                status = RecordStatus.ACTIVE.name,
                createdAt = institutionBeforeSave.createdAt,
                updatedAt = currentTime,
            )

            val academicSession = AcademicSession(
                id = sessionRef.id,
                name = sessionName,
                status = SessionStatus.ACTIVE.name,
                createdAt = sessionBeforeSave?.createdAt ?: currentTime,
                updatedAt = currentTime,
            )

            transaction.set(institutionRef, updatedInstitution)
            transaction.set(sessionRef, academicSession)
            transaction.set(
                userRef,
                mapOf(
                    "uid" to currentUserUid,
                    "email" to currentUserEmail,
                    "institutionId" to institutionRef.id,
                    "institutionalId" to institutionRef.id,
                    "role" to UserRole.HEADMASTER.name,
                    "userType" to UserRole.HEADMASTER.name.lowercase(),
                    "accountStatus" to RecordStatus.ACTIVE.name,
                    "updatedAt" to currentTime,
                ),
                SetOptions.merge(),
            )

            val previousSessionId = institutionBeforeSave.currentSessionId
            if (previousSessionId.isNotBlank() && previousSessionId != sessionRef.id) {
                transaction.update(
                    dataSource.session(institutionRef.id, previousSessionId),
                    mapOf(
                        "status" to SessionStatus.ARCHIVED.name,
                        "updatedAt" to currentTime,
                    ),
                )
            }

            updatedInstitution
        }.await()
    }

    private fun nextInstitutionId(
        transaction: Transaction,
        currentTime: Long,
    ): String {
        val counterRef = dataSource.counterDocument(INSTITUTION_ID_COUNTER)
        val nextSequence = (transaction.get(counterRef).getLong("lastSequence") ?: 0L) + 1L

        transaction.set(
            counterRef,
            mapOf(
                "counterId" to INSTITUTION_ID_COUNTER,
                "lastSequence" to nextSequence,
                "updatedAt" to currentTime,
            ),
        )

        return InstitutionSetupUtils.generateInstitutionId(nextSequence)
    }
}
