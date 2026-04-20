package com.example.edutute.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edutute.domain.model.AppSession
import com.example.edutute.domain.model.SessionState
import com.example.edutute.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val mutableSessionState = MutableStateFlow<SessionState>(SessionState.Loading)
    val sessionState: StateFlow<SessionState> = mutableSessionState.asStateFlow()

    val currentSession: StateFlow<AppSession?> = authRepository.currentSession

    fun refreshSession() {
        viewModelScope.launch {
            mutableSessionState.value = SessionState.Loading
            mutableSessionState.value = authRepository.resolveCurrentSession()
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            mutableSessionState.value = SessionState.Unauthenticated
        }
    }
}
