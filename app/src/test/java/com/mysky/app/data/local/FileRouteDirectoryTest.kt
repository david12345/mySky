package com.mysky.app.data.local

import com.mysky.app.domain.model.Route
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A leitura da tabela.
 *
 * O teste que justifica este ficheiro é `indicativo ausente devolve nada e nunca o vizinho`. Numa
 * pesquisa binária sobre um ficheiro ordenado, um erro de comparação não produz "não encontrado" —
 * produz a rota **real de outro voo**, apresentada ao utilizador com exatamente o mesmo ar de
 * certeza que a correta. É o defeito desta feature que ninguém consegue detetar a olhar para o
 * ecrã.
 */
class FileRouteDirectoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val source = mockk<RouteTableSource>()

    /** Extensão do `TestScope` para o dispatcher partilhar o relógio virtual do `runTest`. */
    private fun TestScope.directory(file: File) = FileRouteDirectory(
        source = source,
        dispatcher = StandardTestDispatcher(testScheduler),
    ).also {
        every { source.open() } answers { FileTableReader(file) }
    }

    private fun tableWith(routes: List<Triple<String, String, String>>) =
        RouteTableFixtures.writeTable(folder.newFile(), routes)

    private fun TestScope.sampleDirectory() = directory(tableWith(RouteTableFixtures.sampleRoutes()))

    // --- O caminho feliz -----------------------------------------------------------------------

    @Test
    fun `um indicativo conhecido devolve a sua rota`() = runTest {
        assertEquals(Route("LIS", "CDG"), sampleDirectory().findByCallsign("TAP1234"))
    }

    @Test
    fun `encontra o primeiro e o ultimo registo da tabela`() = runTest {
        // As duas fronteiras da pesquisa binária, onde um erro de índice se esconde bem.
        val directory = sampleDirectory()

        assertEquals(Route("LIS", "OPO"), directory.findByCallsign("AAA1"))
        assertEquals(Route("GRU", "EZE"), directory.findByCallsign("ZZZ9999"))
    }

    @Test
    fun `encontra indicativos de comprimentos diferentes`() = runTest {
        // A chave é preenchida à direita no ficheiro; um preenchimento mal feito faz os curtos
        // desaparecerem.
        val directory = sampleDirectory()

        assertEquals(Route("LIS", "FNC"), directory.findByCallsign("TAP99"))
        assertEquals(Route("LHR", "JFK"), directory.findByCallsign("BAW11"))
    }

    // --- O que este ficheiro existe para provar ------------------------------------------------

    @Test
    fun `indicativo ausente devolve nada e nunca o vizinho`() = runTest {
        val directory = sampleDirectory()

        // Entre dois registos existentes: `TAP1235` fica entre `TAP1234` e `TAP1236`.
        assertNull(directory.findByCallsign("TAP1235"))
        // Antes do primeiro registo da tabela.
        assertNull(directory.findByCallsign("AAA0"))
        // Depois do último.
        assertNull(directory.findByCallsign("ZZZZZZZ"))
        // Prefixo de um registo existente — o caso que a comparação com preenchimento pode falhar.
        assertNull(directory.findByCallsign("TAP123"))
    }

    // --- Ausências legítimas -------------------------------------------------------------------

    @Test
    fun `indicativo nulo ou vazio devolve nada`() = runTest {
        val directory = sampleDirectory()

        assertNull(directory.findByCallsign(null))
        assertNull(directory.findByCallsign(""))
        assertNull(directory.findByCallsign("   "))
    }

    @Test
    fun `espacos e minusculas encontram a mesma rota`() = runTest {
        assertEquals(Route("LIS", "CDG"), sampleDirectory().findByCallsign("  tap1234 "))
    }

    @Test
    fun `matricula de aviacao privada devolve nada sem erro`() = runTest {
        val directory = sampleDirectory()

        assertNull(directory.findByCallsign("CS-DHA"))
        assertNull(directory.findByCallsign("N123AB"))
    }

    // --- Ficheiros maus não podem partir nada --------------------------------------------------

    @Test
    fun `sem tabela nenhuma a consulta devolve nada em vez de rebentar`() = runTest {
        val directory = FileRouteDirectory(source, StandardTestDispatcher(testScheduler))
        every { source.open() } returns null

        assertNull(directory.findByCallsign("TAP1234"))
    }

    @Test
    fun `tabela com assinatura errada degrada para vazia`() = runTest {
        val file = RouteTableFixtures.writeTable(
            file = folder.newFile(),
            routes = RouteTableFixtures.sampleRoutes(),
            magic = "NOTMYSKY".toByteArray(Charsets.US_ASCII),
        )

        assertNull(directory(file).findByCallsign("TAP1234"))
    }

    @Test
    fun `tabela truncada degrada para vazia`() = runTest {
        val file = tableWith(RouteTableFixtures.sampleRoutes())
        val bytes = file.readBytes()
        file.writeBytes(bytes.copyOfRange(0, bytes.size - 7))

        assertNull(directory(file).findByCallsign("TAP1234"))
    }

    @Test
    fun `a tabela e aberta uma so vez para muitas consultas`() = runTest {
        // Abrir o ficheiro a cada consulta seria um syscall por aeronave, dezenas por ciclo, para
        // nada: o ficheiro não muda entre consultas.
        val directory = sampleDirectory()

        repeat(5) { directory.findByCallsign("TAP1234") }

        io.mockk.verify(exactly = 1) { source.open() }
    }
}
