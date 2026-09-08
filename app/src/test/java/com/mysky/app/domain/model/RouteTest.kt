package com.mysky.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * O `Route` é onde a regra "sem os dois aeroportos não se apresenta nenhum" vive. Se ela sair daqui
 * para quem desenha o ecrã, perde-se — e passa a haver meia rota no ecrã sem ninguém dar por isso.
 */
class RouteTest {

    @Test
    fun `uma rota valida guarda as duas siglas`() {
        val route = Route("LIS", "CDG")

        assertEquals("LIS", route.originIata)
        assertEquals("CDG", route.destinationIata)
    }

    @Test
    fun `origem igual a destino e valida`() {
        // Voo de instrução, de teste, ou de regresso ao ponto de partida: é informação legítima,
        // não um erro a esconder.
        assertEquals("LIS", Route("LIS", "LIS").destinationIata)
    }

    @Test
    fun `siglas que nao sejam tres letras maiusculas sao recusadas`() {
        assertThrows(IllegalArgumentException::class.java) { Route("LI", "CDG") }
        assertThrows(IllegalArgumentException::class.java) { Route("LIS", "CDGX") }
        assertThrows(IllegalArgumentException::class.java) { Route("lis", "CDG") }
        assertThrows(IllegalArgumentException::class.java) { Route("LI5", "CDG") }
        assertThrows(IllegalArgumentException::class.java) { Route("", "CDG") }
    }

    // --- A chave, que tem de coincidir com a que o script gera ---------------------------------

    @Test
    fun `a chave normaliza espacos e minusculas`() {
        assertEquals("TAP1234", Route.callsignKeyOf("  tap1234 "))
        assertEquals("TAP1234", Route.callsignKeyOf("TAP1234"))
    }

    @Test
    fun `indicativo ausente ou vazio nao tem chave`() {
        assertNull(Route.callsignKeyOf(null))
        assertNull(Route.callsignKeyOf(""))
        assertNull(Route.callsignKeyOf("   "))
    }

    @Test
    fun `indicativo maior do que a largura do registo nao tem chave`() {
        // Sete é o máximo medido nos 619 922 registos da fonte, e é a largura do campo no ficheiro.
        assertEquals("ABCDEFG", Route.callsignKeyOf("ABCDEFG"))
        assertNull(Route.callsignKeyOf("ABCDEFGH"))
    }

    @Test
    fun `matriculas com tracos nao sao chaves de rota`() {
        // Aviação privada não tem número de voo comercial, logo não tem rota. Recusar aqui é o que
        // impede uma matrícula de ir bater a uma entrada da tabela por acaso.
        assertNull(Route.callsignKeyOf("CS-DHA"))
        assertNull(Route.callsignKeyOf("N123-A"))
    }
}
