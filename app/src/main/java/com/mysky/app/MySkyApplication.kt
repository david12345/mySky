package com.mysky.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mysky.app.di.ApplicationScope
import com.mysky.app.worker.SkyBackgroundWorkCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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

    @Inject
    lateinit var backgroundWorkCoordinator: SkyBackgroundWorkCoordinator

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // A rede de segurança que resolve a FR-022 sem migração nenhuma.
        //
        // O `onEnabled` de um widget só dispara na transição 0→1 *de sempre*. Quem já tinha o widget
        // partido da v1.0.0 nunca mais o recebe — e sem isto ficaria sem trabalho de fundo para
        // sempre, com um widget que nunca muda e nenhum sintoma que aponte para a causa. Aqui
        // pergunta-se pela verdade e reconcilia-se, no primeiro arranque depois da atualização
        // (AD-026). É idempotente, por isso não custa nada quando já está tudo certo.
        applicationScope.launch { backgroundWorkCoordinator.reconcile() }
    }
}
