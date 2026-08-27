package io.github.guibecko.skydex.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class Coordinates(val latitude: Double, val longitude: Double, val isMock: Boolean = false)

/** A fix at exactly (0, 0) is what the fused provider reports when it has nothing real. */
fun Coordinates.isPlausible(): Boolean =
    latitude in -90.0..90.0 &&
        longitude in -180.0..180.0 &&
        !(latitude == 0.0 && longitude == 0.0)

val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

class DeviceLocation(private val context: Context) {

    fun hasPermission(): Boolean = LOCATION_PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * One-shot position request. Returns null when the permission is missing or the provider
     * cannot produce a fix — callers must handle that rather than substituting a default.
     */
    @SuppressLint("MissingPermission") // guarded by hasPermission() above
    suspend fun current(): Coordinates? {
        if (!hasPermission()) return null

        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(60_000)
            .setDurationMillis(15_000)
            .build()

        return suspendCancellableCoroutine { continuation ->
            client.getCurrentLocation(request, null)
                .addOnSuccessListener { location ->
                    val coordinates = location?.let {
                        val isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            it.isMock                 // API 31+
                        } else {
                            @Suppress("DEPRECATION")
                            it.isFromMockProvider     // API 18-30; minSdk here is 26
                        }
                        Coordinates(it.latitude, it.longitude, isMock)
                    }
                    continuation.resume(coordinates?.takeIf { it.isPlausible() })
                }
                .addOnFailureListener { continuation.resume(null) }
        }
    }
}
