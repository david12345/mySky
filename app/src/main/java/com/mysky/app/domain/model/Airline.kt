package com.mysky.app.domain.model

/**
 * Operador aéreo, resolvido localmente a partir do indicativo de voo.
 *
 * Não é uma entidade com ciclo de vida: é dado de referência estático distribuído com a app
 * (ver AD-007 no CLAUDE.md). Por isso não tem identidade própria para além do designador.
 */
data class Airline(
    /** Designador ICAO de 3 letras maiúsculas (ex.: "TAP"). */
    val icaoCode: String,
    /** Nome apresentável do operador. Nunca vazio. */
    val name: String,
) {
    init {
        require(icaoCode.length == 3 && icaoCode.all { it in 'A'..'Z' }) {
            "Designador ICAO tem de ser 3 letras maiúsculas: $icaoCode"
        }
        require(name.isNotBlank()) { "Nome do operador não pode ser vazio" }
    }

    companion object {
        private const val PREFIX_LENGTH = 3

        /**
         * Extrai o designador do operador a partir do indicativo de voo.
         *
         * O indicativo comercial é o designador seguido do número do voo ("TAP1234"), por isso
         * exige-se pelo menos um carácter depois do prefixo: um indicativo de 3 letras sem número
         * não é um voo comercial. A exigência de serem só letras é o que distingue um operador de
         * uma matrícula usada como indicativo — "N123AB" ou "CSDHA" pertencem a aviação privada e
         * devolvem `null`, o que faz a aeronave aparecer na lista sem operador (FR-012) em vez de
         * lhe ser atribuída uma companhia errada.
         */
        fun icaoPrefixOf(callsign: String?): String? {
            val trimmed = callsign?.trim().orEmpty()
            if (trimmed.length <= PREFIX_LENGTH) return null
            val prefix = trimmed.take(PREFIX_LENGTH).uppercase()
            return prefix.takeIf { code -> code.all { it in 'A'..'Z' } }
        }
    }
}
