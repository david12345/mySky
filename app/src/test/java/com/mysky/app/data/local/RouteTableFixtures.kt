package com.mysky.app.data.local

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Constrói tabelas de rotas reais, em ficheiros temporários.
 *
 * Os testes desta camada usam ficheiros a sério e não duplos do leitor: o que se quer verificar é o
 * comportamento perante ficheiros **maus** — truncados, com cabeçalho estranho, vazios — e um duplo
 * só saberia mentir da forma que o teste lhe mandasse.
 */
object RouteTableFixtures {

    /** Escreve uma tabela válida com as rotas dadas, ordenadas como o script as ordena. */
    fun writeTable(
        file: File,
        routes: List<Triple<String, String, String>>,
        generatedAtEpochSeconds: Long = 1_757_289_600L,
        version: Int = RouteTableFormat.VERSION,
        magic: ByteArray = RouteTableFormat.MAGIC,
        declaredCount: Int = routes.size,
    ): File {
        val sorted = routes.sortedBy { it.first.padEnd(RouteTableFormat.CALLSIGN_WIDTH) }
        val header = ByteBuffer.allocate(RouteTableFormat.HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
        header.put(magic)
        header.putShort(version.toShort())
        header.putInt(declaredCount)
        header.putLong(generatedAtEpochSeconds)

        file.outputStream().use { out ->
            out.write(header.array())
            sorted.forEach { (callsign, origin, destination) ->
                out.write(callsign.padEnd(RouteTableFormat.CALLSIGN_WIDTH).toByteArray(Charsets.US_ASCII))
                out.write(origin.toByteArray(Charsets.US_ASCII))
                out.write(destination.toByteArray(Charsets.US_ASCII))
            }
        }
        return file
    }

    /** Uma tabela pequena mas realista: indicativos de comprimentos diferentes, ordenados. */
    fun sampleRoutes(): List<Triple<String, String, String>> = listOf(
        Triple("AAA1", "LIS", "OPO"),
        Triple("BAW11", "LHR", "JFK"),
        Triple("RYR9999", "STN", "OPO"),
        Triple("TAP1234", "LIS", "CDG"),
        Triple("TAP1236", "LIS", "MAD"),
        Triple("TAP99", "LIS", "FNC"),
        Triple("ZZZ9999", "GRU", "EZE"),
    )

    fun reader(file: File): RouteTableReader = FileTableReader(file)

    /**
     * Uma tabela com [count] rotas sintéticas, para exercitar o limiar de volume real.
     *
     * As chaves têm largura fixa (`A000001`), por isso a ordem alfabética coincide com a numérica —
     * e o ficheiro sai ordenado como a pesquisa binária espera.
     */
    fun largeRoutes(count: Int): List<Triple<String, String, String>> =
        (1..count).map { index -> Triple("A%06d".format(index), "LIS", "OPO") }
}
