package com.mysky.app.domain.usecase

import com.mysky.app.domain.model.OverheadFlight
import com.mysky.app.domain.model.SkyError
import com.mysky.app.domain.repository.LocationRepository
import com.mysky.app.domain.repository.SettingsRepository
import com.mysky.app.domain.time.TimeProvider
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Um ciclo de observação do céu, sem laço à volta e sem política nenhuma.
 *
 * Existe porque o ecrã e o trabalho de fundo precisam **exatamente** da mesma sequência — verificar
 * permissão, obter uma posição pontual, ler os critérios das definições, chamar o
 * [ObserveSkyUseCase] — e a alternativa era escrevê-la duas vezes, em dois sítios obrigados a
 * concordar para sempre. Foi assim que a revisão da 004 encontrou um número escrito de duas maneiras
 * que já tinha divergido; aqui a divergência seria de comportamento, mais lenta de descobrir e com
 * pior sintoma (AD-028).
 *
 * **Não viola a AD-011**, que proíbe injetar a `SkySession` no worker: isto não é a sessão. É um caso
 * de uso de domínio, sem estado, sem `StateFlow`, sem cadência e sem WorkManager. Quem chama decide o
 * que fazer com o resultado — a sessão atualiza um `StateFlow` e recua num 429; o worker grava um
 * snapshot e devolve um `Result` ao sistema.
 */
class RunSkyCycleUseCase @Inject constructor(
    private val locationRepository: LocationRepository,
    private val settingsRepository: SettingsRepository,
    private val observeSky: ObserveSkyUseCase,
    private val timeProvider: TimeProvider,
) {

    /**
     * @param onProgress onde o ciclo vai, para quem mostra um ecrã.
     *
     * Existe porque o ecrã distingue "a localizar-te" de "a obter voos" — são textos diferentes e há
     * testes sobre eles. Sem isto, extrair o ciclo teria apagado essa distinção em silêncio, que é
     * exatamente o tipo de perda que um refactor não pode causar. O trabalho de fundo ignora-o: não
     * tem ecrã nenhum onde a mostrar.
     */
    suspend operator fun invoke(onProgress: (SkyCyclePhase) -> Unit = {}): SkyCycleResult {
        try {
            // Sem permissão não se vai à rede, e é isso que impede o ciclo de gastar uma consulta do
            // orçamento diário para obter um erro previsível.
            if (!locationRepository.hasLocationPermission()) return SkyCycleResult.NoPermission

            onProgress(SkyCyclePhase.LocatingUser)

            // Uma posição pontual por ciclo, em vez de localização contínua: cobre o utilizador em
            // movimento por uma fração do custo de bateria.
            val observer = locationRepository.getCurrentLocation()
                // O repositório devolve `null` em vez de falhar. Sem esta variante, "sem GPS" seria
                // indistinguível de "sem rede" para quem lê o resultado.
                ?: return SkyCycleResult.Failure(SkyError.LocationUnavailable)

            // Uma leitura pontual, no início, e não uma subscrição viva (AD-018): o ciclo trabalha
            // com este snapshot do princípio ao fim, o que torna critérios misturados dentro de um
            // ciclo estruturalmente impossível.
            val criteria = settingsRepository.settings.first().toCriteria()

            onProgress(SkyCyclePhase.LoadingFlights)

            return observeSky(observer, criteria).fold(
                onSuccess = { flights ->
                    SkyCycleResult.Success(flights, timeProvider.nowEpochSeconds())
                },
                onFailure = { throwable ->
                    SkyCycleResult.Failure(throwable as? SkyError ?: SkyError.Unexpected(throwable))
                },
            )
        } catch (cancellation: CancellationException) {
            // Cancelamento não é falha do céu: tem de subir para quem gere o escopo.
            throw cancellation
        } catch (throwable: Throwable) {
            // A promessa de "nunca lança" é desta função, e não pode depender de todos os
            // colaboradores a cumprirem a deles.
            return SkyCycleResult.Failure(SkyError.Unexpected(throwable))
        }
    }
}

/** Onde o ciclo vai. Só interessa a quem tem um ecrã para o mostrar. */
enum class SkyCyclePhase { LocatingUser, LoadingFlights }

/** O que um ciclo pode dar. Selado porque as três situações pedem respostas diferentes. */
sealed interface SkyCycleResult {

    data class Success(
        val flights: List<OverheadFlight>,
        val observedAtEpochSeconds: Long,
    ) : SkyCycleResult

    /** Terminou sem olhar para o céu. Não é falha — repetir não resolveria nada. */
    data object NoPermission : SkyCycleResult

    data class Failure(val error: SkyError) : SkyCycleResult
}
