package com.mysky.app.data

import com.mysky.app.aircraft
import com.mysky.app.data.repository.FlightRepositoryImpl
import com.mysky.app.data.source.FlightDataSource
import com.mysky.app.domain.model.BoundingBox
import com.mysky.app.domain.model.SkyError
import io.mockk.coEvery
import io.mockk.mockk
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class FlightRepositoryImplTest {

    private val dataSource = mockk<FlightDataSource>()

    private val westBox = BoundingBox(38.0, 179.0, 39.0, 180.0)
    private val eastBox = BoundingBox(38.0, -180.0, 39.0, -179.0)

    /** Partilha o `testScheduler` do `runTest`: um scheduler próprio corre noutro tempo virtual. */
    private fun TestScope.repository() =
        FlightRepositoryImpl(dataSource, StandardTestDispatcher(testScheduler))

    private fun httpException(code: Int, headers: Headers = Headers.headersOf()): HttpException =
        HttpException(
            Response.error<Any>(
                "".toResponseBody("application/json".toMediaType()),
                okhttp3.Response.Builder()
                    .code(code)
                    .message("erro $code")
                    .protocol(Protocol.HTTP_1_1)
                    .headers(headers)
                    .request(Request.Builder().url("https://opensky-network.org/api/states/all").build())
                    .build(),
            ),
        )

    @Test
    fun `resultado de varias caixas vem concatenado`() = runTest {
        coEvery { dataSource.fetchAircraftIn(westBox) } returns listOf(aircraft(icao24 = "aaa111"))
        coEvery { dataSource.fetchAircraftIn(eastBox) } returns listOf(aircraft(icao24 = "bbb222"))

        val result = repository().getAircraftIn(listOf(westBox, eastBox)).getOrThrow()

        assertEquals(listOf("aaa111", "bbb222"), result.map { it.icao24 })
    }

    @Test
    fun `aeronave repetida entre caixas fica com o vetor mais recente`() = runTest {
        // Acontece sempre que o círculo do observador cruza o antimeridiano.
        coEvery { dataSource.fetchAircraftIn(westBox) } returns
            listOf(aircraft(icao24 = "aaa111", lastContactEpochSeconds = 1_000L, callsign = "ANTIGO"))
        coEvery { dataSource.fetchAircraftIn(eastBox) } returns
            listOf(aircraft(icao24 = "aaa111", lastContactEpochSeconds = 2_000L, callsign = "RECENTE"))

        val result = repository().getAircraftIn(listOf(westBox, eastBox)).getOrThrow()

        assertEquals(1, result.size)
        assertEquals("RECENTE", result.single().callsign)
    }

    @Test
    fun `duplicado sem instante de contacto perde para um que o tenha`() = runTest {
        coEvery { dataSource.fetchAircraftIn(westBox) } returns
            listOf(aircraft(icao24 = "aaa111", lastContactEpochSeconds = null, callsign = "SEM"))
        coEvery { dataSource.fetchAircraftIn(eastBox) } returns
            listOf(aircraft(icao24 = "aaa111", lastContactEpochSeconds = 1_000L, callsign = "COM"))

        val result = repository().getAircraftIn(listOf(westBox, eastBox)).getOrThrow()

        assertEquals("COM", result.single().callsign)
    }

    @Test
    fun `falha de uma caixa faz falhar o todo`() = runTest {
        // Meio céu apresentado como céu inteiro é pior do que assumir que não se conseguiu obter.
        coEvery { dataSource.fetchAircraftIn(westBox) } returns listOf(aircraft())
        coEvery { dataSource.fetchAircraftIn(eastBox) } throws IOException("sem rede")

        val result = repository().getAircraftIn(listOf(westBox, eastBox))

        assertTrue(result.isFailure)
        assertEquals(SkyError.NoConnection, result.exceptionOrNull())
    }

    @Test
    fun `sem trafego devolve sucesso com lista vazia`() = runTest {
        coEvery { dataSource.fetchAircraftIn(any()) } returns emptyList()

        val result = repository().getAircraftIn(listOf(westBox))

        assertTrue(result.isSuccess)
        assertEquals(emptyList<String>(), result.getOrThrow().map { it.icao24 })
    }

    @Test
    fun `falha de rede traduz-se em NoConnection`() = runTest {
        coEvery { dataSource.fetchAircraftIn(any()) } throws SocketTimeoutException("timeout")

        assertEquals(
            SkyError.NoConnection,
            repository().getAircraftIn(listOf(westBox)).exceptionOrNull(),
        )
    }

    @Test
    fun `erro 5xx traduz-se em FlightServiceUnavailable com o codigo`() = runTest {
        coEvery { dataSource.fetchAircraftIn(any()) } throws httpException(503)

        assertEquals(
            SkyError.FlightServiceUnavailable(503),
            repository().getAircraftIn(listOf(westBox)).exceptionOrNull(),
        )
    }

    @Test
    fun `429 traduz-se em RateLimited com o tempo de espera do cabecalho da fonte`() = runTest {
        coEvery { dataSource.fetchAircraftIn(any()) } throws
            httpException(429, Headers.headersOf("X-Rate-Limit-Retry-After-Seconds", "90"))

        assertEquals(
            SkyError.RateLimited(90L),
            repository().getAircraftIn(listOf(westBox)).exceptionOrNull(),
        )
    }

    @Test
    fun `429 aceita tambem o cabecalho padrao Retry-After`() = runTest {
        coEvery { dataSource.fetchAircraftIn(any()) } throws
            httpException(429, Headers.headersOf("Retry-After", "45"))

        assertEquals(
            SkyError.RateLimited(45L),
            repository().getAircraftIn(listOf(westBox)).exceptionOrNull(),
        )
    }

    @Test
    fun `429 sem cabecalho de espera continua a ser RateLimited`() = runTest {
        coEvery { dataSource.fetchAircraftIn(any()) } throws httpException(429)

        assertEquals(
            SkyError.RateLimited(null),
            repository().getAircraftIn(listOf(westBox)).exceptionOrNull(),
        )
    }

    @Test
    fun `erro inesperado traduz-se em Unexpected sem escapar como excecao`() = runTest {
        coEvery { dataSource.fetchAircraftIn(any()) } throws IllegalStateException("json corrompido")

        val error = repository().getAircraftIn(listOf(westBox)).exceptionOrNull()

        // A causa é comparada pelo tipo e pela mensagem, não por identidade: as corrotinas
        // recuperam o stack trace copiando a exceção original ao atravessar o `coroutineScope`.
        assertTrue(error is SkyError.Unexpected)
        val cause = (error as SkyError.Unexpected).cause
        assertTrue(cause is IllegalStateException)
        assertEquals("json corrompido", cause!!.message)
    }
}
