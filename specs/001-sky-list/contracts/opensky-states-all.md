# Contrato externo — `GET /states/all`

Contrato **consumido**, não fornecido: a app não o controla. Confinado a
`data/source/opensky/`; nada acima dessa pasta o conhece (princípio II).

Base: `https://opensky-network.org/api/` · Autenticação: nenhuma (uso anónimo)

## Pedido

| Parâmetro | Tipo | Notas |
|---|---|---|
| `lamin` | `Double` | Latitude mínima, graus decimais |
| `lomin` | `Double` | Longitude mínima |
| `lamax` | `Double` | Latitude máxima |
| `lomax` | `Double` | Longitude máxima |

A caixa nunca cruza o antimeridiano: `GeoCalculator.boundingBoxesAround` já a divide em duas
quando necessário, e o repositório emite um pedido por caixa.

## Resposta 200

```json
{ "time": 1737550000, "states": [ [ ... ], [ ... ] ] }
```

`states` pode ser `null` (não apenas vazio) quando não há tráfego — tem de ser tratado como lista
vazia, **não** como erro. Cada elemento é um **array posicional**, não um objeto:

| Índice | Campo | Tipo | Mapeia para |
|---|---|---|---|
| 0 | `icao24` | `String` | `Aircraft.icao24` |
| 1 | `callsign` | `String?` | `Aircraft.callsign` — **vem com espaços à direita, exige trim** |
| 2 | `origin_country` | `String?` | `Aircraft.originCountry` |
| 3 | `time_position` | `Long?` | não usado nesta feature |
| 4 | `last_contact` | `Long?` | `Aircraft.lastContactEpochSeconds` |
| 5 | `longitude` | `Double?` | `Aircraft.position` |
| 6 | `latitude` | `Double?` | `Aircraft.position` |
| 7 | `baro_altitude` | `Double?` | `Aircraft.barometricAltitudeMeters` |
| 8 | `on_ground` | `Boolean` | `Aircraft.onGround` |
| 9 | `velocity` | `Double?` | `Aircraft.groundSpeedMetersPerSecond` (m/s) |
| 10 | `true_track` | `Double?` | `Aircraft.headingDegrees` |
| 11 | `vertical_rate` | `Double?` | `Aircraft.verticalRateMetersPerSecond` |
| 12 | `sensors` | `Array?` | ignorado |
| 13 | `geo_altitude` | `Double?` | `Aircraft.geometricAltitudeMeters` |
| 14 | `squawk` | `String?` | ignorado |
| 15 | `spi` | `Boolean?` | ignorado |
| 16 | `position_source` | `Int?` | ignorado |

**Invariantes que o parsing tem de respeitar:**

1. Um array mais curto do que o esperado não é erro — os índices em falta leem como ausentes.
2. `null` é válido em qualquer posição exceto 0 e 8.
3. Campos novos no fim do array são ignorados sem falhar (a API já cresceu antes).
4. Um registo que não converte é descartado em silêncio; **nunca** invalida os restantes.
5. Longitude está no índice 5 e latitude no 6 — ordem invertida face à convenção habitual, e é a
   troca mais fácil de fazer sem dar por isso. Coberta por teste.

## Respostas de erro

| Situação | Traduz para |
|---|---|
| 429 | `SkyError.RateLimited(retryAfterSeconds)` — de `X-Rate-Limit-Retry-After-Seconds` quando presente |
| 5xx | `SkyError.FlightServiceUnavailable(httpCode)` |
| Outro 4xx | `SkyError.FlightServiceUnavailable(httpCode)` |
| `IOException` | `SkyError.NoConnection` |
| Corpo ilegível | `SkyError.Unexpected(cause)` |

A tradução acontece em `FlightRepositoryImpl`. Nenhuma exceção de rede sobe acima dessa fronteira.

## Limites de utilização

Uso anónimo tem orçamento diário de créditos e resolução temporal reduzida. Um ciclo de 30 s
durante 15 minutos consome ~30 pedidos por caixa. A app **não** reintenta um 429 automaticamente
(ver `research.md`, D4).
