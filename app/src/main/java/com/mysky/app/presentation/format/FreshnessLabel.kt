package com.mysky.app.presentation.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mysky.app.R
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

/**
 * "há 12 s", "há 3 min" — a idade dos dados, na mesma escala nos dois ecrãs.
 *
 * Partilhado de propósito: a lista e o detalhe têm de dizer a mesma coisa sobre a mesma observação,
 * e duas implementações divergiriam no primeiro arredondamento.
 */
@Composable
fun freshnessText(nowEpochSeconds: Long, updatedEpochSeconds: Long): String =
    when (val freshness = FlightFormatting.freshnessOf(nowEpochSeconds, updatedEpochSeconds)) {
        Freshness.JustNow -> stringResource(R.string.sky_updated_just_now)
        is Freshness.Seconds -> stringResource(R.string.sky_updated_seconds_ago, freshness.value)
        is Freshness.Minutes -> stringResource(R.string.sky_updated_minutes_ago, freshness.value)
    }

/**
 * Instante atual, a avançar de segundo a segundo enquanto estiver no ecrã.
 *
 * Sem o tique, "há 12 s" ficaria congelado até à atualização seguinte e diria ao utilizador
 * exatamente o contrário do que se pretende. Preso ao ciclo de vida e não só à composição: a árvore
 * do Compose sobrevive ao ecrã ir para segundo plano, e sem isto a app continuaria a acordar de
 * segundo a segundo para atualizar um rótulo que ninguém está a ver.
 *
 * O relógio só é lido aqui, para compor um rótulo: o domínio recebe o tempo por abstração
 * (`TimeProvider`) e nunca o lê (princípio I).
 */
@Composable
fun rememberNowEpochSeconds(): Long {
    val lifecycleOwner = LocalLifecycleOwner.current
    val now by produceState(initialValue = System.currentTimeMillis() / MILLIS_PER_SECOND) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(1.seconds)
                value = System.currentTimeMillis() / MILLIS_PER_SECOND
            }
        }
    }
    return now
}

private const val MILLIS_PER_SECOND = 1_000L
