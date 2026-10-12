package com.example.presencamobile.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.location.Location
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.example.presencamobile.data.model.GeoPosition
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class LocationProvider(context: Context) {
    private val context = context.applicationContext

    suspend fun currentPosition(): GeoPosition {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            throw LocationUnavailable("Permita a localização precisa nas configurações do aplicativo.")
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            throw LocationUnavailable("Ative a localização/GPS do celular e tente novamente.")
        }
        val location = withTimeoutOrNull(25_000) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(manager, LocationManager.GPS_PROVIDER, signal,
                    ContextCompat.getMainExecutor(context)) { result ->
                    if (continuation.isActive) continuation.resume(result)
                }
            }
        } ?: throw LocationUnavailable("Não foi possível obter o GPS. Aproxime-se de uma janela e tente novamente.")
        if (LocationCompat.isMock(location)) throw LocationUnavailable("Use a localização real do dispositivo para registrar a presença.")
        val age = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
        if (age !in 0..30_000 || !location.hasAccuracy() || !location.accuracy.isFinite() || location.accuracy <= 0 || location.accuracy > 50) {
            throw LocationUnavailable("O GPS ainda está impreciso. Aguarde alguns segundos e tente novamente.")
        }
        return GeoPosition(location.latitude, location.longitude, location.accuracy.toDouble())
    }
}

class LocationUnavailable(message: String) : Exception(message)
