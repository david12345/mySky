package com.mysky.app.data.local

import android.content.Context
import com.mysky.app.di.IoDispatcher
import com.mysky.app.domain.model.Airline
import com.mysky.app.domain.repository.AirlineDirectory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Tabela de operadores lida de `assets/airlines.json` e mantida em memória.
 *
 * São ~5800 pares de strings: carregar e indexar uma vez custa poucos milissegundos e transforma a
 * consulta numa leitura de `Map`, que é o que permite cumprir FR-011 sem qualquer pedido de rede.
 * Room seria peso morto para dados que nunca são escritos e não têm migrações (AD-007).
 *
 * Origem dos dados: OpenFlights, sob Open Database License. Ver `assets/airlines-LICENSE.txt` e
 * `tools/airlines/build_airlines_json.py`. Extraído em 2026-09-07 do commit
 * `5d623a6969a1adee7961cf1c9a8a212c4a784713` de `data/airlines.dat`, com 5774 designadores.
 */
@Singleton
class AssetAirlineDirectory @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AirlineDirectory {

    @Volatile
    private var table: Map<String, String>? = null
    private val loadMutex = Mutex()

    override suspend fun findByCallsign(callsign: String?): Airline? {
        val prefix = Airline.icaoPrefixOf(callsign) ?: return null
        val name = table()[prefix] ?: return null
        return Airline(icaoCode = prefix, name = name)
    }

    /**
     * Duplo teste sobre o mutex: o caminho quente — todas as consultas depois da primeira — não
     * chega a suspender, e várias corrotinas a arrancar ao mesmo tempo lêem o asset uma só vez.
     */
    private suspend fun table(): Map<String, String> =
        table ?: loadMutex.withLock { table ?: load().also { table = it } }

    private suspend fun load(): Map<String, String> = withContext(ioDispatcher) {
        runCatching {
            context.assets.open(ASSET_NAME).use { stream ->
                JSON.decodeFromString<Map<String, String>>(stream.readBytes().decodeToString())
            }
        }.getOrElse {
            // Um asset ausente ou corrompido degrada para tabela vazia: a lista continua a
            // funcionar sem nomes de operador. Falhar aqui esconderia as aeronaves todas por
            // causa de um campo decorativo.
            emptyMap()
        }
    }

    private companion object {
        const val ASSET_NAME = "airlines.json"
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
