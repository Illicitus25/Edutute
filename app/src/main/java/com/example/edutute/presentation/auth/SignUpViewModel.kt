package com.example.edutute.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edutute.core.ui.UiState
import com.example.edutute.core.util.ValidationUtils
import com.example.edutute.core.util.userMessage
import com.example.edutute.domain.model.AuthRegistrationRequest
import com.example.edutute.domain.model.UserRole
import com.example.edutute.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SignUpFieldErrors(
    val userType: String? = null,
    val institutionalId: String? = null,
    val fullName: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
    val qualification: String? = null,
    val password: String? = null,
    val confirmPassword: String? = null,
)

class SignUpViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val mutableSignUpState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val signUpState: StateFlow<UiState<Unit>> = mutableSignUpState.asStateFlow()

    private val mutableMessage = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = mutableMessage.asStateFlow()

    private val mutableFieldErrors = MutableStateFlow(SignUpFieldErrors())
    val fieldErrors: StateFlow<SignUpFieldErrors> = mutableFieldErrors.asStateFlow()

    fun register(
        userType: String,
        institutionalId: String,
        fullName: String,
        email: String,
        phoneNumber: String,
        qualification: String,
        password: String,
        confirmPassword: String,
    ) {
        val resolvedRole = userType.toUserRoleOrNull()
        val errors = SignUpFieldErrors(
            userType = if (resolvedRole == null) "Select a user type." else null,
            institutionalId = if (resolvedRole == UserRole.FACULTY && institutionalId.trim().isBlank()) {
                "Institutional ID is required."
            } else {
                null
            },
            fullName = if (fullName.trim().isBlank()) "Full name is required." else null,
            email = if (email.trim().isBlank() || !ValidationUtils.isValidEmail(email.trim())) {
                "Enter a valid email address."
            } else {
                null
            },
            phoneNumber = if (!ValidationUtils.isValidIndianPhone(phoneNumber)) {
                "Enter a valid Indian mobile number."
            } else {
                null
            },
            qualification = if (resolvedRole == UserRole.FACULTY && qualification.trim().isBlank()) {
                "Qualification is required."
            } else {
                null
            },
            password = if (password.length < 6) "Password must be at least 6 characters." else null,
            confirmPassword = if (password != confirmPassword) "Passwords do not match." else null,
        )

        mutableFieldErrors.value = errors
        if (listOf(
                errors.userType,
                errors.institutionalId,
                errors.fullName,
                errors.email,
                errors.phoneNumber,
                errors.qualification,
                errors.password,
                errors.confirmPassword,
            ).any { it != null }
        ) {
            mutableSignUpState.value = UiState.Error("Please correct the highlighted fields.")
            return
        }

        viewModelScope.launch {
            mutableSignUpState.value = UiState.Loading
            mutableSignUpState.value = try {
                authRepository.register(
                    AuthRegistrationRequest(
                        userRole = resolvedRole ?: UserRole.HEADMASTER,
                        institutionalId = institutionalId.trim().uppercase(),
                        fullName = fullName.trim(),
                        email = email.trim(),
                        phoneNumber = phoneNumber,
                        qualification = qualification.trim(),
                        password = password,
                    ),
                )
                mutableFieldErrors.value = SignUpFieldErrors()
                mutableMessage.value = "Account created successfully."
                UiState.Success(Unit)
            } catch (throwable: Throwable) {
                UiState.Error(throwable.userMessage("Unable to create account."))
            }
        }
    }

    fun clearState() {
        mutableSignUpState.value = UiState.Idle
    }

    fun clearFieldError(field: SignUpField) {
        mutableFieldErrors.value = when (field) {
            SignUpField.USER_TYPE -> mutableFieldErrors.value.copy(userType = null)
            SignUpField.INSTITUTIONAL_ID -> mutableFieldErrors.value.copy(institutionalId = null)
            SignUpField.FULL_NAME -> mutableFieldErrors.value.copy(fullName = null)
            SignUpField.EMAIL -> mutableFieldErrors.value.copy(email = null)
            SignUpField.PHONE_NUMBER -> mutableFieldErrors.value.copy(phoneNumber = null)
            SignUpField.QUALIFICATION -> mutableFieldErrors.value.copy(qualification = null)
            SignUpField.PASSWORD -> mutableFieldErrors.value.copy(password = null)
            SignUpField.CONFIRM_PASSWORD -> mutableFieldErrors.value.copy(confirmPassword = null)
        }
    }

    fun clearMessage() {
        mutableMessage.value = null
    }

    enum class SignUpField {
        USER_TYPE,
        INSTITUTIONAL_ID,
        FULL_NAME,
        EMAIL,
        PHONE_NUMBER,
        QUALIFICATION,
        PASSWORD,
        CONFIRM_PASSWORD,
    }

    private fun String.toUserRoleOrNull(): UserRole? = when (trim().uppercase()) {
        UserRole.HEADMASTER.name -> UserRole.HEADMASTER
        UserRole.FACULTY.name -> UserRole.FACULTY
        else -> null
    }
}
