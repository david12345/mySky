package com.mysky.app.worker

import com.mysky.app.domain.model.OverheadFlight
import com.mysky.app.domain.model.SkyError
import com.mysky.app.domain.model.SkyWidgetSnapshot
import com.mysky.app.domain.model.WidgetFlight
import com.mysky.app.domain.usecase.SkyCycleResult

/** O que dizer ao WorkManager. Traduzido para `ListenableWorker.Result` por quem o conhece. */
enum class WorkOutcome { Success, Retry, Failure }

/**
 * O que fazer com o resultado de um ciclo: o que gravar e o que devolver ao sistema.
 *
 * **Função pura, fora do worker de propósito.** Não há `work-testing` nem Robolectric neste projeto,
 * por isso lógica dentro de um `CoroutineWorker` é simplesmente não testável na JVM — e o princípio VI
 * é explícito em que bugs de agendamento "não produzem erros visíveis, produzem resultados errados com
 * ar de certos". A restrição de testes empurrou para o desenho certo: o que sobra dentro do
 * `doWork()` é encadeamento, sem decisão nenhuma.
 */
object SkyRefreshDecision {

    /**
     * @return o snapshot a gravar (`null` quando não se deve tocar no que lá está) e o desfecho.
     *
     * A regra que **não** é óbvia: quem falha não grava. É isso, e não uma verificação em lado nenhum,
     * que faz os dados anteriores sobreviverem a um ciclo falhado (FR-014) — o widget continua a
     * mostrar o que tinha porque ninguém lhe mexeu.
     */
    fun decide(result: SkyCycleResult, nowEpochSeconds: Long, isManual: Boolean = false): Decision = when (result) {
        is SkyCycleResult.Success -> Decision(
            snapshot = snapshotOf(result.flights, result.observedAtEpochSeconds),
            outcome = WorkOutcome.Success,
        )

        // Repetir não resolve nada: a permissão não aparece por insistência, e cada tentativa
        // custaria bateria para chegar à mesma conclusão.
        SkyCycleResult.NoPermission -> Decision(
            snapshot = SkyWidgetSnapshot.PermissionMissing(nowEpochSeconds),
            outcome = WorkOutcome.Success,
        )

        is SkyCycleResult.Failure -> Decision(
            snapshot = null,
            // Duas falhas com remédios diferentes merecem textos diferentes (FR-019). As restantes
            // não têm nada de útil a dizer ao utilizador num espaço de três linhas.
            feedback = when (result.error) {
                SkyError.NoConnection -> RefreshFeedback.NoConnection
                is SkyError.RateLimited -> RefreshFeedback.RateLimited
                else -> RefreshFeedback.None
            },
            outcome = when (result.error) {
                // Transitórios: o período seguinte, ou o backoff, resolvem.
                //
                // **Menos quando o pedido foi manual.** Aí o trabalho tem de terminar, não ficar a
                // reintentar: enquanto não termina, o `ExistingWorkPolicy.KEEP` engole os toques
                // seguintes e o utilizador fica sem forma de voltar a tentar. Quem pediu está a olhar
                // para o ecrã e é ele quem decide se insiste.
                SkyError.NoConnection,
                SkyError.LocationUnavailable,
                is SkyError.FlightServiceUnavailable,
                -> if (isManual) WorkOutcome.Success else WorkOutcome.Retry

                // Insistir num 429 gastaria orçamento exatamente quando ele já se esgotou (AD-010).
                // `Success` porque o ciclo fez o que devia: descobriu que não há nada a fazer hoje.
                is SkyError.RateLimited -> WorkOutcome.Success

                // Não é acionável por repetição. `Failure` num `PeriodicWorkRequest` **não cancela**
                // o trabalho — o período seguinte corre na mesma — e é isso que o torna seguro aqui.
                is SkyError.Unexpected -> WorkOutcome.Failure
            },
        )
    }

    private fun snapshotOf(flights: List<OverheadFlight>, observedAt: Long): SkyWidgetSnapshot {
        // `maxByOrNull` e **não** `first()`. A lista chega ordenada por `relevanceScore`, mas depender
        // disso é um acoplamento implícito: se a ordenação a montante mudar um dia, o widget passa a
        // mostrar o avião errado sem erro nenhum, sem exceção e sem teste a falhar.
        val top = flights.maxByOrNull { it.elevationDegrees } ?: return SkyWidgetSnapshot.EmptySky(observedAt)

        return SkyWidgetSnapshot.Flights(
            top = WidgetFlight(
                callsign = top.aircraft.callsign,
                airlineName = top.airline?.name,
                elevationDegrees = top.elevationDegrees,
            ),
            count = flights.size,
            observedAtEpochSeconds = observedAt,
        )
    }

    data class Decision(
        val snapshot: SkyWidgetSnapshot?,
        val outcome: WorkOutcome,
        val feedback: RefreshFeedback = RefreshFeedback.None,
    )
}
