package com.mysky.app.domain.repository

import com.mysky.app.domain.model.SkySettings
import kotlinx.coroutines.flow.Flow

/** Preferências do utilizador, persistidas em DataStore. */
interface SettingsRepository {
    val settings: Flow<SkySettings>

    suspend fun update(transform: (SkySettings) -> SkySettings)
}
