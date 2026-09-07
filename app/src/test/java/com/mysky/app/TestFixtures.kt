package com.mysky.app

import com.mysky.app.domain.model.Aircraft
import com.mysky.app.domain.model.Airline
import com.mysky.app.domain.model.GeoPosition
import com.mysky.app.domain.model.OverheadFlight
import com.mysky.app.domain.model.SkyError
import com.mysky.app.presentation.sky.LoadPhase
import com.mysky.app.presentation.sky.SkyObservation

/**
 * Construtores de teste partilhados.
 *
 * Existem para que cada teste declare **apenas** os campos que o caso em causa exercita: um teste
 * de ordenação não devia ter de inventar um `icao24` nem uma velocidade. Os valores por omissão
 * descrevem uma aeronave normal, no céu, com dados recentes — o caminho feliz — e cada teste
 * desvia-se dele no que lhe interessa.
 */

/** Lisboa. Observador por omissão de toda a suite. */
val LISBON = GeoPosition(latitudeDegrees = 38.7223, longitudeDegrees = -9.1393)

/** Instante de referência fixo. Nenhum teste lê o relógio do sistema. */
const val NOW_EPOCH_SECONDS = 1_757_000_000L

fun aircraft(
    icao24: String = "3c6444",
    callsign: String? = "TAP1234",
    originCountry: String? = "Portugal",
    position: GeoPosition? = LISBON,
    barometricAltitudeMeters: Double? = null,
    geometricAltitudeMeters: Double? = 10_400.0,
    groundSpeedMetersPerSecond: Double? = 233.0,
    headingDegrees: Double? = 180.0,
    verticalRateMetersPerSecond: Double? = 0.0,
    onGround: Boolean = false,
    lastContactEpochSeconds: Long? = NOW_EPOCH_SECONDS,
): Aircraft = Aircraft(
    icao24 = icao24,
    callsign = callsign,
    originCountry = originCountry,
    position = position,
    barometricAltitudeMeters = barometricAltitudeMeters,
    geometricAltitudeMeters = geometricAltitudeMeters,
    groundSpeedMetersPerSecond = groundSpeedMetersPerSecond,
    headingDegrees = headingDegrees,
    verticalRateMetersPerSecond = verticalRateMetersPerSecond,
    onGround = onGround,
    lastContactEpochSeconds = lastContactEpochSeconds,
)

fun overheadFlight(
    aircraft: Aircraft = aircraft(),
    horizontalDistanceMeters: Double = 5_000.0,
    bearingDegrees: Double = 45.0,
    elevationDegrees: Double = 60.0,
    airline: Airline? = null,
): OverheadFlight = OverheadFlight(
    aircraft = aircraft,
    horizontalDistanceMeters = horizontalDistanceMeters,
    bearingDegrees = bearingDegrees,
    elevationDegrees = elevationDegrees,
    airline = airline,
)

/**
 * Posição a [distanceMeters] a norte de [from]. Evita que os testes de geometria escrevam
 * coordenadas mágicas cuja distância só se percebe correndo o cálculo.
 */
fun northOf(from: GeoPosition = LISBON, distanceMeters: Double): GeoPosition = GeoPosition(
    latitudeDegrees = from.latitudeDegrees + Math.toDegrees(distanceMeters / 6_371_008.8),
    longitudeDegrees = from.longitudeDegrees,
)

/**
 * Observação do céu com valores do caminho feliz: um ciclo concluído com sucesso, sem erro.
 *
 * Os testes desta feature descrevem-se pelo que desviam daqui — um erro, uma lista vazia, um
 * `lastUpdatedEpochSeconds` nulo — em vez de montarem a observação inteira de cada vez.
 */
fun skyObservation(
    phase: LoadPhase = LoadPhase.Idle,
    flights: List<OverheadFlight> = emptyList(),
    lastUpdatedEpochSeconds: Long? = NOW_EPOCH_SECONDS,
    lastError: SkyError? = null,
): SkyObservation = SkyObservation(
    phase = phase,
    flights = flights,
    lastUpdatedEpochSeconds = lastUpdatedEpochSeconds,
    lastError = lastError,
)

/**
 * Voos com os [icao24] pedidos, todos válidos e distintos.
 *
 * Existe porque os testes de presença só se interessam por **que** aeronaves estão na observação,
 * nunca pela geometria de cada uma.
 */
fun flightsWith(vararg icao24: String): List<OverheadFlight> =
    icao24.map { id -> overheadFlight(aircraft = aircraft(icao24 = id)) }
