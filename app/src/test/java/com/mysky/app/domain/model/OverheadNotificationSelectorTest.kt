package com.mysky.app.domain.model

import com.mysky.app.aircraft
import com.mysky.app.overheadFlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverheadNotificationSelectorTest {

    private fun voo(icao: String, elevacao: Double) =
        overheadFlight(aircraft = aircraft(icao24 = icao), elevationDegrees = elevacao)

    @Test
    fun `escolhe o mais alto acima do limiar`() {
        val candidato = OverheadNotificationSelector.selectCandidate(
            listOf(voo("aaa", 35.0), voo("bbb", 72.0), voo("ccc", 41.0)),
            thresholdDegrees = 30.0,
        )

        assertEquals("bbb", candidato?.aircraft?.icao24)
    }

    @Test
    fun `escolhe o mais alto mesmo com a lista fora de ordem`() {
        // A lista chega ordenada, mas depender disso é o acoplamento implícito que esta app já
        // apanhou uma vez. Com `first()`, este teste falharia.
        val candidato = OverheadNotificationSelector.selectCandidate(
            listOf(voo("aaa", 31.0), voo("bbb", 88.0), voo("ccc", 45.0)),
            thresholdDegrees = 30.0,
        )

        assertEquals("bbb", candidato?.aircraft?.icao24)
    }

    @Test
    fun `ignora tudo o que esta abaixo do limiar`() {
        assertNull(
            OverheadNotificationSelector.selectCandidate(
                listOf(voo("aaa", 12.0), voo("bbb", 29.9)),
                thresholdDegrees = 30.0,
            ),
        )
    }

    @Test
    fun `exatamente no limiar conta como acima`() {
        // A fronteira pertence ao lado do aviso. É uma escolha, e fica fixada aqui.
        val candidato = OverheadNotificationSelector.selectCandidate(
            listOf(voo("aaa", 30.0)),
            thresholdDegrees = 30.0,
        )

        assertEquals("aaa", candidato?.aircraft?.icao24)
    }

    @Test
    fun `uma lista vazia nao produz candidato nem excecao`() {
        assertNull(OverheadNotificationSelector.selectCandidate(emptyList(), thresholdDegrees = 30.0))
    }

    @Test
    fun `um limiar no zenite quase nunca tem candidato`() {
        assertNull(
            OverheadNotificationSelector.selectCandidate(listOf(voo("aaa", 89.0)), thresholdDegrees = 90.0),
        )
    }
}
