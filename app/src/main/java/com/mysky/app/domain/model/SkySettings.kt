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
    val refreshIntervalMinutes: Long = DEFAULT_REFRESH_INTERVAL_MINUTES,
    val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
    val altitudeUnit: AltitudeUnit = AltitudeUnit.METERS,
    val notificationsEnabled: Boolean = false,
    val notificationThresholdDegrees: Double = NotificationPolicy.DEFAULT_THRESHOLD_DEGREES,
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
        detectionRadiusMeters = detectionRadiusMeters
            .snappedTo(RADIUS_STEP_METERS, RADIUS_RANGE.start).coerceIn(RADIUS_RANGE),
        minElevationDegrees = minElevationDegrees
            .snappedTo(MIN_ELEVATION_STEP_DEGREES, MIN_ELEVATION_RANGE.start).coerceIn(MIN_ELEVATION_RANGE),
        minAltitudeMeters = minAltitudeMeters
            .snappedTo(MIN_ALTITUDE_STEP_METERS, MIN_ALTITUDE_RANGE.start).coerceIn(MIN_ALTITUDE_RANGE),
        refreshIntervalMinutes = refreshIntervalMinutes
            .let { MIN_REFRESH_INTERVAL_MINUTES + Math.round((it - MIN_REFRESH_INTERVAL_MINUTES).toDouble() / REFRESH_INTERVAL_STEP_MINUTES) * REFRESH_INTERVAL_STEP_MINUTES }
            .coerceIn(REFRESH_INTERVAL_RANGE),
        // O piso **depende** do ângulo mínimo de deteção, e é a única dependência entre limites nesta
        // classe. A AD-021 recusou acoplar limites, mas o caso era outro: lá a relação era sobre
        // utilidade e era simétrica, com um cursor a mover-se debaixo do dedo. Aqui é estrutural e de
        // um sentido só — um limiar de aviso abaixo do mínimo de deteção não é menos útil, é uma faixa
        // **inatingível**, porque essas aeronaves já foram descartadas antes de chegarem à seleção. E
        // o piso do aviso nunca desloca o intervalo do controlo de deteção; só o inverso.
        notificationThresholdDegrees = notificationThresholdDegrees
            .snappedTo(NOTIFICATION_THRESHOLD_STEP_DEGREES, MIN_ELEVATION_RANGE.start)
            .coerceIn(
                minElevationDegrees.snappedTo(MIN_ELEVATION_STEP_DEGREES, MIN_ELEVATION_RANGE.start)
                    .coerceIn(MIN_ELEVATION_RANGE)..90.0,
            ),
    )

    /** Repõe **só** os campos ajustáveis no ecrã de definições, deixando os outros como estão. */
    fun withDefaults(): SkySettings = copy(
        detectionRadiusMeters = OverheadCriteria.DEFAULT_RADIUS_METERS,
        minElevationDegrees = OverheadCriteria.DEFAULT_MIN_ELEVATION_DEGREES,
        minAltitudeMeters = OverheadCriteria.DEFAULT_MIN_ALTITUDE_METERS,
        distanceUnit = DistanceUnit.KILOMETERS,
        altitudeUnit = AltitudeUnit.METERS,
        // Entrou na 005, quando a cadência ganhou controlo no ecrã. A regra desta função não mudou —
        // "os campos ajustáveis no ecrã" — mudou o conjunto a que ela se aplica. Deixá-la de fora
        // faria o botão repor tudo menos uma coisa, sem o utilizador ter como saber qual.
        refreshIntervalMinutes = DEFAULT_REFRESH_INTERVAL_MINUTES,
    )

    companion object {
        /** Mínimo imposto pelo Android para `PeriodicWorkRequest`. */
        const val MIN_REFRESH_INTERVAL_MINUTES = 15L

        /**
         * Cadência de origem do widget: **30 minutos, e não o mínimo de 15**.
         *
         * A 15 minutos o widget já está fora da janela de frescura quando acorda — uma aeronave
         * atravessa um raio de 30 km em 4 a 5 minutos — por isso duplicar a frequência não o torna
         * materialmente mais útil. Mas custa 96 consultas por dia em vez de 48, e as duas passam pelo
         * mesmo orçamento diário que o ecrã: são 24 minutos de tempo de ecrã por dia entregues por um
         * ganho que o utilizador não nota.
         *
         * **Constante separada do mínimo de propósito.** Estavam a ser a mesma, o que fazia o valor
         * de origem contradizer em silêncio o que a especificação da 005 tinha decidido. Um valor de
         * origem e um limite inferior são coisas diferentes; escrevê-los com o mesmo nome garante que
         * mudar um muda o outro sem ninguém reparar.
         */
        const val DEFAULT_REFRESH_INTERVAL_MINUTES = 30L

        /**
         * Cadência do trabalho de fundo do widget, em minutos.
         *
         * O mínimo é o que o Android impõe a trabalho periódico e não é negociável. O máximo de três
         * horas é onde o widget deixa de ter utilidade prática — com uma cadência maior, o que ele
         * mostra está sempre tão velho que mais valia não estar lá.
         *
         * Entra no [coerced] como todos os outros limites (AD-022), e é isso que dispensa o
         * agendador de validar seja o que for: ele nunca vê um valor por corrigir (FR-026).
         */
        val REFRESH_INTERVAL_RANGE = MIN_REFRESH_INTERVAL_MINUTES..180L

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
         * A granularidade de cada valor ajustável, ao lado do intervalo a que pertence.
         *
         * **Não é uma preferência de UI.** É uma afirmação sobre a precisão que os dados suportam: a
         * caixa de consulta e as próprias posições das aeronaves têm erro muito maior do que um
         * quilómetro, por isso um raio de 30 161,8 m não é mais preciso do que 30 000 — é ruído com ar
         * de precisão. Por isso vive aqui, e é aplicada em toda leitura como os limites (AD-022), e
         * não só no cursor.
         *
         * Duas consequências práticas, e a segunda era um defeito a sério:
         *
         * - sem grelha, um utilizador com o dedo num cursor de 426 m por dp **nunca mais** conseguia
         *   voltar ao valor de origem, porque "está de origem" é comparado por igualdade exata;
         * - valores gravados por versões anteriores, que caíam entre pontos da grelha, saltavam ao
         *   primeiro toque. Aplicada na leitura, a correção acontece sozinha e uma só vez.
         */
        const val RADIUS_STEP_METERS = 1_000.0
        const val MIN_ELEVATION_STEP_DEGREES = 1.0
        const val MIN_ALTITUDE_STEP_METERS = 50.0
        const val NOTIFICATION_THRESHOLD_STEP_DEGREES = 1.0

        /** Cinco minutos: o Android adia o trabalho periódico, e escolher 37 contra 38 seria fingir. */
        const val REFRESH_INTERVAL_STEP_MINUTES = 5L

        /** O valor mais próximo que assenta numa grelha alinhada com [origin]. */
        internal fun Double.snappedTo(step: Double, origin: Double): Double =
            if (step <= 0.0) this else origin + Math.round((this - origin) / step) * step

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
