package com.mysky.app.domain.repository

import com.mysky.app.domain.model.Aircraft
import com.mysky.app.domain.model.BoundingBox

/**
 * Contrato do domínio para obter aeronaves. A implementação escolhe e combina fontes concretas
 * (ver `data/source/FlightDataSource`); o domínio nunca sabe qual está em uso.
 */
interface FlightRepository {
    /** Aeronaves reportadas dentro das [boxes] indicadas, já deduplicadas por `icao24`. */
    suspend fun getAircraftIn(boxes: List<BoundingBox>): Result<List<Aircraft>>
}
