---

description: "Task list for 006-overhead-notifications"
---

# Tasks: Notificações de passagem

## Fase 0: A correção da 005 (bloqueia tudo)

Sem posição de segundo plano não há ciclo de fundo, e sem ciclo não há candidato a avisar. Esta fase
tem valor por si: entregue sozinha, corrige o widget que hoje não funciona em Android 10+.

- [X] T001 Acrescentar `hasBackgroundLocationPermission()` a `LocationRepository` e implementá-lo em `LocationRepositoryImpl` — verdadeiro por construção abaixo da API 29, onde a permissão não existe
- [X] T002 Acrescentar `LocationAccessMode` (`FOREGROUND`/`BACKGROUND`) e o caso `BackgroundLocationUnavailable` ao `RunSkyCycleUseCase`, com o parâmetro a omitir por omissão para a `SkySession` não mudar de comportamento (AD-029)
- [X] T003 `SkyRefreshWorker` passa `BACKGROUND`; `SkyRefreshDecision` mapeia o caso novo **como `NoPermission`** — grava snapshot e devolve `success()`, o que mata o `retry` infinito
- [X] T004 [P] Testes: sem permissão de fundo o ciclo não vai à rede; o desfecho é `Success` e **nunca** `Retry`; a `SkySession` continua a não ser afetada
- [X] T005 Verificar que o teste de T004 **falha** contra o código anterior — senão não guarda nada

## Fase 1: Fundações

- [X] T006 [P] `domain/model/NotificationPolicy.kt`: janelas, limiar de origem, e `expectedCaptureRate(limiar, cadência)` com o teto de 100%
- [X] T007 [P] Teste do `NotificationPolicy` com a tabela de [data-model.md](./data-model.md), incluindo a fronteira do teto
- [X] T008 [P] `domain/model/OverheadNotificationSelector.kt`: seleção pura, `maxByOrNull`, fronteira inclusiva
- [X] T009 [P] Teste do seletor: lista fora de ordem, lista vazia, exatamente no limiar, ninguém acima
- [X] T010 `SkySettings` ganha `notificationThresholdDegrees` e o piso dependente no `coerced()` (AD-033)
- [X] T011 [P] Teste do piso: o limiar nunca fica abaixo do ângulo mínimo de deteção, e mexer no limiar **não** altera o intervalo do outro controlo
- [X] T012 [P] `domain/repository/NotificationPermission.kt` e a implementação sobre `NotificationManagerCompat`
- [X] T013 `SightingRepository.record` ganha `notified: Boolean` **sem omissão**; implementar `SightingRepositoryImpl` com `wasNotifiedRecently`, `record` e retenção em linha
- [ ] T014 [P] Testes do `SightingRepositoryImpl` com base de dados em memória: a janela de deduplicação, a retenção, e uma aeronave nunca avisada

## Fase 2: US1 — Ser avisado (P1) 🎯 MVP

- [X] T015 `DecideOverheadNotificationUseCase` com a ordem de verificações do [contrato](./contracts/notification-decision.md) — o interruptor **primeiro**, para não consultar Room a cada ciclo de quem tem a feature desligada
- [X] T016 [P] Testes do caso de uso: cada uma das 7 invariantes, com duplos, mais **desligado de origem não avisa** (FR-001) e **um ciclo falhado nunca chega a decidir** (FR-009)
- [X] T017 Implementar `OverheadNotifier`: canal de notificação, conteúdo, e **nunca lançar**. O conteúdo **inclui o instante da observação** (FR-008): um aviso atrasado pelo Doze pode chegar horas depois, e "está por cima de ti" seria falso — a mesma honestidade temporal que a 005 impôs ao widget
- [X] T018 Deep link para o detalhe da aeronave (FR-007), sobre a rota `flight/{icao24}` que já existe
- [X] T019 `SkyRefreshWorker` ganha o passo de notificação depois de gravar e antes de repintar (AD-034); dispara no ciclo periódico **e** no manual
- [X] T020 [P] Strings das notificações, em português

## Fase 3: US2 — Ligar com a expectativa certa (P2)

- [X] T021 `presentation/permission/`: fluxo de `POST_NOTIFICATIONS` com rationale prévio, e o de segundo plano por encaminhamento às definições do sistema (AD-030)
- [X] T022 `SettingsViewModel`: interruptor de notificações, estado efetivo derivado (intenção **e** permissão, AD-032), recalculado quando o ecrã volta a ficar visível
- [X] T022b **Ligar o interruptor ao `reconcile()`** (FR-013). A AD-026 escreveu a condição como `hasAnyWidget() || notificationsEnabled`, mas sem esta chamada ninguém a avalia depois do toggle: quem liga as notificações **sem widget no ecrã** não agenda trabalho nenhum, e a feature fica silenciosa até ao próximo arranque da app — que pode ser dias. Vale nos dois sentidos: desligar sem widget tem de cancelar
- [X] T022c [P] Teste: ligar as notificações sem widget nenhum chama o `reconcile()`, e desligar também
- [X] T023 `SettingsUiState` ganha a taxa de captura esperada, derivada do `NotificationPolicy`
- [X] T024 Secção de notificações no ecrã, com a taxa esperada à vista **antes** de o utilizador ligar (FR-010)
- [X] T025 [P] Testes: revogar a permissão pelas costas não deixa o estado a mentir, e **não apaga a intenção guardada**

## Fase 4: US3 — Escolher o limiar (P3)

- [X] T026 Controlo do limiar, a escrever em `onValueChangeFinished` como os outros
- [X] T027 A taxa mostrada acompanha o limiar e a cadência ao vivo
- [X] T028 [P] Teste: subir o limiar baixa a taxa mostrada

## Fase 5: Fecho

- [X] T029 `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
- [ ] T030 Invocar o `reviewer` e tratar os achados
- [ ] T031 Validação manual de [quickstart.md](./quickstart.md) — **secção 3 é a que prova a correção da 005**
- [ ] T032 Atualizar o `CLAUDE.md`

## Dependências

```
Fase 0 (correção da 005) → Fase 1 → US1 → US2 → US3
```

A fase 0 é entregável sozinha e vale a pena mesmo que o resto pare.

## MVP

**Fases 0 a 2.** Avisa de verdade, uma vez por passagem. Sem controlo de limiar e sem a taxa à vista —
mas a funcionar, e com o widget da 005 reparado.
