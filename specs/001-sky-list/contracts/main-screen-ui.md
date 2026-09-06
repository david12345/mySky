# Contrato de UI — ecrã principal

O que a UI observa, o que envia, e que transições são legais. É este contrato que os testes de
`MainViewModel` verificam.

## Estado observado

```
MainUiState(
    permission: PermissionState,        // Unknown | Granted | Denied | PermanentlyDenied
    phase: LoadPhase,                   // Idle | LocatingUser | LoadingFlights
    flights: List<OverheadFlight>,      // ordenada por elevação decrescente
    lastUpdatedEpochSeconds: Long?,     // null = nunca houve sucesso
    lastError: SkyError?,               // null = última tentativa correu bem
)
```

Exposto como `StateFlow`, com `stateIn(viewModelScope, WhileSubscribed(5_000), MainUiState())` e
consumido com `collectAsStateWithLifecycle()`.

## Eventos enviados pela UI

| Evento | Quando | Efeito |
|---|---|---|
| `onScreenVisible` | ecrã fica visível | reavalia a permissão (FR-006) |
| `onPermissionResult(granted, canAskAgain)` | resposta ao diálogo do sistema | atualiza `permission`; se concedida, arranca o ciclo |
| `onManualRefresh` | puxar para atualizar | dispara uma atualização e reinicia o relógio |
| `onFlightClick(icao24)` | toque numa entrada | navegação, sem alterar o estado |

## O que a UI renderiza, por ordem de precedência

| # | Condição | Ecrã |
|---|---|---|
| 1 | `permission` é `Denied` ou `PermanentlyDenied` | Explicação + ação (pedir de novo, ou abrir definições do sistema se permanente) — FR-005 |
| 2 | `permission` é `Unknown` | Rationale antes de qualquer diálogo do sistema — FR-001 |
| 3 | `lastUpdatedEpochSeconds == null` e `phase != Idle` | Progresso, com texto distinto para `LocatingUser` e `LoadingFlights` — FR-022 |
| 4 | `lastUpdatedEpochSeconds == null` e `lastError != null` | Erro com repetição, mensagem por variante de `SkyError` — FR-024 |
| 5 | `flights` não vazia | Lista + marca temporal; se `lastError != null`, aviso de dados possivelmente desatualizados — FR-025 |
| 6 | `flights` vazia, `lastError == null`, já houve sucesso | Céu vazio, sem aspeto de erro — FR-023 |

A precedência é o contrato: a permissão ganha sempre a tudo, e ter resultados antigos ganha ao
erro (nunca se limpa a lista por causa de uma falha).

## Transições legais

```
Unknown ──permissão concedida──▶ Granted ──▶ LocatingUser ──▶ LoadingFlights ──▶ Idle
   │                                              │                  │
   └──recusada──▶ Denied/PermanentlyDenied        └── falha ─────────┴──▶ Idle + lastError
```

**Invariantes verificados por teste:**

1. `phase == Idle` sempre que não há operação em curso; nunca fica preso em `LoadingFlights`.
2. Um sucesso limpa `lastError` e atualiza `lastUpdatedEpochSeconds`.
3. Uma falha **nunca** limpa `flights` nem `lastUpdatedEpochSeconds`.
4. Nunca há duas atualizações concorrentes: um refresh manual durante um automático não emite um
   segundo pedido (FR-020).
5. Um refresh manual reinicia o relógio dos 30 s.
6. `RateLimited(retryAfter)` alonga a espera seguinte para `max(30 s, retryAfter)`.
7. Ao deixar de haver subscritores, o ciclo para em 5 s; nenhuma chamada a
   `LocationRepository` ou `FlightRepository` depois disso (FR-019, SC-007).
8. Permissão revogada com o ecrã aberto ⇒ ao voltar, estado passa a `Denied` (FR-006).
9. Uma mudança de configuração (rotação, tema, tamanho de fonte) preserva `flights`,
   `lastUpdatedEpochSeconds` e `lastError`, e **não** dispara um pedido novo — a janela de 5 s do
   `WhileSubscribed` cobre a recriação da activity (FR-026).

## Formatação apresentada

| Grandeza | Formato | Fonte |
|---|---|---|
| Distância | `12,4 km` (1 casa) | `horizontalDistanceMeters / 1000` |
| Altitude | `10 400 m` (inteiro) | `aircraft.altitudeMeters` |
| Velocidade | `840 km/h` (inteiro) | `groundSpeedMetersPerSecond * 3.6` |
| Elevação | `47°` (inteiro) | `elevationDegrees` |
| Direção | `NE`, `SSO`… (16 rumos) | `bearingDegrees` |
| Última atualização | `há 12 s`, `há 2 min` | `lastUpdatedEpochSeconds` |

Quilómetros, metros e km/h são fixos nesta feature; a escolha de unidades pertence à feature de
definições. Campos ausentes são omitidos, sem substituto (FR-014) — **com uma exceção**: quando
falta o indicativo de voo, a entrada é identificada pelo `icao24` em maiúsculas (FR-013). É o
único campo que a lista nunca deixa em branco, porque sem ele não haveria como referir a aeronave.

**Zénite**: com `elevationDegrees >= 85`, a direção deixa de ter significado e a entrada mostra
"mesmo por cima" em vez de um rumo.
