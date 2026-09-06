---
description: "Task list for feature 001-sky-list"
---

# Tasks: Lista de aviões no meu céu

**Input**: Design documents from `/specs/001-sky-list/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: **Incluídos e obrigatórios.** O princípio VI da constituição exige testes unitários em
todos os casos de uso do `domain`, incluindo os casos-limite geométricos, e o
[quickstart.md](./quickstart.md) define critérios de saída baseados em testes. Não é opção.

**Organization**: Tarefas agrupadas por história de utilizador. Cada história é implementável e
testável de forma independente depois da fase Foundational.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode correr em paralelo (ficheiros diferentes, sem dependências por concluir)
- **[Story]**: US1 a US4, conforme [spec.md](./spec.md)
- Caminhos de ficheiro sempre explícitos

## Path Conventions

Módulo Gradle único `:app`, Clean Architecture em três camadas.

- Código: `app/src/main/java/com/mysky/app/`
- Recursos: `app/src/main/res/`, assets em `app/src/main/assets/`
- Testes JVM: `app/src/test/java/com/mysky/app/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: recursos e utilitários partilhados que não existem ainda no projeto

- [ ] T001 Descarregar `airlines.dat` do **OpenFlights** (`https://raw.githubusercontent.com/jpatokal/openflights/master/data/airlines.dat`), publicado sob **Open Database License (ODbL)** — permite a redistribuição dentro do APK desde que a atribuição seja preservada. Guardar o texto de atribuição em `app/src/main/assets/airlines-LICENSE.txt`, que viaja no APK ao lado da tabela. **O ICAO Doc 8585 foi descartado por licença**, não por qualidade: é publicação paga da ICAO e o seu conteúdo não é redistribuível (ver D1 em [research.md](./research.md))
- [ ] T002 Converter `airlines.dat` para `app/src/main/assets/airlines.json` com um script guardado em `tools/airlines/` (a conversão tem de ser reproduzível: a tabela é manutenção periódica, não um ficheiro escrito à mão). Formato de saída `{"TAP": "TAP Air Portugal", "RYR": "Ryanair", ...}` — chave é o campo **ICAO** em maiúsculas, valor é o campo **Name** (FR-011). O `airlines.dat` é CSV com aspas e as colunas `Airline ID, Name, Alias, IATA, ICAO, Callsign, Country, Active`; guardar **apenas** os registos cujo ICAO tenha exactamente 3 letras A–Z (descarta `\N`, `"-"`, `"N/A"` e vazios), e em ICAO repetido preferir a linha com `Active == "Y"`, senão a primeira. **Não filtrar por `Active`**: o dataset está desatualizado e marcar como inativa uma companhia que ainda voa custaria cobertura em SC-004, enquanto um código extinto apenas nunca aparece. Registar no KDoc de `AssetAirlineDirectory` a origem, o commit do `airlines.dat` usado, a data de extração e o número de registos obtidos (depende de T001)
- [ ] T003 [P] Acrescentar as strings dos seis estados do ecrã e das unidades em `app/src/main/res/values/strings.xml` (céu vazio, sem ligação, serviço indisponível, limite de pedidos, sem posição, sem permissão, recusa permanente, dados possivelmente desatualizados, "mesmo por cima", "há X s"/"há X min")
- [ ] T004 [P] Criar construtores de teste partilhados em `app/src/test/java/com/mysky/app/TestFixtures.kt` (fábricas de `Aircraft`, `OverheadFlight` e `GeoPosition` com valores por omissão sensatos), para os testes não repetirem literais

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: modelos de domínio, portas e pipeline de dados que **todas** as histórias usam

**⚠️ CRITICAL**: nenhuma história pode começar antes desta fase estar completa

### Tests (escrever primeiro, confirmar que falham)

- [ ] T005 [P] Testes de `Airline.icaoPrefixOf` em `app/src/test/java/com/mysky/app/domain/model/AirlineTest.kt`: prefixo válido, indicativo com menos de 4 caracteres, prefixo com dígitos (matrícula `CS-DHA`, `N123AB`), indicativo nulo, indicativo só com espaços
- [ ] T006 [P] Testes de contrato do vetor de estado em `app/src/test/java/com/mysky/app/data/OpenSkyStateVectorTest.kt`: array completo, array mais curto do que o esperado, `null` em cada posição opcional, campos extra no fim, indicativo com espaços à direita, e **a ordem longitude(5)/latitude(6)** conforme [contracts/opensky-states-all.md](./contracts/opensky-states-all.md)
- [ ] T007 [P] Testes do mapeamento DTO → domínio em `app/src/test/java/com/mysky/app/data/OpenSkyMapperTest.kt`: coordenadas fora de intervalo descartam o registo sem lançar, `icao24` em branco descarta, indicativo só com espaços vira `null`, altitude geométrica preferida à barométrica
- [ ] T008 [P] Testes do repositório em `app/src/test/java/com/mysky/app/data/FlightRepositoryImplTest.kt`: deduplicação por `icao24` entre duas caixas ficando com o vetor mais recente, falha de uma caixa faz falhar o todo, e cada erro de rede traduz-se na variante certa de `SkyError` (429 → `RateLimited` com `retryAfter`, 5xx → `FlightServiceUnavailable`, `IOException` → `NoConnection`)
- [ ] T009 [P] Testes do diretório de operadores em `app/src/test/java/com/mysky/app/data/AssetAirlineDirectoryTest.kt`, com o `AssetManager` **mockado com MockK** — `context.assets.open(...)` devolve um `ByteArrayInputStream` com JSON de teste, ou lança `IOException` no cenário de asset em falta. Não há Robolectric no catálogo e `testOptions.unitTests.isReturnDefaultValues = true` faria uma leitura real devolver vazio em silêncio, dando um teste verde que não testa nada. Casos: prefixo conhecido, prefixo válido ausente da tabela, matrícula privada, indicativo nulo (FR-012), asset em falta degrada para diretório vazio sem lançar, e carregamento concorrente não duplica trabalho
- [ ] T010 [P] Testes do caso de uso em `app/src/test/java/com/mysky/app/domain/usecase/ObserveSkyUseCaseTest.kt`: resultado ordenado por elevação decrescente (FR-009), operador resolvido, falha do `AirlineDirectory` não falha a operação, falha do repositório propaga o mesmo `SkyError`, o instante vem do `TimeProvider` falso, e um observador junto ao antimeridiano gera **duas** caixas envolventes sem produzir aeronaves duplicadas (princípio VI da constituição)

### Domínio

- [ ] T011 [P] Criar `Airline` com `icaoCode`, `name` e a regra `icaoPrefixOf(callsign)` em `app/src/main/java/com/mysky/app/domain/model/Airline.kt`
- [ ] T012 [P] Criar a hierarquia selada `SkyError` (`NoConnection`, `FlightServiceUnavailable`, `RateLimited`, `LocationUnavailable`, `Unexpected`) em `app/src/main/java/com/mysky/app/domain/model/SkyError.kt`, estendendo `Exception` com `fillInStackTrace` anulada conforme AD-010
- [ ] T013 [P] Criar a interface `TimeProvider` em `app/src/main/java/com/mysky/app/domain/time/TimeProvider.kt`
- [ ] T014 [P] Criar a porta `AirlineDirectory` em `app/src/main/java/com/mysky/app/domain/repository/AirlineDirectory.kt`, com as pós-condições de [contracts/internal-ports.md](./contracts/internal-ports.md)
- [ ] T015 Acrescentar `airline: Airline?` a `OverheadFlight` em `app/src/main/java/com/mysky/app/domain/model/OverheadFlight.kt`, com omissão `null` para não quebrar `DetectOverheadFlightsUseCase` nem os seus testes (depende de T011)

### Dados

- [ ] T016 [P] Implementar `OpenSkyStateVector.parse` em `app/src/main/java/com/mysky/app/data/source/opensky/OpenSkyStateVector.kt`, tolerando arrays curtos, `null` em qualquer posição opcional e campos extra (faz T006 passar)
- [ ] T017 Implementar `OpenSkyStateVectorDto.toDomain()` em `app/src/main/java/com/mysky/app/data/mapper/OpenSkyMapper.kt`, com trim do indicativo e filtragem de coordenadas inválidas **antes** de construir `GeoPosition` (depende de T016; faz T007 passar)
- [ ] T018 Implementar `OpenSkyFlightDataSource.fetchAircraftIn` em `app/src/main/java/com/mysky/app/data/source/opensky/OpenSkyFlightDataSource.kt`, tratando `states: null` como lista vazia e descartando em silêncio os registos que não convertem (depende de T017)
- [ ] T019 [P] Implementar `AssetAirlineDirectory` em `app/src/main/java/com/mysky/app/data/local/AssetAirlineDirectory.kt`: carregamento único e preguiçoso do asset para um `Map` em memória, seguro para chamadas concorrentes, degradando para vazio se o asset faltar (depende de T014; faz T009 passar)
- [ ] T020 [P] Testar a cobertura da tabela em `app/src/test/java/com/mysky/app/data/AirlineTableCoverageTest.kt`, lendo o ficheiro **directamente do disco** com `File("src/main/assets/airlines.json")` — o working directory dos testes unitários é a pasta do módulo, e o asset não está no classpath de um teste JVM (é por isso que T009 mocka o `AssetManager` em vez de ler o ficheiro real). Verificar: o ficheiro carrega e faz parse, tem pelo menos **1000** registos, todas as chaves são 3 letras maiúsculas, nenhum nome é vazio, e uma lista fixa de operadores frequentes no espaço aéreo português (TAP, RYR, EZY, AFR, DLH, BAW, IBE, VLG, UAE, KLM) está presente — falha cedo se a extração saiu pobre, em vez de só se dar por isso no SC-004 (depende de T002)
- [ ] T021 [P] Criar `SystemTimeProvider` em `app/src/main/java/com/mysky/app/data/time/SystemTimeProvider.kt` (depende de T013)
- [ ] T022 Implementar `FlightRepositoryImpl.getAircraftIn` em `app/src/main/java/com/mysky/app/data/repository/FlightRepositoryImpl.kt`: caixas pedidas em paralelo, deduplicação por `icao24` ficando com o `lastContactEpochSeconds` mais recente, falha parcial faz falhar o todo, e tradução de exceções para `SkyError` (depende de T012, T018; faz T008 passar)
- [ ] T023 Implementar `hasLocationPermission` e `getCurrentLocation` em `app/src/main/java/com/mysky/app/data/repository/LocationRepositoryImpl.kt` usando o Fused Location Provider com `PRIORITY_BALANCED_POWER_ACCURACY` e recurso a `lastLocation`; devolver `null` sem lançar `SecurityException` quando falta permissão. Deixar `locationUpdates()` como `TODO` documentado — não é usado nesta feature (ver [contracts/internal-ports.md](./contracts/internal-ports.md))

### Caso de uso e injeção

- [ ] T024 Implementar `ObserveSkyUseCase` em `app/src/main/java/com/mysky/app/domain/usecase/ObserveSkyUseCase.kt` com a assinatura de AD-009 (sem `SettingsRepository`, `criteria` por parâmetro): calcular caixas → pedir ao repositório → aplicar `DetectOverheadFlightsUseCase` com `timeProvider.nowEpochSeconds()` (FR-007, FR-008) → resolver operadores sem deixar que uma falha do diretório falhe a operação (depende de T015, T019, T021, T022; faz T010 passar)
- [ ] T025 Ligar os bindings novos em `app/src/main/java/com/mysky/app/di/RepositoryModule.kt`: `AirlineDirectory` → `AssetAirlineDirectory` e `TimeProvider` → `SystemTimeProvider` (depende de T019, T021)
- [ ] T026 Confirmar que `ObserveSkyUseCase` já não injeta `SettingsRepository`: `grep -n "SettingsRepository" app/src/main/java/com/mysky/app/domain/usecase/ObserveSkyUseCase.kt` não devolve nada. **Compilar não prova isto** — o binding continua a existir em `RepositoryModule` para a feature de definições, e o stub só rebenta quando alguém lhe chama um método. Depois validar o grafo do Hilt com `./gradlew :app:assembleDebug`. O `MainViewModel` larga a mesma dependência em T032 (depende de T024, T025)

**Checkpoint**: pipeline completo e testado. `./gradlew :app:testDebugUnitTest` verde. As histórias podem começar.

---

## Phase 3: User Story 1 - Ver o que está no meu céu agora (Priority: P1) 🎯 MVP

**Goal**: com a permissão concedida, o utilizador abre a app e vê a lista de aeronaves no seu céu,
ordenada da mais alta para a mais baixa, com indicativo, operador, altitude, velocidade e distância.

**Independent Test**: instalar, abrir, conceder a localização e confirmar que a lista aparece
coerente com o céu real. Entrega valor sem qualquer outra história implementada.

### Tests for User Story 1

- [ ] T027 [P] [US1] Testes de formatação em `app/src/test/java/com/mysky/app/presentation/main/FlightFormattingTest.kt`: distância com uma casa decimal, altitude e velocidade inteiras, conversão m/s → km/h, os 16 rumos cardinais, e elevação ≥ 85° a produzir "mesmo por cima" em vez de rumo
- [ ] T028 [P] [US1] Testes de carga inicial do ViewModel em `app/src/test/java/com/mysky/app/presentation/main/MainViewModelTest.kt` com `runTest` e Turbine: fase passa por `LocatingUser` e `LoadingFlights` antes de `Idle`, sucesso preenche `flights` e `lastUpdatedEpochSeconds`, e a lista chega ordenada por elevação

- [ ] T029 [P] [US1] Teste de mudança de configuração em `app/src/test/java/com/mysky/app/presentation/main/MainViewModelConfigChangeTest.kt`: uma nova subscrição dentro da janela de 5 s reaproveita o estado (`flights`, `lastUpdatedEpochSeconds`, `lastError` preservados) e **não** dispara pedido novo — invariante 9 de [contracts/main-screen-ui.md](./contracts/main-screen-ui.md) (FR-026)

### Implementation for User Story 1

- [ ] T030 [P] [US1] Reescrever `MainUiState` em `app/src/main/java/com/mysky/app/presentation/main/MainUiState.kt` com `permission`, `phase`, `flights`, `lastUpdatedEpochSeconds` e `lastError`, mais os estados derivados (céu vazio, dados desatualizados, primeira carga) definidos em [contracts/main-screen-ui.md](./contracts/main-screen-ui.md)
- [ ] T031 [P] [US1] Criar as funções de formatação em `app/src/main/java/com/mysky/app/presentation/main/format/FlightFormatting.kt` (distância, altitude, velocidade, elevação, rumo em 16 pontos, tempo relativo), cada grandeza com a sua unidade e fixando km/m/km-h nesta feature (FR-010, FR-015) (faz T027 passar)
- [ ] T032 [US1] Implementar em `app/src/main/java/com/mysky/app/presentation/main/MainViewModel.kt` a carga única, só depois de a permissão estar concedida (FR-002): obter posição via `LocationRepository` — uma posição `null` com a permissão concedida traduz-se em `lastError = SkyError.LocationUnavailable` e **não** chega a chamar o caso de uso (FR-024; é o único sítio onde esta variante nasce, porque o repositório de localização devolve `null` em vez de falhar) —, chamar `ObserveSkyUseCase` com `OverheadCriteria()` e emitir o estado. Remover do construtor a dependência de `SettingsRepository` (AD-009). Expor `uiState` com `stateIn(viewModelScope, WhileSubscribed(5_000), MainUiState())` conforme AD-008 (depende de T030; faz T028 passar)
- [ ] T033 [P] [US1] Criar o pedido de permissão com rationale prévio em `app/src/main/java/com/mysky/app/presentation/permission/LocationPermission.kt`, usando `accompanist-permissions` e a string `permission_location_rationale`; o rationale aparece **antes** do diálogo do sistema (FR-001) e pede apenas localização aproximada (FR-003)
- [ ] T034 [P] [US1] Criar a entrada da lista em `app/src/main/java/com/mysky/app/presentation/main/FlightRow.kt`, com omissão discreta dos campos ausentes e, quando não há indicativo, identificação pelo `icao24` em maiúsculas — a única exceção à regra de omissão (FR-013, FR-014) (depende de T031)
- [ ] T035 [US1] Implementar o corpo do ecrã em `app/src/main/java/com/mysky/app/presentation/main/MainScreen.kt`: `LazyColumn` de `FlightRow` com **`key` estável = `aircraft.icao24`** — sem ela, a aeronave que sai do céu entre duas atualizações reordena a lista com salto visual e um toque pode cair na entrada errada (edge case da [spec.md](./spec.md)) —, indicador de progresso com texto distinto para `LocatingUser` e `LoadingFlights` (FR-022), e ligação ao fluxo de permissão (depende de T032, T033, T034)

**Checkpoint**: US1 funcional e demonstrável de forma independente.

---

## Phase 4: User Story 2 - Confiar que estou a ver o céu de agora (Priority: P2)

**Goal**: a lista renova-se sozinha de 30 em 30 segundos com o ecrã visível, mostra quando foi
atualizada, pode ser forçada com um gesto, e para completamente quando o ecrã deixa de estar visível.

**Independent Test**: deixar o ecrã aberto e ver a lista mudar sozinha; puxar para atualizar e ver a
marca temporal avançar; mandar para segundo plano e confirmar no Network Inspector que não há mais pedidos.

### Tests for User Story 2

- [ ] T036 [P] [US2] Testes do laço em tempo virtual em `app/src/test/java/com/mysky/app/presentation/main/MainViewModelRefreshLoopTest.kt`: atualiza a cada 30 s; refresh manual reinicia o relógio; refresh manual durante um automático não gera pedido concorrente (FR-020); sem subscritores o laço para em 5 s e não há mais chamadas ao `LocationRepository` nem ao `FlightRepository` (FR-019)

### Implementation for User Story 2

- [ ] T037 [US2] Converter a carga única num laço sequencial em `app/src/main/java/com/mysky/app/presentation/main/MainViewModel.kt`: cada iteração atualiza e depois espera pelo tick de 30 s (FR-016) **ou** por um pedido manual vindo de um `MutableSharedFlow` com `DROP_OLDEST`, o que vier primeiro (AD-008) (depende de T032; faz T036 passar)
- [ ] T038 [P] [US2] Acrescentar a formatação de tempo relativo ("há 12 s", "há 2 min") em `app/src/main/java/com/mysky/app/presentation/main/format/FlightFormatting.kt` (depende de T031)
- [ ] T039 [US2] Acrescentar puxar-para-atualizar e a marca temporal de última atualização em `app/src/main/java/com/mysky/app/presentation/main/MainScreen.kt` (FR-017, FR-018), ligando o gesto a `onManualRefresh` (depende de T035, T037, T038)

**Checkpoint**: US1 e US2 funcionam de forma independente.

---

## Phase 5: User Story 3 - Perceber sempre o que se passa quando não há lista (Priority: P3)

**Goal**: céu vazio, falha de rede, falha do serviço, ausência de posição, permissão recusada e
recusa permanente produzem cada um uma mensagem distinta com a ação certa; uma falha nunca apaga
resultados que já estavam no ecrã.

**Independent Test**: forçar cada condição (modo de avião, permissão recusada, zona sem tráfego) e
confirmar seis ecrãs distintos, cada um com a sua ação seguinte.

### Tests for User Story 3

- [ ] T040 [P] [US3] Testes do mapeamento erro → mensagem em `app/src/test/java/com/mysky/app/presentation/main/SkyErrorMessagesTest.kt`: cada variante de `SkyError` produz uma string distinta e todas oferecem repetição (FR-024)
- [ ] T041 [P] [US3] Testes dos estados em `app/src/test/java/com/mysky/app/presentation/main/MainViewModelStatesTest.kt`: falha não limpa `flights` nem `lastUpdatedEpochSeconds` (FR-025); sucesso limpa `lastError`; posição `null` com a permissão concedida produz `SkyError.LocationUnavailable`, distinta de `NoConnection` (FR-024); `RateLimited(retryAfter)` alonga a espera seguinte para `max(30 s, retryAfter)` sem reintentar de imediato (FR-021); permissão revogada com o ecrã aberto passa a `Denied` ao voltar (FR-006); `phase` nunca fica preso em `LoadingFlights`

### Implementation for User Story 3

- [ ] T042 [P] [US3] Criar o mapeamento de `SkyError` para recurso de string em `app/src/main/java/com/mysky/app/presentation/main/SkyErrorMessages.kt` (faz T040 passar)
- [ ] T043 [US3] Tratar no `MainViewModel` a reavaliação da permissão em `onScreenVisible`, os estados `Denied`/`PermanentlyDenied`, e o alongamento da espera em `RateLimited`, em `app/src/main/java/com/mysky/app/presentation/main/MainViewModel.kt` (depende de T037; faz T041 passar)
- [ ] T044 [US3] Implementar a renderização dos estados em `app/src/main/java/com/mysky/app/presentation/main/MainScreen.kt`, seguindo exatamente a tabela de precedência de [contracts/main-screen-ui.md](./contracts/main-screen-ui.md), incluindo o céu vazio sem aspeto de erro (FR-023) — a permissão ganha a tudo, e ter resultados antigos ganha ao erro (depende de T039, T042, T043)
- [ ] T045 [US3] Acrescentar o encaminhamento para as definições do sistema no caso de recusa permanente, em `app/src/main/java/com/mysky/app/presentation/permission/LocationPermission.kt` (FR-005) (depende de T033)

**Checkpoint**: as três histórias funcionam de forma independente.

---

## Phase 6: User Story 4 - Aprofundar um avião que me interessa (Priority: P3)

**Goal**: tocar numa entrada abre o ecrã de detalhe identificado com aquela aeronave.

**Independent Test**: tocar numa entrada e confirmar que o detalhe abre com o `icao24` certo, e que
voltar atrás regressa à lista.

### Tests for User Story 4

- [ ] T046 [P] [US4] Teste de que o argumento de rota chega ao destino em `app/src/test/java/com/mysky/app/presentation/detail/FlightDetailViewModelTest.kt`, usando um `SavedStateHandle` com `icao24`

### Implementation for User Story 4

- [ ] T047 [US4] Declarar o argumento tipado `icao24` na rota de detalhe (FR-028) em `app/src/main/java/com/mysky/app/presentation/navigation/MySkyNavHost.kt` (faz T046 passar)
- [ ] T048 [US4] Ligar o toque na entrada a `onFlightClick(icao24)` (FR-027) em `app/src/main/java/com/mysky/app/presentation/main/FlightRow.kt` e `MainScreen.kt` (depende de T034, T035, T047)

**Checkpoint**: todas as histórias completas.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T049 [P] Limpar os marcadores `TODO(feature/sky-list)` que ficaram resolvidos em todos os ficheiros tocados, e confirmar que nenhum sobra fora do âmbito de features futuras
- [ ] T050 [P] Verificar que nenhum ficheiro em `app/src/main/java/com/mysky/app/domain/` importa `android.*`, Retrofit, Room, Compose ou WorkManager (princípio I): `grep -rE "^import (android|retrofit2|androidx\.room|androidx\.compose|androidx\.work)" app/src/main/java/com/mysky/app/domain/`
- [ ] T051 [P] Verificar que a feature nunca pede `ACCESS_BACKGROUND_LOCATION` em runtime (FR-004, princípio III): `grep -rn "ACCESS_BACKGROUND_LOCATION" app/src/main/java/` tem de não devolver nada — a permissão continua declarada no manifesto para features futuras, mas declarar não é pedir
- [ ] T052 Correr a suite completa e o lint: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` — zero erros de lint, todos os testes verdes (cobre SC-003, SC-006 e SC-007 na parte automatizada)
- [ ] T053 Executar a validação manual de [quickstart.md](./quickstart.md) secções 4 a 6 e preencher os critérios de saída: os seis estados (SC-005), tempo até à primeira lista abaixo de 5 s (SC-001), cobertura da tabela de operadores em 100 entradas reais (SC-004), ausência de rede em segundo plano (SC-007), e a verificação contra o céu real (SC-002, SC-003)
- [ ] T054 Invocar o subagente `reviewer` sobre a feature completa e tratar os achados críticos e os "deveria corrigir" (exigência da constituição, princípio VI)
- [ ] T055 Atualizar a secção "Estado atual" do `CLAUDE.md` para refletir a feature concluída e apontar a seguinte

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Fase 1)**: sem dependências
- **Foundational (Fase 2)**: depende do Setup — **bloqueia todas as histórias**
- **US1 (Fase 3)**: depende da Foundational
- **US2 (Fase 4)**: depende da Foundational; na prática estende o `MainViewModel` de US1
- **US3 (Fase 5)**: depende da Foundational; estende o laço de US2 para o caso `RateLimited`
- **US4 (Fase 6)**: depende da Foundational; toca em ficheiros de US1/US3
- **Polish (Fase 7)**: depende de todas as histórias desejadas

### User Story Dependencies

As histórias são **independentemente testáveis**, mas não totalmente independentes na escrita:
`MainViewModel.kt` e `MainScreen.kt` são tocados por US1, US2 e US3. Trabalhá-las em paralelo por
pessoas diferentes garante conflitos nesses dois ficheiros. Ordem sequencial recomendada:
US1 → US2 → US3 → US4.

US4 é a única genuinamente paralelizável com as outras: depende apenas de US1 (a entrada da lista
existe em T034/T035) e da Foundational, nunca de US2 nem de US3.

### Within Each User Story

- Testes primeiro, a falhar, depois implementação
- Modelos → dados → caso de uso → estado → UI
- História completa antes de passar à seguinte

### Parallel Opportunities

- **Fase 1**: T001 → T002 em cadeia (descarregar antes de converter); T003 e T004 em paralelo com ambas
- **Fase 2, testes**: T005 a T010 todos em paralelo — seis ficheiros de teste distintos
- **Fase 2, domínio**: T011, T012, T013, T014 em paralelo; T015 depende de T011
- **Fase 2, dados**: T019, T020 e T021 em paralelo com a cadeia T016 → T017 → T018 → T022
- **Fase 3**: T027, T028 e T029 em paralelo; depois T030, T031 e T033 em paralelo
- **Fase 5**: T040 e T041 em paralelo; T042 e T045 em paralelo
- **Fase 7**: T049, T050 e T051 em paralelo

---

## Parallel Example: Phase 2 (Foundational)

```bash
# Os seis ficheiros de teste, todos ao mesmo tempo:
T005 AirlineTest.kt · T006 OpenSkyStateVectorTest.kt · T007 OpenSkyMapperTest.kt
T008 FlightRepositoryImplTest.kt · T009 AssetAirlineDirectoryTest.kt · T010 ObserveSkyUseCaseTest.kt

# Depois, os quatro ficheiros novos de domínio:
T011 Airline.kt · T012 SkyError.kt · T013 TimeProvider.kt · T014 AirlineDirectory.kt
```

---

## Implementation Strategy

### MVP First (US1)

1. Fase 1 (Setup) — 4 tarefas
2. Fase 2 (Foundational) — 22 tarefas, **crítica**: bloqueia tudo
3. Fase 3 (US1) — 9 tarefas
4. **PARAR E VALIDAR**: quickstart secções 1 a 4, com o céu real
5. É demonstrável: a app mostra os aviões que estão por cima

O MVP são **35 tarefas** (T001–T035). A Foundational é dois terços do esforço porque constrói o pipeline
inteiro — que depois o widget e as notificações reutilizam sem escrever nada de novo.

### Incremental Delivery

1. Setup + Foundational → pipeline pronto e testado
2. + US1 → **MVP demonstrável**
3. + US2 → deixa de ser uma fotografia e passa a ser o céu de agora
4. + US3 → deixa de parecer avariada quando não há nada para mostrar
5. + US4 → fecha o ciclo de interação

### Nota sobre paralelismo com equipa

Só a Fase 2 tem paralelismo real (seis testes e quatro modelos em ficheiros distintos). Nas
histórias, `MainViewModel.kt` e `MainScreen.kt` concentram o trabalho de US1, US2 e US3 — dividir
por pessoas custa mais em conflitos do que poupa em tempo.

---

## Notes

- `[P]` = ficheiros diferentes, sem dependências por concluir
- Confirmar que cada teste falha antes de implementar
- Commit por tarefa ou por grupo lógico
- Parar em qualquer checkpoint para validar a história isoladamente
- A ordem longitude(5)/latitude(6) da fonte é a troca mais fácil de fazer sem dar por ela — T006 existe para a apanhar
- **SC-008** (orçamento diário de pedidos) não tem tarefa própria: é consequência aritmética do
  intervalo de 30 s fixado em FR-016. Se o intervalo mudar, passa a precisar de verificação
- **SC-009** (9 em 10 utilizadores percebem o ecrã) é métrica de usabilidade pós-lançamento, não
  trabalho construível — fica fora do âmbito de tasks.md por desenho
