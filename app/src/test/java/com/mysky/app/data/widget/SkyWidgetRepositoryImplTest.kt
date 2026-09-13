package com.mysky.app.data.widget

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.mysky.app.domain.model.SkyWidgetSnapshot
import com.mysky.app.domain.model.WidgetFlight
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
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * O armazenamento do último resultado.
 *
 * O teste que mais importa é o último: **escrever sobre armazenamento corrompido não pode lançar**.
 * Foi um defeito real encontrado pela revisão da 004 no repositório das definições, onde ler
 * degradava com graça e escrever rebentava. Aqui o sintoma seria pior — um worker de fundo a morrer
 * sem deixar rasto, com o widget preso no que tinha e ninguém a saber porquê.
 */
class SkyWidgetRepositoryImplTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.storeOver(file: File): Pair<DataStore<Preferences>, CoroutineScope> {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        return PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }) to scope
    }

    private val voo = WidgetFlight(callsign = "TAP1234", airlineName = "TAP Air Portugal", elevationDegrees = 47.5)

    @Test
    fun `sem nada gravado o snapshot e nulo`() = runTest {
        // `null` significa "nunca correu", e é distinto de céu vazio. Confundi-los diria ao
        // utilizador que não há aviões sem a app alguma vez ter olhado.
        val (store, scope) = storeOver(File(folder.root, "vazio.preferences_pb"))

        assertNull(SkyWidgetRepositoryImpl(store).snapshot.first())
        scope.cancel()
    }

    @Test
    fun `um snapshot com voos sobrevive a volta completa`() = runTest {
        val (store, scope) = storeOver(File(folder.root, "voos.preferences_pb"))
        val repository = SkyWidgetRepositoryImpl(store)

        repository.save(SkyWidgetSnapshot.Flights(top = voo, count = 4, observedAtEpochSeconds = 1_700L))

        val lido = repository.snapshot.first() as SkyWidgetSnapshot.Flights
        assertEquals(voo, lido.top)
        assertEquals(4, lido.count)
        assertEquals(1_700L, lido.observedAtEpochSeconds)
        scope.cancel()
    }

    @Test
    fun `ceu vazio e permissao em falta sao guardados como coisas diferentes`() = runTest {
        val (store, scope) = storeOver(File(folder.root, "variantes.preferences_pb"))
        val repository = SkyWidgetRepositoryImpl(store)

        repository.save(SkyWidgetSnapshot.EmptySky(500L))
        assertEquals(SkyWidgetSnapshot.EmptySky(500L), repository.snapshot.first())

        repository.save(SkyWidgetSnapshot.PermissionMissing(600L))
        assertEquals(SkyWidgetSnapshot.PermissionMissing(600L), repository.snapshot.first())
        scope.cancel()
    }

    @Test
    fun `um ceu vazio a seguir a voos nao deixa o aviao antigo para tras`() = runTest {
        // Sem limpar as chaves do avião, um céu vazio guardaria por baixo os campos do voo anterior,
        // prontos a serem lidos por engano por qualquer alteração futura na leitura.
        val (store, scope) = storeOver(File(folder.root, "sobrepor.preferences_pb"))
        val repository = SkyWidgetRepositoryImpl(store)

        repository.save(SkyWidgetSnapshot.Flights(top = voo, count = 2, observedAtEpochSeconds = 100L))
        repository.save(SkyWidgetSnapshot.EmptySky(200L))

        assertEquals(SkyWidgetSnapshot.EmptySky(200L), repository.snapshot.first())
        scope.cancel()
    }

    @Test
    fun `uma aeronave sem indicativo nem companhia sobrevive com os nulos intactos`() = runTest {
        // A ausência tem de continuar a ser ausência depois da volta ao disco: gravar cadeia vazia
        // tornaria "desconhecido" indistinguível de "em branco", e a FR-005 depende disso.
        val (store, scope) = storeOver(File(folder.root, "anonimo.preferences_pb"))
        val repository = SkyWidgetRepositoryImpl(store)
        val anonimo = WidgetFlight(callsign = null, airlineName = null, elevationDegrees = 12.0)

        repository.save(SkyWidgetSnapshot.Flights(top = anonimo, count = 1, observedAtEpochSeconds = 10L))

        val lido = repository.snapshot.first() as SkyWidgetSnapshot.Flights
        assertNull(lido.top.callsign)
        assertNull(lido.top.airlineName)
        scope.cancel()
    }

    @Test
    fun `um ficheiro ilegivel vale nunca correu, em vez de rebentar`() = runTest {
        val store = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("ilegível") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
                throw IOException("ilegível")
        }

        assertNull(SkyWidgetRepositoryImpl(store).snapshot.first())
    }

    @Test
    fun `escrever sobre armazenamento corrompido nao lanca`() = runTest {
        // O defeito que a revisão da 004 encontrou no repositório irmão. Aqui morreria o worker de
        // fundo, em silêncio, e o widget ficaria preso no último valor sem sintoma nenhum.
        val store = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("ilegível") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
                throw IOException("ilegível")
        }

        SkyWidgetRepositoryImpl(store).save(SkyWidgetSnapshot.EmptySky(1L))
    }
}
