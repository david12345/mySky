package com.mysky.app.di

import com.mysky.app.data.source.FlightDataSource
import com.mysky.app.data.source.opensky.OpenSkyFlightDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Único ponto do projeto onde se escolhe a fonte de dados de voo concreta.
 *
 * Para acrescentar/trocar de fonte (ADS-B Exchange, airplanes.live, mock em testes) basta alterar
 * este binding — nenhuma outra classe conhece a implementação.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataSourceModule {

    @Binds
    @Singleton
    abstract fun bindFlightDataSource(impl: OpenSkyFlightDataSource): FlightDataSource
}
