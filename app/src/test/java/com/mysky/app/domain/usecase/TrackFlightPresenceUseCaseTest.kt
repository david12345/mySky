package com.mysky.app.domain.usecase

import com.mysky.app.NOW_EPOCH_SECONDS
import com.mysky.app.aircraft
import com.mysky.app.domain.model.FlightPresence
import com.mysky.app.domain.model.OverheadFlight
import com.mysky.app.overheadFlight
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A tabela de transições de `data-model.md`, linha a linha.
 *
 * O teste que justifica o ficheiro é `um ciclo falhado nao faz o aviao sair do ceu`: sem ele, perder
 * a rede passaria a anunciar ao utilizador que o avião partiu — e nada no ecrã denunciaria o
 * engano, porque a mensagem seria perfeitamente plausível.
 */
class TrackFlightPresenceUseCaseTest {

    private val useCase = TrackFlightPresenceUseCase()

    private val alvo = overheadFlight(aircraft = aircraft(icao24 = "aaa111"))
    private val outro = overheadFlight(aircraft = aircraft(icao24 = "bbb222"))

    private fun current(
        flight: OverheadFlight = alvo,
        observedAt: Long = NOW_EPOCH_SECONDS,
    ) = FlightPresence.Current(flight, observedAt)

    private fun track(
        previous: FlightPresence,
        flights: List<OverheadFlight>?,
        icao24: String? = "aaa111",
        observedAt: Long = NOW_EPOCH_SECONDS,
    ) = useCase(previous, icao24, flights, observedAt)

    // --- A partir de NeverObserved -------------------------------------------------------------

    @Test
    fun `a primeira observacao com a aeronave torna-a atual`() {
        val presence = track(FlightPresence.NeverObserved, listOf(outro, alvo))

        assertEquals(current(), presence)
    }

    @Test
    fun `nunca observada e ausente continua nunca observada, nao saida`() {
        // Entrar por uma rota antiga não pode produzir "saiu do teu céu": nunca lá esteve.
        val presence = track(FlightPresence.NeverObserved, listOf(outro))

        assertEquals(FlightPresence.NeverObserved, presence)
    }

    @Test
    fun `nunca observada com ciclo falhado continua nunca observada`() {
        val presence = track(FlightPresence.NeverObserved, flights = null)

        assertEquals(FlightPresence.NeverObserved, presence)
    }

    // --- A partir de Current -------------------------------------------------------------------

    @Test
    fun `observacao seguinte com a aeronave atualiza os valores`() {
        val novo = overheadFlight(aircraft = aircraft(icao24 = "aaa111"), elevationDegrees = 80.0)

        val presence = track(current(), listOf(novo), observedAt = NOW_EPOCH_SECONDS + 30)

        assertEquals(current(flight = novo, observedAt = NOW_EPOCH_SECONDS + 30), presence)
    }

    @Test
    fun `ceu observado sem a aeronave faz sair do ceu`() {
        val presence = track(current(), listOf(outro), observedAt = NOW_EPOCH_SECONDS + 30)

        assertEquals(FlightPresence.LeftSky(alvo, NOW_EPOCH_SECONDS), presence)
    }

    @Test
    fun `ceu observado e vazio tambem faz sair do ceu`() {
        // Lista vazia é uma observação com sucesso: o céu esvaziou-se mesmo.
        val presence = track(current(), emptyList(), observedAt = NOW_EPOCH_SECONDS + 30)

        assertEquals(FlightPresence.LeftSky(alvo, NOW_EPOCH_SECONDS), presence)
    }

    @Test
    fun `um ciclo falhado nao faz o aviao sair do ceu`() {
        // A linha que separa informar de mentir: sem rede não se sabe nada sobre o céu, e não saber
        // não é o mesmo que saber que o avião se foi embora.
        val presence = track(current(), flights = null, observedAt = NOW_EPOCH_SECONDS + 30)

        assertEquals(current(), presence)
    }

    @Test
    fun `a saida do ceu e datada de quando a aeronave foi vista, nao de agora`() {
        // Entre ser vista e descobrir-se a ausência vai um ciclo inteiro. Datar com "agora" daria
        // os valores por mais recentes do que são.
        val vistaEm = NOW_EPOCH_SECONDS
        val descobertaEm = NOW_EPOCH_SECONDS + 30

        val presence = track(current(observedAt = vistaEm), emptyList(), observedAt = descobertaEm)

        assertEquals(vistaEm, (presence as FlightPresence.LeftSky).lastSeenEpochSeconds)
    }

    // --- A partir de LeftSky --------------------------------------------------------------------

    @Test
    fun `reaparecer volta a torna-la atual sem intervencao`() {
        val presence = track(
            FlightPresence.LeftSky(alvo, NOW_EPOCH_SECONDS),
            listOf(alvo),
            observedAt = NOW_EPOCH_SECONDS + 60,
        )

        assertEquals(current(observedAt = NOW_EPOCH_SECONDS + 60), presence)
    }

    @Test
    fun `continuar ausente nao mexe no instante da ultima observacao`() {
        val saida = FlightPresence.LeftSky(alvo, NOW_EPOCH_SECONDS)

        val presence = track(saida, emptyList(), observedAt = NOW_EPOCH_SECONDS + 300)

        assertEquals(saida, presence)
    }

    @Test
    fun `um ciclo falhado depois da saida tambem nao mexe em nada`() {
        val saida = FlightPresence.LeftSky(alvo, NOW_EPOCH_SECONDS)

        assertEquals(saida, track(saida, flights = null))
    }

    // --- Rota sem aeronave ----------------------------------------------------------------------

    @Test
    fun `sem identificador na rota nao ha nada a acompanhar`() {
        val presence = track(current(), listOf(alvo), icao24 = null)

        assertEquals(FlightPresence.NeverObserved, presence)
    }
}
