package com.mysky.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * As contas que definem o que esta feature consegue prometer.
 *
 * Existem porque o número é desconfortável e tem de ficar escrito: mesmo na melhor configuração, a app
 * avisa de menos de um terço das passagens. Se alguém mudar as premissas, é aqui que a consequência
 * aparece — e é este número que o ecrã de definições mostra ao utilizador.
 */
class NotificationPolicyTest {

    @Test
    fun `a janela de visibilidade encurta depressa com o angulo`() {
        // 166 s a 30°, 55 s a 60°. É esta diferença que justifica o limiar de origem ser 30 e não 60:
        // a 60° o avião já saiu antes de o utilizador chegar à janela.
        assertEquals(166.0, NotificationPolicy.visibilityWindowSeconds(30.0), 2.0)
        assertEquals(96.0, NotificationPolicy.visibilityWindowSeconds(45.0), 2.0)
        assertEquals(55.0, NotificationPolicy.visibilityWindowSeconds(60.0), 2.0)
    }

    @Test
    fun `a tabela de captura que o ecra mostra ao utilizador`() {
        assertEquals(0.185, NotificationPolicy.expectedCaptureRate(30.0, 15), 0.005)
        assertEquals(0.092, NotificationPolicy.expectedCaptureRate(30.0, 30), 0.005)
        assertEquals(0.031, NotificationPolicy.expectedCaptureRate(60.0, 30), 0.005)
    }

    @Test
    fun `o limiar de origem apanha mais do dobro do que sessenta graus`() {
        // O número que sustenta a escolha contra-intuitiva do valor de origem.
        val aTrinta = NotificationPolicy.expectedCaptureRate(NotificationPolicy.DEFAULT_THRESHOLD_DEGREES, 30)
        val aSessenta = NotificationPolicy.expectedCaptureRate(60.0, 30)

        assertEquals(30.0, NotificationPolicy.DEFAULT_THRESHOLD_DEGREES, 0.0)
        assertTrue("$aTrinta devia ser mais do dobro de $aSessenta", aTrinta > 2 * aSessenta)
    }

    @Test
    fun `a taxa nunca passa de cem por cento`() {
        // Não é defensivo: é a fronteira do modelo. Não se apanha uma passagem mais do que uma vez.
        assertEquals(1.0, NotificationPolicy.expectedCaptureRate(1.0, 15), 0.0)
        assertEquals(1.0, NotificationPolicy.expectedCaptureRate(0.0, 60), 0.0)
    }

    @Test
    fun `uma cadencia invalida da taxa zero em vez de rebentar`() {
        assertEquals(0.0, NotificationPolicy.expectedCaptureRate(30.0, 0), 0.0)
        assertEquals(0.0, NotificationPolicy.expectedCaptureRate(30.0, -5), 0.0)
    }

    @Test
    fun `no zenite a janela e nula e nao se apanha nada`() {
        assertEquals(0.0, NotificationPolicy.visibilityWindowSeconds(90.0), 0.001)
        assertEquals(0.0, NotificationPolicy.expectedCaptureRate(90.0, 15), 0.001)
    }

    @Test
    fun `a janela de deduplicacao cobre a passagem mais longa com folga`() {
        // Se a janela fosse mais curta do que uma passagem, a mesma aeronave podia ser avisada duas
        // vezes na mesma passagem — que é precisamente o que a deduplicação existe para impedir.
        val passagemMaisLonga = NotificationPolicy.visibilityWindowSeconds(20.0)

        assertTrue(
            "a janela ($DEDUP s) tem de cobrir a passagem mais longa ($passagemMaisLonga s)",
            NotificationPolicy.DEDUPLICATION_WINDOW_SECONDS > passagemMaisLonga * 2,
        )
    }

    private companion object {
        const val DEDUP = NotificationPolicy.DEDUPLICATION_WINDOW_SECONDS
    }
}
