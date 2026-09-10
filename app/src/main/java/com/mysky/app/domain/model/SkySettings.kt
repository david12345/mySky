package com.mysky.app.domain.model

/** Unidade de distância escolhida pelo utilizador. */
enum class DistanceUnit { KILOMETERS, MILES }

/** Unidade de altitude escolhida pelo utilizador. */
enum class AltitudeUnit { METERS, FEET }

/**
 * Preferências persistidas do utilizador (ecrã de definições).
 *
 * [refreshIntervalMinutes] nunca deve ser inferior a [MIN_REFRESH_INTERVAL_MINUTES]: é o mínimo
 * imposto pelo WorkManager para trabalho periódico.
 */
data class SkySettings(
    val detectionRadiusMeters: Double = OverheadCriteria.DEFAULT_RADIUS_METERS,
    val minElevationDegrees: Double = OverheadCriteria.DEFAULT_MIN_ELEVATION_DEGREES,
    val minAltitudeMeters: Double = OverheadCriteria.DEFAULT_MIN_ALTITUDE_METERS,
    val refreshIntervalMinutes: Long = MIN_REFRESH_INTERVAL_MINUTES,
    val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
    val altitudeUnit: AltitudeUnit = AltitudeUnit.METERS,
    val notificationsEnabled: Boolean = false,
    val widgetEnabled: Boolean = true,
) {
    fun toCriteria(): OverheadCriteria = OverheadCriteria(
        maxHorizontalDistanceMeters = detectionRadiusMeters,
        minElevationDegrees = minElevationDegrees,
        minAltitudeMeters = minAltitudeMeters,
    )

    /**
     * Uma cópia com todos os campos ajustáveis dentro dos limites permitidos.
     *
     * Aplicada a **toda** leitura das preferências, e não numa migração pontual (AD-022). É isso que
     * dispensa versionar o esquema: se um limite mudar numa versão futura, o valor antigo é corrigido
     * todas as vezes que é lido, para sempre — sem migração e sem número de versão.
     *
     * Um valor **dentro** dos limites passa intacto. Parece óbvio e não é: um limite mal escrito aqui
     * "corrigiria" escolhas legítimas do utilizador em silêncio, o que é pior do que recusá-las.
     */
    fun coerced(): SkySettings = copy(
        detectionRadiusMeters = detectionRadiusMeters.coerceIn(RADIUS_RANGE),
        minElevationDegrees = minElevationDegrees.coerceIn(MIN_ELEVATION_RANGE),
        minAltitudeMeters = minAltitudeMeters.coerceIn(MIN_ALTITUDE_RANGE),
        refreshIntervalMinutes = refreshIntervalMinutes.coerceAtLeast(MIN_REFRESH_INTERVAL_MINUTES),
    )

    /** Repõe **só** os campos ajustáveis no ecrã de definições, deixando os outros como estão. */
    fun withDefaults(): SkySettings = copy(
        detectionRadiusMeters = OverheadCriteria.DEFAULT_RADIUS_METERS,
        minElevationDegrees = OverheadCriteria.DEFAULT_MIN_ELEVATION_DEGREES,
        minAltitudeMeters = OverheadCriteria.DEFAULT_MIN_ALTITUDE_METERS,
        distanceUnit = DistanceUnit.KILOMETERS,
        altitudeUnit = AltitudeUnit.METERS,
    )

    companion object {
        /** Mínimo imposto pelo Android para `PeriodicWorkRequest`. */
        const val MIN_REFRESH_INTERVAL_MINUTES = 15L

        /**
         * Os limites de cada valor, e o **único** ponto de verdade sobre eles.
         *
         * Consumidos por três sítios que não os redefinem: o intervalo do controlo no ecrã (FR-009),
         * a degradação de um valor guardado inválido em [coerced] (FR-008), e a frase que explica o
         * efeito prático (FR-011).
         *
         * **Derivados de utilidade geométrica, não do orçamento de consultas.** Foi verificado que
         * o custo de uma consulta depende da área da caixa envolvente e que qualquer raio utilizável
         * — até cerca de 246 km — fica no primeiro degrau: o raio não toca no orçamento. O teto real
         * da app são cerca de 3h20m de ecrã aberto por dia, e nenhuma destas escolhas o altera.
         */
        val RADIUS_RANGE = 5_000.0..150_000.0

        /**
         * Abaixo de 5° a aeronave está tão baixa que edifícios e relevo a tapam; acima de 60° a
         * lista fica quase sempre vazia. O máximo do raio sai daqui: com o ângulo no mínimo e um
         * teto de altitude de 14 km, o alcance útil é 160 km, arredondado para baixo com folga.
         */
        val MIN_ELEVATION_RANGE = 5.0..60.0

        /** A zero entram helicópteros e tráfego local, que é escolha legítima. */
        val MIN_ALTITUDE_RANGE = 0.0..3_000.0
    }
}
