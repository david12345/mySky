# Contrato: decidir se há aviso

## `OverheadNotificationSelector.selectCandidate` — puro

```kotlin
fun selectCandidate(flights: List<OverheadFlight>, thresholdDegrees: Double): OverheadFlight?
```

**Invariantes:**
1. Devolve o de **maior elevação** entre os que estão acima do limiar, ou `null`.
2. `maxByOrNull`, nunca `first()` — a lista chega ordenada, mas depender disso é o acoplamento
   implícito que a 005 já apanhou uma vez.
3. Exatamente no limiar **conta** como acima: a fronteira pertence ao lado do aviso.
4. Lista vazia devolve `null`. Nunca lança.
5. Determinística: sem relógio, sem I/O.

## `DecideOverheadNotificationUseCase` — orquestra

```kotlin
suspend operator fun invoke(flights: List<OverheadFlight>): NotificationDecision
// Notify(flight) | Skip
```

**Invariantes, e a ordem importa:**
1. `Skip` se `notificationsEnabled` for falso. **Primeiro de tudo** — sem isto, uma consulta a Room
   aconteceria a cada ciclo para quem tem a feature desligada.
2. `Skip` se a permissão não estiver concedida **no momento**, lida do sistema e nunca de cache.
3. `Skip` se não houver candidato acima do limiar.
4. `Skip` se o candidato já foi avisado dentro da janela de deduplicação.
5. Caso contrário `Notify(candidato)`.
6. Nunca lança. Uma falha a consultar a persistência resulta em `Skip` — **não avisar** é sempre mais
   seguro do que avisar duas vezes.
7. **Um aviso por ciclo, no máximo.** Cinco aeronaves acima do limiar dão um aviso, não cinco: cinco
   notificações de uma vez seriam motivo para desligar a feature no mesmo minuto.

## `NotificationPermission`

```kotlin
fun interface NotificationPermission { fun isGranted(): Boolean }
```

Sobre `NotificationManagerCompat.areNotificationsEnabled()`, que cobre a permissão de runtime da API
33+ **e** o interruptor clássico de todas as versões. Lido a cada uso; nunca guardado.
