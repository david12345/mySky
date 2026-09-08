package com.mysky.app.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decide de onde vem a tabela: do ficheiro atualizado, se houver, senão do asset do APK.
 *
 * **Nunca copia o asset para disco.** Seriam 7,6 MB de I/O no primeiro arranque, exatamente no
 * caminho onde o tempo até à lista se mede — e para nada, porque o asset é legível por deslocamento
 * dentro do APK (desde que fique por comprimir, ver `noCompress` no `build.gradle.kts`).
 */
@Singleton
class RouteTableSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** O ficheiro canónico, que só existe depois de uma atualização aceite. */
    val updatedFile: File get() = File(context.filesDir, UPDATED_FILE_NAME)

    /**
     * @return um leitor sobre a melhor tabela disponível, ou `null` se não houver nenhuma legível.
     */
    fun open(): RouteTableReader? {
        val updated = updatedFile
        if (updated.exists()) {
            val reader = runCatching { FileTableReader(updated) }.getOrNull()
            // Um ficheiro atualizado que não valide não pode deixar a app sem rotas: cai-se para o
            // asset, que é sempre o que veio na instalação e nunca foi tocado.
            if (reader != null && RouteTableFormat.readHeader(reader) != null) return reader
            runCatching { reader?.close() }
        }
        return runCatching { AssetTableReader(context.assets.openFd(ASSET_NAME)) }.getOrNull()
    }

    private companion object {
        const val ASSET_NAME = "routes.bin"
        const val UPDATED_FILE_NAME = "routes.bin"
    }
}
