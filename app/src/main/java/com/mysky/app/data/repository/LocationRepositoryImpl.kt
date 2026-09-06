package com.mysky.app.data.repository

import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.mysky.app.domain.model.GeoPosition
import com.mysky.app.domain.repository.LocationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Localização via Fused Location Provider.
 *
 * TODO(feature/sky-list): implementar `getCurrentLocation` (getCurrentLocation com
 *  PRIORITY_BALANCED_POWER_ACCURACY, com fallback para lastLocation) e `locationUpdates` como
 *  `callbackFlow` que remove o callback em `awaitClose`.
 *
 * Bateria: nunca pedir updates contínuos fora de um ecrã visível. O widget e as notificações usam
 * leituras pontuais dentro do worker periódico.
 */
@Singleton
class LocationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient,
) : LocationRepository {

    override fun hasLocationPermission(): Boolean {
        TODO("Implementar durante a feature 'lista de aviões' (ver .specify/)")
    }

    override suspend fun getCurrentLocation(): GeoPosition? {
        TODO("Implementar durante a feature 'lista de aviões' (ver .specify/)")
    }

    override fun locationUpdates(): Flow<GeoPosition> {
        TODO("Implementar durante a feature 'lista de aviões' (ver .specify/)")
    }
}
