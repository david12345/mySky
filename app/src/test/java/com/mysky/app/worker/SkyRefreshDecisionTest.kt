package com.mysky.app.worker

import com.mysky.app.aircraft
import com.mysky.app.domain.model.Airline
import com.mysky.app.domain.model.SkyError
import com.mysky.app.domain.model.SkyWidgetSnapshot
import com.mysky.app.domain.usecase.SkyCycleResult
import com.mysky.app.overheadFlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A tabela de decisão do trabalho de fundo.
 *
 * Existe fora do worker porque, sem `work-testing` nem Robolectric, dentro dele não seria testável de
 * todo. Cada linha aqui é uma decisão que não produz sintoma visível quando está errada: um `retry` a
 * mais gasta bateria em silêncio, um `retry` a menos deixa o widget parado sem ninguém saber porquê.
 */
class SkyRefreshDecisionTest {

    private val agora = 1_700_000_000L
    private val observadoEm = 1_699_999_000L

    @Test
    fun `um ciclo com voos grava o de maior elevacao`() {
        val voos = listOf(
            overheadFlight(aircraft = aircraft(icao24 = "aaa"), elevationDegrees = 20.0),
            overheadFlight(aircraft = aircraft(icao24 = "bbb"), elevationDegrees = 71.0),
            overheadFlight(aircraft = aircraft(icao24 = "ccc"), elevationDegrees = 45.0),
        )

        val decisao = SkyRefreshDecision.decide(SkyCycleResult.Success(voos, observadoEm), agora)

        val snapshot = decisao.snapshot as SkyWidgetSnapshot.Flights
        assertEquals(71.0, snapshot.top.elevationDegrees, 0.001)
        assertEquals(3, snapshot.count)
        assertEquals(observadoEm, snapshot.observedAtEpochSeconds)
        assertEquals(WorkOutcome.Success, decisao.outcome)
    }

    @Test
    fun `o topo e o de maior elevacao mesmo com a lista fora de ordem`() {
        // A lista chega ordenada por `relevanceScore`, mas depender disso é um acoplamento implícito:
        // se a ordenação a montante mudar um dia, `first()` passaria a mostrar o avião errado sem
        // erro nenhum e sem teste a falhar. Este teste é o que impede isso.
        val foraDeOrdem = listOf(
            overheadFlight(aircraft = aircraft(icao24 = "aaa"), elevationDegrees = 12.0),
            overheadFlight(aircraft = aircraft(icao24 = "bbb"), elevationDegrees = 88.0),
            overheadFlight(aircraft = aircraft(icao24 = "ccc"), elevationDegrees = 30.0),
        )

        val decisao = SkyRefreshDecision.decide(SkyCycleResult.Success(foraDeOrdem, observadoEm), agora)

        assertEquals(88.0, (decisao.snapshot as SkyWidgetSnapshot.Flights).top.elevationDegrees, 0.001)
    }

    @Test
    fun `uma aeronave sem indicativo nem companhia continua a ser apresentada`() {
        // FR-005: a falta de um campo nunca esconde o avião. Filtrá-la seria "melhorar" o widget
        // escondendo o que ele devia mostrar.
        val semNada = listOf(overheadFlight(aircraft = aircraft(callsign = null), airline = null, elevationDegrees = 33.0))

        val snapshot = SkyRefreshDecision.decide(
            SkyCycleResult.Success(semNada, observadoEm), agora,
        ).snapshot as SkyWidgetSnapshot.Flights

        assertNull(snapshot.top.callsign)
        assertNull(snapshot.top.airlineName)
        assertEquals(33.0, snapshot.top.elevationDegrees, 0.001)
    }

    @Test
    fun `a companhia entra no snapshot quando e conhecida`() {
        val comCompanhia = listOf(
            overheadFlight(aircraft = aircraft(callsign = "TAP1234"), airline = Airline("TAP", "TAP Air Portugal"), elevationDegrees = 40.0),
        )

        val snapshot = SkyRefreshDecision.decide(
            SkyCycleResult.Success(comCompanhia, observadoEm), agora,
        ).snapshot as SkyWidgetSnapshot.Flights

        assertEquals("TAP1234", snapshot.top.callsign)
        assertEquals("TAP Air Portugal", snapshot.top.airlineName)
    }

    @Test
    fun `um ciclo com o ceu vazio grava ceu vazio e nao ausencia de dados`() {
        // A distinção importa: "não havia aviões" é uma observação, "ainda não há dados" é a falta
        // dela. Confundi-las diria ao utilizador que o céu está vazio sem lá ter olhado.
        val decisao = SkyRefreshDecision.decide(SkyCycleResult.Success(emptyList(), observadoEm), agora)

        assertTrue(decisao.snapshot is SkyWidgetSnapshot.EmptySky)
        assertEquals(WorkOutcome.Success, decisao.outcome)
    }

    @Test
    fun `sem permissao grava o estado e nao repete`() {
        val decisao = SkyRefreshDecision.decide(SkyCycleResult.NoPermission, agora)

        assertEquals(SkyWidgetSnapshot.PermissionMissing(agora), decisao.snapshot)
        assertEquals("repetir não faz a permissão aparecer", WorkOutcome.Success, decisao.outcome)
    }

    @Test
    fun `falhas transitorias pedem nova tentativa e nao tocam no que esta gravado`() {
        listOf(
            SkyError.NoConnection,
            SkyError.LocationUnavailable,
            SkyError.FlightServiceUnavailable(503),
        ).forEach { erro ->
            val decisao = SkyRefreshDecision.decide(SkyCycleResult.Failure(erro), agora)

            assertEquals("$erro devia pedir retry", WorkOutcome.Retry, decisao.outcome)
            assertNull("$erro não pode apagar o snapshot anterior", decisao.snapshot)
        }
    }

    @Test
    fun `o limite diario nao e reintentado`() {
        // Insistir gastaria orçamento exatamente quando ele já se esgotou (AD-010). É `Success`
        // porque o ciclo fez o que devia: descobriu que hoje não há nada a fazer.
        val decisao = SkyRefreshDecision.decide(
            SkyCycleResult.Failure(SkyError.RateLimited(retryAfterSeconds = 3_600)), agora,
        )

        assertEquals(WorkOutcome.Success, decisao.outcome)
        assertNull(decisao.snapshot)
    }

    @Test
    fun `um erro inesperado falha sem apagar nada`() {
        val decisao = SkyRefreshDecision.decide(
            SkyCycleResult.Failure(SkyError.Unexpected(IllegalStateException("boom"))), agora,
        )

        assertEquals(WorkOutcome.Failure, decisao.outcome)
        assertNull(decisao.snapshot)
    }

    // --- O que o widget diz sobre a última tentativa (FR-019) -----------------------------------

    @Test
    fun `sem ligacao e limite diario sao ditos de forma diferente`() {
        // Um resolve-se ligando a rede, o outro só passa amanhã. Uma mensagem genérica deixaria o
        // utilizador a mexer no Wi-Fi durante uma hora sem efeito nenhum.
        assertEquals(
            RefreshFeedback.NoConnection,
            SkyRefreshDecision.decide(SkyCycleResult.Failure(SkyError.NoConnection), agora).feedback,
        )
        assertEquals(
            RefreshFeedback.RateLimited,
            SkyRefreshDecision.decide(SkyCycleResult.Failure(SkyError.RateLimited(60)), agora).feedback,
        )
    }

    @Test
    fun `um ciclo bem sucedido nao deixa mensagem de falha nenhuma`() {
        // Se o feedback não fosse reposto, um "sem ligação" de há uma hora ficaria no widget depois
        // de a rede voltar — uma resposta a uma pergunta que já ninguém fez.
        val decisao = SkyRefreshDecision.decide(SkyCycleResult.Success(emptyList(), observadoEm), agora)

        assertEquals(RefreshFeedback.None, decisao.feedback)
    }

    @Test
    fun `falhas sem nada util a dizer nao inventam mensagem`() {
        listOf(SkyError.LocationUnavailable, SkyError.Unexpected(RuntimeException())).forEach { erro ->
            assertEquals(
                "não há texto útil para $erro em três linhas de widget",
                RefreshFeedback.None,
                SkyRefreshDecision.decide(SkyCycleResult.Failure(erro), agora).feedback,
            )
        }
    }
}
