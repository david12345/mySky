package com.mysky.app.data.local

import com.mysky.app.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** O que pode correr mal ao instalar uma tabela nova. */
enum class InstallResult { Installed, InvalidData, WriteFailed }

/**
 * Instala uma tabela nova sem nunca deixar a app sem tabela (FR-020).
 *
 * A sequência é toda ela a garantia: escrever para um ficheiro temporário, validar **antes** de
 * tocar no canónico, e só então `renameTo`. O `rename` é atómico no sistema de ficheiros do
 * Android, e quem tiver o ficheiro antigo aberto continua a lê-lo em segurança até o fechar — por
 * isso não é preciso lock nenhum, nem coordenação com quem está a consultar a tabela.
 *
 * **Uma tabela meio escrita é pior do que uma tabela velha.** A velha dá rotas desatualizadas; a
 * meio escrita dá lixo com ar de rota, ou rota nenhuma. É por isso que nada é escrito no lugar
 * definitivo antes de estar inteiro e verificado.
 */
@Singleton
class RouteTableInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val source: RouteTableSource,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {

    suspend fun install(content: InputStream): InstallResult = withContext(dispatcher) {
        val temporary = File(context.cacheDir, TEMPORARY_NAME)
        try {
            val written = runCatching {
                temporary.outputStream().use { output -> content.copyTo(output) }
            }.isSuccess
            if (!written) return@withContext InstallResult.WriteFailed

            if (!isAcceptable(temporary)) return@withContext InstallResult.InvalidData

            // O único momento em que o ficheiro canónico muda, e é indivisível.
            val target = source.updatedFile
            if (!temporary.renameTo(target)) return@withContext InstallResult.WriteFailed

            InstallResult.Installed
        } finally {
            runCatching { if (temporary.exists()) temporary.delete() }
        }
    }

    /**
     * A tabela nova tem de ser válida **e** não ser uma regressão face à que já lá está.
     *
     * A verificação de volume (SC-009) é a que não é óbvia: um ficheiro truncado na origem passa a
     * assinatura, a versão e a coerência entre tamanho e contagem — é um ficheiro perfeitamente bem
     * formado, só que com metade das rotas. Sem esta verificação, a app aceitava-o e degradava-se
     * sozinha, que é a única forma de esta feature piorar com o tempo.
     */
    private fun isAcceptable(candidate: File): Boolean {
        val header = runCatching {
            FileTableReader(candidate).use { RouteTableFormat.readHeader(it) }
        }.getOrNull() ?: return false

        if (header.recordCount < RouteTableFormat.MIN_PLAUSIBLE_RECORDS) return false

        val current = runCatching {
            source.open()?.use { RouteTableFormat.readHeader(it) }
        }.getOrNull() ?: return true

        val floor = (current.recordCount * MIN_RETAINED_FRACTION).toInt()
        return header.recordCount >= floor
    }

    private companion object {
        const val TEMPORARY_NAME = "routes.bin.download"

        /**
         * Uma tabela nova pode encolher — companhias fecham, rotas desaparecem — mas não a este
         * ponto. Abaixo disto é mais provável ser um ficheiro truncado do que uma mudança real.
         */
        const val MIN_RETAINED_FRACTION = 0.8
    }
}
