package com.idanalyzer.docupass.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Best-effort single device-location fix via the framework [LocationManager]
 * (no Google Play Services dependency). Used only when a DocuPass session sets
 * `gps = true`; the resulting `"lat,lng,accuracy"` is sent as the Geolocation
 * header on every subsequent request (the server rejects them otherwise).
 *
 * The caller must already hold `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION`.
 */
internal object LocationProvider {

    /** @return (latitude, longitude, accuracy) or null if no fix could be obtained. */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context, timeoutMs: Long = 12_000L): Triple<Double, Double, Double>? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        // Fast path: the most recent last-known fix from any provider.
        lastKnown(lm)?.let { return it.toTriple() }

        // Otherwise request a single fresh update, bounded by a timeout.
        return withTimeoutOrNull(timeoutMs) { singleUpdate(lm) }?.toTriple()
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(lm: LocationManager): Location? =
        listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }

    @SuppressLint("MissingPermission")
    private suspend fun singleUpdate(lm: LocationManager): Location? =
        suspendCancellableCoroutine { cont ->
            val provider = when {
                runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) ->
                    LocationManager.NETWORK_PROVIDER
                runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ->
                    LocationManager.GPS_PROVIDER
                else -> {
                    if (cont.isActive) cont.resume(null)
                    return@suspendCancellableCoroutine
                }
            }
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    runCatching { lm.removeUpdates(this) }
                    if (cont.isActive) cont.resume(location)
                }

                // Required for API < 30 where these are abstract.
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {
                    if (cont.isActive) cont.resume(null)
                }
            }
            runCatching {
                lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            }.onFailure { if (cont.isActive) cont.resume(null) }
            cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
        }

    private fun Location.toTriple(): Triple<Double, Double, Double> =
        Triple(latitude, longitude, if (hasAccuracy()) accuracy.toDouble() else 0.0)
}
