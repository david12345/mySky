package com.mysky.app.presentation.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Ecrã principal: lista de aviões atualmente no céu do utilizador.
 *
 * O MVP é só lista — o mapa fica para uma feature posterior (ver CLAUDE.md, decisão AD-005).
 *
 * TODO(feature/sky-list): lista com callsign, companhia, altitude, velocidade, distância, rumo e
 *  elevação; pull-to-refresh; estados de vazio/erro/sem permissão de localização.
 */
@Composable
fun MainScreen(
    onFlightClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "mySky — ${uiState.flights.size} aviões no céu")
    }
}
