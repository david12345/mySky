package com.mysky.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Entry point do Hilt.
 *
 * Fornece a `Configuration` do WorkManager para que os workers possam ter dependências injetadas
 * (`@HiltWorker`). Por causa disso, a inicialização automática do WorkManager está desativada no
 * manifesto — ver o `provider` `androidx.startup.InitializationProvider`.
 */
@HiltAndroidApp
class MySkyApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
