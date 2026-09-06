package com.mysky.app.presentation.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * TODO(feature/flight-detail): matrícula, tipo de aeronave, origem/destino quando disponível e
 *  trajeto recente a partir do histórico local de avistamentos.
 */
@Composable
fun FlightDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Detalhe do voo")
    }
}
