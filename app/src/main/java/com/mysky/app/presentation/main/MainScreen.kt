package com.mysky.app.presentation.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mysky.app.R
import com.mysky.app.presentation.main.format.FlightFormatting
import com.mysky.app.presentation.main.format.Freshness
import com.mysky.app.presentation.permission.LocationPermanentlyDenied
import com.mysky.app.presentation.permission.LocationRationale
import com.mysky.app.presentation.permission.rememberLocationPermissionController
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

/**
 * Ecrã principal: lista de aviões atualmente no céu do utilizador.
 *
 * O MVP é só lista — o mapa fica para uma feature posterior (AD-005).
 *
 * A ordem de precedência dos estados é o contrato do ecrã: a permissão ganha sempre a tudo, e ter
 * resultados antigos ganha ao erro. Nunca se limpa a lista por causa de uma falha (FR-025).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onFlightClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permission = rememberLocationPermissionController()

    // Reavalia a permissão sempre que o ecrã volta a ficar visível: o utilizador pode tê-la
    // revogado nas definições do sistema enquanto a app esteve em segundo plano (FR-006).
    LifecycleResumeEffect(permission.isGranted) {
        viewModel.onScreenVisible()
        onPauseOrDispose {}
    }

    // Num efeito e não no corpo da composição: chamar o ViewModel a cada recomposição repetiria
    // a notificação sem que nada tivesse mudado.
    LaunchedEffect(permission.requested, permission.isGranted, permission.canAskAgain) {
        if (permission.requested) {
            viewModel.onPermissionResult(permission.isGranted, permission.canAskAgain)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = onSettingsClick) { Text("⚙") }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            SkyContent(
                uiState = uiState,
                onFlightClick = onFlightClick,
                onRefresh = viewModel::onManualRefresh,
                onRequestPermission = permission::request,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkyContent(
    uiState: MainUiState,
    onFlightClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    when {
        // 1. A permissão ganha a tudo: sem ela não há nada que a app possa mostrar.
        uiState.permission == PermissionState.PermanentlyDenied -> LocationPermanentlyDenied()

        uiState.permission == PermissionState.Denied ||
            uiState.permission == PermissionState.Unknown ->
            // 2. Ainda não pediu ou recusou: o rationale vem antes de qualquer diálogo (FR-001).
            LocationRationale(onRequestPermission = onRequestPermission)

        // 3. Primeira carga: progresso com texto distinto por fase (FR-022).
        uiState.isFirstLoad -> LoadingState(uiState.phase)

        // 4. Falhou e não há nada por baixo: o erro ocupa o ecrã, com repetição (FR-024).
        uiState.isBlockingError -> ErrorState(uiState, onRefresh)

        // 5 e 6. Há lista (possivelmente desatualizada) ou o céu está mesmo vazio.
        else -> PullToRefreshBox(
            isRefreshing = uiState.phase != LoadPhase.Idle,
            onRefresh = onRefresh,
        ) {
            if (uiState.flights.isEmpty()) EmptySkyState(uiState) else FlightList(uiState, onFlightClick)
        }
    }
}

@Composable
private fun FlightList(uiState: MainUiState, onFlightClick: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { StatusHeader(uiState) }
        // Chave estável: uma aeronave que sai do céu entre duas atualizações desaparece sem
        // reordenar a lista debaixo do dedo de quem estava a tocar noutra.
        items(items = uiState.flights, key = { it.aircraft.icao24 }) { flight ->
            FlightRow(flight = flight, onClick = onFlightClick)
            HorizontalDivider()
        }
    }
}

/** Marca temporal (FR-018) e, quando a última tentativa falhou, o aviso de desatualização. */
@Composable
private fun StatusHeader(uiState: MainUiState) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        uiState.lastUpdatedEpochSeconds?.let { updated ->
            Text(text = updatedLabel(updated), style = MaterialTheme.typography.labelMedium)
        }
        if (uiState.hasStaleResults) {
            Text(
                text = stringResource(R.string.sky_stale_warning),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun updatedLabel(updatedEpochSeconds: Long): String =
    when (val freshness = FlightFormatting.freshnessOf(rememberNowEpochSeconds(), updatedEpochSeconds)) {
        Freshness.JustNow -> stringResource(R.string.sky_updated_just_now)
        is Freshness.Seconds -> stringResource(R.string.sky_updated_seconds_ago, freshness.value)
        is Freshness.Minutes -> stringResource(R.string.sky_updated_minutes_ago, freshness.value)
    }

/**
 * Instante atual, a avançar de segundo a segundo enquanto o rótulo estiver no ecrã.
 *
 * Sem o tique, "há 12 s" ficaria congelado até à atualização seguinte e diria ao utilizador
 * exatamente o contrário do que FR-018 pretende. O tique não faz rede nem lê localização, e morre
 * com a composição.
 *
 * O relógio só é lido aqui, para compor um rótulo: o domínio recebe o tempo por abstração
 * (`TimeProvider`) e nunca o lê (princípio I).
 */
@Composable
private fun rememberNowEpochSeconds(): Long {
    val now by produceState(initialValue = System.currentTimeMillis() / MILLIS_PER_SECOND) {
        while (true) {
            delay(1.seconds)
            value = System.currentTimeMillis() / MILLIS_PER_SECOND
        }
    }
    return now
}

private const val MILLIS_PER_SECOND = 1_000L

@Composable
private fun LoadingState(phase: LoadPhase) {
    CenteredMessage {
        CircularProgressIndicator()
        Text(
            text = stringResource(
                if (phase == LoadPhase.LocatingUser) {
                    R.string.sky_locating_user
                } else {
                    R.string.sky_loading_flights
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorState(uiState: MainUiState, onRefresh: () -> Unit) {
    val error = uiState.lastError ?: return

    CenteredMessage {
        Text(
            text = stringResource(error.messageRes()),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRefresh) { Text(stringResource(error.retryActionRes)) }
    }
}

/** Sucesso sem resultados. Sem aspeto de erro: um céu vazio é uma resposta, não uma avaria. */
@Composable
private fun EmptySkyState(uiState: MainUiState) {
    Column(modifier = Modifier.fillMaxSize()) {
        StatusHeader(uiState)
        CenteredMessage {
            Text(
                text = stringResource(R.string.sky_empty_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.sky_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}
