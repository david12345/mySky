package com.mysky.app.data.local

import android.content.res.AssetFileDescriptor
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Leitura de acesso aleatório sobre a tabela de rotas, seja ela qual for a origem.
 *
 * Existe porque as duas origens **não são intermutáveis**. Um ficheiro em `filesDir` abre-se com
 * `RandomAccessFile`; um asset dentro do APK não — só é acessível por `AssetManager.openFd()`, que
 * devolve um descritor apontando para dentro do zip, com um deslocamento base. Sem esta abstração,
 * a pesquisa binária teria de ser escrita duas vezes, ou o asset teria de ser copiado para disco no
 * primeiro arranque — 7,6 MB de I/O no caminho onde o tempo até à lista é medido.
 *
 * **Todas as implementações têm de ser seguras para leituras concorrentes.** Não é um requisito
 * teórico: por desenho (AD-015) dezenas de aeronaves consultam a tabela ao mesmo tempo, em
 * `Dispatchers.IO`, que é multi-thread.
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

/**
 * Lê até encher o destino.
 *
 * Uma leitura curta não daria erro nenhum: devolveria um registo meio preenchido, que a comparação
 * da pesquisa binária trataria como um valor legítimo e levaria a saltar para o lado errado do
 * ficheiro. O resultado seria uma rota errada com ar de certa.
 */
private fun FileChannel.readFullyAt(position: Long, destination: ByteArray): Int {
    val buffer = ByteBuffer.wrap(destination)
    var read = 0
    while (buffer.hasRemaining()) {
        // Leitura **posicional**: não usa nem altera a posição do canal, e por isso é segura para
        // várias threads em simultâneo — ao contrário de `seek` seguido de `read`.
        val count = read(buffer, position + read)
        if (count <= 0) break
        read += count
    }
    return read
}

/**
 * Tabela num ficheiro do sistema de ficheiros — o que existe depois de uma atualização aceite.
 *
 * Usa o canal do `RandomAccessFile` com leitura posicional, e **não** `seek` seguido de `read`. O
 * `RandomAccessFile` tem um único ponteiro de posição partilhado, e as duas operações não são
 * atómicas entre si: com duas consultas concorrentes — que é o caso normal aqui — uma faria `seek`
 * para a sua posição e a outra leria a partir dela, devolvendo o registo de outro voo. A rota
 * errada apareceria no ecrã com exatamente o mesmo ar de certeza que a correta.
 */
internal class FileTableReader(file: File) : RouteTableReader {

    private val handle = RandomAccessFile(file, "r")
    private val channel = handle.channel

    override val length: Long = handle.length()

    override fun readAt(offset: Long, destination: ByteArray): Int =
        channel.readFullyAt(offset, destination)

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
        channel.readFullyAt(baseOffset + offset, destination)

    override fun close() {
        stream.close()
        descriptor.close()
    }
}
