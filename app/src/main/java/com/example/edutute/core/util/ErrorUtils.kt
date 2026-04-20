package com.example.edutute.core.util

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

private const val CONFIGURATION_NOT_FOUND = "CONFIGURATION_NOT_FOUND"

fun Throwable.userMessage(defaultMessage: String = "Something went wrong."): String {
    val rawMessage = message.orEmpty()

    return when {
        rawMessage.contains(CONFIGURATION_NOT_FOUND, ignoreCase = true) -> {
            "Firebase Authentication is not configured for this app yet. Enable Email/Password " +
                "sign-in in Firebase Console and verify that this app is connected to the correct " +
                "Firebase project."
        }
        this is FirebaseAuthUserCollisionException -> {
            "An account with this email already exists."
        }
        this is FirebaseAuthWeakPasswordException -> {
            "Choose a stronger password with at least 6 characters."
        }
        this is FirebaseAuthInvalidCredentialsException -> {
            "The email address or password is invalid."
        }
        this is FirebaseAuthInvalidUserException -> {
            "No active account was found for these credentials."
        }
        this is FirebaseTooManyRequestsException -> {
            "Too many attempts were made. Please wait a moment and try again."
        }
        this is FirebaseNetworkException -> {
            "A network error occurred. Check your internet connection and try again."
        }
        this is FirebaseAuthException && errorCode.equals("ERROR_OPERATION_NOT_ALLOWED", ignoreCase = true) -> {
            "This sign-in method is disabled in Firebase Console."
        }
        rawMessage.isNotBlank() -> rawMessage
        else -> defaultMessage
    }
}
