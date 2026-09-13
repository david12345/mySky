---

description: "Task list for 005-sky-widget"
---

# Tasks: Widget de ecrã inicial

**Input**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

## Nota sobre a ordem das histórias

A US1 (ver o céu sem abrir a app) precisa de agendamento para haver dados — não se pode entregar sem
ele. Por isso o agendamento **mínimo** entra na US1, e a **US3 é sobre as garantias**: cancelar quando
o último widget sai, nunca haver mais do que um trabalho, e recuperar o widget partido da v1.0.0. Cada
uma continua testável por si.

---

## Fase 1: Fundações (bloqueia tudo)

- [X] T001 [P] Criar `app/src/main/java/com/mysky/app/domain/model/SkyWidgetSnapshot.kt`: selado com `Flights(top, count, observedAtEpochSeconds)`, `EmptySky(observedAtEpochSeconds)`, `PermissionMissing(observedAtEpochSeconds)`, mais `WidgetFlight(callsign?, airlineName?, elevationDegrees)` conforme [data-model.md](./data-model.md)
- [X] T002 [P] Criar `app/src/main/java/com/mysky/app/domain/model/SkyWidgetState.kt`: selado com os cinco estados e `evaluate(snapshot, nowEpochSeconds, freshnessWindowSeconds)` pura, com `DEFAULT_FRESHNESS_WINDOW_SECONDS = 300L` e o porquê dos 300 s em KDoc (R6)
- [X] T003 [P] Criar `app/src/test/java/com/mysky/app/domain/model/SkyWidgetStateTest.kt` cobrindo a tabela de decisão de [contracts/sky-widget-state.md](./contracts/sky-widget-state.md): as 6 invariantes, a fronteira exata da janela, e o instante no futuro
- [X] T004 [P] Criar `app/src/main/java/com/mysky/app/domain/model/SkyBudget.kt`: `DAILY_QUERY_BUDGET = 400`, `SCREEN_CYCLE_SECONDS = 30`, e as três funções de [data-model.md](./data-model.md). O 400 **só aqui** (AD-027)
- [X] T005 [P] Criar `app/src/test/java/com/mysky/app/domain/model/SkyBudgetTest.kt` com a tabela 15/30/60 min e um teste que verifica o SC-007 literalmente (30 min → 48 → 12% → ≥2h50m)
- [X] T006 Criar `app/src/main/java/com/mysky/app/domain/repository/SkyWidgetRepository.kt` conforme [contracts/sky-widget-repository.md](./contracts/sky-widget-repository.md)
- [X] T007 Acrescentar `REFRESH_INTERVAL_RANGE = 15L..180L` a `SkySettings` e incluir `refreshIntervalMinutes` no `coerced()` — é isto que dá a FR-026 sem uma segunda validação no scheduler (AD-022)
- [X] T008 Estender `app/src/test/java/com/mysky/app/domain/model/SkySettingsTest.kt`: cadência abaixo de 15 sobe para 15, acima de 180 desce, e as fronteiras exatas passam intactas

## Fase 2: O ciclo partilhado (AD-028) — bloqueia US1

- [X] T009 Criar `app/src/main/java/com/mysky/app/domain/usecase/RunSkyCycleUseCase.kt` com `SkyCycleResult` selado, conforme [contracts/background-work.md](./contracts/background-work.md). Nunca lança; sem permissão não vai à rede
- [X] T010 [P] Criar `app/src/test/java/com/mysky/app/domain/usecase/RunSkyCycleUseCaseTest.kt`: as 4 invariantes do contrato, incluindo "sem permissão não gasta consulta" (verificado por o `ObserveSkyUseCase` não ser chamado)
- [X] T011 Alterar `presentation/sky/SkySession.kt` para `refreshOnce()` delegar no `RunSkyCycleUseCase`, mantendo por cima o `StateFlow`, o recuo de 429 e o laço de 30 s
- [X] T012 Correr a suite completa e confirmar que **nenhum teste anterior precisou de alteração** — se precisou, o refactor mudou comportamento observável e isso é um defeito, não um ajuste

---

## Fase 3: US1 — Ver o céu sem abrir a app (P1) 🎯 MVP

**Teste independente**: adicionar o widget, esperar um ciclo, ver uma aeronave real com a hora, sem
abrir a app.

- [X] T013 [P] [US1] Criar `app/src/main/java/com/mysky/app/data/widget/SkyWidgetRepositoryImpl.kt` sobre DataStore **próprio** (não o das definições — ver contrato). Ler e escrever nunca lançam; `retryWhen` limitado + valores de recurso, como o `SettingsRepositoryImpl`
- [X] T014 [P] [US1] Criar `app/src/main/java/com/mysky/app/di/WidgetModule.kt`: DataStore do widget com `ReplaceFileCorruptionHandler` e `produceFile` próprio, e o binding do repositório
- [X] T015 [US1] Criar `app/src/test/java/com/mysky/app/data/widget/SkyWidgetRepositoryImplTest.kt`: volta completa de cada variante, `null` quando nunca gravou, e **escrever sobre armazenamento corrompido não lança** (o defeito real que a revisão da 004 encontrou)
- [X] T016 [P] [US1] Criar `app/src/main/java/com/mysky/app/worker/WidgetRefresher.kt` e `WidgetPresenceCheck.kt` — interfaces sem tipos de Glance (AD-025)
- [X] T017 [US1] Implementar `worker/SkyWorkScheduler.kt`: `schedulePeriodicRefresh` com `ExistingPeriodicWorkPolicy.UPDATE` e nome único, `NetworkType.CONNECTED`, backoff exponencial; `cancelPeriodicRefresh`; `requestImmediateRefresh` com `ExistingWorkPolicy.KEEP`
- [X] T018 [US1] Criar `worker/SkyBackgroundWorkCoordinator.kt` com `reconcile()`, condição `hasAnyWidget() || settings.notificationsEnabled` **desde já** (AD-026)
- [X] T019 [US1] Implementar `worker/SkyRefreshWorker.doWork()`: chama o `RunSkyCycleUseCase`, aplica a tabela de [contracts/background-work.md](./contracts/background-work.md), grava só quando há facto novo, e chama `widgetRefresher.refreshAll()`
- [X] T019b [US1] A construção do `SkyWidgetSnapshot.Flights` escolhe o topo com `maxByOrNull { it.elevationDegrees }` — **não** `first()`. A lista chega ordenada por `relevanceScore`, mas depender disso é um acoplamento implícito: se a ordenação a montante mudar, o widget passa a mostrar o avião errado sem erro nenhum e sem teste a falhar
- [X] T019c [P] [US1] Testar que o topo é o de maior elevação **mesmo com a lista fora de ordem à entrada**, e que uma aeronave sem indicativo ou sem companhia continua a ser apresentada (FR-005)
- [X] T020 [P] [US1] Criar `app/src/test/java/com/mysky/app/worker/SkyRefreshWorkerDecisionTest.kt` sobre a tabela de decisão como função pura — cada linha, incluindo "falha não grava" (FR-014)
- [X] T021 [P] [US1] Criar `app/src/main/java/com/mysky/app/widget/GlanceWidgetRefresher.kt`: implementa `WidgetRefresher` com `updateAll` e `WidgetPresenceCheck` com `GlanceAppWidgetManager.getGlanceIds`
- [X] T022 [US1] Criar `app/src/main/java/com/mysky/app/widget/SkyWidgetContent.kt`: um composable por estado, com o instante sempre visível quando há dados. Nenhum ramo sem texto (FR-007)
- [X] T022b [US1] Criar o `@EntryPoint` de acesso ao `SkyWidgetRepository`, ao `TimeProvider` e ao `SkyWorkScheduler` a partir de `widget/`. **O Hilt não injeta em `GlanceAppWidget` nem em `ActionCallback`** — o `@AndroidEntryPoint` do receiver injeta o *receiver*, não o `SkyWidget()` que ele cria. Sem isto a T023 e a T029 não compilam
- [X] T023 [US1] Implementar `widget/SkyWidget.provideGlance`: lê o repositório, lê o relógio pelo `TimeProvider`, chama `SkyWidgetState.evaluate` **nesse instante** (AD-024) e desenha. Sem rede (FR-009)
- [X] T024 [US1] Ligar o toque no corpo do widget a abrir a `MainActivity` (FR-008)
- [X] T025 [US1] Implementar `widget/SkyWidgetReceiver`: `onEnabled`/`onUpdate` chamam `reconcile()`; **chamar sempre `super`** nos que o Glance já sobrepõe (R3 — omitir isto dá um widget em branco, sem exceção nenhuma)
- [X] T026 [P] [US1] Acrescentar as strings de todos os estados a `res/values/strings.xml`, em português, com presente e passado **separados** — nunca a mesma string com o tempo verbal a variar por interpolação
- [X] T027 [US1] Reduzir o `initialLayout` e o tamanho em `res/xml/sky_widget_info.xml` ao que faz sentido agora que há conteúdo real; confirmar que a informação essencial cabe em 3×1

**Ponto de paragem**: o widget mostra dados reais e honestos. Entregável por si.

---

## Fase 4: US2 — Atualizar já, com um toque (P2)

**Teste independente**: com dados antigos, tocar em atualizar e ver o instante ficar recente.

- [X] T028 [US2] Definir o canal efémero por widget em `GlanceStateDefinition` para "a atualizar"/"falhou por X" (AD-023) — separado do porto de domínio, e é o que o torna por instância e não partilhado
- [X] T029 [US2] Implementar `widget/RefreshSkyWidgetAction.onAction` (usa o `@EntryPoint` da T022b): marca "a atualizar" no canal efémero e chama `SkyWorkScheduler.requestImmediateRefresh()` — sem rede na ação (FR-016)
- [X] T030 [US2] Desenhar o botão de atualizar em `SkyWidgetContent`, visualmente distinto do corpo que abre a app (FR-015)
- [X] T031 [US2] Fazer o worker limpar o estado efémero ao terminar, distinguindo falta de rede de orçamento esgotado (FR-019)
- [X] T032 [P] [US2] Strings de "a atualizar", "sem ligação" e "limite diário atingido" — três textos distintos, nunca um erro genérico

**Ponto de paragem**: o "agora" passa a ser alcançável.

---

## Fase 5: US3 — Não gastar bateria a calcular o que ninguém vê (P3)

**Teste independente**: adicionar e remover widgets e inspecionar o trabalho agendado.

- [X] T033 [US3] Implementar `onDisabled` e `onDeleted` no receiver a chamar `reconcile()` (com `super`), para o último widget removido cancelar o trabalho (FR-020)
- [X] T034 [US3] Chamar `reconcile()` em `MySkyApplication.onCreate()` — a rede de segurança que resolve a FR-022 sem migração nenhuma (AD-026)
- [X] T035 [P] [US3] Criar `app/src/test/java/com/mysky/app/worker/SkyBackgroundWorkCoordinatorTest.kt`: agenda com widget, cancela sem widget, é idempotente, e **agenda com `notificationsEnabled = true` sem widget nenhum** — o teste que entrega a garantia à feature seguinte em vez de uma promessa
- [X] T036 [US3] FR-021 (dois widgets, um só trabalho) fica garantida pelo nome único de `enqueueUniquePeriodicWork` — uma garantia do WorkManager que **não é verificável na JVM** sem `work-testing`. Coberta pela secção 7 do quickstart, e dito aqui em vez de fingir cobertura

**Ponto de paragem**: quem não tem widget não paga nada.

---

## Fase 6: US4 — Escolher a cadência, sabendo o que ela custa (P4)

**Teste independente**: mudar a cadência nas definições e ver o trabalho passar a usá-la.

- [X] T037 [US4] Acrescentar `updateSchedule` ao `SettingsViewModel` — a terceira categoria de escrita, que grava e chama `reconcile()` (AD-027). Não chamar o scheduler diretamente
- [X] T038 [US4] Estender o `SettingsUiState` com `get()` derivados do `SkyBudget` (consultas/dia, fração, tempo de ecrã), como já fez com `usefulRangeMeters` (AD-017: estender, não criar um segundo estado)
- [X] T039 [US4] Acrescentar o controlo de cadência a `SkySettingsSection.kt`, escrevendo em `onValueChangeFinished` como os outros (AD-019), com o custo visível ao lado (FR-027)
- [X] T040 [P] [US4] Strings da cadência e do custo em tempo de ecrã
- [X] T041 [P] [US4] Estender `SettingsViewModelTest`: alterar a cadência chama `reconcile()`; alterar unidades **não** chama; o custo aparece no estado

---

## Fase 7: Fecho

- [X] T042 Correr `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` — tudo verde
- [ ] T043 Invocar o subagente `reviewer` sobre a feature completa e tratar os achados críticos e os "deveria corrigir" (princípio VI)
- [ ] T044 Validação manual de [quickstart.md](./quickstart.md), com prioridade para a **secção 4** (o widget partido da v1.0.0, que só é reproduzível uma vez) e a **secção 7** (0 ou 1 trabalho, sempre)
- [ ] T045 Atualizar a secção "Estado atual" do `CLAUDE.md`

---

## Anotação sobre a FR-011

A FR-011 exige "os mesmos critérios de deteção **e as mesmas unidades**". A parte dos critérios é real e
está na T009. A parte das unidades **não tem efeito nesta feature**: o widget mostra indicativo,
companhia e graus de elevação, e graus não são uma grandeza com unidade escolhível. Fica escrito para
ninguém implementar uma conversão que nada consome — e para que, no dia em que o widget passar a mostrar
altitude ou distância, se saiba que a cláusula existia e passou a ter efeito.

## Dependências

```
Fase 1 (fundações) → Fase 2 (ciclo partilhado) → US1 → US2
                                                  US1 → US3
                                                  US1 → US4
```

US2, US3 e US4 são independentes entre si depois da US1.

## Paralelismo

- **T001–T005**: cinco ficheiros novos e independentes no `domain`
- **T013/T014/T016**: repositório, DI e interfaces não se tocam
- **T020/T021**: teste de decisão e implementação do Glance são de camadas diferentes
- Todas as tarefas de strings ([P]) enquanto o código correspondente é escrito

## MVP

**Fases 1 a 3.** Entrega um widget que mostra o céu com honestidade e fecha a caixa avariada da v1.0.0.
Sem atualização a pedido, sem cancelamento e sem controlo de cadência — mas correto no que mostra.
