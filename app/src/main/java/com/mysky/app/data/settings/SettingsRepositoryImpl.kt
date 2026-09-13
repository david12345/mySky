package com.mysky.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mysky.app.domain.model.AltitudeUnit
import com.mysky.app.domain.model.DistanceUnit
import com.mysky.app.domain.model.SkySettings
import com.mysky.app.di.SettingsStore
import com.mysky.app.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen

// Aqui existia um `Context.settingsDataStore by preferencesDataStore(name = "mysky_settings")`, resto
// do esqueleto anterior ao `SettingsModule`. Ninguém o usava, mas apontava para o **mesmo ficheiro**
// que o `DataStore` fornecido pelo Hilt: bastava alguém aceitá-lo do autocomplete para o DataStore
// lançar `IllegalStateException: There are multiple DataStores active for the same file`. Um único
// ponto de criação, e é o `SettingsModule`.

/**
 * As preferências do utilizador, em DataStore.
 *
 * Duas garantias que valem mais do que o resto desta classe:
 *
 * - **`coerced()` é aplicado a toda leitura e também antes de gravar** (AD-022). Na leitura, é o que
 *   dispensa versionar o esquema: um limite que mude numa versão futura corrige sozinho o valor
 *   antigo, todas as vezes, para sempre. Na escrita, é o que impede um chamador distraído de
 *   persistir um valor que o ecrã nunca deixaria escolher.
 * - **Armazenamento ilegível emite os valores de origem, sem lançar.** Uma preferência corrompida
 *   não pode ser motivo para a app não arrancar: o pior que deve acontecer é o utilizador reencontrar
 *   os valores de fábrica.
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @SettingsStore private val store: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<SkySettings> = store.data
        // Uma falha de I/O transitória volta a tentar antes de desistir. Sem isto, o `catch` abaixo
        // resolvia o arranque e criava um problema pior a meio da sessão: `Flow.catch` emite o valor
        // de recurso e **termina a coleção**. Os `uiState` dos três ecrãs subscrevem isto durante
        // toda a vida do ecrã, por isso uma falha passageira deixava-os presos nos valores de fábrica
        // para sempre, a ignorar em silêncio tudo o que o utilizador gravasse a seguir.
        .retryWhen { cause, attempt -> cause is IOException && attempt < READ_RETRIES }
        // Esgotadas as tentativas, valem os valores de origem. `IOException` é o que o DataStore lança
        // quando não consegue ler ou desserializar; sem isto, um ficheiro corrompido propagaria a
        // exceção até ao laço de atualização.
        .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
        .map { preferences -> preferences.toSettings().coerced() }

    /**
     * Escrever nunca lança.
     *
     * A leitura já degradava com graça perante armazenamento corrompido; a escrita não, e isso era
     * pior do que parecia: uma exceção daqui subiria pelo `viewModelScope` do ecrã e **rebentava a
     * app** ao primeiro toque num cursor, num aparelho onde o ficheiro se estragou por uma razão que
     * não é culpa de ninguém.
     *
     * O `DataStore` de produção já é criado com um tratador que substitui um ficheiro ilegível por um
     * vazio, o que resolve a causa. Isto é a segunda linha de defesa: a promessa de não lançar é do
     * repositório, e não pode depender de como quem o constrói configurou o armazenamento.
     */
    override suspend fun update(transform: (SkySettings) -> SkySettings) {
        try {
            store.edit { preferences ->
                val updated = transform(preferences.toSettings()).coerced()
                preferences[RADIUS] = updated.detectionRadiusMeters
                preferences[MIN_ELEVATION] = updated.minElevationDegrees
                preferences[MIN_ALTITUDE] = updated.minAltitudeMeters
                preferences[DISTANCE_UNIT] = updated.distanceUnit.name
                preferences[ALTITUDE_UNIT] = updated.altitudeUnit.name
                preferences[REFRESH_INTERVAL] = updated.refreshIntervalMinutes
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (io: IOException) {
            // A escolha do utilizador perde-se, o que é mau; a app manter-se de pé é mais
            // importante do que gravar um valor que não podia ser gravado de qualquer forma.
        }
    }

    /**
     * Cada chave ausente vale o valor de origem — é isso que faz a primeira utilização funcionar sem
     * nada gravado, e o que mantém as escolhas de uma versão antiga quando uma chave nova aparece.
     */
    private fun Preferences.toSettings(): SkySettings {
        val defaults = SkySettings()
        return defaults.copy(
            detectionRadiusMeters = this[RADIUS] ?: defaults.detectionRadiusMeters,
            minElevationDegrees = this[MIN_ELEVATION] ?: defaults.minElevationDegrees,
            minAltitudeMeters = this[MIN_ALTITUDE] ?: defaults.minAltitudeMeters,
            distanceUnit = enumOrDefault(this[DISTANCE_UNIT], defaults.distanceUnit),
            altitudeUnit = enumOrDefault(this[ALTITUDE_UNIT], defaults.altitudeUnit),
            refreshIntervalMinutes = this[REFRESH_INTERVAL] ?: defaults.refreshIntervalMinutes,
        )
    }

    /** Um nome de unidade que esta versão não conheça vale o valor de origem, nunca uma exceção. */
    private inline fun <reified T : Enum<T>> enumOrDefault(stored: String?, default: T): T =
        stored?.let { name -> runCatching { enumValueOf<T>(name) }.getOrNull() } ?: default

    private companion object {
        /**
         * Quantas vezes uma leitura falhada volta a ser tentada antes de valerem os valores de origem.
         *
         * Três, e com um limite em vez de sem limite: um ficheiro permanentemente ilegível faria um
         * `retry` infinito girar sem nunca deixar o ecrã aparecer.
         */
        const val READ_RETRIES = 3L

        val RADIUS = doublePreferencesKey("detection_radius_meters")
        val MIN_ELEVATION = doublePreferencesKey("min_elevation_degrees")
        val MIN_ALTITUDE = doublePreferencesKey("min_altitude_meters")
        val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        val ALTITUDE_UNIT = stringPreferencesKey("altitude_unit")

        /**
         * A cadência do widget.
         *
         * **Faltava**, e a revisão da 005 apanhou-o: o ecrã tinha um cursor, o ViewModel gravava, e
         * esta classe descartava o valor em silêncio ao escrever — o trabalho de fundo corria sempre
         * a 15 minutos, gastando o dobro do orçamento previsto, e o cursor voltava ao sítio sozinho.
         * Nenhum teste o apanhou porque os testes do ecrã usam um repositório falso que guarda o
         * objeto inteiro em memória, onde a lacuna não existe.
         */
        val REFRESH_INTERVAL = longPreferencesKey("refresh_interval_minutes")
    }
}
