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

### AD-007 — A tabela de operadores é uma porta própria, não uma fonte de voos
O nome da companhia é resolvido localmente a partir do prefixo de 3 letras do indicativo, via
`domain/repository/AirlineDirectory` implementado por `data/local/AssetAirlineDirectory` sobre um
JSON em `assets/`. O enriquecimento acontece em `ObserveSkyUseCase`, não em
`DetectOverheadFlightsUseCase` (que continua puro e síncrono).
**Porquê:** dados de referência estáticos não têm ciclo de vida nem migrações — Room seria peso
morto. E o princípio II fala de *fontes de dados de voo*: a tabela é dado de referência, não um
fornecedor de posições, por isso tem porta própria em vez de ser espremida no `FlightDataSource`.
**Consequência:** `OverheadFlight` ganha `airline: Airline?`, preenchido num segundo passo. Um
prefixo desconhecido devolve `null` — nunca esconde a aeronave.
**Origem dos dados:** OpenFlights `airlines.dat`, sob Open Database License. A ODbL exige
atribuição, por isso `assets/airlines-LICENSE.txt` viaja no APK ao lado da tabela e o script de
conversão fica em `tools/airlines/` para a atualização periódica ser reproduzível. O ICAO Doc 8585
foi descartado por ser publicação paga e não redistribuível.

### AD-008 — A atualização do ecrã vive no ViewModel, não no WorkManager
Um único laço sequencial no `MainViewModel`, exposto com
`stateIn(viewModelScope, WhileSubscribed(5_000), ...)` e consumido com
`collectAsStateWithLifecycle()`. Cada iteração espera pelo tick de 30 s **ou** por um pedido
manual, o que vier primeiro.
**Porquê:** parar quando o ecrã deixa de estar visível sai de graça do ciclo de vida, e "sem
pedidos concorrentes" sai da forma do laço — uma só corrotina, sequencial, sem mutex nem flag de
"em curso". Um `while` em `viewModelScope` continuaria a consumir rede em segundo plano.
**Consequência:** o WorkManager fica reservado para widget e notificações, como em AD-003. Cada
ciclo pede uma posição pontual em vez de subscrever localização contínua.
**Refinado pela AD-011:** o laço deixa de viver no `MainViewModel` e passa para uma `SkySession`
partilhada, para servir também o ecrã de detalhe (`002-flight-detail`). A forma do laço e as
propriedades que ela compra mantêm-se; muda o dono.
**Correção durante a implementação:** o pedido manual chega por um `Channel(CONFLATED)` e não pelo
`MutableSharedFlow` previsto. Um `SharedFlow` sem replay descarta o que é emitido enquanto não há
coletor, e entre duas iterações do laço existe esse instante — um toque em "atualizar" que caísse
lá perdia-se em silêncio. O canal conflado guarda o último toque sem nunca enfileirar dois, e a
iteração seguinte é sequencial, por isso FR-020 mantém-se.

### AD-009 — `ObserveSkyUseCase` recebe critérios por parâmetro; o tempo entra por abstração
Deixa de depender do `SettingsRepository` e passa a aceitar `criteria: OverheadCriteria =
OverheadCriteria()`. O relógio entra por `domain/time/TimeProvider`.
**Porquê:** o `SettingsRepositoryImpl` é um stub que rebentaria, e implementá-lo aqui invadiria a
feature de definições. Quando essa feature existir, é o ViewModel que alimenta o parâmetro — a
assinatura não muda. O `TimeProvider` mantém o princípio I sem tocar em
`DetectOverheadFlightsUseCase`, que continua a receber `nowEpochSeconds` como parâmetro puro.
**Consequência:** a deduplicação por `icao24` entre caixas fica no `FlightRepositoryImpl` — a
divisão em caixas é artefacto de como as fontes são interrogadas.

### AD-010 — Erros são um tipo de domínio; o rate limiting é visível
`domain/model/SkyError` (selado: `NoConnection`, `FlightServiceUnavailable`, `RateLimited`,
`LocationUnavailable`, `Unexpected`) estende `Exception` para caber no `Result<T>` já usado nos
contratos. A tradução acontece no `FlightRepositoryImpl`. Um 429 sobe até ao ViewModel, que alonga
a espera seguinte para `max(intervalo, retryAfter)` e informa o utilizador.
**Porquê:** a UI tem de distinguir três causas (FR-024) sem inspecionar tipos de rede. Reintentar
um 429 em segredo gastaria o orçamento diário exatamente quando ele já se esgotou, e esconderia do
utilizador a razão de a lista não atualizar.
**Trade-off aceite:** um erro de domínio é tecnicamente lançável. Mitigado anulando
`fillInStackTrace` e nunca o lançando — só embrulhado em `Result.failure`. Alternativa anotada: um
tipo `SkyResult<T>` próprio, se o `Result` vier a estorvar.

### AD-011 — O laço de atualização vive numa `SkySession` partilhada, na `presentation`
O laço periódico sai do `MainViewModel` para `presentation/sky/SkySession`, com escopo
`@ActivityRetainedScoped`. Expõe `observation: StateFlow<SkyObservation>` por
`stateIn(scope próprio, WhileSubscribed(5_000), ...)` e um `requestRefresh()` que substitui o
`Channel(CONFLATED)` hoje privado do ViewModel. `MainViewModel` e `FlightDetailViewModel` passam a
consumidores finos: cada um embrulha a mesma fonte com
`stateIn(viewModelScope, WhileSubscribed(5_000), inicial)` e deriva o seu próprio estado de ecrã.
**Porquê:** a 002 exige que os dois ecrãs mostrem os mesmos valores no mesmo instante e que estarem
ambos vivos não duplique os pedidos. Uma contagem de subscritores partilhada resolve as duas coisas
ao mesmo tempo, sem mutex e sem cache com validade. Na transição lista → detalhe a contagem passa
por 1 → 2 → 1 sem chegar a zero, por isso o laço não reinicia nem repete um pedido. Fica em
`presentation/` e não em `domain/` porque a cadência é política de apresentação — a regra de
negócio continua inteira em `DetectOverheadFlightsUseCase`.
**Rejeitadas:** ViewModel partilhado por `hiltViewModel(parentEntry)`, que acopla a partilha à
topologia da navegação e mistura os campos dos dois ecrãs; laços independentes com mutex ou cache
com validade, que reintroduzem a coordenação explícita que a AD-008 evitou e duplicam o backoff de
429 em dois sítios que podem divergir.
**Consequência:** `PermissionState` e o rationale ficam só no `MainViewModel` — é o único ecrã que
pede permissão, e a `SkySession` nunca sabe o que é uma permissão; limita-se a consultar
`hasLocationPermission()` a cada ciclo. Para que conceder a permissão não implique esperar pelo
tique seguinte, o `MainViewModel` chama `requestRefresh()` na transição para `Granted`. Um refresh
manual em qualquer dos ecrãs renova ambos, o que é o comportamento correto e não um efeito
colateral. O escopo interno da sessão tem de ser cancelado em
`ActivityRetainedLifecycle.addOnClearedListener`: não há limpeza automática. E a `SkySession` nunca
é injetada em `worker/` nem em `widget/`, que continuam pelo `SkyRefreshWorker` (AD-003).

### AD-012 — "Saiu do céu" é uma redução pura no `domain`
A distinção entre aeronave atual, aeronave que saiu do céu e aeronave nunca observada vive em
`domain/usecase/TrackFlightPresenceUseCase`, com a forma da `DetectOverheadFlightsUseCase`: recebe
o estado de presença anterior, a observação mais recente (`List<OverheadFlight>?`, em que `null` é
"o ciclo falhou" e é distinto de lista vazia), o `icao24` e `nowEpochSeconds`. A memória entre
chamadas vive no `FlightDetailViewModel`.
**Porquê:** é a categoria de defeito do princípio VI — mostrar a última posição como atual não
produz erro nenhum, produz um resultado errado com ar de certo. Merece o mesmo tratamento que a
geometria: função pura, sem relógio nem memória implícitos, testável na JVM. Fica fora da
`SkySession` porque a memória é por ecrã e por aeronave, e a sessão tem de continuar sem estado por
aeronave para servir os dois ecrãs.
**Consequência:** um ciclo falhado nunca faz um avião sair do céu — é a linha da tabela de
transições que separa informar de mentir. Depois de o processo morrer e ser restaurado com o
detalhe no topo da pilha não há estado anterior, e a resposta correta é `NeverObserved`: nunca
reaproveitar um voo anterior como se fosse atual.

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

**`001-sky-list` implementada.** O ecrã principal vai à OpenSky, calcula o que está no céu do
utilizador, resolve o operador pelo indicativo e atualiza-se de 30 em 30 segundos enquanto está
visível. 131 testes unitários verdes, lint sem erros, APK de debug a gerar.

O que ficou a funcionar de ponta a ponta: `OpenSkyFlightDataSource` → `FlightRepositoryImpl`
(caixas em paralelo, deduplicação por `icao24`, tradução para `SkyError`) → `ObserveSkyUseCase`
(deteção + operador) → `MainViewModel` (laço sequencial de AD-008) → `MainScreen` (seis estados:
rationale, recusa permanente, primeiro carregamento, erro bloqueante, céu vazio e lista).
`AssetAirlineDirectory` lê 5774 operadores de `assets/airlines.json`, gerado por
`tools/airlines/build_airlines_json.py` a partir do `airlines.dat` do OpenFlights.

Continuam por implementar, como esqueleto com `TODO(feature/...)`: `SettingsRepositoryImpl`,
`SightingRepository`/Room, `LocationRepositoryImpl.locationUpdates`, widget, worker e notificações.

**Validação em dispositivo (T053) feita a 2026-09-07:** os seis estados respondem, a lista
corresponde ao céu real. Por medir, sem sinal de problema: a mediana do arranque (SC-001), a
cobertura da tabela de operadores (SC-004) e a ausência de rede em segundo plano — ver os critérios
de saída de `specs/001-sky-list/quickstart.md`.

**`002-flight-detail` implementada.** O toque numa linha da lista abre um ecrã com tudo o que a app
sabe da aeronave, a atualizar-se sozinho e a dizer de forma explícita quando o avião sai do céu.
171 testes unitários verdes, lint sem erros, APK de debug a gerar.

O laço de atualização deixou de viver no `MainViewModel` e passou para a `SkySession` partilhada
(AD-011): a contagem de subscritores soma os dois ecrãs, o que dá um só pedido por ciclo e os
mesmos valores nos dois sítios sem mutex nem cache. A distinção entre "saiu do céu", "a atualização
falhou" e "nunca foi observada" é uma redução pura no domínio (AD-012).

Continuam por implementar, como esqueleto com `TODO(feature/...)`: `SettingsRepositoryImpl`,
`SightingRepository`/Room, `LocationRepositoryImpl.locationUpdates`, widget, worker e notificações.
O ecrã de definições continua a ser um beco sem saída — é o que resta dos dois que a 001 deixou.

**Falta para dar a feature por fechada:** a validação manual de
`specs/002-flight-detail/quickstart.md` (secções 5 a 7), num dispositivo. O passo que mais importa é
o 7 da secção 5: com o modo de avião ligado, o ecrã **não** pode anunciar que a aeronave saiu do
céu. E a contagem de pedidos no Network Inspector com o detalhe aberto tem de ser igual à da lista
sozinha (SC-008).

**Feature seguinte:** `003-settings` (desbloqueia o `SettingsRepositoryImpl`, que a AD-009 deixou
por fazer, e fecha o segundo beco sem saída) ou `002-widget` como previsto na AD-003.
