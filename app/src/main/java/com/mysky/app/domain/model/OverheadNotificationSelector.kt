package com.mysky.app.domain.model

/**
 * Qual das aeronaves do ciclo, se alguma, merece interromper o utilizador.
 *
 * Puro e separado do caso de uso que o chama, pela mesma razão que separa o
 * `DetectOverheadFlightsUseCase` do `ObserveSkyUseCase`: a deduplicação precisa de consultar
 * persistência e não pode contaminar a regra, que é onde os testes de tabela têm de morder.
 */
object OverheadNotificationSelector {

    /**
     * @return a aeronave de maior elevação acima de [thresholdDegrees], ou `null` se nenhuma estiver.
     *
     * **Uma, no máximo.** Um ciclo pode encontrar cinco acima do limiar; cinco notificações ao mesmo
     * tempo seriam motivo para desligar a feature no mesmo minuto.
     *
     * `maxByOrNull` e não `first()`: a lista chega ordenada por elevação, mas depender disso é o
     * acoplamento implícito que já foi apanhado uma vez nesta app — se a ordenação a montante mudar,
     * avisa-se do avião errado, sem erro nenhum.
     *
     * A fronteira pertence ao lado do aviso: exatamente no limiar **conta** como acima.
     */
    fun selectCandidate(flights: List<OverheadFlight>, thresholdDegrees: Double): OverheadFlight? =
        flights
            .filter { it.elevationDegrees >= thresholdDegrees }
            .maxByOrNull { it.elevationDegrees }
}
