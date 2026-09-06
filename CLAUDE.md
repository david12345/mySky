# mySky — guia para trabalho futuro

App Android nativa que mostra os aviões a passar sobre a localização do utilizador, com widget
de ecrã inicial e notificações opcionais quando uma aeronave passa "por cima".

## Visão geral

| | |
|---|---|
| Linguagem | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Widget | Jetpack Glance |
| Arquitetura | Clean Architecture + MVVM (`data` / `domain` / `presentation`) |
| DI | Hilt |
| Rede | Retrofit + Kotlinx Serialization |
| Background | WorkManager (periódico, mínimo 15 min) |
| Persistência | Room (histórico) + DataStore (preferências) |
| Localização | Fused Location Provider |
| Testes | JUnit + MockK + Turbine |
| minSdk / targetSdk | 26 / 36 |

Fonte de dados inicial: **OpenSky Network** (`GET /states/all`), anónima por omissão.

## Decisões de arquitetura

Registar aqui **todas** as decisões estruturais. O subagente `architect` escreve nesta secção;
decisões com peso suficiente sobem também à constituição (`.specify/memory/constitution.md`).

### AD-001 — A deteção de aviões vive no `domain`, sem rede
`DetectOverheadFlightsUseCase` recebe observador + lista de aeronaves + critérios + instante de
referência e devolve os voos "no céu". Não conhece rede, Android nem UI, e o relógio entra como
parâmetro (`nowEpochSeconds`) para ser determinístico.
**Porquê:** é a regra de negócio da app; tem de ser testável na JVM em milissegundos e não pode
mudar quando a fonte de dados ou a UI mudarem.

### AD-002 — Nenhuma camada acima de `data/source` conhece a OpenSky
A abstração é `data/source/FlightDataSource`. `OpenSkyFlightDataSource` implementa-a e
`di/DataSourceModule` é o **único** ponto onde a fonte concreta é escolhida.
**Porquê:** trocar/adicionar fonte (ADS-B Exchange, airplanes.live, dataset local) tem de ser um
binding, não um refactor.
**Consequência:** a classe de repositório chama-se `FlightRepositoryImpl`, **não**
`OpenSkyRepositoryImpl` — o nome original acoplaria o repositório à fonte, contrariando esta
decisão.

### AD-003 — Widget e notificações partilham um único worker periódico
`SkyRefreshWorker` é a única coisa que vai à rede em background; `SkyWorkScheduler` é o único
sítio que cria `WorkRequest`s.
**Porquê:** garante num só ponto o mínimo de 15 minutos, as `Constraints` de rede e o backoff, e
evita dois agendamentos a duplicar consumo de bateria.
**Consequência:** o widget nunca faz rede em `provideGlance`; lê o último resultado do estado do
Glance. O "tap-to-refresh" enfileira trabalho, não bloqueia a composição.

### AD-004 — Sem foreground service permanente no MVP
As notificações são "best effort" dentro dos limites do Doze/App Standby.
**Porquê:** um serviço em primeiro plano permanente com localização ativa é o pior custo de
bateria possível e exige justificação adicional na Play Store. Se surgir um requisito de deteção
quase-instantânea, é decisão nova a passar pelo `architect`.

### AD-005 — MVP só com lista, sem mapa
O ecrã principal é uma lista ordenada por elevação. O mapa é feature posterior.
**Porquê:** evita a dependência do Google Maps SDK e a gestão de uma API key logo no arranque; a
informação útil (o que está por cima de mim, agora) cabe numa lista.

### AD-006 — Toolchain: AGP 8.13.2 + Hilt 2.58
O AGP 9 traz Kotlin embutido, mas o KSP — necessário para o Hilt **e** para o Room — ainda não é
compatível com ele; usá-lo obrigaria a `android.builtInKotlin=false` **e** `android.newDsl=false`,
duas flags legacy que desaparecem no AGP 10. O Hilt >= 2.59 exige AGP 9, por isso o par estável
hoje é AGP 8.13.2 com Hilt 2.58.
**Rever quando:** o KSP anunciar suporte ao Kotlin embutido do AGP. Aí sobe-se AGP e Hilt juntos.

## Estrutura de pastas

```
app/src/main/java/com/mysky/app/
├── data/
│   ├── source/            FlightDataSource (interface) + implementações por fonte
│   │   └── opensky/       API Retrofit, DTOs, parsing do vetor de estado
│   ├── repository/        Implementações dos repositórios do domain
│   ├── local/             Room (MySkyDatabase, DAOs, entities)
│   ├── settings/          DataStore de preferências
│   └── mapper/            DTO -> modelo de domínio
├── domain/
│   ├── model/             Aircraft, OverheadFlight, GeoPosition, BoundingBox, SkySettings...
│   ├── geo/               GeoCalculator (Haversine, bearing, elevação, bounding boxes)
│   ├── repository/        Interfaces (Flight, Location, Settings, Sighting)
│   └── usecase/           DetectOverheadFlightsUseCase, ObserveSkyUseCase
├── presentation/
│   ├── main/              Lista de aviões no céu
│   ├── detail/            Detalhe de uma aeronave
│   ├── settings/          Definições
│   ├── navigation/        NavHost e rotas
│   └── theme/             Material 3
├── di/                    Módulos Hilt (único sítio com escolhas de implementação)
├── widget/                Glance: SkyWidget, receiver, ação de refresh
├── worker/                SkyRefreshWorker + SkyWorkScheduler
└── notification/          OverheadNotifier
```

Regra de dependências: `presentation` → `domain` ← `data`. **O `domain` não importa nada de
`android.*`, de Retrofit, de Room nem de Compose.** Se um import desses aparecer em `domain`, a
decisão de desenho está errada.

## Comandos úteis

```bash
./gradlew :app:testDebugUnitTest     # testes unitários (JVM) — os do domain têm de passar sempre
./gradlew :app:assembleDebug         # APK de debug
./gradlew :app:lintDebug             # Android Lint
./gradlew :app:installDebug          # instalar num dispositivo/emulador ligado
./gradlew build                      # tudo: compilar, lint e testes
./gradlew --stop                     # matar daemons quando o Gradle se porta mal
```

Relatórios: testes em `app/build/reports/tests/`, lint em `app/build/reports/lint-results-*.html`.

## Convenções de código

- **Idioma:** código, nomes e identificadores em inglês; comentários, KDoc e strings de UI em
  português. Nomes de testes em português, em crases, a descrever o comportamento.
- **Unidades no domínio sempre em SI** (metros, m/s, graus) e explícitas no nome da propriedade
  (`horizontalDistanceMeters`, não `distance`). A conversão para km/milhas/pés é da `presentation`.
- **Versões só no `gradle/libs.versions.toml`.** Nunca escrever uma versão num `build.gradle.kts`.
- **Um `data class` de estado por ecrã** (`MainUiState`), exposto num `StateFlow`; a UI nunca
  compõe estado a partir de vários flows soltos.
- **Nada de `WorkRequest` fora de `SkyWorkScheduler`** e nada de rede em `provideGlance`.
- **Erros de rede** são `Result.failure` na fronteira do repositório; exceções não sobem ao domain.
- **TODOs marcados por feature**: `TODO(feature/<nome>)` para ligar o esqueleto às specs em
  `.specify/specs/`.
- **KDoc explica o "porquê"**, não o "o quê". Não repetir o nome do método em prosa.

## Permissões — regras não negociáveis

- `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION`: pedidas em runtime, sempre com o rationale
  (`R.string.permission_location_rationale`) mostrado **antes** do diálogo do sistema.
- `ACCESS_BACKGROUND_LOCATION`: nunca pedida no arranque. Só a partir das definições, depois de o
  utilizador ativar notificações. A app tem de continuar utilizável sem ela.
- `POST_NOTIFICATIONS` (Android 13+): pedida apenas quando o utilizador ativa notificações.
- Recusar uma permissão nunca pode deixar um ecrã em branco sem explicação.

## Fluxo de desenvolvimento (Spec-Kit)

As 4 features do MVP — lista de aviões, widget, notificações, definições — seguem, **uma de cada
vez**:

```
/speckit-specify → /speckit-clarify → /speckit-plan → /speckit-tasks → /speckit-analyze → /speckit-implement
```

- durante o `/speckit-plan`, invocar o subagente `architect` se houver decisão estrutural;
- no fim de cada `/speckit-implement`, invocar o subagente `reviewer` antes de dar a feature por
  concluída.

A constituição do projeto está em `.specify/memory/constitution.md` e prevalece sobre este
documento em caso de conflito.

## Estado atual

Esqueleto inicial. Compila, o APK de debug gera e os testes do `domain` passam.
Implementado a sério: `GeoCalculator` e `DetectOverheadFlightsUseCase` (com testes).
Tudo o resto são interfaces e stubs com `TODO(feature/...)`, à espera das specs.
