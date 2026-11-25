package com.example.week7speechtools.repository

import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.week7speechtools.auth.SessionStore
import com.example.week7speechtools.model.AuthUser
import com.example.week7speechtools.model.GoogleSignInRequest
import com.example.week7speechtools.network.ApiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import retrofit2.HttpException

class AuthRepository(
    private val apiService: ApiService,
    private val credentialManager: CredentialManager
) {

    suspend fun signInWithGoogle(idToken: String): AuthUser {
        require(idToken.isNotBlank()) {
            "Google ID token is required"
        }

        val response = apiService.signInWithGoogle(
            request = GoogleSignInRequest(idToken = idToken)
        )

        val accessToken = response.accessToken
        val user = response.user

        check(
            response.success &&
                    !accessToken.isNullOrBlank() &&
                    user != null &&
                    user.googleId.isNotBlank()
        ) {
            "The backend returned an invalid sign-in response"
        }

        SessionStore.saveAccessToken(accessToken)

        return user
    }

    suspend fun logout(): String {
        val accessToken = SessionStore.getAccessToken()

        try {
            val serverConfirmed = if (accessToken.isNullOrBlank()) {
                true
            } else {
                try {
                    withTimeout(15_000L) {
                        apiService.logout(
                            authorization = "Bearer $accessToken"
                        ).success
                    }
                } catch (error: TimeoutCancellationException) {
                    false
                } catch (error: CancellationException) {
                    throw error
                } catch (error: HttpException) {
                    // A rejected token can no longer authenticate this session.
                    error.code() == 401
                } catch (error: Exception) {
                    false
                }
            }

            val providerCleared = try {
                withTimeout(5_000L) {
                    credentialManager.clearCredentialState(
                        ClearCredentialStateRequest()
                    )
                }

                true
            } catch (error: TimeoutCancellationException) {
                false
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                false
            }

            return when {
                !serverConfirmed -> {
                    "Signed out on this device. Server sign-out could not be confirmed."
                }

                !providerCleared -> {
                    "Signed out. Google account selection could not be reset."
                }

                else -> {
                    "Signed out successfully."
                }
            }
        } finally {
            SessionStore.clear()
        }
    }

    fun clearSession() {
        SessionStore.clear()
    }
}