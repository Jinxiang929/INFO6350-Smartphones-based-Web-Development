package com.example.week7speechtools.auth

object SessionStore {

    // Keep the application JWT in memory for the current process.
    private var accessToken: String? = null

    fun saveAccessToken(token: String) {
        require(token.isNotBlank()) {
            "Access token must not be blank"
        }

        accessToken = token
    }

    fun getAccessToken(): String? {
        return accessToken
    }

    fun clear() {
        accessToken = null
    }
}