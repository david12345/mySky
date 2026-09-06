package com.mysky.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AirlineTest {

    @Test
    fun `indicativo comercial devolve o prefixo de tres letras`() {
        assertEquals("TAP", Airline.icaoPrefixOf("TAP1234"))
        assertEquals("RYR", Airline.icaoPrefixOf("RYR42XZ"))
    }

    @Test
    fun `indicativo vem da fonte com espacos de padding e e limpo antes de extrair`() {
        assertEquals("TAP", Airline.icaoPrefixOf("TAP1234  "))
    }

    @Test
    fun `indicativo com menos de quatro caracteres nao e voo comercial`() {
        assertNull(Airline.icaoPrefixOf("TAP"))
        assertNull(Airline.icaoPrefixOf("AB1"))
    }

    @Test
    fun `matricula usada como indicativo nao identifica operador`() {
        // Aviação privada: a matrícula tem dígitos ou traço nos três primeiros caracteres.
        assertNull(Airline.icaoPrefixOf("N123AB"))
        assertNull(Airline.icaoPrefixOf("CS-DHA"))
    }

    @Test
    fun `indicativo ausente ou so com espacos nao tem prefixo`() {
        assertNull(Airline.icaoPrefixOf(null))
        assertNull(Airline.icaoPrefixOf(""))
        assertNull(Airline.icaoPrefixOf("     "))
    }

    @Test
    fun `prefixo em minusculas e normalizado para maiusculas`() {
        assertEquals("TAP", Airline.icaoPrefixOf("tap1234"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designador que nao sejam tres letras e rejeitado na construcao`() {
        Airline(icaoCode = "TA", name = "Qualquer")
    }
}
