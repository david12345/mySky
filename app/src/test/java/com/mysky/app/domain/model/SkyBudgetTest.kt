package com.mysky.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * As contas que sustentam a cadência por omissão.
 *
 * Existem porque a escolha dos 30 minutos não é gosto: é o resultado destas contas contra o facto de
 * uma aeronave só estar no céu 4 a 5 minutos. Se alguém mudar o orçamento ou a cadência, é aqui que a
 * consequência aparece.
 */
class SkyBudgetTest {

    @Test
    fun `a tabela de custo das cadencias`() {
        assertEquals(96, SkyBudget.queriesPerDay(15))
        assertEquals(48, SkyBudget.queriesPerDay(30))
        assertEquals(24, SkyBudget.queriesPerDay(60))
    }

    @Test
    fun `o SC-007 verificado literalmente`() {
        // "Com a cadência por omissão, o widget consome no máximo 12% do orçamento diário, deixando
        // pelo menos 2h50m de tempo de ecrã."
        val porOmissao = 30L

        assertEquals(48, SkyBudget.queriesPerDay(porOmissao))
        assertTrue(
            "a fatia devia ser no máximo 12%, foi ${SkyBudget.budgetShare(porOmissao)}",
            SkyBudget.budgetShare(porOmissao) <= 0.12,
        )
        val sobra = SkyBudget.remainingScreenSeconds(porOmissao)
        assertTrue("deviam sobrar pelo menos 2h50m, sobraram ${sobra / 60} min", sobra >= 2 * 3600 + 50 * 60)
        assertEquals(352 * 30L, sobra)
    }

    @Test
    fun `duplicar a frequencia custa vinte e quatro minutos de ecra por dia`() {
        // O número que justifica a cadência por omissão ser 30 e não os 15 do mínimo: aos 15 o widget
        // já está fora da janela de frescura quando acorda, por isso o dobro da frequência não o torna
        // mais útil — só mais caro.
        val diferenca = SkyBudget.remainingScreenSeconds(30) - SkyBudget.remainingScreenSeconds(15)

        assertEquals(24 * 60L, diferenca)
    }

    @Test
    fun `uma cadencia absurda nao produz tempo de ecra negativo`() {
        // 1 minuto dariam 1440 consultas, muito acima das 400. O utilizador tem de ler "zero", não um
        // número negativo — e isto não é hipotético se um dia o mínimo da plataforma mudar.
        assertEquals(0L, SkyBudget.remainingScreenSeconds(1))
        assertEquals(0, SkyBudget.queriesPerDay(0))
        assertEquals(0, SkyBudget.queriesPerDay(-5))
    }
}
