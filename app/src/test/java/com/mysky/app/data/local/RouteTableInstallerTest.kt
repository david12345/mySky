package com.mysky.app.data.local

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A substituição da tabela.
 *
 * Todos estes testes verificam a mesma coisa por caminhos diferentes: **uma atualização que corre
 * mal não pode deixar a app pior do que estava**. Uma tabela meio escrita é pior do que uma tabela
 * velha — a velha dá rotas desatualizadas, a meio escrita dá lixo com ar de rota.
 */
class RouteTableInstallerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context = mockk<Context>()
    private val source = mockk<RouteTableSource>()

    private lateinit var canonical: File

    /** A tabela que já lá estava, e que nenhuma falha pode estragar. */
    private lateinit var existingBytes: ByteArray

    @Before
    fun setUp() {
        val files = folder.newFolder("files")
        canonical = File(files, "routes.bin")
        RouteTableFixtures.writeTable(canonical, RouteTableFixtures.largeRoutes(150_000))
        existingBytes = canonical.readBytes()

        every { context.cacheDir } returns folder.newFolder("cache")
        every { source.updatedFile } returns canonical
        every { source.open() } answers { FileTableReader(canonical) }

    }

    /**
     * Construído dentro do `runTest` para o dispatcher partilhar o relógio do teste: um scheduler
     * à parte fá-lo-ia esperar por um tempo que ninguém avança.
     */
    private fun TestScope.installer() =
        RouteTableInstaller(context, source, StandardTestDispatcher(testScheduler))

    private fun tableBytes(count: Int): ByteArray {
        val file = RouteTableFixtures.writeTable(folder.newFile(), RouteTableFixtures.largeRoutes(count))
        return file.readBytes()
    }

    private fun assertTabelaAnteriorIntacta() {
        assertTrue("a tabela anterior tem de continuar a existir", canonical.exists())
        assertArrayEquals("a tabela anterior não pode ter sido tocada", existingBytes, canonical.readBytes())
    }

    // --- O caminho feliz ------------------------------------------------------------------------

    @Test
    fun `uma tabela valida e maior substitui a anterior`() = runTest {
        val novos = tableBytes(160_000)

        val result = installer().install(ByteArrayInputStream(novos))

        assertEquals(InstallResult.Installed, result)
        assertArrayEquals(novos, canonical.readBytes())
    }

    // --- Interrupções, ponto a ponto ------------------------------------------------------------

    @Test
    fun `interrompida antes de escrever deixa a tabela anterior intacta`() = runTest {
        val stream = object : InputStream() {
            override fun read(): Int = throw IOException("ligação caiu antes do primeiro byte")
        }

        assertEquals(InstallResult.WriteFailed, installer().install(stream))
        assertTabelaAnteriorIntacta()
    }

    @Test
    fun `interrompida a meio da escrita deixa a tabela anterior intacta`() = runTest {
        val novos = tableBytes(160_000)
        val stream = object : InputStream() {
            private var served = 0
            override fun read(destination: ByteArray, offset: Int, length: Int): Int {
                if (served > novos.size / 2) throw IOException("ligação caiu a meio")
                val n = minOf(length, novos.size - served)
                novos.copyInto(destination, offset, served, served + n)
                served += n
                return n
            }
            override fun read(): Int = throw UnsupportedOperationException()
        }

        assertEquals(InstallResult.WriteFailed, installer().install(stream))
        assertTabelaAnteriorIntacta()
    }

    @Test
    fun `ficheiro escrito por inteiro mas invalido nao substitui nada`() = runTest {
        // Uma página de erro em HTML servida no lugar do ficheiro: chega inteira e não é tabela.
        val lixo = "<html><body>404</body></html>".toByteArray()

        assertEquals(InstallResult.InvalidData, installer().install(ByteArrayInputStream(lixo)))
        assertTabelaAnteriorIntacta()
    }

    @Test
    fun `ficheiro truncado na origem nao substitui nada`() = runTest {
        val truncado = tableBytes(160_000).let { it.copyOfRange(0, it.size - 9) }

        assertEquals(InstallResult.InvalidData, installer().install(ByteArrayInputStream(truncado)))
        assertTabelaAnteriorIntacta()
    }

    // --- A regressão de cobertura (SC-009) ------------------------------------------------------

    @Test
    fun `uma tabela nova com muito menos rotas e recusada`() = runTest {
        // Bem formada, coerente, acima do mínimo absoluto — e com **27% menos rotas** do que a que
        // já lá está. É o que um truncamento na origem produz, e passaria todas as outras
        // verificações. Aceitá-la seria a app degradar-se sozinha, sem ninguém dar por isso.
        val encolhida = tableBytes(110_000)

        assertEquals(InstallResult.InvalidData, installer().install(ByteArrayInputStream(encolhida)))
        assertTabelaAnteriorIntacta()
    }

    @Test
    fun `uma tabela ligeiramente mais pequena e aceite`() = runTest {
        // Companhias fecham e rotas desaparecem: encolher um pouco é normal e não pode ser recusado.
        val ligeiramenteMenor = tableBytes(140_000)

        assertEquals(InstallResult.Installed, installer().install(ByteArrayInputStream(ligeiramenteMenor)))
        assertArrayEquals(ligeiramenteMenor, canonical.readBytes())
    }

    @Test
    fun `um rename falhado nao estraga a tabela anterior`() = runTest {
        // A Javadoc de `File.renameTo` avisa que a operação pode não funcionar entre sistemas de
        // ficheiros. Em Android o `cacheDir` e o `filesDir` vivem no mesmo volume, por isso na
        // prática funciona — mas é uma coincidência de arrumação, não um contrato. Se um dia falhar,
        // o que não pode falhar é a garantia: a tabela anterior fica.
        val bloqueado = folder.newFolder("intransponivel")
        every { source.updatedFile } returns bloqueado

        val result = installer().install(ByteArrayInputStream(tableBytes(160_000)))

        assertEquals(InstallResult.WriteFailed, result)
        assertTabelaAnteriorIntacta()
    }

    // --- Sem tabela anterior --------------------------------------------------------------------

    @Test
    fun `sem tabela anterior legivel a nova e aceite na mesma`() = runTest {
        every { source.open() } returns null
        val novos = tableBytes(160_000)

        assertEquals(InstallResult.Installed, installer().install(ByteArrayInputStream(novos)))
    }
}
