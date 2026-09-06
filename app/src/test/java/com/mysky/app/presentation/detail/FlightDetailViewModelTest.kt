package com.mysky.app.presentation.detail

import androidx.lifecycle.SavedStateHandle
import com.mysky.app.domain.repository.SightingRepository
import com.mysky.app.presentation.navigation.MySkyRoutes
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O detalhe é especificado à parte; desta feature só faz parte a navegação lá chegar identificada
 * com a aeronave certa (FR-028). É isso — e só isso — que estes testes fixam.
 */
class FlightDetailViewModelTest {

    private val sightingRepository = mockk<SightingRepository>()

    @Test
    fun `o icao24 da rota chega ao ViewModel`() {
        val savedState = SavedStateHandle(mapOf(FlightDetailViewModel.ARG_ICAO24 to "3c6444"))

        val viewModel = FlightDetailViewModel(savedState, sightingRepository)

        assertEquals("3c6444", viewModel.icao24)
    }

    @Test
    fun `sem argumento na rota o identificador vem nulo em vez de rebentar`() {
        val viewModel = FlightDetailViewModel(SavedStateHandle(), sightingRepository)

        assertNull(viewModel.icao24)
    }

    @Test
    fun `a rota do detalhe usa o mesmo nome de argumento que o ViewModel le`() {
        // Se um dos lados for renomeado sem o outro, o detalhe abre sempre sem aeronave — e nada
        // rebenta, o que é o pior tipo de falha.
        assertTrue(MySkyRoutes.FLIGHT_DETAIL.contains("{${FlightDetailViewModel.ARG_ICAO24}}"))
    }

    @Test
    fun `a rota construida inclui o identificador da aeronave`() {
        assertEquals("flight/3c6444", MySkyRoutes.flightDetail("3c6444"))
    }
}
