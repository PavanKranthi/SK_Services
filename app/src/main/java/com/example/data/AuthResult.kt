package com.example.data

/** Result of a cloud or offline sign-in attempt (no secrets in messages). */
sealed class AuthResult {
    data object Success : AuthResult()
    data object InvalidCredentials : AuthResult()
    data object NetworkError : AuthResult()
}
