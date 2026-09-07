# Phase 1 — Modelo de dados: Detalhe de um voo

Esta feature **não introduz nenhum modelo de domínio novo com dados**: o detalhe apresenta o
`OverheadFlight` que a lista já produz. O que é novo são um tipo de presença (a resposta à pergunta
"este avião ainda está no meu céu?"), a extração do estado partilhado para fora do `MainUiState`, e
o estado do próprio ecrã.

Nenhuma persistência. Nada é escrito em disco por esta feature.

---

## Existentes, inalterados

| Tipo | Camada | Porquê aparece aqui |
|---|---|---|
| `Aircraft` | `domain/model` | Traz todos os campos que o detalhe mostra: `callsign`, `originCountry`, as duas altitudes, `groundSpeedMetersPerSecond`, `headingDegrees`, `verticalRateMetersPerSecond`, `lastContactEpochSeconds` |
| `OverheadFlight` | `domain/model` | Traz a relação com o observador já calculada: distância, azimute, elevação, e o operador |
| `Airline` | `domain/model` | Nome do operador, opcional por natureza |
| `SkyError` | `domain/model` | As causas que o detalhe tem de distinguir (FR-023) |

Nenhum destes muda. Se algum precisasse de mudar para o detalhe funcionar, seria sinal de que a
lista estava a mostrar menos do que a fonte dá — e o sítio de corrigir isso seria a 001.

---

## Novos

### `FlightPresence` — `domain/model`

Resposta à pergunta "a aeronave que o utilizador está a ver ainda está no céu dele?". Selado, três
variantes, porque são exatamente três as situações que FR-019 obriga a distinguir.

```
sealed interface FlightPresence {
    data object NeverObserved : FlightPresence
    data class Current(val flight: OverheadFlight, val observedAtEpochSeconds: Long) : FlightPresence
    data class LeftSky(val lastFlight: OverheadFlight, val lastSeenEpochSeconds: Long) : FlightPresence
}
```

**Invariantes**:
- `Current` transporta o instante da observação que o produziu. Sem isso, a transição para
  `LeftSky` teria de datar a última observação com o instante em que se **descobriu** a ausência —
  um ciclo inteiro mais tarde, o que daria os valores por mais recentes do que são. (Acrescentado
  durante a implementação.)
- `LeftSky` transporta sempre o último voo conhecido **e** o instante em que foi visto. Um sem o
  outro permitiria apresentar dados antigos sem os datar, que é o que FR-020 proíbe.
- `NeverObserved` não é o mesmo que "céu vazio": significa que esta aeronave nunca foi vista
  **nesta sessão**, o que inclui o caso de o processo ter sido morto e restaurado com o detalhe no
  topo da pilha.
- Nunca há uma quarta variante para "a atualização falhou": isso é `lastError` no estado
  partilhado, e é ortogonal à presença. Um ciclo falhado **não** faz um avião sair do céu.

### `SkyObservation` — `presentation/sky`

O que a `SkySession` publica, e que os dois ecrãs consomem. É o `MainUiState` de hoje **menos** o
que é exclusivo do ecrã principal.

```
data class SkyObservation(
    val phase: LoadPhase = LoadPhase.Idle,
    val observationSequence: Long = 0,
    val flights: List<OverheadFlight> = emptyList(),
    val lastUpdatedEpochSeconds: Long? = null,
    val lastError: SkyError? = null,
)
```

`LoadPhase` muda de `presentation/main` para `presentation/sky`: passa a descrever a sessão, não um
ecrã.

**Invariantes**:
- `flights` vem sempre ordenada por elevação decrescente — é o caso de uso que garante.
- Uma falha **nunca** limpa `flights` nem `lastUpdatedEpochSeconds`. É a regra de FR-025 da 001,
  agora no sítio partilhado.
- `lastUpdatedEpochSeconds == null` se e só se nunca houve uma observação bem sucedida nesta
  sessão.
- `observationSequence` conta as observações bem sucedidas e é o que dá **identidade** a uma
  observação. A marca temporal não serve para isso: tem granularidade de segundo, e dois ciclos
  podem cair no mesmo — quem comparasse marcas temporais concluiria que nada mudou e deixaria de
  detetar uma aeronave que saiu do céu nesse segundo. (Acrescentado durante a revisão.)

### `FlightDetailUiState` — `presentation/detail`

Um `data class` por ecrã, como manda o `CLAUDE.md`. Os estados compostos são derivados, não
armazenados.

```
data class FlightDetailUiState(
    val icao24: String? = null,
    val phase: LoadPhase = LoadPhase.Idle,
    val presence: FlightPresence = FlightPresence.NeverObserved,
    val lastUpdatedEpochSeconds: Long? = null,
    val lastError: SkyError? = null,
)
```

`phase` vem da `SkyObservation` sem alteração. Existe porque a precedência do ecrã distingue "a
obter a tua localização" de "a procurar aviões" (FR-014), e sem ela essa distinção seria
impossível de renderizar. **Nenhum derivado entra no construtor**: a regra do `CLAUDE.md` é um
`data class` de estado por ecrã com os estados compostos calculados, não armazenados.

**Derivações** (a espelhar as do `MainUiState`, para os dois ecrãs não divergirem em conceitos):

| Derivado | Regra | Requisito |
|---|---|---|
| `flight` | o `OverheadFlight` de `Current` ou de `LeftSky`, `null` em `NeverObserved` | FR-005, FR-006 |
| `hasLeftSky` | `presence is LeftSky` | FR-020 |
| `isWaitingFirstObservation` | `presence is NeverObserved && lastUpdatedEpochSeconds == null && lastError == null` | — |
| `hasStaleData` | `lastError != null && presence !is NeverObserved` | FR-022 |
| `isBlockingError` | `lastError != null && presence is NeverObserved` | FR-023 |

---

## Alteração ao `MainUiState`

Deixa de ser a fonte dos campos que passam para `SkyObservation` e passa a compô-los:

```
data class MainUiState(
    val permission: PermissionState = PermissionState.Unknown,
    val observation: SkyObservation = SkyObservation(),
)
```

Os derivados existentes (`isSkyEmpty`, `hasStaleResults`, `isFirstLoad`, `isBlockingError`) mantêm
o significado e passam a ler de `observation`. **A tabela de precedência do ecrã principal não
muda** — é contrato verificado por testes da 001, e esta feature não tem licença para o alterar.

`PermissionState` fica onde está: o ecrã principal é o único que pede permissão, e a `SkySession`
nunca sabe o que é uma permissão.

---

## Transições de presença

Produzidas por `TrackFlightPresenceUseCase`, a partir do estado anterior e da observação nova.
`observado` significa que o `icao24` consta da lista mais recente; `ciclo falhado` significa que a
observação chegou com erro e sem lista nova.

| Estado anterior | Evento | Estado novo | Porquê |
|---|---|---|---|
| `NeverObserved` | observado | `Current` | primeira observação |
| `NeverObserved` | não observado, ciclo com sucesso | `NeverObserved` | nunca esteve lá; não "saiu" |
| `NeverObserved` | ciclo falhado | `NeverObserved` | uma falha não prova ausência |
| `Current` | observado | `Current` | atualiza o voo |
| `Current` | não observado, ciclo com sucesso | `LeftSky(voo anterior, instante da última observação)` | **é isto que FR-019 deteta** |
| `Current` | ciclo falhado | `Current`, com `lastError` no estado | uma falha **não** é uma saída (FR-022) |
| `LeftSky` | observado | `Current` | reapareceu (FR-021) |
| `LeftSky` | não observado, com ou sem sucesso | `LeftSky` inalterado | o instante da última observação não se mexe |

A linha que interessa é a quinta, e a sexta é a que a torna difícil: distinguir "não veio na lista"
de "não houve lista" é toda a diferença entre informar e mentir.
