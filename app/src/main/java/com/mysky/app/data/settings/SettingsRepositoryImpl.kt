package com.mysky.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.mysky.app.domain.model.SkySettings
import com.mysky.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mysky_settings",
)

/**
 * TODO(feature/settings): implementar leitura/escrita em DataStore com valores por omissão de
 *  [SkySettings] e validação (`refreshIntervalMinutes` nunca abaixo de 15 minutos).
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    override val settings: Flow<SkySettings>
        get() = TODO("Implementar durante a feature 'definições' (ver .specify/)")

    override suspend fun update(transform: (SkySettings) -> SkySettings) {
        TODO("Implementar durante a feature 'definições' (ver .specify/)")
    }
}
