---
description: "Task list for feature 003-flight-route"
---

# Tasks: Origem e destino do voo

**Input**: Design documents from `/specs/003-flight-route/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: **Incluídos e obrigatórios.** O princípio VI exige testes nos casos de uso do `domain`, e
esta feature tem três falhas que não produzem erro visível: a pesquisa binária que devolve a rota do
vizinho, a chave que o script gera e a app não encontra, e o `noCompress` em falta. Nenhuma delas dá
sinal; todas têm teste ou gate próprio.

**Organization**: tarefas agrupadas por história. A fase Foundational é grande porque contém o
formato do ficheiro e a leitura — sem isso nenhuma história existe.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode correr em paralelo (ficheiros diferentes, sem dependências por concluir)
- **[Story]**: US1 a US4, conforme [spec.md](./spec.md)
- Caminhos de ficheiro sempre explícitos

## Path Conventions

Módulo Gradle único `:app`, Clean Architecture em três camadas.

- Código: `app/src/main/java/com/mysky/app/`
- Recursos e assets: `app/src/main/res/`, `app/src/main/assets/`
- Testes JVM: `app/src/test/java/com/mysky/app/`
- Ferramentas offline: `tools/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: medir a linha de base, produzir a tabela, e garantir que ela viaja no APK de forma
legível por deslocamento.

- [X] T001 ~~Medir a linha de base do arranque~~ — **dispensada por decisão de 2026-09-08.** Consequência assumida: o SC-005 (mediana do arranque não pior do que antes) fica **inverificável** nesta feature, porque o valor de antes nunca foi apurado e os 7,6 MB do asset apagam a janela para o medir. Os outros critérios de desempenho mantêm-se verificáveis: SC-004 tem linha de base conhecida (4 pedidos por 2 minutos) e a consulta de rota está medida em 37 µs
- [X] T002 Escrever `tools/routes/build_routes_bin.py`: descarrega `routes.csv` e `airports.csv` do espelho, junta ICAO→IATA, guarda só rotas de duas pernas com as duas pontas traduzíveis, normaliza o indicativo com a mesma regra do domínio, desduplica de forma **determinística e documentada**, ordena por chave e escreve o cabeçalho de 32 bytes mais os registos de 13 conforme [data-model.md](./data-model.md). Imprime contagem, data de geração e quantas rotas se perderam
- [X] T003 Gerar `app/src/main/assets/routes.bin` e confirmar que duas execuções sobre os mesmos dados produzem ficheiros com o mesmo `sha256sum` — é o que prova que a desduplicação é determinística e que duas gerações não produzem diffs espúrios
- [X] T004 Acrescentar a extensão `bin` ao `noCompress` em `app/build.gradle.kts` (bloco `androidResources`; o `noCompress` é por **extensão de ficheiro**, não por caminho, e afeta portanto todos os `.bin`). **Sem isto a feature falha em silêncio**: o AAPT comprime a entrada, a leitura por deslocamento deixa de funcionar e o ficheiro passa a ser descomprimido inteiro para memória, sem erro nenhum
- [X] T005 [P] Acrescentar a `app/src/main/res/values/strings.xml` as strings desta feature: rótulo da rota no detalhe, texto da secção de definições, data dos dados, ação de atualizar, e uma mensagem por variante de `RouteUpdateError`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: o modelo, o formato do ficheiro e a leitura por pesquisa binária (AD-013).

**⚠️ CRITICAL**: nenhuma história pode começar antes desta fase estar completa.

- [X] T006 [P] Escrever `app/src/test/java/com/mysky/app/domain/model/RouteTest.kt`: `Route` recusa siglas que não sejam 3 letras A–Z, recusa ser construída com um lado só, aceita origem igual a destino, e `Route.callsignKeyOf` normaliza espaços e minúsculas e devolve `null` para vazio
- [X] T007 [P] Escrever `app/src/test/java/com/mysky/app/data/local/RouteTableFileTest.kt` com ficheiros temporários reais (não mocks): cabeçalho válido lê-se; assinatura errada, versão desconhecida, ficheiro truncado a meio de um registo, contagem que não bate certo com o tamanho e ficheiro vazio são todos rejeitados **sem lançar**
- [X] T008 Criar `app/src/main/java/com/mysky/app/domain/model/Route.kt` com as invariantes validadas no construtor e `callsignKeyOf` no companion — a mesma regra que o script usa, e a razão de viver no domínio e não na camada de dados
- [X] T009 Criar `app/src/main/java/com/mysky/app/data/local/RouteTableFile.kt`: leitura e validação do cabeçalho de 32 bytes, contagem de registos, data de geração, e a ordem de validação de [data-model.md](./data-model.md)
- [X] T010 Criar `app/src/main/java/com/mysky/app/data/local/RouteTableReader.kt`: leitura aleatória sobre `(descritor, deslocamento base, comprimento)`, com **duas implementações** — ficheiro de `filesDir` e asset dentro do APK. São mecanismos diferentes e não intermutáveis: um `RandomAccessFile` opera sobre o sistema de ficheiros, e um asset só é acessível por `AssetManager.openFd()`, que devolve um descritor com deslocamento **dentro do zip do APK** (usar `FileInputStream(afd.fileDescriptor).channel` posicionado em `afd.startOffset + deslocamento`). Sem esta abstração, a pesquisa binária não consegue servir as duas origens
- [X] T011 Criar `app/src/main/java/com/mysky/app/data/local/RouteTableSource.kt`: decide entre o ficheiro de `filesDir` (se existir e validar) e o asset, devolvendo o `RouteTableReader` adequado. **Nunca copia o asset para `filesDir`** — seriam 7,6 MB de I/O no caminho do primeiro arranque, exatamente onde SC-005 é medido
- [X] T012 [P] Criar `app/src/main/java/com/mysky/app/domain/repository/RouteDirectory.kt` conforme [contracts/route-directory.md](./contracts/route-directory.md)
- [X] T013 Implementar `app/src/main/java/com/mysky/app/data/local/FileRouteDirectory.kt` com pesquisa binária sobre o `RouteTableReader` de T010 — nunca sobre um `RandomAccessFile` diretamente, para servir as duas origens sem duplicar a pesquisa. Nunca lança: ficheiro ausente ou inválido degrada para diretório vazio
- [X] T014 Acrescentar o binding de `RouteDirectory` em `app/src/main/java/com/mysky/app/di/RouteModule.kt` (novo), com `@IoDispatcher`
- [X] T015 Gate da fase: `./gradlew :app:testDebugUnitTest` com os 171 testes anteriores verdes e os novos a passar

**Checkpoint**: a tabela lê-se, e um ficheiro mau não parte nada.

---

## Phase 3: User Story 1 - Saber de onde vem e para onde vai o avião (Priority: P1)

**Goal**: os voos com rota conhecida mostram `LIS → CDG` na lista e no detalhe.

**Independent Test**: com a lista preenchida num local com tráfego comercial, verificar que os voos
de linha mostram as duas siglas e que o detalhe da mesma aeronave mostra exatamente as mesmas.

- [X] T016 [P] [US1] Acrescentar a `app/src/test/java/com/mysky/app/domain/usecase/ObserveSkyUseCaseTest.kt` os testes do enriquecimento: rota conhecida chega ao `OverheadFlight`, indicativo desconhecido deixa `route` a `null` sem falhar a operação, e uma falha do diretório de rotas não derruba a lista
- [X] T017 [P] [US1] Escrever `app/src/test/java/com/mysky/app/presentation/format/RouteFormattingTest.kt`: o par formata-se com o sentido inequívoco, e uma rota ausente não produz texto nenhum
- [X] T018 [US1] Acrescentar `route: Route? = null` a `app/src/main/java/com/mysky/app/domain/model/OverheadFlight.kt`, com KDoc a dizer que a ausência é o caso normal
- [X] T019 [US1] Criar `app/src/main/java/com/mysky/app/presentation/format/RouteFormatting.kt`, no pacote partilhado pelos dois ecrãs — a mesma razão de D3 da feature anterior: dois formatadores divergiriam no primeiro detalhe e ninguém daria por isso
- [X] T020 [US1] Ligar o diretório de rotas ao `app/src/main/java/com/mysky/app/domain/usecase/ObserveSkyUseCase.kt`, resolvendo **operador e rota em concorrência**, e as aeronaves entre si também (AD-015). Encadear duas pesquisas de disco por aeronave passa despercebido em teste e aparece como atraso no dispositivo
- [X] T021 [US1] Apresentar a rota em `app/src/main/java/com/mysky/app/presentation/main/FlightRow.kt`, omitida por completo quando `null`, sem espaço reservado
- [X] T022 [US1] Apresentar a rota em `app/src/main/java/com/mysky/app/presentation/detail/FlightDetailScreen.kt`, na secção de identificação. **Sem hora, estado ou qualquer palavra que a dê por confirmada** (FR-009): a rota é a agendada do número de voo, e o ecrã não pode sugerir que segue o voo em curso
- [X] T023 [US1] Escrever em `app/src/test/java/com/mysky/app/presentation/detail/FlightDetailFormattingParityTest.kt` o caso da rota: lista e detalhe produzem o mesmo texto para o mesmo voo (FR-002, SC-003)

**Checkpoint**: a feature é visível e útil.

---

## Phase 4: User Story 2 - Não ser enganado sobre a rota (Priority: P2)

**Goal**: a app só mostra a rota quando a sabe, e nunca a de outro voo.

**Independent Test**: alimentar a app com indicativos ausentes da tabela e verificar que nenhuma
sigla aparece — em vez de aparecer a rota de um indicativo vizinho.

- [X] T024 [P] [US2] Escrever em `app/src/test/java/com/mysky/app/data/local/FileRouteDirectoryTest.kt` o teste da invariante 2: um indicativo ausente da tabela devolve `null`, **nunca a rota do vizinho**. Numa pesquisa binária, um erro de comparação não devolve "não encontrado" — devolve a rota real de outro voo, com o mesmo ar de certeza que a correta. Cobrir chaves imediatamente antes do primeiro registo, imediatamente depois do último, e entre dois registos consecutivos
- [X] T025 [P] [US2] *(feito em `RouteTableCoverageTest.kt`, contra a tabela real de 584 832 rotas em vez de chaves sintéticas — um teste sobre uma tabela inventada teria as duas pontas iguais e não provaria nada)* Gerar chaves com a mesma normalização que o script usa e confirmar que `Route.callsignKeyOf` as encontra. Uma divergência aqui produz uma tabela inteira de rotas que nunca são encontradas, **sem erro em lado nenhum**
- [X] T026 [P] [US2] Acrescentar a `FileRouteDirectoryTest.kt` os casos de ausência legítima: indicativo nulo ou vazio, matrícula de aviação privada (`CS-DHA`, `N123AB`), indicativo com espaços e em minúsculas a encontrar a mesma rota
- [X] T027 [US2] Acrescentar a `FileRouteDirectoryTest.kt` a degradação: ficheiro ausente, truncado, com assinatura errada e com versão desconhecida produzem diretório vazio e a lista continua a funcionar sem rotas (FR-014)

**Checkpoint**: a app cala-se quando não sabe, em vez de inventar.

---

## Phase 5: User Story 3 - A app continua rápida e dentro do orçamento (Priority: P2)

**Goal**: a feature não custa nem um pedido a mais nem um milissegundo visível.

**Independent Test**: comparar pedidos ao serviço de voos e tempo até à primeira lista, antes e
depois da feature, no mesmo local e à mesma hora.

- [X] T028 [P] [US3] Escrever em `app/src/test/java/com/mysky/app/domain/usecase/ObserveSkyEnrichmentConcurrencyTest.kt` o teste de que operador e rota de várias aeronaves se resolvem em concorrência e não em cadeia: com resoluções travadas, o tempo total tem de ser o da mais lenta e não a soma
- [X] T029 [US3] Gate do armazenamento: `unzip -lv app/build/outputs/apk/debug/app-debug.apk | grep routes.bin` tem de mostrar **`Stored`**, nunca `Defl`. É o gate mais importante da feature e o único que apanha o `noCompress` em falta
- [X] T030 [US3] Confirmar por inspeção e por teste que nenhum caminho novo faz rede durante o ciclo de observação: a tabela é local, e `grep -rn "routes" app/src/main/java/com/mysky/app/data/source/` não pode devolver nada

**Checkpoint**: o orçamento e o arranque continuam iguais.

---

## Phase 6: User Story 4 - Atualizar a tabela de rotas (Priority: P3)

**Goal**: o utilizador pede a atualização nas definições, e uma falha nunca o deixa sem tabela.

**Independent Test**: com uma tabela antiga instalada, pedir a atualização e verificar que voos sem
rota passam a tê-la; e interromper a atualização, verificando que a tabela antiga fica.

- [X] T031 [P] [US4] Escrever `app/src/test/java/com/mysky/app/data/route/RouteTableReplaceTest.kt` com a interrupção nos quatro pontos — antes de escrever, a meio da escrita, depois de escrever e antes de validar, depois de validar e antes do `rename` — confirmando nos quatro que a tabela anterior fica íntegra e utilizável (FR-020, SC-008), e um quinto caso: uma tabela nova válida mas com muito menos registos é recusada (SC-009)
- [X] T032 [P] [US4] Escrever `app/src/test/java/com/mysky/app/data/route/RouteTableRepositoryImplTest.kt`: a tradução para `RouteUpdateState`, com uma variante de `RouteUpdateError` por causa distinguível, e o pedido duplicado a não enfileirar duas vezes
- [X] T033 [US4] Criar `app/src/main/java/com/mysky/app/domain/model/RouteUpdateState.kt` e `app/src/main/java/com/mysky/app/domain/repository/RouteTableRepository.kt` conforme [contracts/route-table-update.md](./contracts/route-table-update.md)
- [X] T034 [US4] Acrescentar a `app/src/main/java/com/mysky/app/data/local/RouteTableFile.kt` a substituição: escrita para `cacheDir`, validação completa, e `renameTo` para `filesDir` só no fim. É o `rename` atómico que dá FR-020 sem locks
- [X] T035 [US4] Acrescentar à validação a **regressão de cobertura** (SC-009): uma tabela nova com menos registos do que uma fração da anterior é recusada e a anterior fica. Um ficheiro truncado a montante passaria todas as outras verificações — assinatura, versão, tamanho múltiplo, contagem coerente — e degradaria a app em silêncio, que é a única forma de esta feature piorar sozinha
- [X] T036 [US4] Implementar `app/src/main/java/com/mysky/app/worker/RouteTableUpdateWorker.kt`: descarrega o ficheiro da release, delega a validação e a substituição em `RouteTableFile`
- [X] T037 [US4] Implementar `app/src/main/java/com/mysky/app/worker/RouteTableUpdateWorkScheduler.kt` com `OneTimeWorkRequest`, `NetworkType.CONNECTED` e `ExistingWorkPolicy.KEEP` — o **único** ponto que cria `WorkRequest`s deste tipo de trabalho (AD-016, princípio IV 1.1.0)
- [X] T038 [US4] Implementar `app/src/main/java/com/mysky/app/data/route/RouteTableRepositoryImpl.kt`, traduzindo `WorkInfo` para `RouteUpdateState` — é aqui que o Android para, e não no ViewModel
- [X] T039 [US4] Implementar `app/src/main/java/com/mysky/app/presentation/settings/SettingsUiState.kt` e `SettingsViewModel.kt`, com um único estado por ecrã e **sem importar `androidx.work.*`** (AD-017)
- [X] T040 [US4] Implementar `app/src/main/java/com/mysky/app/presentation/settings/SettingsScreen.kt`: data dos dados em uso, ação de atualizar, progresso e resultado, com uma mensagem por causa de falha
- [X] T041 [US4] Acrescentar os bindings de `RouteTableRepository` e do scheduler em `app/src/main/java/com/mysky/app/di/RouteModule.kt`
- [X] T042 [US4] Escrever em `RouteTableRepositoryImplTest.kt` o teste pela negativa de SC-010: uma sessão inteira sem o utilizador pedir nada não enfileira trabalho nenhum. É o que se estraga com um `init` bem-intencionado, sem dar erro

**Checkpoint**: todas as histórias completas.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T043 [P] Verificar que nenhum ficheiro em `app/src/main/java/com/mysky/app/domain/` importa `android.*`, Retrofit, Room, Compose ou WorkManager (princípio I): `grep -rE "^import (android|retrofit2|androidx\.room|androidx\.compose|androidx\.work)" app/src/main/java/com/mysky/app/domain/`
- [X] T044 [P] Verificar que `WorkRequest` só é **criado** nos schedulers dedicados (princípio IV, 1.1.0): `grep -rn "WorkRequestBuilder\|OneTimeWorkRequest\.\|PeriodicWorkRequest\." app/src/main/java/com/mysky/app/ | grep -vE ":\s*(\*|//)" | grep -v WorkScheduler` tem de não devolver nada. Procurar a **construção** e excluir comentários é o que torna o gate decidível à máquina: a palavra `WorkRequest` aparece em KDoc de três ficheiros que não agendam nada
- [X] T045 [P] Verificar que o `SettingsViewModel` não conhece o WorkManager (AD-017): `grep -rn "androidx.work" app/src/main/java/com/mysky/app/presentation/` tem de não devolver nada
- [X] T046 Correr a suite completa e o lint: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` — zero erros de lint, todos os testes verdes
- [X] T047 Executar a validação manual de [quickstart.md](./quickstart.md) secções 6 a 9, com atenção aos passos 10 (matar a app a meio de uma atualização) e 12 (nenhuma transferência sem pedido), e à contagem de pedidos e ao tempo de arranque
- [X] T048 Invocar o subagente `reviewer` sobre a feature completa e tratar os achados críticos e os "deveria corrigir" (princípio VI)
- [ ] T049 Atualizar a secção "Estado atual" do `CLAUDE.md` para refletir a feature concluída e apontar a seguinte

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Fase 1)**: sem dependências. T001 a T003 são sequenciais entre si — sem script não há ficheiro, sem ficheiro não há o que marcar `noCompress`
- **Foundational (Fase 2)**: depende da Fase 1 e **bloqueia tudo**
- **US1 (Fase 3)**: depende da Fase 2
- **US2 (Fase 4)**: depende da Fase 2. Independente da US1 — testa a leitura, não a apresentação
- **US3 (Fase 5)**: depende da US1, porque mede o custo do enriquecimento a funcionar
- **US4 (Fase 6)**: depende da Fase 2 (partilha `RouteTableFile`). Independente de US1
- **Polish (Fase 7)**: depende de todas

### O caminho crítico

T002 → T003 → T004 → T009 → T013 → T020 → T021. Sem a tabela gerada e legível não há nada.

---

## Parallel Example: Fase 2

```bash
# Os dois ficheiros de teste, antes de existir código nenhum:
T006 RouteTest.kt  ·  T007 RouteTableFileTest.kt
```

E na Fase 4, as quatro tarefas de teste da US2 tocam em dois ficheiros e podem correr juntas. Fora
disso o paralelismo é escasso: `FileRouteDirectory.kt` e `RouteTableFile.kt` concentram o trabalho
da leitura, e o ecrã de definições concentra o da US4.

---

## Implementation Strategy

**MVP**: Fase 1 + Fase 2 + US1 + US2. As duas histórias andam juntas porque a US2 é o que impede a
US1 de mentir — entregar a apresentação sem os testes de exatidão seria pôr no ecrã uma informação
que o utilizador não tem como verificar, apoiada em nada.

**A US4 é separável e pode ficar para depois.** É P3, é metade do trabalho da feature, e a app
funciona sem ela — com uma tabela que envelhece. Se for preciso cortar âmbito, é aqui que se corta.

**A Fase 1 é indivisível.** Sem `noCompress`, tudo o que vier a seguir é construído sobre uma
leitura que não funciona como se pensa.

---

## Notes

- `[P]` = ficheiros diferentes, sem dependências por concluir
- Confirmar que cada teste falha antes de implementar
- Commit por tarefa ou por grupo lógico
- Os 171 testes anteriores correm-se ao fim de cada tarefa da Fase 2, não só no fim
- **SC-001 (70% de cobertura) só se verifica em campo**, com voos reais. Se ficar abaixo, a decisão
  a rever é a D3 (rotas com escalas, +5,9%), não a implementação a forçar
- **SC-002 e SC-006** não têm tarefa própria de implementação: saem de o `Route` não poder existir
  com um lado só e de a apresentação omitir por completo o que é `null`
