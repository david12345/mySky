package com.mysky.app.data.widget

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mysky.app.di.WidgetSnapshotStore
import com.mysky.app.domain.model.SkyWidgetSnapshot
import com.mysky.app.domain.model.WidgetFlight
import com.mysky.app.domain.repository.SkyWidgetRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen

/**
 * O último resultado do trabalho de fundo, em DataStore próprio.
 *
 * **Próprio, e não o das definições**, de propósito: um guarda escolhas que o utilizador espera que
 * durem para sempre, o outro guarda um cache descartável reescrito de 30 em 30 minutos. Misturá-los
 * faria o worker escrever, a cada ciclo, no ficheiro onde vivem as preferências — e uma corrupção
 * desse ficheiro passaria a poder custar as escolhas do utilizador em vez de um snapshot que se
 * recalcula sozinho.
 *
 * Ler e escrever **nunca lançam**, pelo mesmo padrão do `SettingsRepositoryImpl` — e pela mesma razão,
 * que ali foi descoberta por revisão e não por raciocínio: um worker que rebentasse a gravar morria
 * sem deixar estado nenhum, e o widget ficava preso no que tinha sem ninguém saber porquê.
 */
@Singleton
class SkyWidgetRepositoryImpl @Inject constructor(
    @WidgetSnapshotStore private val store: DataStore<Preferences>,
) : SkyWidgetRepository {

    override val snapshot: Flow<SkyWidgetSnapshot?> = store.data
        .retryWhen { cause, attempt -> cause is IOException && attempt < READ_RETRIES }
        .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
        .map { preferences -> preferences.toSnapshot() }

    override suspend fun save(snapshot: SkyWidgetSnapshot) {
        try {
            store.edit { preferences ->
                preferences[OBSERVED_AT] = snapshot.observedAtEpochSeconds
                preferences[KIND] = snapshot.kindName()
                when (snapshot) {
                    is SkyWidgetSnapshot.Flights -> {
                        preferences[COUNT] = snapshot.count
                        preferences[ELEVATION] = snapshot.top.elevationDegrees
                        // Uma chave ausente é a forma de dizer "não sabemos" — gravar cadeia vazia
                        // tornaria indistinguível um indicativo desconhecido de um indicativo em
                        // branco, e a FR-005 depende dessa distinção.
                        snapshot.top.callsign?.let { preferences[CALLSIGN] = it }
                            ?: preferences.remove(CALLSIGN)
                        snapshot.top.airlineName?.let { preferences[AIRLINE] = it }
                            ?: preferences.remove(AIRLINE)
                    }

                    is SkyWidgetSnapshot.EmptySky, is SkyWidgetSnapshot.PermissionMissing -> {
                        // Sem isto, um céu vazio a seguir a um céu com aviões deixaria os campos do
                        // avião antigo para trás, prontos a serem lidos por engano.
                        preferences.remove(COUNT)
                        preferences.remove(ELEVATION)
                        preferences.remove(CALLSIGN)
                        preferences.remove(AIRLINE)
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (io: IOException) {
            // Perde-se o snapshot novo e fica o antigo, que é sempre melhor do que derrubar o worker.
        }
    }

    private fun SkyWidgetSnapshot.kindName(): String = when (this) {
        is SkyWidgetSnapshot.Flights -> KIND_FLIGHTS
        is SkyWidgetSnapshot.EmptySky -> KIND_EMPTY
        is SkyWidgetSnapshot.PermissionMissing -> KIND_NO_PERMISSION
    }

    /** `null` quando nunca gravou, ou quando o que está gravado não é interpretável. */
    private fun Preferences.toSnapshot(): SkyWidgetSnapshot? {
        val observedAt = this[OBSERVED_AT] ?: return null
        return when (this[KIND]) {
            KIND_EMPTY -> SkyWidgetSnapshot.EmptySky(observedAt)
            KIND_NO_PERMISSION -> SkyWidgetSnapshot.PermissionMissing(observedAt)
            KIND_FLIGHTS -> SkyWidgetSnapshot.Flights(
                top = WidgetFlight(
                    callsign = this[CALLSIGN],
                    airlineName = this[AIRLINE],
                    elevationDegrees = this[ELEVATION] ?: return null,
                ),
                count = this[COUNT] ?: 1,
                observedAtEpochSeconds = observedAt,
            )
            // Um tipo escrito por uma versão futura vale "nunca correu", nunca uma exceção.
            else -> null
        }
    }

    private companion object {
        const val READ_RETRIES = 3L

        const val KIND_FLIGHTS = "flights"
        const val KIND_EMPTY = "empty"
        const val KIND_NO_PERMISSION = "no_permission"

        val OBSERVED_AT = longPreferencesKey("observed_at_epoch_seconds")
        val KIND = stringPreferencesKey("kind")
        val COUNT = intPreferencesKey("count")
        val ELEVATION = doublePreferencesKey("top_elevation_degrees")
        val CALLSIGN = stringPreferencesKey("top_callsign")
        val AIRLINE = stringPreferencesKey("top_airline_name")
    }
}
