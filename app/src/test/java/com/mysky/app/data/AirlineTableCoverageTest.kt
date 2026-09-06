package com.mysky.app.data

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica o asset real, não o código que o lê.
 *
 * Lê o ficheiro directamente do disco porque `assets/` não está no classpath de um teste JVM — o
 * working directory dos testes unitários é a pasta do módulo `:app`. Existe para que uma extração
 * pobre falhe aqui, em segundos, em vez de só se dar por ela ao contar operadores em 100 entradas
 * reais durante a validação de SC-004.
 */
class AirlineTableCoverageTest {

    private val table: Map<String, String> by lazy {
        val file = File("src/main/assets/airlines.json")
        assertTrue(
            "airlines.json não existe — correr tools/airlines/build_airlines_json.py",
            file.exists(),
        )
        Json.decodeFromString<Map<String, String>>(file.readText())
    }

    @Test
    fun `a tabela tem operadores que cheguem para cobrir o trafego comercial`() {
        assertTrue("apenas ${table.size} operadores na tabela", table.size >= 1_000)
    }

    @Test
    fun `todas as chaves sao designadores ICAO de tres letras maiusculas`() {
        val invalid = table.keys.filterNot { it.length == 3 && it.all { c -> c in 'A'..'Z' } }

        assertTrue("designadores inválidos: $invalid", invalid.isEmpty())
    }

    @Test
    fun `nenhum operador tem nome vazio`() {
        val blank = table.filterValues { it.isBlank() }.keys

        assertTrue("operadores sem nome: $blank", blank.isEmpty())
    }

    @Test
    fun `operadores frequentes no espaco aereo portugues estao presentes`() {
        // Se a extração correr mal, é aqui que se nota: são os prefixos que qualquer utilizador
        // em Portugal vê na lista num dia normal.
        val expected = listOf("TAP", "RYR", "EZY", "AFR", "DLH", "BAW", "IBE", "VLG", "UAE", "KLM")

        val missing = expected.filterNot { table.containsKey(it) }

        assertTrue("operadores em falta na tabela: $missing", missing.isEmpty())
    }
}
