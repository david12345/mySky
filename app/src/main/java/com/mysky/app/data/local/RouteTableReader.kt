package com.mysky.app.data.local

import android.content.res.AssetFileDescriptor
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * Leitura de acesso aleatório sobre a tabela de rotas, seja ela qual for a origem.
 *
 * Existe porque as duas origens **não são intermutáveis**. Um ficheiro em `filesDir` lê-se com
 * `RandomAccessFile`; um asset dentro do APK não — só é acessível por `AssetManager.openFd()`, que
 * devolve um descritor apontando para dentro do zip, com um deslocamento base. Sem esta abstração,
 * a pesquisa binária teria de ser escrita duas vezes, ou o asset teria de ser copiado para disco no
 * primeiro arranque — 7,6 MB de I/O no caminho onde o tempo até à lista é medido.
 */
interface RouteTableReader : Closeable {

    /** Comprimento útil da tabela, já sem o deslocamento base da origem. */
    val length: Long

    /**
     * Lê `destination.size` bytes a partir de [offset], relativo ao início da tabela.
     *
     * @return quantos bytes foram efetivamente lidos.
     */
    fun readAt(offset: Long, destination: ByteArray): Int
}

/** Tabela num ficheiro do sistema de ficheiros — o que existe depois de uma atualização aceite. */
internal class FileTableReader(file: File) : RouteTableReader {

    private val handle = RandomAccessFile(file, "r")

    override val length: Long = handle.length()

    override fun readAt(offset: Long, destination: ByteArray): Int {
        handle.seek(offset)
        return handle.read(destination)
    }

    override fun close() = handle.close()
}

/**
 * Tabela dentro do APK.
 *
 * O canal é posicionado em `startOffset + offset` porque o descritor aponta para o APK inteiro e
 * não para a entrada. Isto só funciona com o asset **por comprimir**: uma entrada comprimida não
 * tem representação contínua no zip, e o `AssetManager` recusa dar-lhe um descritor.
 */
internal class AssetTableReader(private val descriptor: AssetFileDescriptor) : RouteTableReader {

    private val stream = FileInputStream(descriptor.fileDescriptor)
    private val channel = stream.channel
    private val baseOffset = descriptor.startOffset

    override val length: Long = descriptor.length

    override fun readAt(offset: Long, destination: ByteArray): Int =
        channel.read(ByteBuffer.wrap(destination), baseOffset + offset)

    override fun close() {
        stream.close()
        descriptor.close()
    }
}
