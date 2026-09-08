package com.mysky.app.data.local

import com.mysky.app.domain.model.Route
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * A tabela **real**, a que viaja no APK, lida pelo código real.
 *
 * Existe para apanhar a falha mais silenciosa desta feature: a chave que
 * `tools/routes/build_routes_bin.py` escreve e a que `Route.callsignKeyOf` procura serem
 * diferentes. Se divergirem, gera-se um ficheiro de 7,6 MB cheio de rotas que a app nunca encontra
 * — todas as consultas devolvem `null`, tudo parece funcionar, e o utilizador limita-se a nunca ver
 * uma rota. Nada, em lado nenhum, dá erro.
 *
 * Um teste sobre uma tabela sintética não apanharia isto: as duas pontas seriam a mesma.
 */
class RouteTableCoverageTest {

    // O diretório de trabalho dos testes JVM é o do módulo.
    private val asset = File("src/main/assets/routes.bin")

    private fun directory(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler): FileRouteDirectory {
        val source = mockk<RouteTableSource>()
        every { source.open() } answers { FileTableReader(asset) }
        return FileRouteDirectory(source, StandardTestDispatcher(scheduler))
    }

    /** Lê as chaves de [count] registos espalhados pela tabela, diretamente do ficheiro. */
    private fun sampleKeys(count: Int): List<String> {
        val header = FileTableReader(asset).use { RouteTableFormat.readHeader(it) }
        assertNotNull("cabeçalho da tabela real", header)
        val total = header!!.recordCount
        val step = (total / count).coerceAtLeast(1)

        return FileTableReader(asset).use { reader ->
            (0 until total step step).take(count).map { index ->
                val record = ByteArray(RouteTableFormat.RECORD_SIZE)
                val offset = RouteTableFormat.HEADER_SIZE.toLong() +
                    index.toLong() * RouteTableFormat.RECORD_SIZE
                reader.readAt(offset, record)
                String(record, 0, RouteTableFormat.CALLSIGN_WIDTH, Charsets.US_ASCII).trim()
            }
        }
    }

    @Test
    fun `a tabela real tem cabecalho valido e o volume esperado`() {
        assumeTrue("tabela gerada", asset.exists())

        val header = FileTableReader(asset).use { RouteTableFormat.readHeader(it) }

        assertNotNull(header)
        assertTrue(
            "esperava pelo menos ${RouteTableFormat.MIN_PLAUSIBLE_RECORDS} rotas, tem ${header!!.recordCount}",
            header.recordCount >= RouteTableFormat.MIN_PLAUSIBLE_RECORDS,
        )
    }

    @Test
    fun `todas as chaves do ficheiro sao encontradas pela regra do dominio`() = runTest {
        assumeTrue("tabela gerada", asset.exists())
        val directory = directory(testScheduler)

        val notFound = sampleKeys(count = 300).filter { key ->
            directory.findByCallsign(key) == null
        }

        assertEquals(
            "chaves presentes no ficheiro que a app não encontra: $notFound",
            emptyList<String>(),
            notFound,
        )
    }

    @Test
    fun `a normalizacao do dominio aceita todas as chaves geradas`() {
        assumeTrue("tabela gerada", asset.exists())

        val rejected = sampleKeys(count = 300).filter { Route.callsignKeyOf(it) != it }

        assertEquals(
            "chaves do ficheiro que `callsignKeyOf` rejeita ou altera: $rejected",
            emptyList<String>(),
            rejected,
        )
    }

    @Test
    fun `um indicativo real conhecido devolve uma rota plausivel`() = runTest {
        assumeTrue("tabela gerada", asset.exists())

        val route = directory(testScheduler).findByCallsign(sampleKeys(count = 1).single())

        assertNotNull("a primeira chave da tabela tem de ter rota", route)
    }
}
