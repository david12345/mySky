---
description: "Task list for feature 004-settings"
---

# Tasks: Definições

**Input**: Design documents from `/specs/004-settings/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: **Incluídos e obrigatórios.** O princípio VI exige testes no domínio, e esta feature tem
duas falhas que não produzem erro visível: um controlo ligado ao evento errado, que gasta o orçamento
de um dia num arrasto sem dar sinal; e uma degradação de valores demasiado zelosa, que "corrigiria"
escolhas legítimas do utilizador em silêncio.

**Organization**: tarefas agrupadas por história. Esta feature **altera código já validado** por 232
testes — não é greenfield, e a Fase 2 existe para o provar antes de qualquer história começar.

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

**Purpose**: fixar a linha de base que falta há três features, e preparar os limites.

- [ ] T001 Medir e registar na secção 0 de [quickstart.md](./quickstart.md) a **mediana do tempo até à primeira lista**, em cinco arranques a frio com a permissão concedida, **antes** de tocar em código. Não é requisito desta feature: é a dívida que deixou o SC-005 da 003 inverificável, e esta feature acrescenta mais uma leitura de disco ao primeiro ciclo (AD-018). É a última oportunidade fácil de fixar um número que já falta a duas features
- [ ] T002 [P] Acrescentar a `app/src/main/res/values/strings.xml` as strings desta feature: rótulo e explicação de cada definição, unidades, ação de repor, e a frase do alcance útil com o valor interpolado
- [ ] T003 [P] Acrescentar a `app/src/test/java/com/mysky/app/TestFixtures.kt` um construtor `skySettings(...)` com os valores de origem por omissão, para os testes declararem só o que desviam

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: os limites, a degradação e o alcance útil — tudo no domínio, tudo puro. E a prova de que
o que já existe continua intacto.

**⚠️ CRITICAL**: nenhuma história pode começar antes desta fase estar completa e com os 232 testes
anteriores verdes.

- [ ] T004 [P] Escrever `app/src/test/java/com/mysky/app/domain/model/SkySettingsTest.kt`: um valor abaixo do mínimo é corrigido para o mínimo e um acima do máximo para o máximo; e — o par indispensável — **um valor dentro dos limites passa intacto**. Sem esse segundo teste, um limite mal escrito "corrigiria" escolhas legítimas e ninguém daria por isso
- [ ] T005 [P] Acrescentar a `SkySettingsTest.kt` os casos dos campos que **não** são desta feature: repor os valores de origem não toca no intervalo do trabalho periódico, nem nas preferências de notificações e widget (FR-005, FR-015)
- [ ] T006 [P] Escrever `app/src/test/java/com/mysky/app/domain/model/SkyRangeTest.kt`: o alcance útil de 25° com teto de 12 km dá ~26 km, de 5° dá ~137 km, e de 60° dá ~7 km — os valores da tabela de [data-model.md](./data-model.md)
- [ ] T007 Acrescentar a `app/src/main/java/com/mysky/app/domain/model/SkySettings.kt` os limites de cada valor (mínimo, origem, máximo) conforme a tabela de [data-model.md](./data-model.md), e a função pura `coerced()` que devolve uma cópia com todos os campos dentro deles. É o **único ponto de verdade** dos limites, consumido pelo controlo, pela degradação e pela frase explicativa — nenhum dos três os redefine
- [ ] T008 Criar `app/src/main/java/com/mysky/app/domain/model/SkyRange.kt` com o alcance útil: `altitude / tan(ângulo)`, a inversa da função de elevação que já existe no `GeoCalculator`. Sem geometria nova, e com KDoc a dizer que é uma **aproximação** — assume um teto de altitude típico, e há tráfego que voa acima dele (AD-021)
- [ ] T009 Gate da fase: `./gradlew :app:testDebugUnitTest` com os 232 testes anteriores verdes. Nesta fase nada mudou no comportamento **nem no código deles**; se algum falhar, é a alteração que está errada

**Checkpoint**: os limites existem, são puros e testados, e nada do que já funcionava mudou.

---

## Phase 3: User Story 1 - Ajustar o que conta como "o meu céu" (Priority: P1)

**Goal**: o utilizador ajusta raio, ângulo e altitude, e a lista reflete a escolha sem reiniciar.

**Independent Test**: alterar o raio nas definições, voltar à lista, e verificar que aeronaves que
antes não apareciam passam a aparecer — ou o contrário.

- [ ] T010 [P] [US1] Escrever `app/src/test/java/com/mysky/app/data/settings/SettingsRepositoryImplTest.kt` com as sete invariantes de [contracts/settings-repository.md](./contracts/settings-repository.md), sobre um DataStore **real** em ficheiro temporário e não um duplo: o que se quer verificar é o comportamento perante armazenamento ausente e valores fora dos limites
- [ ] T011 [P] [US1] Acrescentar a `app/src/test/java/com/mysky/app/presentation/sky/SkySessionTest.kt` os testes da AD-018: cada ciclo lê os critérios no início, uma alteração a meio de um ciclo só aparece no seguinte, e a lista **nunca** mistura critérios (FR-017)
- [ ] T012 [US1] Implementar `app/src/main/java/com/mysky/app/data/settings/SettingsRepositoryImpl.kt` sobre o DataStore que já lá está declarado, aplicando `coerced()` a **toda** leitura e também antes de gravar — para um chamador distraído não conseguir persistir um valor fora dos limites (AD-022)
- [ ] T013 [US1] Acrescentar o binding de `SettingsRepository` em `app/src/main/java/com/mysky/app/di/RepositoryModule.kt`
- [ ] T014 [US1] Fazer a `app/src/main/java/com/mysky/app/presentation/sky/SkySession.kt` depender do `SettingsRepository` e ler `settings.first().toCriteria()` no início de cada ciclo (AD-018). É o que fecha a promessa que a AD-009 deixou aberta desde a primeira feature — e o snapshot por ciclo é o que torna o FR-017 estruturalmente impossível de violar
- [ ] T015 [US1] Acrescentar `settings: SkySettings` ao `app/src/main/java/com/mysky/app/presentation/settings/SettingsUiState.kt`, estendendo o estado da 003 em vez de criar um segundo (AD-017), com os derivados de FR-012 e FR-014 calculados e não armazenados
- [ ] T016 [US1] Acrescentar ao `app/src/main/java/com/mysky/app/presentation/settings/SettingsViewModel.kt` as escritas de raio, ângulo e altitude, e a reposição — cada uma seguida de `skySession.requestRefresh()` (AD-019)
- [ ] T017 [US1] Acrescentar os controlos a `app/src/main/java/com/mysky/app/presentation/settings/SettingsScreen.kt`, com o valor em trânsito mantido **localmente** e a escrita só quando o utilizador larga. Ligar a escrita a cada movimento funcionaria perfeitamente e gastaria o orçamento de um dia num arrasto, sem dar sinal nenhum (AD-019)
- [ ] T018 [US1] Escrever em `app/src/test/java/com/mysky/app/presentation/settings/SettingsViewModelTest.kt` as invariantes 1 a 4 de [contracts/settings-ui.md](./contracts/settings-ui.md): uma alteração de critério grava e acorda o laço, um arrasto produz **uma** escrita, e repor devolve tudo ao início numa só ação

**Checkpoint**: a app deixa de ter critérios fixos no código — a promessa da AD-009 fecha-se aqui.

---

## Phase 4: User Story 2 - Ver na unidade que uso (Priority: P2)

**Goal**: distâncias e altitudes na unidade escolhida, em todos os ecrãs, sem mistura.

**Independent Test**: mudar para milhas e pés, e verificar que a lista, o detalhe e o próprio ecrã de
definições passam todos a essas unidades.

- [ ] T019 [P] [US2] Acrescentar a `app/src/test/java/com/mysky/app/presentation/format/FlightFormattingTest.kt` os casos das duas unidades: 30 000 m dá 30,0 km ou 18,6 milhas; 10 400 m dá 10 400 m ou 34 121 pés
- [ ] T020 [US2] Acrescentar o parâmetro de unidade às funções de conversão de `app/src/main/java/com/mysky/app/presentation/format/FlightFormatting.kt`, mantendo-as puras, estáticas e sem `Context` (AD-020)
- [ ] T021 [US2] Acrescentar as duas unidades a `app/src/main/java/com/mysky/app/presentation/main/MainUiState.kt` e a `app/src/main/java/com/mysky/app/presentation/detail/FlightDetailUiState.kt`, combinadas a partir das preferências como a permissão já é combinada com a observação. **Sem `CompositionLocal`** — seria um segundo canal de estado implícito ao lado do estado do ecrã (AD-020)
- [ ] T022 [US2] Atualizar os pontos de chamada em `app/src/main/java/com/mysky/app/presentation/main/FlightRow.kt` e `app/src/main/java/com/mysky/app/presentation/detail/FlightDetailScreen.kt` para passarem a unidade que recebem no estado
- [ ] T023 [US2] Acrescentar ao `app/src/main/java/com/mysky/app/presentation/settings/SettingsViewModel.kt` as escritas das duas unidades, **sem** chamar `requestRefresh()` — não afetam a deteção, e pedir dados por causa delas gastaria um crédito para obter as mesmas aeronaves (AD-019)
- [ ] T024 [US2] Acrescentar a `app/src/main/java/com/mysky/app/presentation/settings/SettingsScreen.kt` o **seletor de cada unidade** (FR-004). Sem isto a US2 constrói toda a canalização das unidades e deixa o utilizador sem torneira: o seu próprio teste independente — "mudar para milhas e pés" — não seria executável
- [ ] T025 [US2] Apresentar os valores das próprias definições na unidade escolhida, em `SettingsScreen.kt` (FR-010) — o ecrã que as define não pode ser o único a ignorá-las
- [ ] T026 [US2] Adaptar `app/src/test/java/com/mysky/app/presentation/detail/FlightDetailFormattingParityTest.kt` à assinatura nova, mantendo o que ele prova: os dois ecrãs produzem o mesmo valor para o mesmo voo. Agora também **na mesma unidade** — e incluindo o ecrã de definições, que é o terceiro sítio onde a mesma grandeza aparece (SC-006)

**Checkpoint**: nenhum ecrã mistura unidades.

---

## Phase 5: User Story 3 - Não conseguir estragar a app (Priority: P2)

**Goal**: os extremos permitidos funcionam, e nada fora deles é aceite.

**Independent Test**: pôr todos os valores nos extremos e verificar que a lista continua a aparecer e
a atualizar-se.

- [ ] T027 [P] [US3] Escrever em `SettingsViewModelTest.kt` o teste da invariante 8 de [contracts/settings-ui.md](./contracts/settings-ui.md): o intervalo de um controlo **não** muda quando o outro é alterado. Testa o contrário do que a intuição sugere — estreitar pareceria ajudar, e moveria o limite debaixo do dedo do utilizador (AD-021)
- [ ] T028 [P] [US3] Acrescentar a `SettingsRepositoryImplTest.kt` o caso de armazenamento ilegível ou corrompido: emite os valores de origem, **sem lançar** (FR-008)
- [ ] T029 [P] [US3] Acrescentar a `SettingsRepositoryImplTest.kt` o caso do FR-016: gravar com os limites de hoje e ler com limites diferentes — a escolha do utilizador sobrevive **corrigida**, nunca perdida. É o teste que prova que a decisão de aplicar a degradação em cada leitura (AD-022) faz o que promete, e o que dispensa versionar o esquema
- [ ] T030 [US3] Confirmar que os controlos limitam ao intervalo de `SkySettings` e não a valores escritos à mão em `SettingsScreen.kt` (FR-009) — os limites têm um dono só
- [ ] T031 [US3] Escrever em `app/src/test/java/com/mysky/app/presentation/sky/SkySessionTest.kt` o caso dos extremos: com o raio no máximo e o ângulo no mínimo, e depois com o ângulo no máximo e o raio no mínimo, a sessão continua a produzir observações e os critérios chegam ao caso de uso tal como foram guardados (SC-004)

---

## Phase 6: User Story 4 - Perceber o que cada definição faz (Priority: P3)

**Goal**: cada definição explica o seu efeito prático, e a incoerência entre raio e ângulo é avisada.

**Independent Test**: mostrar o ecrã a alguém que nunca o viu e perguntar o que espera que aconteça
se mexer em cada definição.

- [ ] T032 [P] [US4] Escrever em `SettingsViewModelTest.kt` o teste da invariante 7: com o raio acima do alcance útil do ângulo escolhido, o estado di-lo (FR-014)
- [ ] T033 [US4] Acrescentar a explicação de cada definição a `SettingsScreen.kt`, com o efeito prático e não a paráfrase do nome (FR-011)
- [ ] T034 [US4] Apresentar a frase do alcance útil quando o raio o excede, com o valor calculado ao vivo pelo `SkyRange` (FR-014). É a única forma de o utilizador descobrir que as duas definições interagem
- [ ] T035 [US4] Indicar em `SettingsScreen.kt` quando um valor está no seu valor de origem (FR-012)

**Checkpoint**: todas as histórias completas.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T036 [P] Verificar que nenhum ficheiro em `app/src/main/java/com/mysky/app/domain/` importa `android.*`, Retrofit, Room, Compose ou WorkManager (princípio I): `grep -rE "^import (android|retrofit2|androidx\.room|androidx\.compose|androidx\.work)" app/src/main/java/com/mysky/app/domain/`
- [ ] T037 [P] Verificar que nenhum controlo do ecrã liga ao intervalo do trabalho periódico (FR-015): `grep -rn "refreshIntervalMinutes" app/src/main/java/com/mysky/app/presentation/` tem de não devolver nada
- [ ] T038 [P] Verificar que a unidade não viaja por `CompositionLocal` (AD-020): `grep -rn "compositionLocalOf\|staticCompositionLocalOf" app/src/main/java/com/mysky/app/presentation/` tem de não devolver nada
- [ ] T039 [P] Limpar os marcadores `TODO(feature/settings)` resolvidos em `data/settings/` e `presentation/settings/`, e confirmar que os que sobram pertencem a features futuras
- [ ] T040 Correr a suite completa e o lint: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` — zero erros de lint, todos os testes verdes
- [ ] T041 Executar a validação manual de [quickstart.md](./quickstart.md) secções 5 a 7, com atenção ao passo 8 (um arrasto tem de produzir **um** pedido, verificado no Network Inspector) e ao passo 7 (repor não pode alterar a data da tabela de rotas)
- [ ] T042 Invocar o subagente `reviewer` sobre a feature completa e tratar os achados críticos e os "deveria corrigir" (princípio VI)
- [ ] T043 Atualizar a secção "Estado atual" do `CLAUDE.md` para refletir a feature concluída e apontar a seguinte

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Fase 1)**: sem dependências. A T001 tem de ser **a primeira de todas** — mede o arranque antes de a feature o alterar
- **Foundational (Fase 2)**: depende da Fase 1 e **bloqueia tudo**
- **US1 (Fase 3)**: depende da Fase 2
- **US2 (Fase 4)**: depende da Fase 2. Independente da US1 — a unidade é apresentação, os critérios são comportamento
- **US3 (Fase 5)**: depende da US1, porque testa os limites a funcionar de facto
- **US4 (Fase 6)**: depende da US1 e usa o `SkyRange` da Fase 2
- **Polish (Fase 7)**: depende de todas

### O caminho crítico

T001 → T007 → T012 → T014 → T016 → T017. Sem os limites não há repositório, e sem a sessão a lê-los
nada do que o utilizador escolhe chega à lista.

---

## Parallel Example: Fase 2

```bash
# Os três ficheiros de teste, antes de existir código nenhum:
T004 SkySettingsTest.kt · T005 (o mesmo ficheiro) · T006 SkyRangeTest.kt
```

O paralelismo real é escasso, como nas features anteriores: `SettingsScreen.kt` concentra o trabalho
de US1, US2, US3 e US4, e `SettingsViewModelTest.kt` recebe testes de três histórias.

---

## Implementation Strategy

**MVP**: Fase 1 + Fase 2 + US1. Dá ao utilizador controlo real sobre o que vê, e fecha a promessa que
a AD-009 deixou aberta desde a primeira feature. É a metade que importa.

**A US2 é separável e mais espalhada do que parece.** Toca em cinco ficheiros — as funções de
formatação, os dois estados de ecrã, os dois ecrãs — e em dois ficheiros de teste. Não é difícil, é
transversal.

**Se for preciso cortar, corta-se a US4.** As explicações são o que faz as definições serem usadas em
vez de temidas, mas a app funciona sem elas. Cortá-las tem um custo real que vale a pena dizer em voz
alta: sem a frase do alcance útil, o utilizador não tem **nenhuma** forma de descobrir que o raio e o
ângulo interagem.

---

## Notes

- `[P]` = ficheiros diferentes, sem dependências por concluir
- Confirmar que cada teste falha antes de implementar
- Commit por tarefa ou por grupo lógico
- Os **232 testes anteriores** correm-se ao fim de cada tarefa da Fase 2 e da US2, não só no fim: as
  duas alteram código já validado
- **"Testes anteriores inalterados" refere-se ao comportamento, não ao código.** A US2 muda as
  assinaturas das funções de formatação, por isso dois ficheiros de teste **têm** de mudar (T019,
  T026). O que não pode mudar é o que eles provam
- **SC-003** (o custo por consulta não muda) não tem tarefa de implementação: é consequência de os
  limites terem sido derivados com essa conta feita (`research.md`, D1). Verifica-se em T038
- **SC-007** (as pessoas percebem as explicações) só se verifica com pessoas, e está no passo 7 do
  quickstart. Se ninguém acertar numa explicação, é a frase que está errada — não o utilizador
