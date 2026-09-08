package com.mysky.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mysky.app.data.local.InstallResult
import com.mysky.app.data.local.RouteTableInstaller
import com.mysky.app.data.local.FileRouteDirectory
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Descarrega e instala uma tabela de rotas nova, a pedido do utilizador.
 *
 * Worker próprio, e não uma extensão do `SkyRefreshWorker` (AD-016): este é raro, único, sempre
 * iniciado por quem usa a app, e não tem relação nenhuma com o orçamento de posições nem com o
 * intervalo mínimo de 15 minutos que aquele existe para garantir.
 *
 * Descarrega um **binário já convertido**, publicado no repositório do projeto — nunca os CSV em
 * bruto (AD-014). Fazer a junção e a ordenação aqui seria ter a mesma regra em Kotlin e em Python, a
 * divergir em silêncio.
 *
 * Não escreve nada no lugar definitivo: quem o faz é o [RouteTableInstaller], e só depois de
 * validar. Uma falha aqui, em qualquer ponto, deixa a tabela anterior intacta.
 */
@HiltWorker
class RouteTableUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val installer: RouteTableInstaller,
    private val directory: FileRouteDirectory,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = try {
        val connection = (URL(TABLE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            requestMethod = "GET"
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                failure(REASON_UNREACHABLE)
            } else {
                when (connection.inputStream.use { installer.install(it) }) {
                    InstallResult.Installed -> {
                        // A tabela mudou debaixo de quem a lê: invalidar é o que faz a consulta
                        // seguinte ver a nova sem reiniciar a app (FR-021).
                        directory.invalidate()
                        Result.success()
                    }
                    InstallResult.InvalidData -> failure(REASON_INVALID)
                    InstallResult.WriteFailed -> failure(REASON_UNEXPECTED)
                }
            }
        } finally {
            connection.disconnect()
        }
    } catch (io: IOException) {
        // Sem rede e servidor inalcançável são indistinguíveis daqui; a diferença que o utilizador
        // precisa de saber — "tens rede?" — é feita antes, por quem pede o trabalho.
        failure(REASON_UNREACHABLE)
    } catch (throwable: Exception) {
        failure(REASON_UNEXPECTED)
    }

    /** A falha é definitiva e não se repete sozinha: o utilizador é que decide voltar a pedir. */
    private fun failure(reason: String) = Result.failure(workDataOf(KEY_REASON to reason))

    companion object {
        const val WORK_NAME = "mysky_route_table_update"

        const val KEY_REASON = "reason"
        const val REASON_UNREACHABLE = "unreachable"
        const val REASON_INVALID = "invalid"
        const val REASON_UNEXPECTED = "unexpected"

        /**
         * Ficheiro de uma release do próprio repositório, e não do espelho dos dados em bruto nem
         * de `raw.githubusercontent`: as releases são servidas por CDN, têm URL estável e não fazem
         * o histórico do git crescer a cada geração da tabela.
         */
        const val TABLE_URL = "https://github.com/david12345/mySky/releases/latest/download/routes.bin"

        private const val TIMEOUT_MILLIS = 30_000
    }
}
