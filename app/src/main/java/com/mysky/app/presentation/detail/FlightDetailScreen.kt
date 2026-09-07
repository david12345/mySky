package com.mysky.app.presentation.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mysky.app.R
import com.mysky.app.domain.model.OverheadFlight
import com.mysky.app.presentation.format.CompassPoint
import com.mysky.app.presentation.format.FlightFormatting
import com.mysky.app.presentation.format.VerticalMovement
import com.mysky.app.presentation.format.freshnessText
import com.mysky.app.presentation.format.messageRes
import com.mysky.app.presentation.format.rememberNowEpochSeconds
import com.mysky.app.presentation.format.retryActionRes
import com.mysky.app.presentation.sky.LoadPhase

/**
 * Detalhe de uma aeronave.
 *
 * Não vai à rede: lê a mesma observação que a lista, pela sessão partilhada (AD-011). Por isso
 * abre já preenchido, sem estado de carregamento no meio, e os valores coincidem ao dígito com os
 * da linha que o utilizador tocou.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FlightDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val nowEpochSeconds = rememberNowEpochSeconds()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.flight?.let { flightTitle(it) } ?: stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.phase != LoadPhase.Idle,
            onRefresh = viewModel::onManualRefresh,
            modifier = Modifier.padding(padding),
        ) {
            // A precedência é o contrato: o que está mais acima ganha sempre ao que está abaixo.
            when {
                state.icao24 == null -> Message(stringResource(R.string.detail_no_aircraft))

                state.isBlockingError -> Message(
                    text = stringResource(state.lastError!!.messageRes()),
                    action = stringResource(state.lastError!!.retryActionRes),
                    onAction = viewModel::onManualRefresh,
                )

                state.isWaitingFirstObservation -> Loading(state.phase)

                state.isAbsentFromSky -> Message(stringResource(R.string.detail_not_in_sky))

                else -> FlightDetail(state = state, nowEpochSeconds = nowEpochSeconds)
            }
        }
    }
}

@Composable
private fun FlightDetail(
    state: FlightDetailUiState,
    nowEpochSeconds: Long,
    modifier: Modifier = Modifier,
) {
    val flight = state.flight ?: return

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // A aeronave saiu do céu: os valores ficam, mas nunca sem dizer que já não são de agora.
        state.lastSeenEpochSeconds?.let { lastSeen ->
            Notice(
                title = stringResource(R.string.detail_left_sky_title),
                body = stringResource(
                    R.string.detail_left_sky_body,
                    freshnessText(nowEpochSeconds = nowEpochSeconds, updatedEpochSeconds = lastSeen),
                ),
            )
        }

        if (state.hasStaleData && !state.hasLeftSky) {
            Notice(
                title = stringResource(state.lastError!!.messageRes()),
                body = stringResource(R.string.sky_stale_warning),
            )
        }

        Section(stringResource(R.string.detail_section_identity))
        flight.airline?.let { Field(stringResource(R.string.detail_airline), it.name) }
        flight.aircraft.originCountry?.let {
            Field(stringResource(R.string.detail_origin_country), it)
        }

        Section(stringResource(R.string.detail_section_flight))
        AltitudeFields(flight)
        flight.aircraft.groundSpeedMetersPerSecond?.let {
            Field(stringResource(R.string.detail_speed), stringResource(R.string.flight_speed_kmh, FlightFormatting.speedKmh(it)))
        }
        flight.aircraft.headingDegrees?.let {
            // Sem a regra do zénite: um avião mesmo por cima continua a ir para algum lado.
            Field(stringResource(R.string.detail_heading), stringResource(FlightFormatting.compassPointOf(it).labelRes))
        }
        flight.aircraft.verticalRateMetersPerSecond?.let {
            Field(stringResource(R.string.detail_vertical_rate), verticalMovementText(it))
        }

        Section(stringResource(R.string.detail_section_observer))
        Field(
            stringResource(R.string.detail_distance),
            stringResource(R.string.flight_distance_km, FlightFormatting.distanceKm(flight.horizontalDistanceMeters)),
        )
        BearingField(flight)
        Field(
            stringResource(R.string.detail_elevation),
            stringResource(R.string.flight_elevation_degrees, FlightFormatting.elevationDegrees(flight.elevationDegrees)),
        )

        Spacer(Modifier.padding(4.dp))
        state.lastUpdatedEpochSeconds?.let { updated ->
            Text(
                text = freshnessText(nowEpochSeconds = nowEpochSeconds, updatedEpochSeconds = updated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * As duas altitudes, cada uma identificada, com a que entrou no cálculo assinalada.
 *
 * Sem essa marca, quem reparasse que a lista concorda com uma e não com a outra concluiria que uma
 * delas está errada — quando divergirem dezenas de metros é legítimo.
 */
@Composable
private fun AltitudeFields(flight: OverheadFlight) {
    val aircraft = flight.aircraft
    // A elevação é calculada com a geométrica quando existe. Assinalar qual entrou na conta só faz
    // sentido — e só é preciso — quando estão as duas no ecrã.
    val ambas = aircraft.geometricAltitudeMeters != null && aircraft.barometricAltitudeMeters != null
    val marca = " (" + stringResource(R.string.detail_altitude_used) + ")"

    aircraft.geometricAltitudeMeters?.let {
        Field(
            label = stringResource(R.string.detail_altitude_geometric) + if (ambas) marca else "",
            value = stringResource(R.string.flight_altitude_meters, FlightFormatting.altitudeMeters(it)),
        )
    }
    aircraft.barometricAltitudeMeters?.let {
        Field(
            label = stringResource(R.string.detail_altitude_barometric),
            value = stringResource(R.string.flight_altitude_meters, FlightFormatting.altitudeMeters(it)),
        )
    }
}

@Composable
private fun BearingField(flight: OverheadFlight) {
    val point: CompassPoint? = FlightFormatting.compassPointOrNull(
        bearingDegrees = flight.bearingDegrees,
        elevationDegrees = flight.elevationDegrees,
    )
    Field(
        label = stringResource(R.string.detail_bearing),
        value = point?.let { stringResource(it.labelRes) } ?: stringResource(R.string.flight_overhead),
    )
}

@Composable
private fun verticalMovementText(metersPerSecond: Double): String =
    when (val movement = FlightFormatting.verticalMovementOf(metersPerSecond)) {
        is VerticalMovement.Climbing -> stringResource(
            R.string.detail_climbing,
            FlightFormatting.verticalRateMetersPerSecond(movement.metersPerSecond),
        )
        is VerticalMovement.Descending -> stringResource(
            R.string.detail_descending,
            FlightFormatting.verticalRateMetersPerSecond(movement.metersPerSecond),
        )
        VerticalMovement.Level -> stringResource(R.string.detail_level)
    }

@Composable
private fun flightTitle(flight: OverheadFlight): String =
    flight.aircraft.callsign?.takeIf { it.isNotBlank() } ?: flight.aircraft.icao24.uppercase()

@Composable
private fun Section(title: String) {
    Spacer(Modifier.padding(6.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
}

/** Um campo ausente não chega aqui: quem chama omite-o por completo, sem espaço reservado. */
@Composable
private fun Field(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Notice(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Loading(phase: LoadPhase) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(
                if (phase == LoadPhase.LocatingUser) R.string.sky_locating_user else R.string.sky_loading_flights,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Message(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (action != null && onAction != null) {
            androidx.compose.material3.Button(onClick = onAction) { Text(action) }
        }
    }
}
