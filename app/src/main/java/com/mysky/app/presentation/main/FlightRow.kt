package com.mysky.app.presentation.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mysky.app.R
import com.mysky.app.domain.model.AltitudeUnit
import com.mysky.app.domain.model.DistanceUnit
import com.mysky.app.domain.model.OverheadFlight
import com.mysky.app.presentation.format.FlightFormatting
import com.mysky.app.presentation.format.RouteFormatting
import com.mysky.app.presentation.format.UnitLabels

/**
 * Uma aeronave na lista.
 *
 * Campos em falta são **omitidos**, sem espaço vazio nem valor de substituição (FR-014). A única
 * exceção é a identificação: sem indicativo, a linha mostra o `icao24` em maiúsculas (FR-013),
 * porque sem ele o utilizador não teria como referir a aeronave em que tocou.
 */
@Composable
fun FlightRow(
    flight: OverheadFlight,
    distanceUnit: DistanceUnit,
    altitudeUnit: AltitudeUnit,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val aircraft = flight.aircraft

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(aircraft.icao24) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = aircraft.callsign ?: aircraft.icao24.uppercase(),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    R.string.flight_elevation_degrees,
                    FlightFormatting.elevationDegrees(flight.elevationDegrees),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        // Operador desconhecido não deixa linha em branco: simplesmente não há segunda linha.
        flight.airline?.let { airline ->
            Text(text = airline.name, style = MaterialTheme.typography.bodyMedium)
        }

        // A rota é opcional e desaparece por completo quando não é conhecida — sem espaço
        // reservado, sem travessão, sem interrogação. É o mesmo tratamento da companhia, e a
        // maioria dos indicativos que passa no céu não terá rota (FR-005).
        RouteFormatting.pairOrNull(flight.route)?.let { (origin, destination) ->
            Text(
                text = stringResource(R.string.route_pair, origin, destination),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = flight.detailsLine(distanceUnit, altitudeUnit),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Altitude, velocidade, distância e rumo numa linha só, com os ausentes simplesmente fora dela.
 * Montar a linha a partir das partes que existem evita os "— · —" que sobram de campos vazios.
 */
@Composable
private fun OverheadFlight.detailsLine(
    distanceUnit: DistanceUnit,
    altitudeUnit: AltitudeUnit,
): String = buildList {
    aircraft.altitudeMeters?.let {
        add(stringResource(UnitLabels.altitude(altitudeUnit), FlightFormatting.altitude(it, altitudeUnit)))
    }
    aircraft.groundSpeedMetersPerSecond?.let {
        add(stringResource(UnitLabels.speed(distanceUnit), FlightFormatting.speed(it, distanceUnit)))
    }
    add(
        stringResource(
            UnitLabels.distance(distanceUnit),
            FlightFormatting.distance(horizontalDistanceMeters, distanceUnit),
        ),
    )
    // No zénite o rumo deixa de querer dizer alguma coisa: quem olha para cima vê o avião, não
    // precisa de uma direção para onde virar a cabeça.
    val compassPoint = FlightFormatting.compassPointOrNull(bearingDegrees, elevationDegrees)
    add(
        if (compassPoint == null) {
            stringResource(R.string.flight_overhead)
        } else {
            stringResource(compassPoint.labelRes)
        },
    )
}.joinToString(separator = " · ")
