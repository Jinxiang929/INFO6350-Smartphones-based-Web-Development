
package com.example.retrofitclient.model

data class AuthResponse(
    val success: Boolean,
    val user: GoogleUser
)

data class GoogleUser(
    val id: String,
    val name: String,
    val email: String
)
