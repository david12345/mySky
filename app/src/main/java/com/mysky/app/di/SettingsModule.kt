package com.mysky.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.mysky.app.data.settings.settingsDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * O armazenamento das preferências.
 *
 * Fornecido aqui em vez de ser construído dentro do repositório: assim o repositório recebe-o por
 * construtor, e os testes podem dar-lhe um sobre ficheiro temporário sem precisarem de `Context` —
 * o que é o que permite verificar o comportamento perante armazenamento ausente e corrompido.
 *
 * O binding do próprio `SettingsRepository` continua no `RepositoryModule`, onde já estava desde o
 * esqueleto inicial, ao lado dos outros repositórios.
 */
@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.settingsDataStore
}
