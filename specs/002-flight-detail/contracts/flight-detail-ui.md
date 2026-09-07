# Contrato de UI — ecrã de detalhe

O que a UI observa, o que envia, e o que renderiza em cada situação. É este contrato que os testes
de `FlightDetailViewModel` verificam.

## Estado observado

```
FlightDetailUiState(
    icao24: String?,                    // da rota; null = rota malformada
    presence: FlightPresence,           // NeverObserved | Current | LeftSky
    lastUpdatedEpochSeconds: Long?,     // da sessão partilhada
    lastError: SkyError?,               // da sessão partilhada
)
```

Exposto como `StateFlow`, com `stateIn(viewModelScope, WhileSubscribed(5_000), inicial)` sobre
`skySession.observation`, e consumido com `collectAsStateWithLifecycle()`.

## Eventos enviados pela UI

| Evento | Quando | Efeito |
|---|---|---|
| `onManualRefresh` | puxar para atualizar | `skySession.requestRefresh()` — renova os dois ecrãs |
| `onBack` | controlo de voltar ou gesto do sistema | navegação, sem alterar o estado |

Não há `onScreenVisible`: o detalhe não pede permissões. A visibilidade já é tratada pela regra de
subscrição do contrato da sessão.

## O que a UI renderiza, por ordem de precedência

| # | Condição | Ecrã | Requisito |
|---|---|---|---|
| 1 | `icao24 == null` | Erro de navegação com caminho de volta; nunca ecrã em branco | FR-003 |
| 2 | `presence is NeverObserved` e `lastError != null` | Erro que ocupa o ecrã, com a causa distinguida e ação de repetir | FR-023 |
| 3 | `presence is NeverObserved` e nunca houve observação | Progresso, com texto distinto para localizar e para obter voos | FR-014 |
| 4 | `presence is NeverObserved` e já houve observação com sucesso | "Esta aeronave não está no teu céu" — caso de entrada por rota antiga ou processo restaurado | FR-019 |
| 5 | `presence is LeftSky` | Todos os dados, marcados como última observação, com o instante em que foi vista e aviso explícito de que saiu do céu | **FR-020** |
| 6 | `presence is Current` | Todos os dados; se `lastError != null`, aviso de possivelmente desatualizados | FR-005 a FR-013, FR-022 |

A precedência é o contrato. A linha 5 é a que distingue esta feature de um ecrã ingénuo: os valores
ficam, mas nunca sem a marca de que já não são de agora.

## Campos apresentados

Todos opcionais exceto a identificação. Um campo ausente **desaparece**: sem espaço reservado, sem
travessão, sem zero.

| Campo | Origem | Regra |
|---|---|---|
| Identificação | `callsign`, ou `icao24` em maiúsculas | nunca vazio (FR-003) |
| Operador | `airline.name` | linha omitida quando `null` (FR-008) |
| País de registo | `originCountry` | — |
| Altitude geométrica | `geometricAltitudeMeters` | identificada como tal (FR-009, D5) |
| Altitude barométrica | `barometricAltitudeMeters` | idem; a usada no cálculo é assinalada |
| Velocidade | `groundSpeedMetersPerSecond` | km/h, como na lista (FR-013) |
| Rumo da aeronave | `headingDegrees` | ponto da rosa dos ventos; **não** suprimido no zénite (D6) |
| Subida/descida | `verticalRateMetersPerSecond` | três estados; nivelado abaixo de 0,5 m/s (D4) |
| Distância | `horizontalDistanceMeters` | km com uma casa, como na lista |
| Direção a olhar | `bearingDegrees` | suprimida acima de 85° de elevação (FR-012) |
| Elevação | `elevationDegrees` | graus inteiros |
| Frescura | `lastUpdatedEpochSeconds` | mesma escala da lista (FR-007) |

## Invariantes verificados por teste

| # | Invariante | Requisito |
|---|---|---|
| 1 | O voo apresentado é o do `icao24` da rota, mesmo com outras aeronaves na observação | FR-002 |
| 2 | Uma observação com sucesso onde o `icao24` não consta transforma `Current` em `LeftSky` | FR-019 |
| 3 | Um ciclo falhado **não** transforma `Current` em `LeftSky` | FR-022 |
| 4 | Reaparecer transforma `LeftSky` em `Current` sem intervenção | FR-021 |
| 5 | `LeftSky` conserva o instante da última observação, que não se mexe com ciclos posteriores | FR-020 |
| 6 | Uma falha não limpa os dados apresentados | FR-022 |
| 7 | Os valores formatados são idênticos aos que a `FlightRow` produz para o mesmo voo | FR-013, SC-002 |
| 8 | Uma mudança de configuração não repete um pedido nem esvazia o ecrã | FR-018 |

O invariante 7 é o que mantém honesta a promessa de SC-002 e o único que precisa de um teste que
toque nos dois ecrãs.
