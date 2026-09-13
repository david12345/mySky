# Research: Widget de ecrã inicial

**Feature**: `005-sky-widget` | **Data**: 2026-09-13

Este documento tem duas metades. A primeira são **restrições verificadas** — factos sobre a
plataforma e sobre este repositório que qualquer desenho tem de respeitar, apurados por inspeção e
não por memória. A segunda são as **decisões**, que saem dessas restrições.

---

## Parte 1 — Restrições verificadas

### R1. O estado do Glance é por instância de widget, não por app

Extraído com `javap` do `glance-appwidget-1.2.0.aar` na cache do Gradle:

```
updateAppWidgetState(Context, GlanceStateDefinition<T>, GlanceId, updateBlock)   // exige GlanceId
getAppWidgetState(Context, GlanceStateDefinition<T>, GlanceId)                   // exige GlanceId
```

`GlanceStateDefinition.getLocation(Context, fileKey)` devolve um ficheiro **por chave de widget**.

**Consequência:** guardar o resultado no estado do Glance significa escrever **N cópias** do mesmo
resultado, uma por widget no ecrã. A FR-021 diz que um só trabalho alimenta todos, e as notificações
— que vão precisar do mesmo resultado — não são um widget e não têm `GlanceId` nenhum para consultar.

### R2. Existe forma de saber se há widgets, sem depender de callbacks

```
GlanceAppWidgetManager(context).getGlanceIds(SkyWidget::class.java): List<GlanceId>
GlanceAppWidget.updateAll(context)      // extensão: redesenha todas as instâncias
GlanceAppWidget.updateIf<State>(context, predicate)
```

**Consequência:** a FR-022 (garantir agendamento para quem já tinha o widget da v1.0.0) **não precisa**
de `onEnabled`, que nunca mais dispara para esses utilizadores. Perguntar "há widgets?" é uma chamada
direta, e é a mesma pergunta que a FR-020 faz ao remover o último.

### R3. O receiver do Glance já sobrepõe metade dos callbacks

`GlanceAppWidgetReceiver extends AppWidgetProvider` e **já sobrepõe** `onUpdate`, `onDeleted` e
`onAppWidgetOptionsChanged`. Herda `onEnabled` e `onDisabled` sem os tocar.

**Consequência:** qualquer override de `onUpdate`/`onDeleted` **tem de chamar `super`**, senão o Glance
deixa de desenhar. É a categoria de erro que não dá exceção nenhuma — dá um widget em branco.

### R4. Não há infraestrutura para testar código Android na JVM

`app/build.gradle.kts` tem, em `testImplementation`: `junit`, `mockk`, `turbine`,
`kotlinx-coroutines-test`. **Não tem** `androidx.work:work-testing` nem Robolectric.

**Consequência, e é de desenho e não de testes:** tudo o que decida comportamento tem de viver **fora**
do worker e **fora** do `provideGlance`, ou não é testável de todo. O princípio VI é explícito em que
bugs de agendamento "não produzem erros visíveis — produzem resultados errados com ar de certos, por
isso são cobertos por teste e não por inspeção visual". Duas coisas têm de sair para funções puras:

1. a decisão dos cinco estados e da janela de frescura (FR-003, FR-004, SC-003);
2. a tabela de decisão do worker — que resultado devolver para cada falha (FR-012, FR-013, FR-014).

Acrescentar Robolectric foi ponderado e rejeitado para esta feature: traria uma dependência pesada e um
segundo modo de correr testes para verificar lógica que **não devia estar** dentro do componente
Android, à luz de R4. A restrição está a empurrar para o desenho certo, não a estorvar.

### R5. O orçamento é partilhado entre o widget e o ecrã

Apurado na feature 004 e não repetido aqui: fonte anónima, **400 consultas por dia**, uma por ciclo.

| Cadência | Consultas/dia | Sobra para o ecrã |
|---|---|---|
| 15 min | 96 | 2h32m |
| 30 min | 48 | 2h56m |
| 60 min | 24 | 3h08m |

### R6. A aeronave sai do céu antes de o widget acordar

Uma aeronave atravessa um raio de 30 km (60 km de diâmetro) em **4,0 min** a 250 m/s e **5,0 min** a
200 m/s. O mínimo da plataforma para trabalho periódico é de 15 minutos.

**Consequência:** aos 15 minutos o widget já está fora da janela de frescura quando acorda. Duplicar a
frequência em relação aos 30 minutos não o torna materialmente mais útil — mas custa mais 48 consultas
por dia, que são 24 minutos de tempo de ecrã. É o que sustenta a cadência por omissão de 30 minutos.

### R7. O ciclo que o worker precisa já existe, noutro sítio

`SkySession.refreshOnce()` faz exatamente a sequência de que o worker precisa: verifica permissão,
pede uma posição pontual, chama `ObserveSkyUseCase(observer, criteria)` com os critérios lidos das
definições (AD-018), e traduz o erro. A AD-011 **proíbe** injetar a `SkySession` em `worker/`.

**Consequência:** há risco real de duas implementações da mesma sequência a divergir em silêncio — a
categoria que o princípio VI manda evitar. Resolver isto é o ponto 6 da consulta ao `architect`.

### R8. Alvos de integração confirmados

- `ObserveSkyUseCase(observer: GeoPosition, criteria: OverheadCriteria): Result<List<OverheadFlight>>`
- `LocationRepository.hasLocationPermission(): Boolean` e `getCurrentLocation(): GeoPosition?` (`null`
  sem permissão ou indisponível)
- `SkyError`: `NoConnection`, `FlightServiceUnavailable(httpCode)`, `RateLimited(retryAfterSeconds)`,
  `LocationUnavailable`, `Unexpected(cause)`
- `OverheadFlight`: `aircraft`, `horizontalDistanceMeters`, `bearingDegrees`, `elevationDegrees`,
  `airline?`, `route?`
- `MainActivity` é exportada com intent-filter LAUNCHER — a FR-008 tem alvo.
- Room já está montado de ponta a ponta (entidade, DAO, base de dados, schema exportado, bindings do
  Hilt); só faltam corpos. Relevante para as notificações, não para esta feature.

---

## Parte 2 — Decisões

O subagente `architect` foi consultado (obrigatório pelo `CLAUDE.md` quando há decisão estrutural) e
produziu **AD-023 a AD-028**, registadas no `CLAUDE.md`. Aqui fica só o resumo e o que cada uma resolve;
o texto integral, com alternativas rejeitadas, está lá.

| AD | Decisão | Restrição que a força |
|---|---|---|
| AD-023 | O último resultado vive num porto de domínio (`SkyWidgetRepository`) sobre um DataStore da app, **não** no estado do Glance | R1 |
| AD-024 | Os cinco estados são função pura no `domain`, avaliada **no instante da leitura** | R4, R6 |
| AD-025 | O `worker/` não importa `androidx.glance`; a ponte é `WidgetRefresher` | R4 |
| AD-026 | O agendamento é **reconciliado a partir da verdade**, não contado por eventos | R2, R3 |
| AD-027 | A cadência reagenda pelo `reconcile()`; o custo em orçamento é função pura de domínio | R5 |
| AD-028 | O "um ciclo" sai para `RunSkyCycleUseCase`, partilhado com a `SkySession` | R7 |

### A correção que esta feature faz a uma decisão anterior

A **AD-003 estava errada** neste ponto, e é a primeira vez que uma AD do projeto é corrigida por outra.
Dizia que o widget "lê o último resultado do estado do Glance". A restrição R1 mostra que isso obrigaria
a escrever N cópias do mesmo resultado — uma por widget — e deixaria as notificações sem forma de ler o
que precisam, por não terem `GlanceId`. A AD-023 substitui essa parte; o resto da AD-003 (um worker só,
partilhado com as notificações, sem rede na composição) mantém-se intacto e é o que sustenta esta
feature toda.

### As duas decisões que a restrição de testes forçou

A R4 — não há `work-testing` nem Robolectric — não foi tratada como limitação a contornar, mas como
sinal. Empurrou duas coisas para fora dos componentes Android:

1. **A decisão dos cinco estados** (AD-024) é uma função pura sobre `(snapshot, agora, janela)`. O SC-003
   passa a ser um teste de tabela na JVM, não uma inspeção visual de um widget num telefone.
2. **A tabela de resultados do worker** (AD-028) é o mapeamento de um resultado selado para um `Result`
   do WorkManager. O que sobra dentro do `doWork()` é encadeamento, sem decisão.

Acrescentar Robolectric foi ponderado e rejeitado: traria uma dependência pesada e um segundo modo de
correr testes, para verificar lógica que — pelo princípio VI — não devia estar dentro do componente
Android à partida.

### O que fica anotado para a feature seguinte

A AD-026 escreve a condição de agendamento como `hasAnyWidget() || notificationsEnabled` **desde já**,
apesar de `notificationsEnabled` ser sempre `false` nesta feature. Não é código morto por descuido: é a
diferença entre a feature das notificações acrescentar uma leitura e a feature das notificações
reescrever o ciclo de vida do agendamento. Fica coberto por um teste que fixa o comportamento com o
interruptor ligado, para que a próxima feature herde a garantia em vez da promessa.
