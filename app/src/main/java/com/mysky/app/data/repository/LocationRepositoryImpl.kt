package com.mysky.app.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.mysky.app.domain.model.GeoPosition
import com.mysky.app.domain.repository.LocationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Localização via Fused Location Provider.
 *
 * Uma leitura pontual por ciclo de atualização, nunca um fluxo contínuo: o ecrã pede uma posição
 * de 30 em 30 segundos e subscrever atualizações contínuas custaria bateria sem trazer nada
 * (AD-008). Por isso [locationUpdates] fica por implementar.
 */
@Singleton
class LocationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient,
) : LocationRepository {

    /**
     * Aproximada chega (FR-003). Exigir localização precisa seria pedir mais do que a app precisa:
     * a 30 km de raio, algumas centenas de metros não mudam o ângulo de elevação de forma visível.
     */
    override fun hasLocationPermission(): Boolean =
        LOCATION_PERMISSIONS.any { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        }

    /**
     * Devolve `null` — nunca lança [SecurityException] — quando falta a permissão ou não há fix
     * possível. Quem chama traduz isso em `SkyError.LocationUnavailable`, que é uma causa distinta
     * de uma falha de rede aos olhos do utilizador (FR-024).
     */
    override suspend fun getCurrentLocation(): GeoPosition? {
        if (!hasLocationPermission()) return null

        return try {
            // `BALANCED_POWER_ACCURACY` usa rede e Wi-Fi antes do GPS: mais rápido a devolver e
            // muito mais barato, o que é o que sustenta o arranque abaixo de 5 s (SC-001).
            currentLocation() ?: lastLocation()
        } catch (security: SecurityException) {
            // A permissão pode ser revogada entre a verificação e a chamada.
            null
        }
    }

    private suspend fun currentLocation(): GeoPosition? {
        val cancellation = CancellationTokenSource()
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancellation.cancel() }
            fusedLocationClient
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
                .addOnSuccessListener { continuation.resume(it?.toGeoPosition()) }
                .addOnFailureListener { continuation.resume(null) }
        }
    }

    /** Recurso para quando não há fix novo a tempo: uma posição antiga é melhor do que nenhuma. */
    private suspend fun lastLocation(): GeoPosition? = suspendCancellableCoroutine { continuation ->
        fusedLocationClient.lastLocation
            .addOnSuccessListener { continuation.resume(it?.toGeoPosition()) }
            .addOnFailureListener { continuation.resume(null) }
    }

    override fun locationUpdates(): Flow<GeoPosition> =
        TODO("Não usado nesta feature: o ciclo do ecrã pede posições pontuais (AD-008)")

    private fun Location.toGeoPosition() = GeoPosition(
        latitudeDegrees = latitude,
        longitudeDegrees = longitude,
    )

    private companion object {
        val LOCATION_PERMISSIONS = listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }
}
