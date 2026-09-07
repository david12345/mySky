package com.mysky.app.domain.usecase

import com.mysky.app.domain.model.FlightPresence
import com.mysky.app.domain.model.OverheadFlight
import javax.inject.Inject

/**
 * Decide se a aeronave que o utilizador está a ver continua no céu dele.
 *
 * Redução pura, na forma da [DetectOverheadFlightsUseCase]: o estado anterior entra como parâmetro
 * em vez de viver num campo, e o instante entra como parâmetro em vez de ser lido de um relógio.
 * Assim é testável na JVM em milissegundos e não tem memória escondida.
 *
 * A distinção que justifica esta classe está no tipo de [flights]: `null` é **o ciclo falhou** e é
 * diferente de lista vazia, que é **o céu foi observado e estava vazio**. Sem essa diferença, uma
 * falha de rede passaria a anunciar que o avião saiu do céu — que é a mentira mais fácil de contar
 * neste ecrã, e a que ninguém consegue desmentir a olhar para ele.
 */
class TrackFlightPresenceUseCase @Inject constructor() {

    /**
     * @param flights observação mais recente, ou `null` se o ciclo falhou.
     * @param observedAtEpochSeconds instante a que [flights] diz respeito. Ignorado quando `null`.
     */
    operator fun invoke(
        previous: FlightPresence,
        icao24: String?,
        flights: List<OverheadFlight>?,
        observedAtEpochSeconds: Long,
    ): FlightPresence {
        // Sem aeronave na rota não há nada a acompanhar; o ecrã mostra o erro de navegação.
        if (icao24 == null) return FlightPresence.NeverObserved

        // Uma falha não prova ausência: o estado anterior mantém-se tal e qual.
        val observed = flights ?: return previous

        val flight = observed.firstOrNull { it.aircraft.icao24 == icao24 }
        if (flight != null) return FlightPresence.Current(flight, observedAtEpochSeconds)

        return when (previous) {
            // Estava, deixou de estar: é isto que o ecrã tem de comunicar.
            is FlightPresence.Current ->
                FlightPresence.LeftSky(previous.flight, previous.observedAtEpochSeconds)
            // Já tinha saído; o instante da última observação não se mexe.
            is FlightPresence.LeftSky -> previous
            // Nunca esteve lá: não "saiu" coisa nenhuma.
            FlightPresence.NeverObserved -> FlightPresence.NeverObserved
        }
    }
}
