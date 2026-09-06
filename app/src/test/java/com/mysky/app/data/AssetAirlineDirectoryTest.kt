package com.mysky.app.data

import android.content.Context
import android.content.res.AssetManager
import com.mysky.app.data.local.AssetAirlineDirectory
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * O asset é mockado em vez de lido do disco: `assets/` não está no classpath de um teste JVM, e
 * `testOptions.unitTests.isReturnDefaultValues = true` faria a leitura real devolver vazio em
 * silêncio — um teste verde que não testaria nada. A tabela real é verificada em
 * [AirlineTableCoverageTest], que lê o ficheiro directamente do disco.
 */
class AssetAirlineDirectoryTest {

    private val assets = mockk<AssetManager>()
    private val context = mockk<Context> { every { assets } returns this@AssetAirlineDirectoryTest.assets }

    private val table = """{"TAP":"TAP Air Portugal","RYR":"Ryanair"}"""

    /**
     * Extensão de [TestScope] para o dispatcher do diretório partilhar o `testScheduler` do
     * `runTest`: um scheduler próprio faria as corrotinas correrem noutro tempo virtual.
     */
    private fun TestScope.directory(json: String? = table): AssetAirlineDirectory {
        if (json == null) {
            every { assets.open(any()) } throws IOException("asset em falta")
        } else {
            // Um stream novo por chamada: um `InputStream` já consumido daria um falso positivo
            // no teste de carregamento único.
            every { assets.open(any()) } answers { ByteArrayInputStream(json.toByteArray()) }
        }
        return AssetAirlineDirectory(context, StandardTestDispatcher(testScheduler))
    }

    @Test
    fun `prefixo conhecido resolve o operador`() = runTest {
        val airline = directory().findByCallsign("TAP1234")!!

        assertEquals("TAP", airline.icaoCode)
        assertEquals("TAP Air Portugal", airline.name)
    }

    @Test
    fun `prefixo valido ausente da tabela devolve nulo sem esconder a aeronave`() = runTest {
        assertNull(directory().findByCallsign("XXX9999"))
    }

    @Test
    fun `matricula de aviacao privada nao resolve operador`() = runTest {
        assertNull(directory().findByCallsign("CS-DHA"))
        assertNull(directory().findByCallsign("N123AB"))
    }

    @Test
    fun `indicativo ausente devolve nulo`() = runTest {
        assertNull(directory().findByCallsign(null))
        assertNull(directory().findByCallsign("   "))
    }

    @Test
    fun `asset em falta degrada para diretorio vazio sem lancar`() = runTest {
        assertNull(directory(json = null).findByCallsign("TAP1234"))
    }

    @Test
    fun `asset corrompido degrada para diretorio vazio sem lancar`() = runTest {
        assertNull(directory(json = "isto não é JSON").findByCallsign("TAP1234"))
    }

    @Test
    fun `carregamento concorrente le o asset uma so vez`() = runTest {
        val directory = directory()

        val results = List(50) { async { directory.findByCallsign("TAP1234") } }.awaitAll()

        assertEquals(50, results.count { it?.icaoCode == "TAP" })
        verify(exactly = 1) { assets.open(any()) }
    }

    @Test
    fun `consultas seguintes reutilizam a tabela em memoria`() = runTest {
        val directory = directory()

        repeat(5) { directory.findByCallsign("RYR4321") }

        verify(exactly = 1) { assets.open(any()) }
    }
}
