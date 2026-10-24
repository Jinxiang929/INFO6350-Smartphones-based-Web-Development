package com.example.week7speechtools

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.week7speechtools.ui.LoginScreen
import com.example.week7speechtools.ui.SpeechScreen
import com.example.week7speechtools.ui.theme.Week7SpeechToolsTheme
import com.example.week7speechtools.viewmodel.AuthViewModel
import com.example.week7speechtools.viewmodel.SpeechViewModel
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            Week7SpeechToolsTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Week7App(
                        activity = this@MainActivity,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

private sealed interface NetworkAction {
    data object SignIn : NetworkAction
    data object StartRecording : NetworkAction
    data object Translate : NetworkAction
    data object GenerateSpeech : NetworkAction

    data class RetrySave(
        val recordId: String
    ) : NetworkAction
}

@Composable
private fun Week7App(
    activity: Activity,
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.Factory
    ),
    speechViewModel: SpeechViewModel = viewModel(
        factory = SpeechViewModel.Factory
    )
) {
    val authState by authViewModel.uiState
        .collectAsStateWithLifecycle()

    val speechState by speechViewModel.uiState
        .collectAsStateWithLifecycle()

    val googleClientId =
        stringResource(R.string.google_web_client_id)

    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var pendingAction by remember {
        mutableStateOf<NetworkAction?>(null)
    }

    LaunchedEffect(authState.user?.googleId) {
        if (authState.user == null) {
            speechViewModel.reset()
        }
    }

    LaunchedEffect(speechState.sessionExpired) {
        if (speechState.sessionExpired) {
            speechViewModel.reset()
            authViewModel.onSessionExpired()
        }
    }

    DisposableEffect(lifecycleOwner, speechViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                speechViewModel.onBackground()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            speechViewModel.onBackground()
        }
    }

    fun startGoogleSignIn() {
        if (
            googleClientId.startsWith("YOUR_") ||
            !googleClientId.endsWith(".apps.googleusercontent.com")
        ) {
            authViewModel.showSignInError(
                "Configure the Web Client ID in strings.xml."
            )
            return
        }

        if (!authViewModel.beginGoogleSignIn()) {
            return
        }

        scope.launch {
            try {
                val idToken = requestGoogleIdToken(
                    activity = activity,
                    serverClientId = googleClientId
                )

                authViewModel.signInWithGoogleToken(idToken)
            } catch (error: GetCredentialCancellationException) {
                authViewModel.onGoogleSignInCancelled()
            } catch (error: NoCredentialException) {
                authViewModel.showSignInError(
                    "No Google account is available. Add an account on this device."
                )
            } catch (error: GoogleIdTokenParsingException) {
                authViewModel.showSignInError(
                    "Unable to read the Google sign-in response."
                )
            } catch (error: GetCredentialException) {
                Log.e(
                    "GoogleSignIn",
                    "Credential request failed. Type: ${error.type}",
                    error
                )

                authViewModel.showSignInError(
                    "Google sign-in failed. Check Google Play services and OAuth settings."
                )
            } catch (error: CancellationException) {
                authViewModel.onGoogleSignInCancelled()
                throw error
            } catch (error: Exception) {
                authViewModel.showSignInError(
                    "Unable to complete Google sign-in."
                )
            }
        }
    }

    val microphonePermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (
                isGranted &&
                authState.user != null &&
                !authState.isBusy
            ) {
                speechViewModel.startRecording()
            } else if (!isGranted) {
                speechViewModel.showError(
                    "Microphone permission is required. Allow it in the app settings."
                )
            }
        }

    fun startRecordingWithPermission() {
        if (authState.user == null || authState.isBusy) {
            return
        }

        val isGranted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (isGranted) {
            speechViewModel.startRecording()
        } else {
            microphonePermissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    fun performAction(action: NetworkAction) {
        if (action == NetworkAction.SignIn) {
            startGoogleSignIn()
            return
        }

        if (authState.user == null || authState.isBusy) {
            return
        }

        when (action) {
            NetworkAction.SignIn -> Unit

            NetworkAction.StartRecording -> {
                startRecordingWithPermission()
            }

            NetworkAction.Translate -> {
                speechViewModel.translateText()
            }

            NetworkAction.GenerateSpeech -> {
                speechViewModel.generateAndPlay()
            }

            is NetworkAction.RetrySave -> {
                speechViewModel.retrySave(action.recordId)
            }
        }
    }

    val networkPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            val action = pendingAction
            pendingAction = null

            if (action != null) {
                if (isGranted) {
                    performAction(action)
                } else {
                    val message =
                        "Allow local network access to connect to the backend."

                    when (action) {
                        NetworkAction.SignIn -> {
                            authViewModel.showSignInError(message)
                        }

                        NetworkAction.Translate -> {
                            speechViewModel.showTranslationError(message)
                        }

                        NetworkAction.GenerateSpeech -> {
                            speechViewModel.showTtsError(message)
                        }

                        is NetworkAction.RetrySave -> {
                            speechViewModel.showSaveError(
                                recordId = action.recordId,
                                message = message
                            )
                        }

                        NetworkAction.StartRecording -> {
                            speechViewModel.showError(message)
                        }
                    }
                }
            }
        }

    fun requestNetworkAction(action: NetworkAction) {
        if (pendingAction != null) {
            return
        }

        if (Build.VERSION.SDK_INT >= 37) {
            val permission =
                Manifest.permission.ACCESS_LOCAL_NETWORK

            val isGranted = ContextCompat.checkSelfPermission(
                activity,
                permission
            ) == PackageManager.PERMISSION_GRANTED

            if (!isGranted) {
                pendingAction = action
                networkPermissionLauncher.launch(permission)
                return
            }
        }

        performAction(action)
    }

    val user = authState.user

    if (user == null) {
        LoginScreen(
            state = authState,
            permissionPending = pendingAction != null,
            onSignIn = {
                requestNetworkAction(NetworkAction.SignIn)
            },
            modifier = modifier
        )
    } else {
        SpeechScreen(
            user = user,
            state = speechState,
            isSigningOut = authState.isBusy,
            permissionPending = pendingAction != null,
            onStartRecording = {
                requestNetworkAction(
                    NetworkAction.StartRecording
                )
            },
            onStopRecording = {
                speechViewModel.stopAndTranscribe()
            },
            onTtsTextChange = {
                speechViewModel.updateTtsText(it)
            },
            onTargetLanguageChange = {
                speechViewModel.selectTargetLanguage(it)
            },
            onTranslate = {
                requestNetworkAction(
                    NetworkAction.Translate
                )
            },
            onReadAloud = {
                requestNetworkAction(
                    NetworkAction.GenerateSpeech
                )
            },
            onStopAudio = {
                speechViewModel.stopAudio()
            },
            onReplay = {
                speechViewModel.replay()
            },
            onRetrySave = { recordId ->
                requestNetworkAction(
                    NetworkAction.RetrySave(recordId)
                )
            },
            onLogout = {
                speechViewModel.reset()
                authViewModel.logout()
            },
            modifier = modifier
        )
    }
}

private suspend fun requestGoogleIdToken(
    activity: Activity,
    serverClientId: String
): String {
    val option = GetSignInWithGoogleOption.Builder(
        serverClientId = serverClientId
    ).build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(option)
        .build()

    val result = CredentialManager.create(activity)
        .getCredential(
            context = activity,
            request = request
        )

    val credential = result.credential

    check(
        credential is CustomCredential &&
                credential.type ==
                GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        "Unexpected credential type"
    }

    return GoogleIdTokenCredential.createFrom(
        credential.data
    ).idToken
}