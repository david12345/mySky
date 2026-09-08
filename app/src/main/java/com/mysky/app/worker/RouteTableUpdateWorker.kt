package com.mysky.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mysky.app.data.local.InstallResult
import com.mysky.app.data.local.RouteTableInstaller
import com.mysky.app.data.local.RouteTableCache
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request

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
    private val cache: RouteTableCache,
    private val httpClient: OkHttpClient,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = try {
        // O mesmo cliente que serve a fonte de voos, e não uma segunda forma de fazer rede no
        // projeto: os timeouts e a configuração ficam num sítio só.
        val request = Request.Builder().url(TABLE_URL).get().build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body
            if (!response.isSuccessful || body == null) {
                failure(REASON_UNREACHABLE)
            } else {
                when (installer.install(body.byteStream())) {
                    InstallResult.Installed -> {
                        // A tabela mudou debaixo de quem a lê: invalidar é o que faz a consulta
                        // seguinte ver a nova sem reiniciar a app (FR-021).
                        cache.invalidate()
                        Result.success()
                    }
                    InstallResult.InvalidData -> failure(REASON_INVALID)
                    InstallResult.WriteFailed -> failure(REASON_UNEXPECTED)
                }
            }
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
    }
}
