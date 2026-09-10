package com.mysky.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mysky.app.domain.model.AltitudeUnit
import com.mysky.app.domain.model.DistanceUnit
import com.mysky.app.domain.model.SkySettings
import com.mysky.app.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mysky_settings",
)

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
    private val store: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<SkySettings> = store.data
        // `IOException` é o que o DataStore lança quando não consegue ler ou desserializar. Sem
        // isto, um ficheiro corrompido propagaria a exceção até ao laço de atualização.
        .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
        .map { preferences -> preferences.toSettings().coerced() }

    override suspend fun update(transform: (SkySettings) -> SkySettings) {
        store.edit { preferences ->
            val updated = transform(preferences.toSettings()).coerced()
            preferences[RADIUS] = updated.detectionRadiusMeters
            preferences[MIN_ELEVATION] = updated.minElevationDegrees
            preferences[MIN_ALTITUDE] = updated.minAltitudeMeters
            preferences[DISTANCE_UNIT] = updated.distanceUnit.name
            preferences[ALTITUDE_UNIT] = updated.altitudeUnit.name
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
        )
    }

    /** Um nome de unidade que esta versão não conheça vale o valor de origem, nunca uma exceção. */
    private inline fun <reified T : Enum<T>> enumOrDefault(stored: String?, default: T): T =
        stored?.let { name -> runCatching { enumValueOf<T>(name) }.getOrNull() } ?: default

    private companion object {
        val RADIUS = doublePreferencesKey("detection_radius_meters")
        val MIN_ELEVATION = doublePreferencesKey("min_elevation_degrees")
        val MIN_ALTITUDE = doublePreferencesKey("min_altitude_meters")
        val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        val ALTITUDE_UNIT = stringPreferencesKey("altitude_unit")
    }
}
