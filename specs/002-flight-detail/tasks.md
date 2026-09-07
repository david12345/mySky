---
description: "Task list for feature 002-flight-detail"
---

# Tasks: Detalhe de um voo

**Input**: Design documents from `/specs/002-flight-detail/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: **Incluídos e obrigatórios.** O princípio VI da constituição exige testes nos casos de
uso do `domain`, e esta feature tem duas falhas silenciosas identificadas no plano — confundir
ciclo falhado com saída do céu, e subscrever a sessão de forma a nunca parar o laço. Nenhuma das
duas produz erro visível, e é por isso que ambas têm teste dedicado.

**Organization**: tarefas agrupadas por história de utilizador. A fase Foundational é maior do que
o habitual porque contém a refatoração da AD-011 — sem ela nenhuma história pode ser implementada
como especificada.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode correr em paralelo (ficheiros diferentes, sem dependências por concluir)
- **[Story]**: US1 a US4, conforme [spec.md](./spec.md)
- Caminhos de ficheiro sempre explícitos

## Path Conventions

Módulo Gradle único `:app`, Clean Architecture em três camadas.

- Código: `app/src/main/java/com/mysky/app/`
- Recursos: `app/src/main/res/`
- Testes JVM: `app/src/test/java/com/mysky/app/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: preparar o terreno da refatoração sem ainda lhe mexer.

- [ ] T001 Criar o pacote `app/src/main/java/com/mysky/app/presentation/sky/` com `SkyObservation.kt`: o `data class SkyObservation` (fase, `flights`, `lastUpdatedEpochSeconds`, `lastError`) conforme [data-model.md](./data-model.md), e mover para aqui o `enum LoadPhase` que hoje vive em `presentation/main/MainUiState.kt`
- [ ] T002 [P] Acrescentar a `app/src/test/java/com/mysky/app/TestFixtures.kt` os construtores de teste desta feature: `skyObservation(...)` com valores por omissão do caminho feliz, e um atalho para produzir uma lista de `OverheadFlight` com `icao24` conhecidos
- [ ] T003 [P] Acrescentar a `app/src/main/res/values/strings.xml` as strings do ecrã de detalhe: título de cada campo, rótulos das duas altitudes, "a subir"/"a descer"/"nivelado", aviso de saída do céu, erro de navegação sem aeronave, e a ação de voltar

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: mover o laço de atualização do `MainViewModel` para a `SkySession` partilhada (AD-011),
sem alterar uma vírgula do comportamento do ecrã principal.

**⚠️ CRITICAL**: nenhuma história pode começar antes desta fase estar completa e com os testes da
001 verdes.

- [ ] T004 [P] Escrever `app/src/test/java/com/mysky/app/presentation/sky/SkySessionTest.kt` com as invariantes 1 a 4 de [contracts/sky-session.md](./contracts/sky-session.md), em tempo virtual: dois coletores em simultâneo produzem **um** pedido por ciclo; os dois recebem a mesma sequência de emissões; a transição lista → detalhe (entra o segundo, sai o primeiro) não reinicia o laço nem dispara pedido extra; zero coletores durante mais de 5 s não produz pedidos novos
- [ ] T005 [P] Acrescentar a `SkySessionTest.kt` as invariantes 5 a 8: uma falha não limpa `flights` nem `lastUpdatedEpochSeconds`; um 429 alonga a espera para `max(30 s, retryAfter)`; `requestRefresh()` acorda o ciclo de imediato e reinicia o relógio; dois `requestRefresh()` seguidos valem por um e nenhum se perde
- [ ] T006 Implementar `app/src/main/java/com/mysky/app/presentation/sky/SkySession.kt` movendo para lá o laço, o canal conflado e a lógica de backoff que hoje estão em `MainViewModel`, com `@ActivityRetainedScoped` e `stateIn(scope próprio, WhileSubscribed(5_000), SkyObservation())`
- [ ] T007 Ligar o cancelamento do escopo interno da sessão a `ActivityRetainedLifecycle.addOnClearedListener` em `SkySession.kt` (invariante 9), acrescentando um módulo em `app/src/main/java/com/mysky/app/di/` apenas se o binding o exigir
- [ ] T008 Reescrever `app/src/main/java/com/mysky/app/presentation/main/MainUiState.kt` para compor `SkyObservation` em vez de replicar os seus campos, mantendo o significado exato de `isSkyEmpty`, `hasStaleResults`, `isFirstLoad` e `isBlockingError`
- [ ] T009 Reduzir `app/src/main/java/com/mysky/app/presentation/main/MainViewModel.kt` a consumidor fino da sessão: mantém `PermissionState`, `onScreenVisible` e `onPermissionResult`, perde o laço e o canal, e chama `skySession.requestRefresh()` na transição para `Granted` — sem isso o utilizador que acaba de conceder espera até 30 s pela primeira lista
- [ ] T010 Adaptar `app/src/main/java/com/mysky/app/presentation/main/MainScreen.kt` à nova forma do estado, **sem alterar a tabela de precedência** do contrato da 001
- [ ] T011 Adaptar os testes existentes em `app/src/test/java/com/mysky/app/presentation/main/` à nova forma do estado, alterando apenas como o estado é construído e lido — nenhum comportamento verificado pode mudar
- [ ] T012 Gate da refatoração: `./gradlew :app:testDebugUnitTest` com os 131 testes da 001 verdes. Um teste da 001 a falhar significa que a refatoração está errada, não o teste

**Checkpoint**: o ecrã principal comporta-se exatamente como antes, com o laço noutro sítio.

---

## Phase 3: User Story 1 - Ver tudo o que se sabe do avião que escolhi (Priority: P1)

**Goal**: tocar numa entrada da lista abre um ecrã com todos os dados disponíveis daquela aeronave,
com um controlo de voltar.

**Independent Test**: com a lista preenchida, tocar numa linha e verificar que o ecrã mostra a
aeronave certa, com os campos disponíveis preenchidos e os ausentes omitidos, e que voltar devolve
à lista na mesma posição.

- [ ] T013 [P] [US1] Acrescentar a `app/src/test/java/com/mysky/app/presentation/main/FlightFormattingTest.kt` os testes das funções novas: razão de subida acima e abaixo do limiar de 0,5 m/s nos dois sentidos, o caso nivelado, e o rumo da aeronave a **não** ser suprimido no zénite (D4, D6)
- [ ] T014 [P] [US1] Substituir `app/src/test/java/com/mysky/app/presentation/detail/FlightDetailViewModelTest.kt` pelos testes da invariante 1 de [contracts/flight-detail-ui.md](./contracts/flight-detail-ui.md): o voo apresentado é o do `icao24` da rota mesmo com outras aeronaves na observação, e uma rota sem `icao24` não rebenta
- [ ] T015 [US1] Acrescentar a `app/src/main/java/com/mysky/app/presentation/main/format/FlightFormatting.kt` o `sealed interface VerticalMovement` (Climbing/Descending/Level) e a função que o produz a partir de `verticalRateMetersPerSecond`, com o limiar de 0,5 m/s
- [ ] T016 [US1] Acrescentar a `FlightFormatting.kt` a função que devolve o `CompassPoint` do rumo da aeronave, distinta de `compassPointOrNull` e **sem** a regra do zénite
- [ ] T017 [US1] Criar `app/src/main/java/com/mysky/app/presentation/detail/FlightDetailUiState.kt` conforme [data-model.md](./data-model.md), com os derivados calculados e não armazenados
- [ ] T018 [US1] Implementar `app/src/main/java/com/mysky/app/presentation/detail/FlightDetailViewModel.kt` como consumidor fino: `skySession.observation` embrulhado com `stateIn(viewModelScope, WhileSubscribed(5_000), inicial)`, a selecionar o voo pelo `icao24` do `SavedStateHandle`
- [ ] T019 [US1] Implementar `app/src/main/java/com/mysky/app/presentation/detail/FlightDetailScreen.kt` com todos os campos da tabela de [contracts/flight-detail-ui.md](./contracts/flight-detail-ui.md), cada um omitido quando ausente, e as duas altitudes identificadas com a marca de qual entrou no cálculo (D5)
- [ ] T020 [US1] Ligar o controlo de voltar visível no ecrã e confirmar que o gesto do sistema faz o mesmo, em `FlightDetailScreen.kt` e `app/src/main/java/com/mysky/app/presentation/navigation/MySkyNavHost.kt`
- [ ] T021 [US1] Escrever o teste da invariante 7 em `app/src/test/java/com/mysky/app/presentation/detail/FlightDetailFormattingParityTest.kt`: para o mesmo `OverheadFlight`, os valores formatados que o detalhe apresenta são idênticos aos que a `FlightRow` produz (sustenta SC-002)
- [ ] T022 [US1] Apresentar a marca temporal em `FlightDetailScreen.kt`, com a mesma escala da lista, reutilizando o ticker de 1 segundo já existente em `presentation/main/MainScreen.kt` e a `Freshness` do `FlightFormatting`. É FR-007 e o critério de aceitação 2 da US1, e o princípio IV da constituição exige que a UI mostre **sempre** quando os dados foram obtidos: um ecrã de detalhe sem esta linha não é entregável, nem sequer como MVP
- [ ] T023 [P] [US1] Escrever em `FlightDetailViewModelTest.kt` (ou no teste de ecrã) o caso dos campos ausentes: um `Aircraft` sem operador, sem altitude barométrica, sem rumo e sem razão de subida não produz nenhum campo vazio, nenhum travessão e nenhum zero por omissão (SC-006, US1 cenário 3)

**Checkpoint**: o beco sem saída da 001 está fechado — o toque numa linha abre um ecrã útil e datado.

---

## Phase 4: User Story 2 - Acompanhar o avião enquanto ele passa (Priority: P2)

**Goal**: com o detalhe aberto, os valores mudam sozinhos à cadência da lista, e tudo pára quando o
ecrã deixa de estar visível.

**Independent Test**: abrir o detalhe de uma aeronave em movimento e observar durante dois minutos
que os valores mudam sem ação nenhuma e que a marca temporal nunca passa de um ciclo.

- [ ] T024 [P] [US2] Acrescentar a `FlightDetailViewModelTest.kt` o teste de que uma emissão nova da sessão atualiza o ecrã sem qualquer ação, e o da invariante 8: uma mudança de configuração não repete um pedido nem esvazia o ecrã
- [ ] T025 [US2] Ligar o gesto de atualizar do detalhe a `skySession.requestRefresh()` em `FlightDetailScreen.kt` e `FlightDetailViewModel.kt` — o mesmo canal da lista, portanto renova os dois ecrãs
- [ ] T026 [US2] Gate da subscrição: `grep -rn "viewModelScope.launch" app/src/main/java/com/mysky/app/presentation/` não pode devolver nenhuma subscrição de `skySession.observation` — uma subscrição feita assim prende o laço à vida do ViewModel e a app passa a consultar a rede em segundo plano sem qualquer sinal visível

**Checkpoint**: o detalhe é uma janela viva, e continua a parar quando ninguém a vê.

---

## Phase 5: User Story 3 - Perceber que o avião saiu do meu céu (Priority: P2)

**Goal**: quando a aeronave deixa de constar das observações, o ecrã di-lo de forma explícita e os
valores ficam, marcados como última observação (AD-012, FR-020).

**Independent Test**: abrir o detalhe de uma aeronave prestes a sair do critério e verificar que,
quando ela desaparece da lista, o ecrã explica a situação e deixa de apresentar os valores como se
fossem de agora.

- [ ] T027 [P] [US3] Escrever `app/src/test/java/com/mysky/app/domain/usecase/TrackFlightPresenceUseCaseTest.kt` com a tabela de transições completa de [data-model.md](./data-model.md) — as oito linhas. A que não pode faltar é **ciclo falhado a partir de `Current` mantém `Current`**: confundir isso com uma saída faz a app anunciar que o avião partiu sempre que a rede cai
- [ ] T028 [P] [US3] Acrescentar a `TrackFlightPresenceUseCaseTest.kt` os casos de fronteira: `LeftSky` a reaparecer volta a `Current` sem intervenção (FR-021), e `LeftSky` que continua ausente conserva o instante da primeira ausência sem o mexer (FR-020)
- [ ] T029 [US3] Criar `app/src/main/java/com/mysky/app/domain/model/FlightPresence.kt` com as três variantes seladas e as invariantes de [data-model.md](./data-model.md)
- [ ] T030 [US3] Implementar `app/src/main/java/com/mysky/app/domain/usecase/TrackFlightPresenceUseCase.kt` como função pura: estado anterior, `List<OverheadFlight>?` (com `null` distinto de lista vazia), `icao24` e `nowEpochSeconds` como parâmetros, sem relógio e sem memória lá dentro
- [ ] T031 [US3] Ligar o caso de uso ao `FlightDetailViewModel.kt`, guardando o estado de presença anterior entre emissões da sessão
- [ ] T032 [US3] Apresentar o estado `LeftSky` em `FlightDetailScreen.kt`: aviso explícito de saída do céu, instante da última observação, e os valores mantidos mas marcados como já não atuais
- [ ] T033 [US3] Apresentar o caso `NeverObserved` com observação já bem sucedida (linha 4 da precedência): a aeronave não está no teu céu — cobre a rota antiga e o processo restaurado

**Checkpoint**: o desfecho normal de qualquer avião observado é comunicado em vez de congelado.

---

## Phase 6: User Story 4 - Continuar a perceber o que se passa quando algo corre mal (Priority: P3)

**Goal**: falhas de rede, do serviço e de localização são explicadas no detalhe sem apagar o que
está no ecrã.

**Independent Test**: com o detalhe aberto, ativar o modo de avião e verificar que os dados
permanecem, marcados como possivelmente desatualizados, com a causa.

- [ ] T034 [P] [US4] Acrescentar a `FlightDetailViewModelTest.kt` os testes da precedência 1 a 3 e da invariante 6: rota sem `icao24`, erro sem nada por baixo, primeiro carregamento, e uma falha que não limpa os dados apresentados
- [ ] T035 [US4] Implementar em `FlightDetailScreen.kt` os estados de erro, de primeiro carregamento e de rota inválida, pela ordem de precedência de [contracts/flight-detail-ui.md](./contracts/flight-detail-ui.md)
- [ ] T036 [US4] Reutilizar `app/src/main/java/com/mysky/app/presentation/main/SkyErrorMessages.kt` para as mensagens por variante de `SkyError`, sem duplicar textos no detalhe

**Checkpoint**: todas as histórias completas.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T037 [P] Verificar que nenhum ficheiro em `app/src/main/java/com/mysky/app/domain/` importa `android.*`, Retrofit, Room, Compose ou WorkManager (princípio I): `grep -rE "^import (android|retrofit2|androidx\.room|androidx\.compose|androidx\.work)" app/src/main/java/com/mysky/app/domain/`
- [ ] T038 [P] Verificar que a `SkySession` não é injetada em `worker/` nem em `widget/` (AD-003, AD-011): `grep -rn "SkySession" app/src/main/java/com/mysky/app/worker/ app/src/main/java/com/mysky/app/widget/` tem de não devolver nada
- [ ] T039 [P] Limpar os marcadores `TODO(feature/flight-detail)` resolvidos em `presentation/detail/` e confirmar que os que sobram pertencem a features futuras
- [ ] T040 Correr a suite completa e o lint: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` — zero erros de lint, todos os testes verdes
- [ ] T041 Executar a validação manual de [quickstart.md](./quickstart.md) secções 5 a 7, com atenção ao passo 7 (modo de avião **não** pode anunciar saída do céu) e à contagem de pedidos no Network Inspector (SC-008)
- [ ] T042 Invocar o subagente `reviewer` sobre a feature completa e tratar os achados críticos e os "deveria corrigir" (princípio VI)
- [ ] T043 Atualizar a secção "Estado atual" do `CLAUDE.md` para refletir a feature concluída e apontar a seguinte

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Fase 1)**: sem dependências
- **Foundational (Fase 2)**: depende da Fase 1 e **bloqueia tudo o resto**
- **US1 (Fase 3)**: depende da Fase 2
- **US2 (Fase 4)**: depende de US1 — não há o que atualizar antes de haver ecrã
- **US3 (Fase 5)**: depende de US1. Independente de US2: a presença é calculada a cada emissão, haja ou não gesto manual
- **US4 (Fase 6)**: depende de US1
- **Polish (Fase 7)**: depende de todas

### Ordem dentro das histórias

Testes primeiro, depois o modelo, depois o ViewModel, depois o ecrã. Confirmar que cada teste falha
antes de implementar.

### O caminho crítico

T001 → T006 → T009 → T012 → T018 → T019. Tudo o resto pendura-se daqui.

---

## Parallel Example: Fase 2

```bash
# Os dois ficheiros de teste da sessão, antes de existir sessão nenhuma:
T004 SkySessionTest.kt (invariantes 1-4)  ·  T005 SkySessionTest.kt (invariantes 5-8)
```

Na prática T004 e T005 tocam no mesmo ficheiro, por isso o paralelismo real desta feature é escasso:
`FlightDetailScreen.kt` e `FlightDetailViewModel.kt` concentram US1, US2, US3 e US4. Dividir por
pessoas custaria mais em conflitos do que pouparia em tempo — como na 001.

---

## Implementation Strategy

**MVP**: Fase 1 + Fase 2 + US1. Dá um ecrã de detalhe completo, correto e **datado**, ainda que
estático entre atualizações da lista. É entregável e resolve o problema que motivou a feature.

A marca temporal (T022) faz parte do MVP e não da US2, apesar de parecer trabalho de atualização
contínua: o princípio IV da constituição exige que a UI mostre sempre quando os dados foram
obtidos. Um ecrã sem essa linha apresenta valores sem idade — que é a definição de prometer tempo
real.

**Incremento seguinte**: US3 antes de US2, se for preciso escolher. Um ecrã que não se atualiza
sozinho é uma limitação visível; um ecrã que mostra a última posição como se fosse atual é uma
mentira invisível — e é o desfecho de todos os aviões.

**A refatoração da Fase 2 é indivisível.** Parar a meio deixa o ecrã principal com o laço em dois
sítios. Se ela não couber numa sessão de trabalho, é melhor não a começar.

---

## Notes

- `[P]` = ficheiros diferentes, sem dependências por concluir
- Confirmar que cada teste falha antes de implementar
- Commit por tarefa ou por grupo lógico
- Os 131 testes da 001 são a rede de segurança da Fase 2: correm-se depois de cada tarefa dela, não
  só no fim
- **SC-001** (detalhe preenchido em menos de 1 s) não tem tarefa própria: é consequência de o
  detalhe ler uma observação já em memória. Se alguma vez precisar de um pedido próprio para
  abrir, a decisão de desenho está errada
- **SC-007** (9 em 10 utilizadores voltam à lista à primeira) é métrica de usabilidade
  pós-lançamento, não trabalho construível — fica fora do âmbito por desenho
