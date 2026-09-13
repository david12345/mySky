package com.mysky.app.domain.model

import kotlin.math.tan

/**
 * As regras de quando avisar, e a conta que diz ao utilizador o que esperar.
 *
 * A conta é a parte que importa, e é desconfortável: com sondagem de 15 a 30 minutos, esta app avisa
 * de uma **pequena fração** das passagens. Não há desenho que o resolva sem um serviço em primeiro
 * plano permanente, que a constituição proíbe e que seria o pior custo de bateria possível.
 *
 * A saída não é esconder — é dizer. Um utilizador que ligue notificações e receba dois avisos por
 * semana conclui que a app está avariada, e tem razão em concluir, porque ninguém lhe disse o que
 * esperar. Daí a [expectedCaptureRate] ser um requisito e não um enfeite.
 *
 * Como no [SkyBudget], as constantes vivem **num sítio só**: o defeito que a revisão da 004 encontrou
 * foi um número escrito de duas maneiras que divergiu.
 */
object NotificationPolicy {

    /**
     * Quanto tempo depois de avisar uma aeronave se recusa avisá-la outra vez, em segundos.
     *
     * 30 minutos: cobre uma passagem inteira com folga larga — a mais longa da tabela abaixo tem menos
     * de 3 minutos — e não impede um aviso legítimo numa passagem posterior do mesmo voo, horas mais
     * tarde.
     */
    const val DEDUPLICATION_WINDOW_SECONDS = 1_800L

    /** Sete dias. O suficiente para a deduplicação nunca falhar, e pouco para a tabela não crescer. */
    const val RETENTION_WINDOW_SECONDS = 604_800L

    /**
     * O ângulo a partir do qual vale a pena interromper alguém — **30°, e não os 60° da intuição**.
     *
     * A 60° a janela é de 55 segundos: menos tempo do que o utilizador leva a tirar o telefone do
     * bolso e olhar para cima, o que faria a app avisar de aviões que já lá não estão quando ele
     * chega à janela. A 30° são 166 segundos, e a taxa de captura triplica.
     */
    const val DEFAULT_THRESHOLD_DEGREES = 30.0

    /** As mesmas premissas do [SkyRange]: tráfego de cruzeiro típico. */
    private const val CRUISE_ALTITUDE_METERS = 12_000.0
    private const val TYPICAL_GROUND_SPEED_MPS = 250.0

    /**
     * Quantos segundos uma aeronave em cruzeiro fica acima de [thresholdDegrees].
     *
     * É a inversa da elevação outra vez — a mesma relação que o [SkyRange] usa —, lida como distância
     * e convertida em tempo pela velocidade típica.
     */
    fun visibilityWindowSeconds(thresholdDegrees: Double): Double {
        if (thresholdDegrees <= 0.0) return Double.POSITIVE_INFINITY
        if (thresholdDegrees >= 90.0) return 0.0
        val radius = CRUISE_ALTITUDE_METERS / tan(Math.toRadians(thresholdDegrees))
        return 2 * radius / TYPICAL_GROUND_SPEED_MPS
    }

    /**
     * Que fração das passagens esta configuração espera apanhar, entre 0 e 1.
     *
     * O teto de 1.0 não é defensivo: é a fronteira do modelo. Uma cadência muito curta com um limiar
     * muito baixo daria uma fração acima de 1, que não significa nada — não se apanha uma passagem
     * mais do que uma vez.
     */
    fun expectedCaptureRate(thresholdDegrees: Double, refreshIntervalMinutes: Long): Double {
        if (refreshIntervalMinutes <= 0) return 0.0
        val window = visibilityWindowSeconds(thresholdDegrees)
        if (window == Double.POSITIVE_INFINITY) return 1.0
        return (window / (refreshIntervalMinutes * 60.0)).coerceIn(0.0, 1.0)
    }
}
