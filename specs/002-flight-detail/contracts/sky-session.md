# Contrato — `SkySession`, o detentor partilhado do laço

O que a sessão promete aos dois ecrãs, e as regras cuja violação não produz erro visível. É este
contrato que os testes de `SkySession` verificam.

## Superfície

```
@ActivityRetainedScoped
class SkySession @Inject constructor(
    observeSky: ObserveSkyUseCase,
    locationRepository: LocationRepository,
    timeProvider: TimeProvider,
    lifecycle: ActivityRetainedLifecycle,
) {
    val observation: StateFlow<SkyObservation>
    fun requestRefresh()
    fun onPermissionMayHaveChanged()
}
```

## Invariantes

| # | Invariante | Requisito | Como se verifica |
|---|---|---|---|
| 1 | Há no máximo **um** pedido à fonte de voos em curso, independentemente de quantos ecrãs observam | FR-017, SC-008 | contar chamadas a `ObserveSkyUseCase` com dois coletores em tempo virtual |
| 2 | Dois coletores veem exatamente a mesma sequência de emissões | FR-013, SC-002 | duas subscrições, comparar o que cada uma recebe |
| 3 | Com um coletor a sair e outro a entrar, o laço **não** reinicia nem dispara um pedido extra | FR-017 | simular a transição lista → detalhe dentro dos 5 s |
| 4 | Zero coletores durante mais de 5 s ⇒ nenhum pedido novo | FR-016, SC-005 | avançar o tempo virtual sem coletores |
| 5 | Uma falha nunca limpa `flights` nem `lastUpdatedEpochSeconds` | FR-022 | ciclo com sucesso seguido de ciclo com erro |
| 6 | Um 429 alonga a espera seguinte para `max(30 s, retryAfter)` | herdado de FR-021 da 001 | tempo virtual |
| 7 | `requestRefresh()` acorda o ciclo de imediato e reinicia o relógio dos 30 s | FR-015 | tempo virtual |
| 8 | Vários `requestRefresh()` durante um ciclo em curso valem por um; nenhum se perde nem gera pedido concorrente | herdado de AD-008 | gate no ciclo em curso, com toques durante ele |
| 9 | O escopo interno é cancelado quando a `ActivityRetainedLifecycle` termina | — | `addOnClearedListener` |

A invariante 1 é a razão de existir desta classe. A 3 é a que se parte sem ninguém dar por isso: um
`stateIn` com `WhileSubscribed` sem o `stopTimeout` correto reinicia o laço a cada navegação e
duplica o pedido.

## Regra de subscrição — obrigatória, não estilística

Os dois ViewModels **têm de** consumir a sessão com:

```
skySession.observation
    .map { ... }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), inicial)
```

e **nunca** com `viewModelScope.launch { skySession.observation.collect { ... } }`.

A diferença não é de estilo. Um `launch` prende a subscrição à vida do ViewModel, que sobrevive a
estar fora da composição — o `MainViewModel` fica vivo na pilha de retrocesso enquanto o detalhe
está aberto. Com um `launch`, a contagem de subscritores nunca chegaria a zero, o laço nunca
pararia, e a app continuaria a consultar a rede em segundo plano: FR-016 e SC-005 violados sem
qualquer sinal visível.

## Permissão

A `SkySession` não sabe o que é uma permissão. Consulta `locationRepository.hasLocationPermission()`
no início de cada ciclo; sem permissão, publica `phase = Idle` e espera pelo tique seguinte ou por
um `requestRefresh()`.

Cabe ao `MainViewModel` — o único ecrã que pede permissão — chamar `onPermissionMayHaveChanged()`
sempre que a permissão esteja concedida, sem tentar adivinhar se isso é novidade. A sessão acorda o
laço **só** se o último ciclo tiver sido saltado por falta de permissão, o que separa dois casos que
de fora se confundem: quem acabou de conceder tem de ver a lista já, e quem abriu a app com a
permissão de ontem não pode gerar um segundo pedido em cima do primeiro ciclo.

Decidir isto no ViewModel, a partir do estado de permissão anterior, **não funciona**: a reavaliação
ao retomar o ecrã e a resposta ao diálogo do sistema disparam na mesma transição e sem ordem
garantida entre si. Basta a primeira pôr a permissão em `Granted` para a segunda ver
`previous == Granted` e nenhuma das duas acordar o laço — e quem acabou de conceder fica até 30 s a
olhar para o rationale, contra o SC-001 da 001.

## Fronteiras

`SkySession` **não** é injetada em `worker/` nem em `widget/`. Esses continuam a passar pelo
`SkyRefreshWorker` (AD-003). Um `SkySession` dentro de um worker faria trabalho de primeiro plano
sobreviver ao ecrã, que é exatamente o que a AD-008 existe para impedir. O `reviewer` verifica isto
explicitamente no fim da implementação.
