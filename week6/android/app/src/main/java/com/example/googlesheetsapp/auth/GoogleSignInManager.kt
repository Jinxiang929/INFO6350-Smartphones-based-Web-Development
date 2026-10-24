
package com.example.googlesheetsapp.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.googlesheetsapp.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

class GoogleSignInManager(
    private val activity: Activity
) {
    private val credentialManager =
        CredentialManager.create(activity)

    suspend fun signIn(): GoogleIdTokenCredential {

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(
                activity.getString(R.string.web_client_id)
            )
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = credentialManager.getCredential(
            context = activity,
            request = request
        )

        val credential = result.credential

        if (
            credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(
                credential.data
            )
        }

        throw IllegalStateException(
            "Unexpected credential type"
        )
    }
}
