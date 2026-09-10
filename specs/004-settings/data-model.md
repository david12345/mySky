# Phase 1 — Modelo de dados: Definições

Nenhum tipo de domínio novo. O `SkySettings` já existe desde o esqueleto inicial, com os campos e os
valores de origem certos, à espera desta feature. O que é novo são os **limites**, uma função de
degradação, e o alargamento dos estados de ecrã para transportarem a unidade escolhida.

---

## `SkySettings` — existente, com dois acrescentos

```
data class SkySettings(
    val detectionRadiusMeters: Double = OverheadCriteria.DEFAULT_RADIUS_METERS,       // 30 000
    val minElevationDegrees: Double = OverheadCriteria.DEFAULT_MIN_ELEVATION_DEGREES, // 25
    val minAltitudeMeters: Double = OverheadCriteria.DEFAULT_MIN_ALTITUDE_METERS,     // 300
    val refreshIntervalMinutes: Long = MIN_REFRESH_INTERVAL_MINUTES,                  // do worker
    val distanceUnit: DistanceUnit = KILOMETERS,
    val altitudeUnit: AltitudeUnit = METERS,
    val notificationsEnabled: Boolean = false,
    val widgetEnabled: Boolean = true,
)
```

**Nesta feature mexem-se cinco campos**: raio, elevação, altitude, e as duas unidades.

`refreshIntervalMinutes` **não é desta feature.** Pertence ao trabalho periódico do widget, com o
seu mínimo de 15 minutos (AD-003), e não tem relação com o laço de 30 segundos do ecrã. Está na
mesma classe, o que o torna a armadilha mais fácil de pisar — FR-015 proíbe ligar-lhe um controlo.
`notificationsEnabled` e `widgetEnabled` são das features respetivas.

### Os limites, e de onde vêm

Derivados de **utilidade geométrica**, não do orçamento (D1, AD-021). Todos com contas:

| Valor | Mínimo | Origem | Máximo | Donde vem o máximo |
|---|---|---|---|---|
| Raio | 5 km | **30 km** | **150 km** | Com o ângulo no valor mais baixo (5°) e um teto de altitude de 14 km, o alcance útil é 160 km. Arredondado para baixo, com folga |
| Ângulo mínimo | **5°** | **25°** | 60° | Abaixo de 5° a aeronave está tão baixa que edifícios e relevo a tapam; acima de 60° a lista fica quase sempre vazia |
| Altitude mínima | 0 m | **300 m** | 3 000 m | A zero entram helicópteros e tráfego local, que é escolha legítima; acima de 3 000 m já se perdem voos de aproximação |

**O que o máximo do raio custa**, verificado: a 150 km a caixa envolvente tem 9,3 sq° em Lisboa e
14,6 sq° a 60° de latitude — ambas no primeiro degrau de custo, **1 crédito**, igual ao de hoje. E
traz da ordem de 47 aeronaves na caixa em hora de ponta europeia, das quais poucas passam o filtro
de elevação. Nem o orçamento nem o desempenho são a razão dos limites.

### `SkySettings.coerced(): SkySettings`

Função pura que devolve uma cópia com todos os campos dentro dos limites. Aplicada em **toda**
leitura do armazenamento, não numa migração (AD-022).

É o **único ponto de verdade** dos limites, consumido por três sítios que não os redefinem:

| Quem consome | Para quê | Requisito |
|---|---|---|
| O controlo no ecrã | intervalo do cursor | FR-009 |
| `coerced()` | degradar um valor guardado inválido | FR-008 |
| A frase explicativa | dizer o efeito prático | FR-011 |

Aplicar em cada leitura, e não uma vez só, é o que dispensa versionar o esquema: se um limite mudar
numa versão futura, o valor antigo é corrigido todas as vezes que é lido, para sempre.

---

## O alcance útil — inversa de geometria que já existe

O `GeoCalculator` já calcula o ângulo de elevação a partir da altitude e da distância. O alcance
útil é a sua inversa, e **não há geometria nova a escrever**:

```
alcanceUtilMetros(anguloMinimoGraus, altitudeTetoMetros) = altitude / tan(angulo)
```

| Ângulo mínimo | Alcance útil (teto de 12 km) |
|---|---|
| 5° | 137 km |
| 15° | 45 km |
| **25°** *(origem)* | **26 km** |
| 45° | 12 km |

**É uma aproximação, e é assim que tem de ser lida.** Assume um teto de altitude típico; há tráfego
executivo que voa bem mais alto e para o qual a conta dá outro resultado. Serve para informar o
utilizador (FR-014), nunca para lhe impedir uma escolha (AD-021).

---

## `SettingsUiState` — alargado

```
data class SettingsUiState(
    // da feature 003, inalterados
    val routeTableGeneratedAtEpochSeconds: Long? = null,
    val routeCount: Int = 0,
    val updateState: RouteUpdateState = RouteUpdateState.Idle,
    // desta feature
    val settings: SkySettings = SkySettings(),
)
```

A feature 003 fixou na AD-017 que este ecrã cresce **estendendo esta classe**, e não criando um
segundo estado ao lado. É o que se faz aqui.

**Derivados**, calculados e não armazenados:

| Derivado | Regra | Requisito |
|---|---|---|
| `isRadiusAtDefault` e afins | valor igual ao de origem | FR-012 |
| `usefulRangeMeters` | alcance útil do ângulo escolhido | FR-014 |
| `radiusExceedsUsefulRange` | raio escolhido > alcance útil | FR-014 |

---

## `MainUiState` e o estado do detalhe — alargados

Cada um ganha as duas unidades, combinadas a partir das preferências como o `MainUiState` já
combina a permissão com a observação (AD-020):

```
val distanceUnit: DistanceUnit = KILOMETERS
val altitudeUnit: AltitudeUnit = METERS
```

Não há `CompositionLocal`: seria um segundo canal de estado implícito ao lado do estado do ecrã,
contra a convenção do `CLAUDE.md`.

---

## O que **não** muda

O `OverheadCriteria` e o `SkySettings.toCriteria()` já existem e estão certos. O
`ObserveSkyUseCase` já recebe os critérios por parâmetro desde a AD-009 — e é por isso que esta
feature não lhe toca. O `DetectOverheadFlightsUseCase` continua puro e não sabe que existem
definições.

Nenhuma tabela, nenhuma migração, nenhum tipo de domínio novo. É uma feature que liga o que já
estava desenhado para ser ligado.
