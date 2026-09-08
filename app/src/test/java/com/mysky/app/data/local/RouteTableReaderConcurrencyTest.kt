package com.mysky.app.data.local

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Leituras concorrentes sobre a mesma tabela.
 *
 * Isto tem de ser um teste com **threads reais**, e não com corrotinas em tempo virtual: o defeito
 * que existe para apanhar é uma corrida entre `seek` e `read`, e um `StandardTestDispatcher`
 * executa tudo em série — não a reproduziria nunca.
 *
 * E a corrida importa porque é o caso normal, não o excecional: por desenho (AD-015) dezenas de
 * aeronaves consultam a tabela ao mesmo tempo, em `Dispatchers.IO`, que é multi-thread. Com um
 * ponteiro de posição partilhado, uma consulta lê a partir da posição para onde outra saltou — e
 * devolve a rota de outro voo, com o mesmo ar de certeza que a correta.
 */
class RouteTableReaderConcurrencyTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val routes = RouteTableFixtures.largeRoutes(RECORDS)

    @Test
    fun `mil leituras concorrentes devolvem sempre o registo do deslocamento pedido`() {
        val file = RouteTableFixtures.writeTable(folder.newFile(), routes)

        FileTableReader(file).use { reader ->
            val pool = Executors.newFixedThreadPool(THREADS)
            try {
                val tasks = (0 until READS).map { attempt ->
                    val index = attempt % RECORDS
                    Callable {
                        val record = ByteArray(RouteTableFormat.RECORD_SIZE)
                        val offset = RouteTableFormat.HEADER_SIZE.toLong() +
                            index.toLong() * RouteTableFormat.RECORD_SIZE
                        reader.readAt(offset, record)
                        index to String(record, 0, RouteTableFormat.CALLSIGN_WIDTH, Charsets.US_ASCII).trim()
                    }
                }

                val wrong = pool.invokeAll(tasks).map { it.get(30, TimeUnit.SECONDS) }
                    .filter { (index, key) -> key != routes[index].first }

                assertEquals("leituras que devolveram o registo errado: ${wrong.take(5)}", 0, wrong.size)
            } finally {
                pool.shutdownNow()
            }
        }
    }

    private companion object {
        const val RECORDS = 500
        const val THREADS = 16
        const val READS = 2_000
    }
}
