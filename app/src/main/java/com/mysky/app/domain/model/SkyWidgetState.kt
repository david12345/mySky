package com.mysky.app.domain.model

/**
 * O que o widget mostra, decidido no instante em que é lido.
 *
 * Nunca é persistido — é derivado de um [SkyWidgetSnapshot] e do relógio de **agora** (AD-024). É a
 * mesma categoria de cuidado da AD-012: afirmar que um avião está no céu com base numa observação
 * velha não produz erro nenhum, produz um resultado errado com ar de certo.
 */
sealed interface SkyWidgetState {

    /** Nunca correu um ciclo. Não é céu vazio: é ausência de observação. */
    data object NoDataYet : SkyWidgetState

    /** Falta a permissão de localização. Manda sobre a idade dos dados. */
    data object PermissionMissing : SkyWidgetState

    /** Observação dentro da janela: é honesto falar no presente. */
    data class Fresh(
        val flight: WidgetFlight,
        val count: Int,
        val observedAtEpochSeconds: Long,
    ) : SkyWidgetState

    /** Observação fora da janela: só passado, e sempre com o instante à vista. */
    data class Stale(
        val flight: WidgetFlight,
        val count: Int,
        val observedAtEpochSeconds: Long,
    ) : SkyWidgetState

    /** O céu estava vazio quando se olhou. [isFresh] decide se "está" ou "estava". */
    data class EmptySky(
        val observedAtEpochSeconds: Long,
        val isFresh: Boolean,
    ) : SkyWidgetState

    companion object {

        /**
         * Quanto tempo depois da observação ainda é honesto falar no presente, em segundos.
         *
         * **300 segundos, e o número tem origem física, não de gosto.** Uma aeronave atravessa um raio
         * de 30 km — 60 km de diâmetro — em 4,0 minutos a 250 m/s e 5,0 minutos a 200 m/s. Passado
         * isso, quem estava no céu já não está, e dizer que está é afirmar o que provavelmente é falso.
         */
        const val DEFAULT_FRESHNESS_WINDOW_SECONDS = 300L

        /**
         * A decisão dos cinco estados, pura e determinística.
         *
         * Não lê relógio nenhum: o "agora" entra por parâmetro, como em
         * [com.mysky.app.domain.usecase.DetectOverheadFlightsUseCase]. É isso que faz o SC-003 ser um
         * teste de tabela na JVM em vez de uma inspeção visual num telefone.
         */
        fun evaluate(
            snapshot: SkyWidgetSnapshot?,
            nowEpochSeconds: Long,
            freshnessWindowSeconds: Long = DEFAULT_FRESHNESS_WINDOW_SECONDS,
        ): SkyWidgetState {
            if (snapshot == null) return NoDataYet
            // A permissão manda sobre a idade: sem ela não há observação nenhuma para envelhecer.
            if (snapshot is SkyWidgetSnapshot.PermissionMissing) return PermissionMissing

            // Uma idade negativa acontece com o relógio do dispositivo atrasado face ao da fonte.
            // Conta como zero, nunca como negativa — é o mesmo tratamento que `freshnessOf` dá desde
            // a 002, e a alternativa era um avião "observado no futuro" aparecer como obsoleto.
            val age = (nowEpochSeconds - snapshot.observedAtEpochSeconds).coerceAtLeast(0L)
            val fresh = age <= freshnessWindowSeconds

            return when (snapshot) {
                is SkyWidgetSnapshot.EmptySky ->
                    EmptySky(snapshot.observedAtEpochSeconds, isFresh = fresh)

                is SkyWidgetSnapshot.Flights ->
                    if (fresh) {
                        Fresh(snapshot.top, snapshot.count, snapshot.observedAtEpochSeconds)
                    } else {
                        Stale(snapshot.top, snapshot.count, snapshot.observedAtEpochSeconds)
                    }

                is SkyWidgetSnapshot.PermissionMissing -> PermissionMissing
            }
        }
    }
}
