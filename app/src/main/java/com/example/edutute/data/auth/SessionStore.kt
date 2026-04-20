package com.example.edutute.data.auth

import com.example.edutute.domain.model.AppSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionStore {
    private val mutableSession = MutableStateFlow<AppSession?>(null)
    val session: StateFlow<AppSession?> = mutableSession.asStateFlow()

    fun update(session: AppSession?) {
        mutableSession.value = session
    }

    fun clear() {
        mutableSession.value = null
    }

    fun requireSession(): AppSession =
        mutableSession.value ?: throw IllegalStateException("No active admin session.")

    fun requireInstitutionId(): String =
        requireSession().institutionId ?: throw IllegalStateException("Institution setup is incomplete.")

    fun requireAcademicSessionId(): String =
        requireSession().currentSessionId ?: throw IllegalStateException("Academic session is not configured.")
}
