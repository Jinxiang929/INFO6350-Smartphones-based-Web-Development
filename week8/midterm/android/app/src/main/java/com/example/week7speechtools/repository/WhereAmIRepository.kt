package com.example.week7speechtools.repository

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.example.week7speechtools.model.DevicePosition
import com.example.week7speechtools.permissions.LocationPermissions
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class WhereAmIRepository(context: Context) {

    private val appContext = context.applicationContext

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun getPosition(): DevicePosition {
        if (!LocationPermissions.hasAccess(appContext)) {
            throw SecurityException("Location permission is required.")
        }

        val manager = appContext.getSystemService(LocationManager::class.java)
            ?: throw IOException("Location is unavailable on this device.")

        if (!LocationManagerCompat.isLocationEnabled(manager)) {
            throw IOException("Turn on Location in Android Settings.")
        }

        val precise = LocationPermissions.hasPreciseAccess(appContext)
        val enabledProviders = manager.getProviders(true)

        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31) {
                add(LocationManager.FUSED_PROVIDER)
            }

            if (precise) {
                add(LocationManager.GPS_PROVIDER)
            }

            add(LocationManager.NETWORK_PROVIDER)
        }.filter { it in enabledProviders }

        if (providers.isEmpty()) {
            throw IOException("No location provider is available.")
        }

        for (provider in providers) {
            // Allow GPS more time to acquire a fix.
            val timeoutMillis = if (provider == LocationManager.GPS_PROVIDER) {
                12_000L
            } else {
                4_000L
            }

            val location = withTimeoutOrNull(timeoutMillis) {
                requestLocation(manager, provider)
            } ?: continue

            return DevicePosition(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() },
                approximate = !precise
            )
        }

        throw IOException("Could not get your location. Please try again.")
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestLocation(
        manager: LocationManager,
        provider: String
    ): Location? = suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()

        // Cancel the request on timeout or when the dialog closes.
        continuation.invokeOnCancellation { signal.cancel() }

        LocationManagerCompat.getCurrentLocation(
            manager,
            provider,
            signal,
            ContextCompat.getMainExecutor(appContext)
        ) { location ->
            continuation.resume(location)
        }
    }

    suspend fun getPublicIp(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api64.ipify.org")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Public IP lookup failed.")
            }

            response.body
                ?.string()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: throw IOException("The IP service returned no address.")
        }
    }
}