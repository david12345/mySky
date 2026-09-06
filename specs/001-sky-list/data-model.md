# Phase 1 — Data Model: Lista de aviões no meu céu

Entidades do domínio para esta feature. Unidades sempre em SI e explícitas no nome, conforme as
convenções do projeto. Nada aqui persiste em disco: a feature não escreve estado.

Legenda: **novo** = criado nesta feature · **alterado** = já existe e muda · sem marca = já existe
e fica intacto.

---

## `Aircraft`

Aeronave tal como a fonte a reporta, num instante. Quase tudo é opcional porque as fontes públicas
reportam vetores de estado incompletos com frequência.

| Campo | Tipo | Obrigatório | Notas |
|---|---|---|---|
| `icao24` | `String` | sim | Identidade estável, hexadecimal minúsculo. Chave de deduplicação. |
| `callsign` | `String?` | não | Já sem espaços de padding. Vazio depois do trim conta como ausente. |
| `originCountry` | `String?` | não | País de registo do operador, não a origem do voo. |
| `position` | `GeoPosition?` | não | Ausente invalida a aeronave para esta feature. |
| `barometricAltitudeMeters` | `Double?` | não | |
| `geometricAltitudeMeters` | `Double?` | não | Preferida para geometria quando existe. |
| `groundSpeedMetersPerSecond` | `Double?` | não | |
| `headingDegrees` | `Double?` | não | Rumo da aeronave, não a direção a partir do observador. |
| `verticalRateMetersPerSecond` | `Double?` | não | |
| `onGround` | `Boolean` | sim | `true` exclui da lista. |
| `lastContactEpochSeconds` | `Long?` | não | Ausente é tolerado; presente e antigo exclui. |

**Derivado**: `altitudeMeters` = geométrica, senão barométrica, senão ausente.

**Regras de validação** (aplicadas no mapeamento DTO → domínio):
- `icao24` em branco ⇒ registo descartado.
- Latitude fora de `[-90, 90]` ou longitude fora de `[-180, 180]` ⇒ posição descartada, aeronave
  descartada. O mapeamento **não** pode deixar o construtor de `GeoPosition` rebentar.
- `callsign` só com espaços ⇒ tratado como `null`.

---

## `Airline` — **novo**

Operador aéreo, resolvido localmente a partir do indicativo de voo.

| Campo | Tipo | Obrigatório | Notas |
|---|---|---|---|
| `icaoCode` | `String` | sim | Exatamente 3 letras maiúsculas. |
| `name` | `String` | sim | Nome apresentável. Nunca vazio. |

**Regra de domínio** (`Airline.icaoPrefixOf(callsign)`): o prefixo são os 3 primeiros caracteres do
indicativo depois do trim, **apenas se** forem todas letras e houver pelo menos um carácter a
seguir. Casos que devolvem `null`:
- indicativo com menos de 4 caracteres;
- prefixo com dígitos — cobre matrículas de aviação privada (`N123AB`, `CS-DHA`);
- indicativo ausente ou vazio.

Um prefixo válido mas ausente da tabela devolve `null` — a aeronave aparece na lista sem operador
(FR-012), nunca é escondida.

Quando falta o próprio indicativo, a aeronave é identificada na lista pelo `icao24` em maiúsculas
(FR-013) — o único campo que nunca fica em branco.

---

## `OverheadFlight` — **alterado**

Aeronave com a geometria já calculada face ao observador. É o que a lista apresenta.

| Campo | Tipo | Obrigatório | Notas |
|---|---|---|---|
| `aircraft` | `Aircraft` | sim | |
| `horizontalDistanceMeters` | `Double` | sim | Observador → projeção da aeronave no solo. `>= 0`. |
| `bearingDegrees` | `Double` | sim | Direção a partir do observador. `[0, 360)`. Sem significado no zénite, onde vale 0 por convenção. |
| `elevationDegrees` | `Double` | sim | Altura acima do horizonte. `[0, 90]`. |
| `airline` | `Airline?` | não | **Novo.** Preenchido por `ObserveSkyUseCase`, nunca por `DetectOverheadFlightsUseCase`. |

**Derivado**: `relevanceScore` = `elevationDegrees`. É o critério de ordenação da lista e o que
escolherá o avião do widget.

**Porque é que `airline` é preenchido em dois passos**: `DetectOverheadFlightsUseCase` é síncrono
e puro; consultar o diretório de operadores é `suspend`. Manter a deteção intacta preserva os seus
testes e o princípio I.

---

## `OverheadCriteria`

Limiares que definem "está no meu céu". Nesta feature são sempre os valores por omissão.

| Campo | Tipo | Omissão | Requisito |
|---|---|---|---|
| `maxHorizontalDistanceMeters` | `Double` | 30 000 | FR-007 |
| `minElevationDegrees` | `Double` | 25 | FR-007 |
| `minAltitudeMeters` | `Double` | 300 | FR-008 |
| `maxStateAgeSeconds` | `Long` | 120 | FR-008 |

---

## `GeoPosition` e `BoundingBox`

Inalterados. `GeoPosition` valida os intervalos no construtor — por isso o mapeamento tem de filtrar
antes de construir. `BoundingBox` exige `minLongitude <= maxLongitude`, o que é a razão de
`GeoCalculator.boundingBoxesAround` devolver **duas** caixas quando o círculo cruza o antimeridiano
e uma caixa de longitude completa quando envolve um polo.

---

## `SkyError` — **novo**

Hierarquia selada de erros de domínio. Estende `Exception` para caber em `Result<T>`; nunca é
lançada, só embrulhada.

| Variante | Campos | Origem | Mensagem que a UI mostra |
|---|---|---|---|
| `NoConnection` | — | `IOException` sem resposta | Sem ligação à Internet |
| `FlightServiceUnavailable` | `httpCode: Int?` | HTTP 5xx ou 4xx não previsto | O serviço de voos não respondeu |
| `RateLimited` | `retryAfterSeconds: Long?` | HTTP 429 | Demasiados pedidos; nova tentativa mais tarde |
| `LocationUnavailable` | — | Sem posição apesar da permissão | Não foi possível obter a tua localização |
| `Unexpected` | `cause: Throwable?` | Tudo o resto | Erro inesperado |

Cada variante mapeia para uma mensagem distinta e todas oferecem repetição (FR-024).

---

## `TimeProvider` — **novo**

Abstração de relógio no domínio: `fun nowEpochSeconds(): Long`. Existe para que
`ObserveSkyUseCase` possa avaliar a antiguidade dos vetores de estado sem o domínio ler o relógio
do sistema. Substituída por um valor fixo nos testes.

---

## Estado de apresentação

`MainUiState` não é entidade de domínio, mas é o que a UI observa. Definição completa e transições
em [contracts/main-screen-ui.md](./contracts/main-screen-ui.md).

| Campo | Tipo | Significado |
|---|---|---|
| `permission` | `PermissionState` | `Unknown` · `Granted` · `Denied` · `PermanentlyDenied` |
| `phase` | `LoadPhase` | `Idle` · `LocatingUser` · `LoadingFlights` — distingue as duas fases (FR-022) |
| `flights` | `List<OverheadFlight>` | Já ordenada por elevação decrescente |
| `lastUpdatedEpochSeconds` | `Long?` | `null` = nunca houve sucesso |
| `lastError` | `SkyError?` | `null` = última tentativa correu bem |

**Estados derivados, não armazenados** (evita combinações impossíveis):
- céu vazio ⟺ `lastError == null && lastUpdatedEpochSeconds != null && flights.isEmpty()`
- resultados possivelmente desatualizados ⟺ `lastError != null && flights.isNotEmpty()`
- primeira carga ⟺ `lastUpdatedEpochSeconds == null && phase != Idle`
