# Portas internas — contratos entre camadas

Interfaces que o `domain` define e o `data` implementa. Regra de dependência:
`presentation → domain ← data`.

---

## `FlightRepository` (existente, inalterado)

```
suspend fun getAircraftIn(boxes: List<BoundingBox>): Result<List<Aircraft>>
```

**Pré-condições**: `boxes` não vazia; cada caixa com `minLongitude <= maxLongitude`.

**Pós-condições**:
- sucesso ⇒ lista **deduplicada por `icao24`**; em duplicado ganha o vetor com
  `lastContactEpochSeconds` mais recente;
- falha ⇒ `Result.failure` com um `SkyError`, nunca com uma exceção de rede;
- falha parcial (uma caixa de duas falha) ⇒ **falha**, para não apresentar um céu incompleto como
  se fosse completo;
- sem tráfego ⇒ sucesso com lista vazia, não falha.

**Notas de implementação**: as caixas são pedidas em paralelo; a deduplicação existe porque a
mesma aeronave aparece nas duas caixas quando o círculo cruza o antimeridiano.

---

## `AirlineDirectory` — **novo**

```
suspend fun findByCallsign(callsign: String?): Airline?
```

**Pós-condições**:
- `callsign` nulo, vazio ou sem prefixo válido de 3 letras ⇒ `null`;
- prefixo válido mas ausente da tabela ⇒ `null`;
- consulta **nunca** faz rede nem I/O de disco depois do primeiro carregamento;
- nunca lança: um asset ausente ou ilegível degrada para diretório vazio e a lista continua a
  funcionar sem nomes de operador.

**Concorrência**: o carregamento é feito uma vez e é seguro chamar de várias corrotinas em
paralelo sem duplicar o trabalho.

---

## `LocationRepository` (existente)

```
fun hasLocationPermission(): Boolean
suspend fun getCurrentLocation(): GeoPosition?
fun locationUpdates(): Flow<GeoPosition>
```

**Nesta feature só se usam os dois primeiros.** `locationUpdates()` fica por implementar: um fluxo
contínuo de localização custaria bateria sem benefício, dado que o ciclo de atualização já pede uma
posição pontual de 30 em 30 segundos.

**Pós-condições de `getCurrentLocation`**:
- sem permissão ⇒ `null` (nunca lança `SecurityException`);
- com permissão mas sem fix possível ⇒ `null`, que o chamador traduz em
  `SkyError.LocationUnavailable`;
- funciona com permissão apenas aproximada.

---

## `TimeProvider` — **novo**

```
fun nowEpochSeconds(): Long
```

Único ponto por onde o tempo real entra no sistema. Substituído por valor fixo nos testes.

---

## `ObserveSkyUseCase` — **alterado**

```
suspend operator fun invoke(
    observer: GeoPosition,
    criteria: OverheadCriteria = OverheadCriteria(),
): Result<List<OverheadFlight>>
```

**Passos**: calcular caixas para o raio de `criteria` → pedir ao `FlightRepository` → aplicar
`DetectOverheadFlightsUseCase` com `timeProvider.nowEpochSeconds()` → resolver o operador de cada
resultado via `AirlineDirectory`.

**Pós-condições**:
- resultado ordenado por `elevationDegrees` decrescente;
- falha do repositório propaga-se inalterada, com o mesmo `SkyError`;
- falha do `AirlineDirectory` **não** falha a operação — devolve voos sem operador;
- nenhuma aeronave em solo, sem posição, sem altitude ou com dados obsoletos no resultado.

**Deixou de depender de `SettingsRepository`**: nesta feature os critérios são os valores por
omissão passados pelo ViewModel. Quando a feature de definições existir, é o ViewModel que passa a
alimentar este parâmetro — a assinatura não muda.
