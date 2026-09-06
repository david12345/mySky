package com.mysky.app.di

import com.mysky.app.data.local.AssetAirlineDirectory
import com.mysky.app.data.repository.FlightRepositoryImpl
import com.mysky.app.data.repository.LocationRepositoryImpl
import com.mysky.app.data.repository.SightingRepositoryImpl
import com.mysky.app.data.settings.SettingsRepositoryImpl
import com.mysky.app.data.time.SystemTimeProvider
import com.mysky.app.domain.repository.AirlineDirectory
import com.mysky.app.domain.repository.FlightRepository
import com.mysky.app.domain.repository.LocationRepository
import com.mysky.app.domain.repository.SettingsRepository
import com.mysky.app.domain.repository.SightingRepository
import com.mysky.app.domain.time.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindFlightRepository(impl: FlightRepositoryImpl): FlightRepository

    @Binds
    @Singleton
    abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindSightingRepository(impl: SightingRepositoryImpl): SightingRepository

    /** Tabela de operadores: porta própria, não uma fonte de voos (AD-007). */
    @Binds
    @Singleton
    abstract fun bindAirlineDirectory(impl: AssetAirlineDirectory): AirlineDirectory

    /** Único ponto por onde o relógio real entra no sistema (AD-009). */
    @Binds
    @Singleton
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider
}
