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

    /**
     * `true` quando a app pode obter posição **sem ter ecrã visível**.
     *
     * É uma pergunta diferente de [hasLocationPermission], e a diferença não é académica: desde a API
     * 29 o Android bloqueia a localização em segundo plano sem `ACCESS_BACKGROUND_LOCATION`, e é
     * exatamente aí que o trabalho de fundo corre. Ter a permissão normal e não ter esta produz uma
     * app que funciona no ecrã e nunca funciona no widget — sem erro nenhum a apontar para a causa.
     *
     * Abaixo da API 29 é sempre `true`: a permissão não existe e a restrição também não.
     */
    fun hasBackgroundLocationPermission(): Boolean

    /** Última posição conhecida ou uma leitura pontual; `null` se sem permissão ou indisponível. */
    suspend fun getCurrentLocation(): GeoPosition?

    /** Atualizações contínuas, apenas enquanto houver ecrã ativo a observá-las. */
    fun locationUpdates(): Flow<GeoPosition>
}
