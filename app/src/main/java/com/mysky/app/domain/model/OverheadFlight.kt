package com.mysky.app.domain.model

/**
 * Aeronave considerada visível no céu do observador, com a geometria já calculada.
 *
 * Produzido por [com.mysky.app.domain.usecase.DetectOverheadFlightsUseCase].
 */
data class OverheadFlight(
    val aircraft: Aircraft,
    /** Distância horizontal observador -> projeção da aeronave no solo, em metros. */
    val horizontalDistanceMeters: Double,
    /** Azimute do observador para a aeronave, em graus (0 = norte, 90 = este). */
    val bearingDegrees: Double,
    /** Elevação acima do horizonte, em graus (0 = horizonte, 90 = zénite). */
    val elevationDegrees: Double,
    /**
     * Operador aéreo, quando o indicativo o identifica.
     *
     * Preenchido por [com.mysky.app.domain.usecase.ObserveSkyUseCase] num segundo passo, nunca
     * pelo caso de uso de deteção: consultar a tabela é `suspend` e a deteção é pura e síncrona
     * (AD-007). Por isso a omissão é `null` — um voo detetado mas ainda não enriquecido é válido.
     */
    val airline: Airline? = null,
    /**
     * Rota agendada do número de voo, quando o indicativo a identifica.
     *
     * Preenchida no mesmo segundo passo que [airline], em `ObserveSkyUseCase`. A **ausência é o
     * caso normal**, não a exceção: a maioria dos indicativos que passa no céu de um observador não
     * tem rota conhecida — aviação privada, militar, carga fora de rotas regulares. Nunca esconde a
     * aeronave nem impede a sua apresentação.
     */
    val route: Route? = null,
) {
    /**
     * Critério de ordenação: quanto mais alto no céu, mais "relevante" para o utilizador
     * (é o que o widget mostra). Usar sempre isto em vez de ordenar só por distância.
     */
    val relevanceScore: Double
        get() = elevationDegrees
}
