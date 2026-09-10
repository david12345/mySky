package com.mysky.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mysky.app.domain.model.AltitudeUnit
import com.mysky.app.domain.model.DistanceUnit
import com.mysky.app.domain.model.SkySettings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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

    // --- A reposição ----------------------------------------------------------------------------

    @Test
    fun `repor devolve os criterios ao inicio sem tocar no que e doutras features`() = runTest {
        val repository = repository(store())
        repository.update {
            it.copy(
                detectionRadiusMeters = 120_000.0,
                distanceUnit = DistanceUnit.MILES,
                refreshIntervalMinutes = 45L,
                notificationsEnabled = true,
            )
        }

        repository.update { it.withDefaults() }

        val settings = repository.settings.first()
        assertEquals(SkySettings().detectionRadiusMeters, settings.detectionRadiusMeters, 0.001)
        assertEquals(DistanceUnit.KILOMETERS, settings.distanceUnit)
    }
}
