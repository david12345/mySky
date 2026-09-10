package com.mysky.app.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mysky.app.R
import com.mysky.app.domain.model.RouteUpdateError
import com.mysky.app.domain.model.RouteUpdateState
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Definições.
 *
 * Hoje tem uma entrada só — a tabela de rotas — e é a primeira coisa real que este ecrã mostra. As
 * preferências (raio, elevação mínima, unidades, notificações) pertencem à sua própria feature.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.updateState) { viewModel.onUpdateFinished(state.updateState) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // O que conta como "o meu céu" vem primeiro: é a razão de ser deste ecrã, e a tabela
            // de rotas é manutenção.
            Text(
                text = stringResource(R.string.settings_sky_title),
                style = MaterialTheme.typography.titleMedium,
            )
            HorizontalDivider()

            SkySettingsSection(
                state = state,
                onRadiusChanged = viewModel::onRadiusChanged,
                onMinElevationChanged = viewModel::onMinElevationChanged,
                onMinAltitudeChanged = viewModel::onMinAltitudeChanged,
            )

            Spacer(Modifier.padding(4.dp))
            Text(
                text = stringResource(R.string.settings_units_title),
                style = MaterialTheme.typography.titleMedium,
            )
            HorizontalDivider()

            UnitsSection(
                state = state,
                onDistanceUnitChanged = viewModel::onDistanceUnitChanged,
                onAltitudeUnitChanged = viewModel::onAltitudeUnitChanged,
            )

            OutlinedButton(
                onClick = viewModel::onResetToDefaults,
                enabled = !state.isAtDefaults,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.settings_reset))
            }

            Spacer(Modifier.padding(8.dp))
            Text(
                text = stringResource(R.string.settings_routes_title),
                style = MaterialTheme.typography.titleMedium,
            )
            HorizontalDivider()

            // A data em uso é o que torna o botão uma decisão em vez de um gesto às cegas: sem ela,
            // ninguém sabe se vale a pena atualizar (FR-018).
            Text(
                text = if (state.hasTableInfo) {
                    stringResource(
                        R.string.settings_routes_generated_at,
                        formatDate(state.routeTableGeneratedAtEpochSeconds!!),
                    )
                } else {
                    stringResource(R.string.settings_routes_unknown)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.routeCount > 0) {
                Text(
                    text = stringResource(R.string.settings_routes_count, state.routeCount.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            UpdateOutcome(state.updateState)

            Button(
                onClick = viewModel::onUpdateRouteTable,
                enabled = !state.isUpdating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (state.isUpdating) R.string.settings_routes_updating
                        else R.string.settings_routes_update,
                    ),
                )
            }
        }
    }
}

@Composable
private fun UpdateOutcome(state: RouteUpdateState) {
    when (state) {
        RouteUpdateState.Idle -> Unit

        RouteUpdateState.InProgress -> CircularProgressIndicator(Modifier.padding(vertical = 8.dp))

        is RouteUpdateState.Success -> Text(
            text = stringResource(
                R.string.settings_routes_updated,
                state.routeCount.toString(),
                formatDate(state.generatedAtEpochSeconds),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )

        // Cada causa tem a sua mensagem, e todas dizem a mesma coisa no fim: a tabela em uso
        // mantém-se. É a informação que evita que o utilizador pense que ficou sem nada.
        is RouteUpdateState.Failure -> Text(
            text = stringResource(
                when (state.reason) {
                    RouteUpdateError.NoConnection -> R.string.settings_routes_error_no_connection
                    RouteUpdateError.Unreachable -> R.string.settings_routes_error_unreachable
                    RouteUpdateError.InvalidData -> R.string.settings_routes_error_invalid
                    RouteUpdateError.Unexpected -> R.string.settings_routes_error_unexpected
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private fun formatDate(epochSeconds: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
        .format(Date(epochSeconds * MILLIS_PER_SECOND))

private const val MILLIS_PER_SECOND = 1_000L
