# Contrato: trabalho de fundo

## `RunSkyCycleUseCase` — o "um ciclo", partilhado (AD-028)

```kotlin
suspend operator fun invoke(): SkyCycleResult

sealed interface SkyCycleResult {
    data class Success(val flights: List<OverheadFlight>, val observedAtEpochSeconds: Long)
    data object NoPermission
    data class Failure(val error: SkyError)
}
```

Faz o que o `SkySession.refreshOnce()` já fazia, **sem o laço**: verifica permissão, obtém posição, lê
`settings.first().toCriteria()` (AD-018), chama o `ObserveSkyUseCase`. Não conhece WorkManager, Glance,
`StateFlow` nem cadência.

**Invariantes:**
1. Nunca lança — tudo sai em `Failure`, incluindo o inesperado.
2. Sem permissão devolve `NoPermission` **sem** ir à rede e sem gastar consulta.
3. Localização indisponível com permissão dada é `Failure(LocationUnavailable)`, distinto do anterior.
4. Cada invocação lê os critérios **uma vez**, no início — um snapshot imutável por ciclo (AD-018).

## `SkyWorkScheduler` — o único ponto de criação de `WorkRequest` do sky refresh

```kotlin
fun schedulePeriodicRefresh(settings: SkySettings)   // UPDATE, nome único, NetworkType.CONNECTED
fun cancelPeriodicRefresh()
fun requestImmediateRefresh()                        // OneTimeWork, KEEP
```

**Invariantes:**
1. O período nunca é inferior a 15 minutos. Garantido a montante por `coerced()` (AD-022), e o
   scheduler nunca vê um valor por corrigir.
2. `enqueueUniquePeriodicWork` com `ExistingPeriodicWorkPolicy.UPDATE` e um nome fixo → no máximo um
   trabalho periódico, qualquer que seja o número de widgets (FR-021), e uma mudança de cadência
   substitui em vez de acumular (FR-025).
3. `requestImmediateRefresh` usa `ExistingWorkPolicy.KEEP` → dois toques com pedido em curso não
   duplicam (FR-017).

## `SkyBackgroundWorkCoordinator.reconcile()` — o único ponto de decisão (AD-026)

```kotlin
suspend fun reconcile()
```

Decide a partir do estado real, não de eventos:

```
deveExistir = widgetPresence.hasAnyWidget() || settings.notificationsEnabled
se deveExistir  -> scheduler.schedulePeriodicRefresh(settings)
senão           -> scheduler.cancelPeriodicRefresh()
```

**Invariantes:**
1. **Idempotente.** Chamar N vezes seguidas tem o mesmo efeito que chamar uma.
2. **Não conta eventos.** É isto que resolve a FR-022: quem tinha o widget da v1.0.0 não recebe
   `onEnabled` outra vez, mas o `reconcile()` do arranque encontra o widget e agenda.
3. **`notificationsEnabled` já está na condição**, embora seja sempre `false` nesta feature. A feature
   seguinte acrescenta chamadas, não reescreve a decisão.

**Chamado de:** `onEnabled`, `onDisabled` e `onUpdate` do receiver; `Application.onCreate()`; e a escrita
da cadência nas definições (AD-027).

## `SkyRefreshWorker.doWork()` — encadeamento, sem decisão

| `SkyCycleResult` | grava snapshot | `Result` | Porquê |
|---|---|---|---|
| `NoPermission` | `PermissionMissing` | `success()` | repetir não resolve nada |
| `Failure(NoConnection)` | não grava | `retry()` | transitório |
| `Failure(LocationUnavailable)` | não grava | `retry()` | transitório |
| `Failure(RateLimited)` | não grava | `success()` | insistir gasta orçamento já esgotado (AD-010) |
| `Failure(FlightServiceUnavailable)` | não grava | `retry()` | o serviço pode voltar |
| `Failure(Unexpected)` | não grava | `failure()` | não é acionável por retry |
| `Success` com voos | `Flights` | `success()` | |
| `Success` vazio | `EmptySky` | `success()` | |

Depois de gravar, chama `widgetRefresher.refreshAll()`. **`failure()` num `PeriodicWorkRequest` não
cancela o trabalho** — o período seguinte corre na mesma; é por isso que é seguro usá-lo para o
inesperado.
