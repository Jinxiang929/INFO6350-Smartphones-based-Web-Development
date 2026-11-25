package com.example.week7speechtools.ui

import android.Manifest
import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.week7speechtools.model.DevicePosition
import com.example.week7speechtools.permissions.LocationPermissions
import com.example.week7speechtools.viewmodel.WhereAmIViewModel
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalUriHandler

@Composable
fun WhereAmIButton(
    enabled: Boolean = true,
    model: WhereAmIViewModel = viewModel(factory = WhereAmIViewModel.Factory)
) {
    val context = LocalContext.current
    val state by model.state.collectAsStateWithLifecycle()

    var showDialog by remember { mutableStateOf(false) }
    var locationAllowed by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (showDialog) {
            locationAllowed = LocationPermissions.hasAccess(context)

            if (!locationAllowed) {
                model.onLocationPermissionDenied()
            }
        }
    }

    OutlinedButton(
        enabled = enabled,
        onClick = {
            model.reset()
            locationAllowed = LocationPermissions.hasAccess(context)
            showDialog = true

            if (!locationAllowed) {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    ) {
        Text("Where am I?")
    }

    if (showDialog) {
        LaunchedEffect(model) {
            model.loadPublicIp()
        }

        LaunchedEffect(model, locationAllowed) {
            if (locationAllowed) {
                model.loadLocation()
            }
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Where am I?") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Public IP: ${state.publicIp}")

                    val position = state.position

                    if (position == null) {
                        Text(state.locationMessage)
                    } else {
                        Text(
                            if (position.approximate) {
                                "Approximate device location"
                            } else {
                                "Current device location"
                            }
                        )

                        Text(
                            String.format(
                                Locale.US,
                                "Latitude: %.5f\nLongitude: %.5f",
                                position.latitude,
                                position.longitude
                            )
                        )

                        position.accuracyMeters?.let { accuracy ->
                            Text("Estimated accuracy: ${accuracy.roundToInt()} m")
                        }

                        OpenGoogleMapsButton(position)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun OpenGoogleMapsButton(position: DevicePosition) {
    val uriHandler = LocalUriHandler.current
    var openFailed by remember(position) { mutableStateOf(false) }

    val mapsUrl = Uri.parse("https://www.google.com/maps/search/")
        .buildUpon()
        .appendQueryParameter("api", "1")
        .appendQueryParameter("query", "${position.latitude},${position.longitude}")
        .build()
        .toString()

    OutlinedButton(
        onClick = {
            openFailed = false
            try {
                uriHandler.openUri(mapsUrl)
            } catch (_: IllegalArgumentException) {
                openFailed = true
            }
        }
    ) {
        Text("Open in Google Maps")
    }

    if (openFailed) {
        Text("Could not open Google Maps on this device.")
    }
}