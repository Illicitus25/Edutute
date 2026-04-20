package com.example.edutute.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edutute.core.ui.UiState
import com.example.edutute.core.util.ValidationUtils
import com.example.edutute.core.util.userMessage
import com.example.edutute.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val mutableLoginState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val loginState: StateFlow<UiState<Unit>> = mutableLoginState.asStateFlow()

    private val mutableMessage = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = mutableMessage.asStateFlow()

    fun login(email: String, password: String) {
        if (!ValidationUtils.isValidEmail(email.trim())) {
            mutableLoginState.value = UiState.Error("Enter a valid email address.")
            return
        }
        if (password.isBlank()) {
            mutableLoginState.value = UiState.Error("Password is required.")
            return
        }

        viewModelScope.launch {
            mutableLoginState.value = UiState.Loading
            mutableLoginState.value = try {
                authRepository.login(email, password)
                UiState.Success(Unit)
            } catch (throwable: Throwable) {
                UiState.Error(throwable.userMessage("Login failed."))
            }
        }
    }

    fun sendPasswordReset(email: String) {
        if (!ValidationUtils.isValidEmail(email.trim()) || email.isBlank()) {
            mutableMessage.value = "Enter a valid email address first."
            return
        }
        viewModelScope.launch {
            mutableMessage.value = try {
                authRepository.sendPasswordReset(email)
                "Password reset link sent to $email."
            } catch (throwable: Throwable) {
                throwable.userMessage("Unable to send reset email.")
            }
        }
    }

    fun clearLoginState() {
        mutableLoginState.value = UiState.Idle
    }

    fun clearMessage() {
        mutableMessage.value = null
    }
}
