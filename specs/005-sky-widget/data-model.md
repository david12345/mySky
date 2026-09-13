# Data Model: Widget de ecrã inicial

**Feature**: `005-sky-widget` | **Data**: 2026-09-13

## A separação que sustenta tudo: facto contra interpretação

O erro fácil desta feature era guardar "está no teu céu o voo TAP1234". Guarda-se o **facto**: quais os
voos e **quando** foram observados. A frase é derivada no instante da leitura.

A razão é aritmética, não estética: a cadência por omissão são 30 minutos e a janela de frescura são 5.
Se o texto fosse decidido na escrita, o widget diria "está no teu céu" durante **25 dos 30 minutos** em
que isso já era falso. É o mesmo defeito que a AD-012 evitou no ecrã de detalhe.

## `SkyWidgetSnapshot` — o que se persiste

Modelo de domínio, sem tipos Android. Selado, porque as três situações não são o mesmo com campos a
`null`:

| Variante | Campos | Quando |
|---|---|---|
| `Flights` | `top: WidgetFlight`, `count: Int`, `observedAtEpochSeconds: Long` | o ciclo correu e encontrou aeronaves |
| `EmptySky` | `observedAtEpochSeconds: Long` | o ciclo correu e o céu estava vazio |
| `PermissionMissing` | `observedAtEpochSeconds: Long` | o ciclo terminou por falta de permissão |

Ausência de snapshot (`null`) é a quarta situação — nunca correu — e é distinta de `EmptySky`. Essa
distinção é a FR-004 e o cenário 1 e 3 da US1.

**`count` existe porquê:** "1 avião" e "7 aviões" são informações diferentes para quem olha, e o campo
sai de graça do mesmo cálculo. Não obriga a guardar a lista toda.

### `WidgetFlight` — só o que o widget desenha

`callsign: String?`, `airlineName: String?`, `elevationDegrees: Double`.

Deliberadamente **não** é um `OverheadFlight`. O widget desenha três campos; persistir o modelo inteiro
guardaria posição, rumo, rota, altitude e velocidade que ninguém lê, e amarrava o formato persistido à
evolução de um modelo de domínio que muda por outras razões. Os dois `null` são reais: a FR-005 exige
que a falta de indicativo ou de companhia nunca esconda a aeronave.

## `SkyWidgetState` — o que se mostra

Selado, produzido por função pura, nunca persistido:

| Estado | Significa | Texto usa |
|---|---|---|
| `NoDataYet` | não há snapshot | — |
| `Fresh(flight, count, observedAt)` | observação dentro da janela | **presente** |
| `Stale(flight, count, observedAt)` | observação fora da janela | **passado**, com o instante |
| `EmptySky(observedAt, isFresh)` | céu vazio no instante do cálculo | conforme `isFresh` |
| `PermissionMissing` | falta permissão de localização | — |

```
evaluate(snapshot: SkyWidgetSnapshot?, nowEpochSeconds: Long, freshnessWindowSeconds: Long): SkyWidgetState
```

**Regras que a função garante, e que são o SC-003:**

1. `snapshot == null` → `NoDataYet`, sempre.
2. `PermissionMissing` nunca vira `Fresh` nem `Stale` — a permissão manda sobre a idade.
3. `Fresh` se e só se `now - observedAt <= janela`.
4. Um `observedAt` no **futuro** (relógio do dispositivo atrasado) conta como fresco, não como idade
   negativa — o mesmo tratamento que o `FlightFormatting.freshnessOf` já dá desde a 002.

**A janela de frescura são 300 segundos.** Deriva de R6: 4,0 min a 250 m/s e 5,0 min a 200 m/s para
atravessar um raio de 30 km. Não é um valor de gosto; é o tempo que a aeronave lá está.

## `SkyBudget` — o custo da cadência

Função pura ao lado do `SkyRange`, com o mesmo formato: uma constante única e contas determinísticas.

```
DAILY_QUERY_BUDGET = 400          // consultas/dia, fonte anónima
SCREEN_CYCLE_SECONDS = 30         // uma consulta por ciclo de ecrã

queriesPerDay(intervalMinutes): Int
budgetShare(intervalMinutes): Double          // fração de 400
remainingScreenSeconds(intervalMinutes): Long
```

| Cadência | Consultas/dia | Fração | Ecrã restante |
|---|---|---|---|
| 15 min | 96 | 24% | 2h32m |
| **30 min** *(origem)* | **48** | **12%** | **2h56m** |
| 60 min | 24 | 6% | 3h08m |

O 400 fica **num sítio só**. O defeito da 004 foi um número escrito de duas maneiras que divergiu; aqui
o risco simétrico seria repeti-lo num ficheiro de UI (AD-027).

## O que muda em modelos existentes

**`SkySettings.refreshIntervalMinutes`** deixa de ser um campo sem dono. Ganha `REFRESH_INTERVAL_RANGE`
(15..180 minutos) e entra no `coerced()`, como todos os outros — o que dá a FR-026 sem uma segunda
validação no scheduler. O mínimo é o da plataforma; o máximo de 3 horas é onde o widget deixa de ter
utilidade prática.

**Nada mais muda.** `OverheadFlight`, `Aircraft` e `OverheadCriteria` ficam como estão.
