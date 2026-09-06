# Implementation Plan: Lista de aviões no meu céu

**Branch**: `001-sky-list` | **Date**: 2026-09-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-sky-list/spec.md`

## Summary

Primeiro código real da mySky: o ecrã principal obtém a posição do utilizador, pede à fonte de
voos as aeronaves na zona em redor, filtra as que estão efetivamente no céu (raio + ângulo de
elevação) e apresenta-as ordenadas da mais alta para a mais baixa, com o operador aéreo resolvido
localmente a partir do indicativo.

A abordagem técnica assenta em quatro decisões tomadas nesta fase (AD-007 a AD-010, registadas no
`CLAUDE.md`): a tabela de operadores é uma segunda porta de dados, distinta do `FlightDataSource`;
a atualização periódica vive no ViewModel como um único laço sequencial ligado ao ciclo de vida do
ecrã, não no WorkManager; o `ObserveSkyUseCase` deixa de depender do `SettingsRepository` e recebe
os critérios como parâmetro; e os erros passam a ser um tipo de domínio (`SkyError`) traduzido na
fronteira do repositório, com o rate limiting a propagar-se até à UI em vez de ser reintentado em
silêncio.

## Technical Context

**Language/Version**: Kotlin 2.3.21, JVM target 17

**Primary Dependencies**: Jetpack Compose (BOM 2025.12.01, Material 3), Hilt 2.58, Retrofit 3.0.0
com Kotlinx Serialization 1.11.0, Coroutines 1.11.0, Play Services Location 21.3.0,
Lifecycle 2.9.4 (`collectAsStateWithLifecycle`)

**Storage**: Nenhuma persistência nesta feature. Apenas leitura de `assets/` (tabela de operadores
aéreos e a respetiva nota de atribuição ODbL). Room e DataStore existem no projeto mas não são tocados aqui.

**Testing**: JUnit 4, MockK, Turbine, kotlinx-coroutines-test (tempo virtual para o laço de
atualização)

**Target Platform**: Android 8.0 (API 26) a Android 16 (API 36)

**Project Type**: Aplicação Android nativa, módulo Gradle único (`:app`), Clean Architecture em
três camadas

**Performance Goals**: lista visível em menos de 5 s a partir de arranque a frio com permissão já
concedida (SC-001); atualização automática a cada 30 s; toda a atividade de rede e localização
cessa nos 60 s após o ecrã deixar de estar visível (SC-007)

**Constraints**: orçamento diário gratuito da fonte de voos em uso anónimo (SC-008); o `domain`
não pode importar `android.*`, Retrofit, Room, Compose ou WorkManager; nenhum pedido de rede para
resolver o operador aéreo (FR-011)

**Scale/Scope**: um ecrã com seis estados distintos, ~1500 operadores na tabela local, tipicamente
dezenas de aeronaves por consulta e poucas unidades a passar o filtro de elevação

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Princípio | Gate | Estado |
|---|-----------|------|--------|
| I | Domínio isolado e testável sem rede | Nenhum ficheiro novo em `domain/` importa `android.*`, Retrofit, Room, Compose ou WorkManager; o relógio entra por abstração (`TimeProvider`), não é lido no domínio | **PASS** — ver AD-009 |
| II | Fontes de dados atrás de uma interface | Nenhuma classe acima de `data/source` conhece a OpenSky; a tabela de operadores entra por uma porta própria (`AirlineDirectory`), não por dentro do `FlightDataSource` | **PASS** — ver AD-007 |
| III | Localização com consentimento informado | Rationale mostrado antes do diálogo do sistema (FR-001); apenas localização aproximada (FR-003); `ACCESS_BACKGROUND_LOCATION` não é pedida nesta feature (FR-004); recusa tem sempre saída (FR-005) | **PASS** |
| IV | Respeitar os limites da plataforma e a bateria | Sem WorkManager nesta feature (é trabalho em primeiro plano); atualização cessa com o ecrã (FR-019); 429 espaça pedidos em vez de insistir (FR-021); marca temporal sempre visível (FR-018) | **PASS** — ver AD-008 |
| V | Decisões de arquitetura registadas | AD-007 a AD-010 escritas no `CLAUDE.md` nesta fase, antes de haver código | **PASS** |
| VI | Testar o que pode falhar em silêncio | `GeoCalculator` e `DetectOverheadFlightsUseCase` já cobertos; acrescentam-se testes para `ObserveSkyUseCase`, para o mapeamento de erros no repositório, para o parsing do vetor de estado e para o laço de atualização em tempo virtual | **PASS** |

### Re-avaliação pós-desenho (Fase 1)

Refeita depois de `research.md`, `data-model.md`, `contracts/` e `quickstart.md` estarem escritos.
Os seis gates continuam **PASS**, com três verificações que só o desenho tornou possíveis:

- **Princípio I**: as entidades novas do domínio — `Airline`, `SkyError`, `TimeProvider` — não têm
  qualquer dependência de plataforma. `SkyError` estende `Exception`, que é `java.lang`, não
  Android; aceitável e registado como trade-off em AD-010.
- **Princípio II**: `contracts/opensky-states-all.md` confina tudo o que é específico da OpenSky a
  `data/source/opensky/`. `AirlineDirectory` é porta separada, não uma segunda responsabilidade
  dentro do `FlightDataSource`.
- **Princípio VI**: `contracts/main-screen-ui.md` fixa nove invariantes verificáveis por teste, e o
  `quickstart.md` define critérios de saída que incluem a verificação contra o céu real — a única
  que nenhuma suite substitui.

**Sem violações a justificar.** Nenhuma decisão desta fase contradiz um princípio, por isso a
constituição não é emendada. Nota de leitura registada em AD-007: o princípio II fala de "fontes de
dados de voo"; a tabela de operadores é dado de referência estático, não um fornecedor de posições,
e por isso tem porta própria em vez de ser espremida no `FlightDataSource`.

## Project Structure

### Documentation (this feature)

```text
specs/001-sky-list/
├── plan.md              # Este ficheiro
├── research.md          # Fase 0 — decisões técnicas e alternativas
├── data-model.md        # Fase 1 — entidades e regras de validação
├── quickstart.md        # Fase 1 — como validar a feature ponta a ponta
├── contracts/           # Fase 1 — contratos externos e portas internas
│   ├── opensky-states-all.md
│   ├── internal-ports.md
│   └── main-screen-ui.md
├── checklists/
│   └── requirements.md  # Qualidade da especificação (16/16)
└── tasks.md             # Fase 2 — criado por /speckit-tasks, não por este comando
```

### Source Code (repository root)

```text
app/src/main/
├── assets/
│   ├── airlines.json                         # NOVO — tabela prefixo ICAO -> operador
│   └── airlines-LICENSE.txt                  # NOVO — atribuição ODbL exigida pelo OpenFlights
├── java/com/mysky/app/
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Airline.kt                    # NOVO — operador aéreo
│   │   │   ├── SkyError.kt                   # NOVO — erros de domínio
│   │   │   ├── OverheadFlight.kt             # ALTERADO — campo airline
│   │   │   └── (Aircraft, GeoPosition, BoundingBox, OverheadCriteria — inalterados)
│   │   ├── time/TimeProvider.kt              # NOVO — relógio injetável
│   │   ├── repository/
│   │   │   ├── AirlineDirectory.kt           # NOVO — porta da tabela de operadores
│   │   │   └── (FlightRepository, LocationRepository — inalterados)
│   │   ├── geo/GeoCalculator.kt              # inalterado
│   │   └── usecase/
│   │       ├── DetectOverheadFlightsUseCase.kt  # inalterado
│   │       └── ObserveSkyUseCase.kt          # ALTERADO — implementado, sem SettingsRepository
│   ├── data/
│   │   ├── source/opensky/                   # OpenSkyStateVector.parse + data source
│   │   ├── mapper/OpenSkyMapper.kt           # DTO -> Aircraft
│   │   ├── repository/
│   │   │   ├── FlightRepositoryImpl.kt       # boxes em paralelo, dedup, erros -> SkyError
│   │   │   └── LocationRepositoryImpl.kt     # Fused Location, permissões
│   │   ├── local/AssetAirlineDirectory.kt    # NOVO — lê e cacheia airlines.json
│   │   └── time/SystemTimeProvider.kt        # NOVO — relógio do sistema
│   ├── presentation/
│   │   ├── main/
│   │   │   ├── MainUiState.kt                # REESCRITO — máquina de estados
│   │   │   ├── MainViewModel.kt              # laço de atualização + eventos
│   │   │   ├── MainScreen.kt                 # lista e estados
│   │   │   ├── FlightRow.kt                  # NOVO — entrada da lista
│   │   │   ├── SkyErrorMessages.kt           # NOVO — SkyError -> recurso de string
│   │   │   └── format/FlightFormatting.kt    # NOVO — unidades e texto
│   │   ├── navigation/MySkyNavHost.kt        # ALTERADO — argumento tipado icao24
│   │   └── permission/LocationPermission.kt  # NOVO — rationale e pedido em runtime
│   └── di/                                   # bindings novos: AirlineDirectory, TimeProvider
└── res/values/strings.xml                    # mensagens dos seis estados

app/src/test/java/com/mysky/app/
├── TestFixtures.kt                           # NOVO — fábricas partilhadas
├── domain/
│   ├── model/AirlineTest.kt                  # NOVO — extração do prefixo ICAO
│   └── usecase/ObserveSkyUseCaseTest.kt      # NOVO
├── data/
│   ├── OpenSkyStateVectorTest.kt             # NOVO — contrato da fonte
│   ├── OpenSkyMapperTest.kt                  # NOVO — DTO -> domínio
│   ├── FlightRepositoryImplTest.kt           # NOVO — dedup e mapeamento de erros
│   └── AssetAirlineDirectoryTest.kt          # NOVO — prefixos conhecidos e desconhecidos
└── presentation/
    ├── detail/FlightDetailViewModelTest.kt   # NOVO — argumento de rota
    └── main/
        ├── MainViewModelTest.kt              # NOVO — carga inicial
        ├── MainViewModelRefreshLoopTest.kt   # NOVO — laço em tempo virtual
        ├── MainViewModelStatesTest.kt        # NOVO — erros, rate limit, permissões
        ├── FlightFormattingTest.kt           # NOVO — unidades e rumos
        └── SkyErrorMessagesTest.kt           # NOVO — erro -> mensagem
```

**Structure Decision**: mantém-se o módulo único `:app` com as camadas já criadas no commit
inicial — não há razão para modularizar com um só ecrã implementado, e o `domain` já está isolado
por convenção verificável (nenhum import de plataforma). Os ficheiros novos assentam nas pastas
existentes; as duas pastas novas são `presentation/main/format/` e `presentation/permission/`,
ambas dentro da camada a que pertencem.

## Complexity Tracking

> Sem violações constitucionais a justificar. Secção deixada vazia por decisão, não por omissão.
