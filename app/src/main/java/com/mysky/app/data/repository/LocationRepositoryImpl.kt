package com.mysky.app.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

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
     * Abaixo da API 29 devolve `true` sem verificar nada: a permissão não existe nessa versão, e
     * `checkSelfPermission` sobre uma permissão inexistente devolveria negado, o que faria a app
     * recusar-se a trabalhar em segundo plano precisamente nos aparelhos onde nada a impede.
     */
    override fun hasBackgroundLocationPermission(): Boolean =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            true
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * Devolve `null` — nunca lança [SecurityException] — quando falta a permissão ou não há fix
     * possível. Quem chama traduz isso em `SkyError.LocationUnavailable`, que é uma causa distinta
     * de uma falha de rede aos olhos do utilizador (FR-024).
     */
    override suspend fun getCurrentLocation(): GeoPosition? {
        if (!hasLocationPermission()) return null

        // `BALANCED_POWER_ACCURACY` usa rede e Wi-Fi antes do GPS: mais rápido a devolver e muito
        // mais barato, o que é o que sustenta o arranque abaixo de 5 s (SC-001).
        return currentLocation() ?: lastLocation()
    }

    /**
     * A `SecurityException` é apanhada aqui, junto da chamada, e não à volta de [getCurrentLocation]:
     * a permissão pode ser revogada entre a verificação e o pedido, e é este o sítio onde isso se
     * manifesta.
     */
    private suspend fun currentLocation(): GeoPosition? = try {
        val cancellation = CancellationTokenSource()
        // Prazo nosso e não o do Fused Location: sem ele, um sítio sem fix (interior, GPS
        // desligado) deixaria o ecrã em "A obter a tua localização…" por tempo indeterminado, e
        // SC-001 pede a lista em menos de 5 s. Esgotado o prazo, uma posição antiga vale mais do
        // que continuar à espera de uma nova.
        withTimeoutOrNull(CURRENT_FIX_TIMEOUT) {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { cancellation.cancel() }
                fusedLocationClient
                    .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
                    .addOnSuccessListener { continuation.resume(it?.toGeoPosition()) }
                    .addOnFailureListener { continuation.resume(null) }
            }
        }
    } catch (security: SecurityException) {
        null
    }

    /** Recurso para quando não há fix novo a tempo: uma posição antiga é melhor do que nenhuma. */
    private suspend fun lastLocation(): GeoPosition? = try {
        // É uma leitura de cache e devia ser imediata, mas o prazo existe para que um serviço do
        // Google Play em mau estado não prenda o ciclo de atualização.
        withTimeoutOrNull(LAST_KNOWN_TIMEOUT) {
            suspendCancellableCoroutine { continuation ->
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { continuation.resume(it?.toGeoPosition()) }
                    .addOnFailureListener { continuation.resume(null) }
            }
        }
    } catch (security: SecurityException) {
        null
    }

    override fun locationUpdates(): Flow<GeoPosition> =
        TODO("Não usado nesta feature: o ciclo do ecrã pede posições pontuais (AD-008)")

    private fun Location.toGeoPosition() = GeoPosition(
        latitudeDegrees = latitude,
        longitudeDegrees = longitude,
    )

    private companion object {
        /** Cabe dentro dos 5 s de SC-001 e deixa margem para o pedido de voos. */
        val CURRENT_FIX_TIMEOUT = 3.seconds
        val LAST_KNOWN_TIMEOUT = 2.seconds

        val LOCATION_PERMISSIONS = listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }
}
