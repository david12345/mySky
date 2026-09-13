package com.mysky.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.mysky.app.data.widget.SkyWidgetRepositoryImpl
import com.mysky.app.domain.repository.SkyWidgetRepository
import com.mysky.app.widget.GlanceWidgetRefresher
import com.mysky.app.worker.WidgetPresenceCheck
import com.mysky.app.worker.WidgetRefresher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * O armazenamento do último resultado e as pontes para o Glance.
 *
 * As duas pontes ([WidgetRefresher], [WidgetPresenceCheck]) são declaradas em `worker/` e
 * implementadas em `widget/` (AD-025). É aqui — e só aqui — que as duas metades se encontram, o que
 * mantém o `worker/` inteiramente testável na JVM.
 */
@Module
@InstallIn(SingletonComponent::class)
object WidgetModule {

    @Provides
    @Singleton
    @WidgetSnapshotStore
    fun provideWidgetSnapshotDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        // Um snapshot corrompido é descartável: o ciclo seguinte reescreve-o. O que não é aceitável
        // é a corrupção derrubar o worker ou o widget.
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = { context.preferencesDataStoreFile(WIDGET_SNAPSHOT_NAME) },
    )

    /** Ficheiro **separado** do das preferências — ver a KDoc de `SkyWidgetRepositoryImpl`. */
    private const val WIDGET_SNAPSHOT_NAME = "mysky_widget_snapshot"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetBindingsModule {

    @Binds
    @Singleton
    abstract fun bindSkyWidgetRepository(impl: SkyWidgetRepositoryImpl): SkyWidgetRepository

    @Binds
    @Singleton
    abstract fun bindWidgetRefresher(impl: GlanceWidgetRefresher): WidgetRefresher

    @Binds
    @Singleton
    abstract fun bindWidgetPresenceCheck(impl: GlanceWidgetRefresher): WidgetPresenceCheck
}
