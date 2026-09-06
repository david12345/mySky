package com.mysky.app.presentation.main

import com.mysky.app.domain.model.SkyError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyErrorMessagesTest {

    private val allVariants = listOf(
        SkyError.NoConnection,
        SkyError.FlightServiceUnavailable(503),
        SkyError.RateLimited(90L),
        SkyError.LocationUnavailable,
        SkyError.Unexpected(IllegalStateException("x")),
    )

    @Test
    fun `cada causa produz uma mensagem distinta`() {
        // FR-024: sem isto, "sem rede" e "o serviço está em baixo" seriam o mesmo ecrã, e o
        // utilizador não saberia se o problema era dele.
        val messages = allVariants.map { it.messageRes() }

        assertEquals(allVariants.size, messages.toSet().size)
    }

    @Test
    fun `nenhuma variante fica sem mensagem`() {
        assertTrue(allVariants.all { it.messageRes() != 0 })
    }

    @Test
    fun `todas as causas oferecem repeticao`() {
        assertTrue(allVariants.all { it.retryActionRes != 0 })
    }

    @Test
    fun `o codigo HTTP nao muda a mensagem de servico indisponivel`() {
        // A mensagem é sobre o que o utilizador pode fazer, não sobre o código da resposta.
        assertEquals(
            SkyError.FlightServiceUnavailable(500).messageRes(),
            SkyError.FlightServiceUnavailable(503).messageRes(),
        )
    }
}
