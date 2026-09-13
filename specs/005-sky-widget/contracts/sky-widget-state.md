# Contrato: `SkyWidgetState.evaluate`

Função pura. Sem relógio implícito, sem I/O, sem Android. É onde o SC-003 é garantido.

```kotlin
fun evaluate(
    snapshot: SkyWidgetSnapshot?,
    nowEpochSeconds: Long,
    freshnessWindowSeconds: Long = DEFAULT_FRESHNESS_WINDOW_SECONDS,
): SkyWidgetState
```

## Invariantes

1. **Sem snapshot, `NoDataYet`.** Nunca inventa um estado a partir de nada.
2. **A permissão manda sobre a idade.** Um snapshot `PermissionMissing` produz `PermissionMissing`
   qualquer que seja o `now` — nunca `Stale`.
3. **`Fresh` se e só se `now - observedAt <= janela`.** Exatamente na fronteira conta como fresco.
4. **Um instante no futuro é fresco, não negativo.** Acontece com o relógio do dispositivo atrasado
   face ao da fonte, e já foi tratado assim na 002.
5. **A função nunca lança**, para nenhuma combinação de entradas, incluindo janela zero ou negativa.
6. **Determinismo:** as mesmas entradas dão sempre a mesma saída. Não lê relógio nenhum.

## Tabela de decisão

| snapshot | idade | resultado |
|---|---|---|
| `null` | — | `NoDataYet` |
| `PermissionMissing` | qualquer | `PermissionMissing` |
| `EmptySky` | ≤ janela | `EmptySky(isFresh = true)` |
| `EmptySky` | > janela | `EmptySky(isFresh = false)` |
| `Flights` | ≤ janela | `Fresh` |
| `Flights` | > janela | `Stale` |
| qualquer com dados | negativa (futuro) | tratado como 0 |

## O que a apresentação garante por cima

Nenhum texto de presente pode ser produzido a partir de `Stale` nem de `EmptySky(isFresh = false)`.
Isto é verificável sem um dispositivo: a escolha do recurso de texto é uma função do estado.
