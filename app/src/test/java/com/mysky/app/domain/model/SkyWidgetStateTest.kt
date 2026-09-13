package com.mysky.app.domain.model

import com.mysky.app.domain.model.SkyWidgetState.Companion.DEFAULT_FRESHNESS_WINDOW_SECONDS
import com.mysky.app.domain.model.SkyWidgetState.Companion.evaluate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A tabela de decisão dos cinco estados.
 *
 * É aqui que o SC-003 se cumpre — "nenhum estado afirma presença com base numa observação mais velha
 * do que a janela". Sem esta função pura, verificar isso exigia um telefone, esperar cinco minutos e
 * olhar para o ecrã, que é exatamente o que o princípio VI diz para não fazer.
 */
class SkyWidgetStateTest {

    private val voo = WidgetFlight(callsign = "TAP1234", airlineName = "TAP", elevationDegrees = 47.0)
    private val agora = 1_000_000L

    private fun comVoos(observadoEm: Long) =
        SkyWidgetSnapshot.Flights(top = voo, count = 3, observedAtEpochSeconds = observadoEm)

    @Test
    fun `sem snapshot nenhum o estado e nunca correu`() {
        // Distinto de céu vazio: dizer "não há aviões" sem alguma vez ter olhado seria inventar.
        assertEquals(SkyWidgetState.NoDataYet, evaluate(null, agora))
    }

    @Test
    fun `uma observacao recente permite falar no presente`() {
        val estado = evaluate(comVoos(agora - 60), agora)

        assertTrue(estado is SkyWidgetState.Fresh)
        assertEquals(voo, (estado as SkyWidgetState.Fresh).flight)
        assertEquals(3, estado.count)
    }

    @Test
    fun `uma observacao velha nunca produz um estado de presente`() {
        // O coração do SC-003. Aos 30 minutos de cadência, este é o caso comum e não a exceção.
        val estado = evaluate(comVoos(agora - 1_800), agora)

        assertTrue("uma observação de há 30 min não pode ser Fresh", estado is SkyWidgetState.Stale)
        assertEquals(agora - 1_800, (estado as SkyWidgetState.Stale).observedAtEpochSeconds)
    }

    @Test
    fun `a fronteira exata da janela ainda conta como recente`() {
        // Exatamente 300 s é fresco; 301 já não. A fronteira é uma escolha e fica fixada aqui.
        assertTrue(evaluate(comVoos(agora - DEFAULT_FRESHNESS_WINDOW_SECONDS), agora) is SkyWidgetState.Fresh)
        assertTrue(evaluate(comVoos(agora - DEFAULT_FRESHNESS_WINDOW_SECONDS - 1), agora) is SkyWidgetState.Stale)
    }

    @Test
    fun `a permissao em falta manda sobre a idade`() {
        // Mesmo antiquíssima, uma observação que terminou por falta de permissão continua a ser sobre
        // a permissão. Deixá-la virar Stale mostraria "não havia aviões" quando a verdade é que a app
        // não teve como olhar.
        val antiga = SkyWidgetSnapshot.PermissionMissing(observedAtEpochSeconds = agora - 86_400)

        assertEquals(SkyWidgetState.PermissionMissing, evaluate(antiga, agora))
    }

    @Test
    fun `ceu vazio recente e ceu vazio antigo sao o mesmo estado com frescura diferente`() {
        val recente = evaluate(SkyWidgetSnapshot.EmptySky(agora - 10), agora)
        val antigo = evaluate(SkyWidgetSnapshot.EmptySky(agora - 10_000), agora)

        assertTrue((recente as SkyWidgetState.EmptySky).isFresh)
        assertFalse((antigo as SkyWidgetState.EmptySky).isFresh)
    }

    @Test
    fun `uma observacao no futuro conta como recente e nao como idade negativa`() {
        // Acontece com o relógio do dispositivo atrasado face ao da fonte. Sem o `coerceAtLeast`, a
        // idade seria negativa — o que por acaso também daria Fresh, mas por acidente aritmético e não
        // por decisão. Fixar isto impede que uma mudança futura no sinal produza um Stale absurdo.
        assertTrue(evaluate(comVoos(agora + 600), agora) is SkyWidgetState.Fresh)
    }

    @Test
    fun `a funcao nunca lanca, nem com uma janela absurda`() {
        // Uma janela a zero torna tudo obsoleto de imediato; uma negativa também. Nenhuma delas pode
        // rebentar o widget — o pior aceitável é o utilizador ler sempre no passado.
        assertTrue(evaluate(comVoos(agora), agora, freshnessWindowSeconds = 0L) is SkyWidgetState.Fresh)
        assertTrue(evaluate(comVoos(agora - 1), agora, freshnessWindowSeconds = 0L) is SkyWidgetState.Stale)
        assertTrue(evaluate(comVoos(agora), agora, freshnessWindowSeconds = -5L) is SkyWidgetState.Stale)
    }

    @Test
    fun `as mesmas entradas dao sempre a mesma saida`() {
        // Determinismo explícito: a função não lê relógio nenhum por dentro.
        val snapshot = comVoos(agora - 100)

        assertEquals(evaluate(snapshot, agora), evaluate(snapshot, agora))
    }
}
