package com.mysky.app.di

import com.mysky.app.data.local.FileRouteDirectory
import com.mysky.app.data.local.RouteTableCache
import com.mysky.app.data.route.AndroidNetworkAvailability
import com.mysky.app.data.route.NetworkAvailability
import com.mysky.app.data.route.RouteTableRepositoryImpl
import com.mysky.app.domain.repository.RouteDirectory
import com.mysky.app.domain.repository.RouteTableRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Ligações da tabela de rotas. Módulo próprio, e não uma linha no `RepositoryModule`, porque a
 * feature traz também o worker e o repositório da atualização — e é aqui que tudo isso se junta.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RouteModule {

    @Binds
    @Singleton
    abstract fun bindRouteDirectory(implementation: FileRouteDirectory): RouteDirectory

    @Binds
    @Singleton
    abstract fun bindRouteTableCache(implementation: FileRouteDirectory): RouteTableCache

    @Binds
    @Singleton
    abstract fun bindNetworkAvailability(
        implementation: AndroidNetworkAvailability,
    ): NetworkAvailability

    @Binds
    @Singleton
    abstract fun bindRouteTableRepository(
        implementation: RouteTableRepositoryImpl,
    ): RouteTableRepository
}
