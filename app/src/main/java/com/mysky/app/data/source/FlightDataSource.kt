package com.mysky.app.data.source

import com.mysky.app.domain.model.Aircraft
import com.mysky.app.domain.model.BoundingBox

/**
 * Abstração sobre uma fonte de dados de voo.
 *
 * **Regra do projeto:** nenhuma camada acima de `data/source` pode conhecer a OpenSky (ou qualquer
 * outra API concreta). Acrescentar uma fonte — ADS-B Exchange, airplanes.live, um dataset local —
 * é implementar esta interface e ligá-la em `di/DataSourceModule`, sem tocar em `domain` nem em
 * `presentation`.
 *
 * As implementações devem lançar exceções em caso de erro; o mapeamento para `Result` é feito no
 * repositório.
 */
interface FlightDataSource {
    /** Identificador estável da fonte, para logging e para escolher entre fontes. */
    val id: String

    /**
     * Aeronaves reportadas dentro de [box].
     *
     * @throws java.io.IOException em falha de rede
     * @throws retrofit2.HttpException em resposta HTTP de erro (inclui 429 de rate limit)
     */
    suspend fun fetchAircraftIn(box: BoundingBox): List<Aircraft>
}
