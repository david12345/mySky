package com.mysky.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
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
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        // Um ficheiro de preferências corrompido é substituído por um vazio, e o utilizador
        // reencontra os valores de fábrica. Sem isto, ler degradaria com graça mas **escrever
        // lançaria** — e tocar num cursor rebentaria a app, num aparelho onde o ficheiro se
        // estragou por uma razão que não é culpa de ninguém.
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = { context.preferencesDataStoreFile(SETTINGS_NAME) },
    )

    private const val SETTINGS_NAME = "mysky_settings"
}
