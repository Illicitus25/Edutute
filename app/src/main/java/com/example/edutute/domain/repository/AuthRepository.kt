package com.example.edutute.domain.repository

import com.example.edutute.domain.model.AppSession
import com.example.edutute.domain.model.AuthRegistrationRequest
import com.example.edutute.domain.model.SessionState
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val currentSession: StateFlow<AppSession?>

    suspend fun register(request: AuthRegistrationRequest)

    suspend fun login(email: String, password: String)

    suspend fun logout()

    suspend fun sendPasswordReset(email: String)

    suspend fun resolveCurrentSession(): SessionState
}
