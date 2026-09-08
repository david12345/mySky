package com.mysky.app.data.local

import com.mysky.app.di.IoDispatcher
import com.mysky.app.domain.model.Route
import com.mysky.app.domain.repository.RouteDirectory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A tabela de rotas, lida por pesquisa binária sobre o ficheiro (AD-013).
 *
 * São 585 mil registos: num `Map` custariam da ordem de 100 MB só em overhead de objeto, o que num
 * telemóvel não é ineficiência, é inviabilidade. Assim, **nada** vai para o heap — o ficheiro fica
 * na cache de páginas do sistema, e cada consulta são cerca de 20 leituras de 13 bytes.
 *
 * Nunca lança. Ficheiro ausente, truncado ou com cabeçalho estranho degradam para tabela vazia: a
 * rota é decoração e nunca pode esconder uma aeronave.
 */
@Singleton
class FileRouteDirectory @Inject constructor(
    private val source: RouteTableSource,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : RouteDirectory {

    private val mutex = Mutex()
    private var table: OpenTable? = null
    private var loaded = false

    private class OpenTable(val reader: RouteTableReader, val header: RouteTableHeader)

    override suspend fun findByCallsign(callsign: String?): Route? {
        val key = Route.callsignKeyOf(callsign) ?: return null
        return withContext(dispatcher) {
            val open = openTable() ?: return@withContext null
            runCatching { search(open, key) }.getOrNull()
        }
    }

    /**
     * Invalidar depois de a tabela ser substituída.
     *
     * O ficheiro antigo continua legível para quem já o tem aberto — é o `rename` do sistema a
     * garanti-lo — por isso a consulta em curso termina em segurança; só a seguinte é que passa a
     * ver a tabela nova.
     */
    suspend fun invalidate() = mutex.withLock {
        table?.reader?.close()
        table = null
        loaded = false
    }

    private suspend fun openTable(): OpenTable? {
        table?.let { return it }
        return mutex.withLock {
            table?.let { return@withLock it }
            if (loaded) return@withLock null
            loaded = true

            val reader = runCatching { source.open() }.getOrNull() ?: return@withLock null
            val header = RouteTableFormat.readHeader(reader)
            if (header == null) {
                runCatching { reader.close() }
                return@withLock null
            }
            OpenTable(reader, header).also { table = it }
        }
    }

    /**
     * Pesquisa binária sobre registos de largura fixa.
     *
     * O cuidado que importa: quando a chave não existe, isto tem de devolver `null` — **nunca o
     * vizinho**. Num ficheiro ordenado, um erro de comparação não produz "não encontrado", produz a
     * rota real de outro voo, apresentada com o mesmo ar de certeza que a correta. É por isso que a
     * igualdade é verificada explicitamente no fim, e não inferida da posição.
     */
    private fun search(open: OpenTable, key: String): Route? {
        val target = paddedKey(key)
        val record = ByteArray(RouteTableFormat.RECORD_SIZE)
        var low = 0
        var high = open.header.recordCount - 1

        while (low <= high) {
            val middle = (low + high) ushr 1
            val offset = RouteTableFormat.HEADER_SIZE.toLong() +
                middle.toLong() * RouteTableFormat.RECORD_SIZE
            if (open.reader.readAt(offset, record) != RouteTableFormat.RECORD_SIZE) return null

            when {
                compareKey(record, target) < 0 -> low = middle + 1
                compareKey(record, target) > 0 -> high = middle - 1
                else -> return routeOf(record)
            }
        }
        return null
    }

    /** A chave alinhada à esquerda e preenchida, exatamente como o ficheiro a guarda. */
    private fun paddedKey(key: String): ByteArray {
        val bytes = ByteArray(RouteTableFormat.CALLSIGN_WIDTH) { RouteTableFormat.PAD }
        val ascii = key.toByteArray(Charsets.US_ASCII)
        ascii.copyInto(bytes, endIndex = minOf(ascii.size, bytes.size))
        return bytes
    }

    /** Comparação **sem sinal**: com bytes assinados, tudo acima de 0x7F ordenaria ao contrário. */
    private fun compareKey(record: ByteArray, target: ByteArray): Int {
        for (index in 0 until RouteTableFormat.CALLSIGN_WIDTH) {
            val left = record[index].toInt() and 0xFF
            val right = target[index].toInt() and 0xFF
            if (left != right) return left - right
        }
        return 0
    }

    private fun routeOf(record: ByteArray): Route? {
        val width = RouteTableFormat.CALLSIGN_WIDTH
        val iata = RouteTableFormat.IATA_WIDTH
        val origin = String(record, width, iata, Charsets.US_ASCII)
        val destination = String(record, width + iata, iata, Charsets.US_ASCII)
        // Um registo corrompido não pode derrubar a consulta: `Route` valida, e o que não passar
        // vira ausência de rota, que é o comportamento seguro.
        return runCatching { Route(origin, destination) }.getOrNull()
    }
}
