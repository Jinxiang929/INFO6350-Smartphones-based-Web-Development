
package com.example.googlesheetsapp

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.unit.dp
import androidx.credentials.exceptions.GetCredentialException
import com.example.googlesheetsapp.auth.GoogleSignInManager
import com.example.googlesheetsapp.model.SubmitRequest
import com.example.googlesheetsapp.network.RetrofitClient
import kotlinx.coroutines.launch
import com.example.googlesheetsapp.model.AuthRequest

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                GoogleSheetsApp()
            }
        }
    }
}

@Composable
fun GoogleSheetsApp() {

    val activity = LocalActivity.current
        ?: error("Google Sign-In requires an Activity")

    val googleSignInManager = remember(activity) {
        GoogleSignInManager(activity)
    }

    var signedInEmail by remember {
        mutableStateOf<String?>(null)
    }

    var googleIdToken by remember {
        mutableStateOf<String?>(null)
    }

    var signInStatus by remember {
        mutableStateOf("")
    }

    var isSigningIn by remember {
        mutableStateOf(false)
    }

    val scope = rememberCoroutineScope()

    if (googleIdToken == null) {

        // Screen 1: Login
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = "Google Sheets App",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text("Please sign in to continue")

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    scope.launch {
                        isSigningIn = true
                        signInStatus = ""

                        try {

                            val credential = googleSignInManager.signIn()

// Send Google ID Token to Flask for verification
                            val authResponse =
                                RetrofitClient.apiService.authenticateGoogle(
                                    AuthRequest(
                                        id_token = credential.idToken
                                    )
                                )

                            if (authResponse.success && authResponse.user != null) {

                                // Only allow access after backend verification
                                signedInEmail = authResponse.user.email
                                    ?: credential.id

                                googleIdToken = credential.idToken

                            } else {
                                signInStatus = "Backend authentication failed"
                            }


                        } catch (e: GetCredentialException) {
                            signInStatus =
                                "Google Sign-In failed: ${e.message}"

                        } catch (e: Exception) {
                            signInStatus =
                                "Error: ${e.message}"

                        } finally {
                            isSigningIn = false
                        }
                    }
                },
                enabled = !isSigningIn,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isSigningIn) {
                        "Signing in..."
                    } else {
                        "Sign in with Google"
                    }
                )
            }

            if (signInStatus.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(signInStatus)
            }
        }

    } else {
        val verifiedToken = googleIdToken ?: return

        SubmitScreen(
            email = signedInEmail.orEmpty(),
            idToken = verifiedToken,
            onLogout = {
                googleIdToken = null
                signedInEmail = null
                signInStatus = ""
            }
        )
    }
}

@Composable
fun SubmitScreen(
    email: String,
    idToken: String,
    onLogout: () -> Unit
) {

    var name by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Google Sheets App",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text("Signed in as: $email")

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onLogout) {
            Text("Log out")
        }

        HorizontalDivider()

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Message") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    result = ""

                    try {
                        val response =
                            RetrofitClient.apiService.submitData(
                                authorization = "Bearer $idToken",
                                request = SubmitRequest(
                                    name = name.trim(),
                                    message = message.trim()
                                )
                            )


                        if (response.success) {
                            name = ""
                            message = ""
                            result = "Data saved successfully"
                        } else {
                            result = "Failed to save data"
                        }

                    } catch (e: Exception) {
                        result = "Error: ${e.message}"

                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading &&
                    name.isNotBlank() &&
                    message.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (isLoading) "Submitting..." else "Submit"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (result.isNotEmpty()) {
            Text(result)
        }
    }
}
