package com.mysky.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A validação do cabeçalho.
 *
 * Nada aqui pode lançar: um ficheiro mau tem de degradar para "sem rotas", nunca para app
 * rebentada. E cada uma destas verificações apanha uma forma diferente de um download correr mal —
 * a assinatura apanha uma página de erro em HTML, a contagem apanha um ficheiro truncado que por
 * azar ainda trouxe o cabeçalho inteiro.
 */
class RouteTableFormatTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun table(
        routes: List<Triple<String, String, String>> = RouteTableFixtures.sampleRoutes(),
        version: Int = RouteTableFormat.VERSION,
        magic: ByteArray = RouteTableFormat.MAGIC,
        declaredCount: Int = routes.size,
    ) = RouteTableFixtures.writeTable(
        file = folder.newFile(),
        routes = routes,
        version = version,
        magic = magic,
        declaredCount = declaredCount,
    )

    private fun headerOf(file: java.io.File) =
        RouteTableFixtures.reader(file).use { RouteTableFormat.readHeader(it) }

    @Test
    fun `um cabecalho valido le-se com a contagem e a data`() {
        val header = headerOf(table())

        assertNotNull(header)
        assertEquals(RouteTableFormat.VERSION, header!!.formatVersion)
        assertEquals(7, header.recordCount)
        assertEquals(1_757_289_600L, header.generatedAtEpochSeconds)
    }

    @Test
    fun `assinatura errada e recusada`() {
        // É o que apanha uma página de erro em HTML servida no lugar do ficheiro.
        assertNull(headerOf(table(magic = "NOTMYSKY".toByteArray(Charsets.US_ASCII))))
    }

    @Test
    fun `versao de formato desconhecida e recusada`() {
        // Uma app antiga não pode tentar ler um formato que ainda não sabe interpretar.
        assertNull(headerOf(table(version = RouteTableFormat.VERSION + 1)))
    }

    @Test
    fun `contagem que nao bate certo com o tamanho e recusada`() {
        assertNull(headerOf(table(declaredCount = 999)))
    }

    @Test
    fun `ficheiro truncado a meio de um registo e recusado`() {
        val file = table()
        val bytes = file.readBytes()
        file.writeBytes(bytes.copyOfRange(0, bytes.size - 5))

        assertNull(headerOf(file))
    }

    @Test
    fun `ficheiro mais curto do que o cabecalho e recusado`() {
        val file = folder.newFile()
        file.writeBytes(ByteArray(10))

        assertNull(headerOf(file))
    }

    @Test
    fun `ficheiro vazio e recusado`() {
        assertNull(headerOf(folder.newFile()))
    }

    @Test
    fun `tabela sem registos e recusada`() {
        // Tecnicamente coerente — cabeçalho válido, tamanho a bater certo — e inútil.
        assertNull(headerOf(table(routes = emptyList())))
    }
}
