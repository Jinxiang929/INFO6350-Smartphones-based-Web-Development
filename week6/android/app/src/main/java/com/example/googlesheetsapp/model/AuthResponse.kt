package com.example.googlesheetsapp.model

data class AuthResponse(
    val success: Boolean,
    val user: GoogleUser?
)

data class GoogleUser(
    val google_id: String,
    val email: String?,
    val name: String?
)