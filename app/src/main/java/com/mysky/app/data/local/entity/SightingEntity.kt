package com.mysky.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Um avistamento gravado: aeronave X esteve no céu do utilizador no instante Y.
 *
 * Guarda a geometria já calculada para o ecrã de detalhe poder desenhar o trajeto recente sem
 * voltar a pedir nada à rede.
 */
@Entity(tableName = "sightings")
data class SightingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val icao24: String,
    val callsign: String?,
    val observedAtEpochSeconds: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val horizontalDistanceMeters: Double,
    val bearingDegrees: Double,
    val elevationDegrees: Double,
    val notified: Boolean = false,
)
