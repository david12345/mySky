# Data Model: Notificações de passagem

## `NotificationPolicy` — as constantes e a conta que o utilizador precisa de ver

```
DEDUPLICATION_WINDOW_SECONDS = 1_800      // 30 min: cobre uma passagem inteira com folga
RETENTION_WINDOW_SECONDS     = 604_800    // 7 dias de avisos guardados
DEFAULT_THRESHOLD_DEGREES    = 30.0
CRUISE_ALTITUDE_METERS       = 12_000.0
TYPICAL_GROUND_SPEED_MPS     = 250.0

expectedCaptureRate(thresholdDegrees, refreshIntervalMinutes): Double
```

A conta: a aeronave está acima do limiar enquanto a distância horizontal for menor que
`altitude / tan(limiar)`; atravessa esse diâmetro em `2r / v` segundos; a fração de passagens apanhadas
é essa janela a dividir pela cadência.

| Limiar | @15 min | @30 min | @60 min |
|---|---|---|---|
| 20° | 29,3% | 14,7% | 7,3% |
| **30°** *(origem)* | **18,5%** | **9,2%** | 4,6% |
| 45° | 10,7% | 5,3% | 2,7% |
| 60° | 6,2% | 3,1% | 1,5% |

**Porque 30° e não 60°:** a 60° a janela é de 55 segundos — menos tempo do que o utilizador leva a
tirar o telefone do bolso e olhar para cima. A 30° são 166 segundos, e a taxa de captura triplica.

**O limite de 100%** existe porque uma cadência muito curta com um limiar muito baixo daria uma fração
acima de 1, que não significa nada. Não é defensivo: é a fronteira do modelo.

## `SkySettings` — dois campos novos

| Campo | Origem | Limites |
|---|---|---|
| `notificationsEnabled` | `false` | — (já existia, sem uso) |
| `notificationThresholdDegrees` | `30.0` | `minElevationDegrees..90.0` |

O piso do limiar **depende** do ângulo mínimo de deteção, e é aplicado no `coerced()` (AD-033). Uma
dependência de um só sentido: mexer no limiar de aviso nunca altera o intervalo do controlo de deteção.

## `SkyCycleResult` — um caso novo

`BackgroundLocationUnavailable`, distinto de `NoPermission` e de `Failure(LocationUnavailable)`. Três
causas com três remédios: pedir a permissão normal, ir às definições do sistema, ou esperar por um fix
de GPS. Colapsá-las faria a app pedir o que já tem.

## `SightingEntity` — o que se grava, e o que não

Só as aeronaves **avisadas**, com `notified = true`. A tabela existe para responder a uma pergunta —
"já avisei esta aeronave nos últimos 30 minutos?" — e não para guardar histórico. Gravar todas as
observadas seria implementar a feature do histórico por acidente e acrescentar milhares de linhas por
dia para nada.

`record` ganha `notified: Boolean` **sem valor por omissão**: quando a feature do histórico chegar e
começar a gravar não-avisadas, o compilador obriga cada chamador a dizer o que quer, em vez de herdar
em silêncio um significado que deixou de ser o certo.
