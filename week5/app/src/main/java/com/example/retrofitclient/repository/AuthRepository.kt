
package com.example.retrofitclient.repository

import android.app.Activity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.retrofitclient.R
import com.example.retrofitclient.model.AuthRequest
import com.example.retrofitclient.model.AuthResponse
import com.example.retrofitclient.network.RetrofitClient
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

class AuthRepository {

    // Get Google ID Token from Credential Manager
    suspend fun signInWithGoogle(activity: Activity): String {
        val credentialManager = CredentialManager.create(activity)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(
                activity.getString(R.string.google_web_client_id)
            )
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        try {
            val result = credentialManager.getCredential(
                context = activity,
                request = request
            )

            val credential = result.credential

            if (
                credential is CustomCredential &&
                credential.type ==
                GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCredential =
                    GoogleIdTokenCredential.createFrom(credential.data)

                return googleCredential.idToken
            }

            throw IllegalStateException("Unsupported credential type")

        } catch (e: GetCredentialCancellationException) {
            throw e
        }
    }

    // Verify Google ID Token with Flask
    suspend fun authenticateWithBackend(
        googleIdToken: String
    ): AuthResponse {
        return RetrofitClient.apiService.googleLogin(
            AuthRequest(idToken = googleIdToken)
        )
    }

    // Clear local Google credential session
    suspend fun logout(activity: Activity) {
        val credentialManager = CredentialManager.create(activity)

        credentialManager.clearCredentialState(
            ClearCredentialStateRequest()
        )
    }
}
