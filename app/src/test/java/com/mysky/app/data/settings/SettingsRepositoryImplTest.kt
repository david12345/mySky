package com.mysky.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mysky.app.domain.model.AltitudeUnit
import com.mysky.app.domain.model.DistanceUnit
import com.mysky.app.domain.model.SkySettings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * As preferências, sobre um DataStore **real** em ficheiro temporário.
 *
 * Não é um duplo de propósito: o que se quer verificar é o comportamento perante armazenamento que
 * não existe, que está corrompido, ou que guarda valores que esta versão já não aceita. Um duplo só
 * saberia mentir da forma que o teste lhe mandasse.
 */
class SettingsRepositoryImplTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** Um ficheiro que ainda não existe: o DataStore cria-o, e um ficheiro vazio não é válido. */
    private fun newFile(name: String = "prefs"): File =
        File(folder.root, "$name.preferences_pb").also { it.delete() }

    /**
     * O escopo é devolvido para o teste o poder cancelar: o DataStore recusa duas instâncias ativas
     * sobre o mesmo ficheiro, e é isso que obriga a fechar a primeira antes de abrir a segunda.
     */
    private fun TestScope.storeOver(file: File): Pair<DataStore<Preferences>, CoroutineScope> {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        return PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }) to scope
    }

    private fun TestScope.store(file: File = newFile()): DataStore<Preferences> = storeOver(file).first

    private fun repository(store: DataStore<Preferences>) = SettingsRepositoryImpl(store)

    // --- Sem nada guardado ----------------------------------------------------------------------

    @Test
    fun `sem nada guardado saem os valores de origem`() = runTest {
        val settings = repository(store()).settings.first()

        assertEquals(SkySettings(), settings)
    }

    // --- Persistência ---------------------------------------------------------------------------

    @Test
    fun `uma escrita e lida de volta tal e qual`() = runTest {
        val repository = repository(store())

        repository.update { it.copy(detectionRadiusMeters = 80_000.0, minElevationDegrees = 12.0) }

        val settings = repository.settings.first()
        assertEquals(80_000.0, settings.detectionRadiusMeters, 0.001)
        assertEquals(12.0, settings.minElevationDegrees, 0.001)
    }

    @Test
    fun `uma escrita sobrevive a fechar e reabrir o armazenamento`() = runTest {
        // É o que "sobrevive a fechar e reabrir a app" significa aqui: o primeiro armazenamento é
        // fechado, e um segundo, novo, encontra os dados no mesmo ficheiro.
        val file = newFile("persistido")
        val (primeiro, escopo) = storeOver(file)
        repository(primeiro).update { it.copy(distanceUnit = DistanceUnit.MILES) }
        escopo.cancel()

        val relido = repository(storeOver(file).first).settings.first()

        assertEquals(DistanceUnit.MILES, relido.distanceUnit)
    }

    @Test
    fun `as unidades persistem as duas`() = runTest {
        val repository = repository(store())

        repository.update { it.copy(distanceUnit = DistanceUnit.MILES, altitudeUnit = AltitudeUnit.FEET) }

        val settings = repository.settings.first()
        assertEquals(DistanceUnit.MILES, settings.distanceUnit)
        assertEquals(AltitudeUnit.FEET, settings.altitudeUnit)
    }

    // --- Degradação de valores inválidos --------------------------------------------------------

    @Test
    fun `um valor guardado fora dos limites e lido como o mais proximo valido`() = runTest {
        // Simula o que uma versão anterior — ou uma escrita por outro caminho — poderia ter deixado.
        val store = store(newFile("fora"))
        store.edit { it[doublePreferencesKey("detection_radius_meters")] = 900_000.0 }

        val settings = repository(store).settings.first()

        assertEquals(150_000.0, settings.detectionRadiusMeters, 0.001)
    }

    @Test
    fun `um valor guardado dentro dos limites e lido intacto`() = runTest {
        // O par indispensável do teste anterior. Sem ele, um limite mal escrito "corrigiria"
        // escolhas legítimas e ninguém daria por isso.
        val store = store()
        store.edit { it[doublePreferencesKey("detection_radius_meters")] = 77_000.0 }

        assertEquals(77_000.0, repository(store).settings.first().detectionRadiusMeters, 0.001)
    }

    @Test
    fun `uma escrita com valor fora dos limites grava o valor corrigido`() = runTest {
        // O ecrã nunca deixaria escolher isto; um chamador distraído também não deve conseguir.
        val repository = repository(store())

        repository.update { it.copy(minElevationDegrees = 89.0) }

        assertEquals(60.0, repository.settings.first().minElevationDegrees, 0.001)
    }

    @Test
    fun `uma unidade guardada que esta versao nao conhece vale o valor de origem`() = runTest {
        val store = store()
        store.edit { it[stringPreferencesKey("distance_unit")] = "PARSECS" }

        assertEquals(DistanceUnit.KILOMETERS, repository(store).settings.first().distanceUnit)
    }

    // --- Armazenamento corrompido ---------------------------------------------------------------

    @Test
    fun `armazenamento ilegivel emite os valores de origem em vez de lancar`() = runTest {
        // Uma preferência corrompida não pode ser motivo para a app não arrancar: o pior que deve
        // acontecer é o utilizador reencontrar os valores de fábrica.
        val file = File(folder.root, "corrompido.preferences_pb")
        file.writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05))
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            produceFile = { file },
        )

        assertEquals(SkySettings(), repository(store).settings.first())
    }

    @Test
    fun `escrever sobre armazenamento corrompido nao lanca`() = runTest {
        // A leitura já degradava com graça; a escrita não, e isso era pior do que parecia — uma
        // exceção daqui subiria pelo `viewModelScope` do ecrã e rebentava a app ao primeiro toque
        // num cursor, num aparelho onde o ficheiro se estragou por razões que não são de ninguém.
        val file = File(folder.root, "corrompido-na-escrita.preferences_pb")
        file.writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05))
        val repository = repository(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
                produceFile = { file },
            ),
        )

        // Sem tratador de corrupção — de propósito: a promessa de não lançar é do repositório e não
        // pode depender de como quem o constrói configurou o armazenamento.
        repository.update { it.copy(detectionRadiusMeters = 80_000.0) }
    }

    // --- A reposição ----------------------------------------------------------------------------

    @Test
    fun `repor devolve ao inicio tudo o que esta feature grava`() = runTest {
        // A revisão apontou que este teste afirmava mais do que verificava: mexia em
        // `refreshIntervalMinutes` e `notificationsEnabled` e nunca os voltava a olhar.
        //
        // Verificá-los aqui teria **falhado**, e por uma razão que vale a pena ficar escrita: este
        // repositório grava cinco chaves, e nenhuma delas é dessas duas. Os campos das features que
        // ainda não existem não são persistidos, por isso não há aqui nada para proteger — e passá-los
        // ao `update` dava a impressão errada de que havia. A invariante de que `withDefaults()` não
        // toca no que é de outras features vive onde é verdadeira e verificável: em `SkySettingsTest`,
        // sobre o modelo puro. Quando essas features chegarem e trouxerem chaves, é aqui que a
        // verificação passa a fazer sentido.
        //
        // O que este teste fecha, e antes deixava a meio, é a volta completa das cinco chaves.
        val repository = repository(store())
        repository.update {
            it.copy(
                detectionRadiusMeters = 120_000.0,
                minElevationDegrees = 40.0,
                minAltitudeMeters = 2_000.0,
                distanceUnit = DistanceUnit.MILES,
                altitudeUnit = AltitudeUnit.FEET,
            )
        }

        repository.update { it.withDefaults() }

        val origem = SkySettings()
        val settings = repository.settings.first()
        assertEquals(origem.detectionRadiusMeters, settings.detectionRadiusMeters, 0.001)
        assertEquals(origem.minElevationDegrees, settings.minElevationDegrees, 0.001)
        assertEquals(origem.minAltitudeMeters, settings.minAltitudeMeters, 0.001)
        assertEquals(origem.distanceUnit, settings.distanceUnit)
        assertEquals(origem.altitudeUnit, settings.altitudeUnit)
    }

    // --- A cadência do widget (005), e porque este teste tem de existir aqui -------------------

    @Test
    fun `a cadencia escolhida sobrevive a gravacao`() = runTest {
        // O defeito que a revisão da 005 apanhou, e que não tinha teste nenhum: esta classe gravava
        // cinco chaves e a cadência não era uma delas. O cursor no ecrã movia-se, o ViewModel chamava
        // `update`, e o valor era descartado em silêncio — o trabalho de fundo corria sempre a 15
        // minutos, a gastar o dobro do orçamento previsto, e o cursor voltava sozinho ao sítio.
        //
        // Nenhum teste o apanhou porque os do ecrã usam `FakeSettingsRepository`, que guarda o objeto
        // inteiro em memória e onde a lacuna simplesmente não existe. É por isso que este teste tem
        // de estar aqui, sobre o DataStore verdadeiro.
        val (store, scope) = storeOver(File(folder.root, "cadencia.preferences_pb"))
        val repository = repository(store)

        repository.update { it.copy(refreshIntervalMinutes = 90L) }

        assertEquals(90L, repository.settings.first().refreshIntervalMinutes)
        scope.cancel()
    }

    @Test
    fun `a cadencia sobrevive a uma releitura do mesmo ficheiro`() = runTest {
        // Gravar e ler na mesma instância podia passar com um cache em memória. O que interessa é
        // que o valor esteja no disco.
        val file = File(folder.root, "cadencia-persistida.preferences_pb")
        val (primeiro, scope1) = storeOver(file)
        repository(primeiro).update { it.copy(refreshIntervalMinutes = 45L) }
        scope1.cancel()

        val (segundo, scope2) = storeOver(file)
        assertEquals(45L, repository(segundo).settings.first().refreshIntervalMinutes)
        scope2.cancel()
    }

    @Test
    fun `sem nada gravado a cadencia e a de origem e nao o minimo`() = runTest {
        // 30 minutos, não os 15 do mínimo da plataforma. Eram a mesma constante, o que fazia o código
        // contradizer em silêncio o que a especificação tinha decidido.
        val (store, scope) = storeOver(File(folder.root, "origem.preferences_pb"))

        assertEquals(
            SkySettings.DEFAULT_REFRESH_INTERVAL_MINUTES,
            repository(store).settings.first().refreshIntervalMinutes,
        )
        assertEquals(30L, SkySettings.DEFAULT_REFRESH_INTERVAL_MINUTES)
        scope.cancel()
    }

    // --- Falhas de leitura: tentar outra vez antes de desistir ----------------------------------

    /**
     * Um `DataStore` que falha as primeiras [failures] coleções e só depois entrega o valor.
     *
     * Existe para provar o que nenhum ficheiro real deixa provar de forma determinística: que uma
     * falha passageira não deixa o ecrã preso nos valores de fábrica.
     */
    private class FlakyStore(
        private val failures: Int,
        private val preferences: Preferences,
    ) : DataStore<Preferences> {
        var attempts = 0
            private set

        override val data: Flow<Preferences> = flow {
            attempts++
            if (attempts <= failures) throw IOException("falha passageira #$attempts")
            emit(preferences)
        }

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences,
        ): Preferences = throw UnsupportedOperationException("não é o que este teste verifica")
    }

    @Test
    fun `uma falha passageira de leitura volta a ser tentada`() = runTest {
        val guardado = mutablePreferencesOf().apply {
            this[doublePreferencesKey("detection_radius_meters")] = 90_000.0
        }
        val store = FlakyStore(failures = 2, preferences = guardado)

        val settings = SettingsRepositoryImpl(store).settings.first()

        assertEquals("o valor gravado devia sobreviver a duas falhas", 90_000.0, settings.detectionRadiusMeters, 0.001)
        assertEquals(3, store.attempts)
    }

    @Test
    fun `uma leitura sempre falhada acaba nos valores de origem em vez de girar para sempre`() = runTest {
        // O limite de tentativas é o que distingue degradar de pendurar: sem ele, um ficheiro
        // permanentemente ilegível fazia o `retry` girar e o ecrã nunca aparecia.
        val store = FlakyStore(failures = Int.MAX_VALUE, preferences = mutablePreferencesOf())

        val settings = SettingsRepositoryImpl(store).settings.first()

        assertEquals(SkySettings(), settings)
        assertEquals("três tentativas depois da primeira leitura", 4, store.attempts)
    }
}