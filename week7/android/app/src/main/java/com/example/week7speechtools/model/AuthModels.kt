package com.example.week7speechtools.model

import com.google.gson.annotations.SerializedName

data class GoogleSignInRequest(
    @SerializedName("id_token")
    val idToken: String
)

data class AuthUser(
    @SerializedName("google_id")
    val googleId: String = "",
    val email: String? = null,
    val name: String? = null
)

data class GoogleSignInResponse(
    val success: Boolean = false,
    @SerializedName("access_token")
    val accessToken: String? = null,
    val user: AuthUser? = null
)

data class MeResponse(
    val success: Boolean = false,
    val user: AuthUser? = null
)