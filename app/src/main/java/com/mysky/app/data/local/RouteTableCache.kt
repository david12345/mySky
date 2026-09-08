package com.mysky.app.data.local

/**
 * Invalidação da tabela em memória depois de ela ser substituída em disco.
 *
 * Porta pequena e interna à camada de dados, para o worker não depender de uma implementação
 * concreta do `RouteDirectory`. Não sobe ao domínio porque é uma preocupação de cache — o domínio
 * pergunta rotas e não quer saber que existe um ficheiro por baixo.
 */
fun interface RouteTableCache {

    /**
     * Faz a consulta seguinte reabrir a tabela.
     *
     * Quem estiver a meio de uma consulta continua a ler o ficheiro antigo em segurança — é o
     * `rename` do sistema a garanti-lo — e só a consulta a seguir vê a tabela nova (FR-021).
     */
    suspend fun invalidate()
}
