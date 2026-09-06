package com.mysky.app.domain.repository

import com.mysky.app.domain.model.GeoPosition
import kotlinx.coroutines.flow.Flow

/**
 * Acesso à localização do utilizador. Esconde do domínio o Fused Location Provider e o estado
 * das permissões em runtime.
 */
interface LocationRepository {
    /** `true` quando a app tem pelo menos permissão de localização aproximada. */
    fun hasLocationPermission(): Boolean

    /** Última posição conhecida ou uma leitura pontual; `null` se sem permissão ou indisponível. */
    suspend fun getCurrentLocation(): GeoPosition?

    /** Atualizações contínuas, apenas enquanto houver ecrã ativo a observá-las. */
    fun locationUpdates(): Flow<GeoPosition>
}
