package com.mysky.app.worker

/**
 * O que dizer ao utilizador sobre a última tentativa, no widget.
 *
 * Duas falhas distintas e não uma genérica (FR-019): "sem ligação" resolve-se ligando a rede, "limite
 * diário atingido" só passa amanhã. Dizer "não foi possível atualizar" nos dois casos deixaria o
 * utilizador a tentar ligar o Wi-Fi durante uma hora sem efeito nenhum.
 */
enum class RefreshFeedback { None, NoConnection, RateLimited }

/**
 * Manda repintar todos os widgets no ecrã, e diz-lhes como correu a última tentativa.
 *
 * Existe como interface, e sem um único tipo do Glance na assinatura, para o `worker/` **não**
 * importar `androidx.glance` (AD-025). Importá-lo acoplaria um componente de fundo — testável na JVM
 * com duplos — a um toolkit de UI que exigiria Robolectric ou instrumentação, que este projeto não
 * tem e cuja ausência é deliberada.
 *
 * A implementação vive em `widget/`, e é o `widget/` que depende do `worker/` — nunca o contrário.
 */
interface WidgetRefresher {
    /**
     * @param clearPending limpa o indicador "a atualizar". **Só o pedido que o pôs lá o deve tirar**:
     *   um ciclo periódico a terminar durante um pedido manual apagaria o indicador antes de tempo.
     */
    suspend fun refreshAll(
        feedback: RefreshFeedback = RefreshFeedback.None,
        clearPending: Boolean = false,
    )
}
