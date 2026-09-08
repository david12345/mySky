package com.mysky.app.presentation.format

import com.mysky.app.domain.model.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteFormattingTest {

    @Test
    fun `uma rota conhecida produz o par pela ordem origem destino`() {
        // A ordem é a informação: trocada, o utilizador lê o percurso ao contrário e não tem como
        // dar por isso.
        assertEquals("LIS" to "CDG", RouteFormatting.pairOrNull(Route("LIS", "CDG")))
    }

    @Test
    fun `sem rota nao ha nada a apresentar`() {
        // `null` e não um par vazio: é o que faz o ecrã omitir a linha inteira em vez de reservar
        // espaço para ela.
        assertNull(RouteFormatting.pairOrNull(null))
    }
}
