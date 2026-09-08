package com.mysky.app.domain.model

/**
 * De onde o voo parte e para onde vai, só a sigla de três letras que o utilizador reconhece.
 *
 * É a rota **agendada** daquele número de voo, não a rota real de hoje: num desvio, dirá o destino
 * previsto. É o preço de não gastar um pedido de rede por avião, e está assumido na especificação.
 *
 * Não existe meia rota. A regra de "sem os dois aeroportos não se apresenta nenhum" vive aqui, no
 * construtor, e não em quem desenha o ecrã — que é onde ela se perderia.
 */
data class Route(
    val originIata: String,
    val destinationIata: String,
) {
    init {
        require(isIata(originIata)) { "Origem não é uma sigla IATA: $originIata" }
        require(isIata(destinationIata)) { "Destino não é uma sigla IATA: $destinationIata" }
    }

    companion object {
        /** Comprimento da chave no ficheiro. O máximo medido nos dados da fonte é exatamente 7. */
        const val MAX_CALLSIGN_LENGTH = 7

        private const val IATA_LENGTH = 3

        private fun isIata(code: String): Boolean =
            code.length == IATA_LENGTH && code.all { it in 'A'..'Z' }

        /**
         * Chave de pesquisa a partir do indicativo, ou `null` se não puder ser chave.
         *
         * Esta regra tem de ser **exatamente** a mesma que a ferramenta de conversão aplica ao
         * gerar a tabela. Se as duas divergirem, produz-se um ficheiro cheio de rotas que a app
         * nunca encontra — e nada, em lado nenhum, dá erro. É por isso que vive no domínio, ao lado
         * do modelo, e não escondida na camada de dados.
         */
        fun callsignKeyOf(callsign: String?): String? {
            val key = callsign?.trim()?.uppercase() ?: return null
            if (key.isEmpty() || key.length > MAX_CALLSIGN_LENGTH) return null
            if (!key.all { it in 'A'..'Z' || it in '0'..'9' }) return null
            return key
        }
    }
}
