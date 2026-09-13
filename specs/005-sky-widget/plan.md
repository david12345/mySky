# Implementation Plan: Widget de ecrã inicial

**Branch**: `005-sky-widget` | **Date**: 2026-09-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/005-sky-widget/spec.md`

## Summary

O widget mostra a aeronave de maior elevação que o trabalho de fundo encontrou, **com o instante da
observação**, e só fala no presente enquanto isso for plausível. O trabalho de fundo existe se e só se
houver widget no ecrã, corre com a cadência escolhida pelo utilizador, e é o mesmo que as notificações
vão usar.

A abordagem em cinco movimentos, todos derivados de restrições verificadas (ver [research.md](./research.md)):

1. **Extrair o ciclo** que a `SkySession` já implementa para `RunSkyCycleUseCase` (AD-028), para o
   worker não ser uma segunda cópia da mesma sequência.
2. **Um porto de domínio** (`SkyWidgetRepository`) guarda o último resultado como facto bruto (AD-023).
3. **Uma função pura** classifica o facto em cinco estados no instante em que o widget é composto
   (AD-024) — é aqui que a honestidade da feature vive.
4. **Um coordenador** reconcilia o agendamento a partir do estado real (AD-026), o que resolve de graça
   o widget partido que a v1.0.0 deixou no ecrã de quem o adicionou.
5. **A cadência** ganha controlo nas definições, com o custo em tempo de ecrã à vista (AD-027).

## Technical Context

**Linguagem**: Kotlin 2.3.21, JVM target 17
**Dependências novas**: nenhuma. Glance 1.2.0, WorkManager 2.10.5, DataStore e Hilt já estão no
catálogo e no `build.gradle.kts`. O widget e o worker já existem como esqueleto com `TODO(feature/widget)`.
**Armazenamento**: DataStore de preferências próprio para o snapshot do widget (AD-023); o DataStore das
definições mantém-se separado.
**Testes**: JUnit + MockK + Turbine + kotlinx-coroutines-test, tudo na JVM. Sem Robolectric e sem
`work-testing`, por decisão — ver R4 no research.
**Plataforma**: Android, minSdk 26, targetSdk 36.
**Restrições**: mínimo de 15 min para trabalho periódico; 400 consultas/dia partilhadas com o ecrã;
nenhum serviço em primeiro plano.

## Constitution Check

| Princípio | Como esta feature o cumpre |
|---|---|
| **I. Domínio isolado e testável sem rede** | `RunSkyCycleUseCase` e `SkyWidgetState.evaluate` vivem no `domain` e testam-se na JVM. O `SkyWidgetSnapshot` é modelo de domínio, sem tipos Android. |
| **II. Fontes atrás de uma interface** | Nada muda. O worker chega à rede pelo `ObserveSkyUseCase`, como o ecrã. |
| **III. Localização com consentimento informado** | O widget **nunca** pede permissão — não tem como mostrar o rationale. Mostra o estado e encaminha para a app. Sem permissão o trabalho termina sem repetir. |
| **IV. Limites da plataforma e bateria** | Mínimo de 15 min respeitado e imposto por `coerced()`. Sem foreground service. Sem rede na composição (AD-023: leitura local é permitida). Um único ponto de criação de `WorkRequest` (FR-023) e, novo, um único ponto de **decisão** de agendar (AD-026). A UI mostra sempre quando os dados foram obtidos — que é literalmente o que o princípio exige e o que a FR-002 codifica. |
| **V. Decisões registadas** | AD-023 a AD-028 escritas no `CLAUDE.md` antes de haver código. Inclui a **correção da AD-003**, que estava errada quanto ao estado do Glance. |
| **VI. Testar o que falha em silêncio** | É o eixo do plano. A R4 forçou a decisão dos cinco estados e a tabela de falhas do worker para funções puras, precisamente porque bugs de agendamento não dão erro visível. O SC-003 vira teste de tabela. |

**Veredicto: passa, sem desvios a justificar.** A única alteração a uma decisão anterior (AD-003) é
feita pelo procedimento correto — nova AD que a corrige explicitamente, com a razão verificável.

## Project Structure

### Documentation (this feature)

```
specs/005-sky-widget/
├── spec.md
├── plan.md                 ← este ficheiro
├── research.md             ← restrições verificadas + decisões
├── data-model.md
├── contracts/
│   ├── sky-widget-repository.md
│   ├── sky-widget-state.md
│   └── background-work.md
├── quickstart.md
├── tasks.md                ← /speckit-tasks
└── checklists/requirements.md
```

### Source Code

```
app/src/main/java/com/mysky/app/
├── domain/
│   ├── model/
│   │   ├── SkyWidgetSnapshot.kt      NOVO  facto bruto persistido
│   │   ├── SkyWidgetState.kt         NOVO  os 5 estados + evaluate() pura
│   │   └── SkyBudget.kt              NOVO  custo da cadência (400 consultas/dia)
│   ├── repository/
│   │   └── SkyWidgetRepository.kt    NOVO  porto do último resultado
│   └── usecase/
│       └── RunSkyCycleUseCase.kt     NOVO  o "um ciclo", partilhado
├── data/
│   └── widget/
│       └── SkyWidgetRepositoryImpl.kt NOVO  DataStore próprio
├── worker/
│   ├── SkyRefreshWorker.kt           corpo   (esqueleto existe)
│   ├── SkyWorkScheduler.kt           corpo   (esqueleto existe)
│   ├── WidgetRefresher.kt            NOVO  interface, sem Glance
│   ├── WidgetPresenceCheck.kt        NOVO  interface, "há widgets?"
│   └── SkyBackgroundWorkCoordinator.kt NOVO  reconcile()
├── widget/
│   ├── SkyWidget.kt                  corpo   (esqueleto existe)
│   ├── SkyWidgetReceiver.kt          corpo   (esqueleto existe)
│   ├── RefreshSkyWidgetAction.kt     corpo   (esqueleto existe)
│   ├── GlanceWidgetRefresher.kt      NOVO  implementa as duas interfaces
│   └── SkyWidgetContent.kt           NOVO  composables por estado
├── presentation/
│   ├── sky/SkySession.kt             ALTERADO  delega no RunSkyCycleUseCase
│   └── settings/                     ALTERADO  cadência + custo
└── di/
    └── WidgetModule.kt               NOVO  bindings
```

**Nota sobre `presentation/settings`**: estende o `SettingsUiState` existente, como a AD-017 fixou —
não cria um segundo estado.

## Complexity Tracking

Três coisas neste plano parecem mais complicadas do que o pedido, e cada uma paga-se:

**`RunSkyCycleUseCase` é uma classe nova para código que já existe.** Podia-se copiar a sequência para
o worker e acabar. A razão de não o fazer é empírica e desta app: a revisão da 004 apanhou um número
escrito de duas maneiras que tinha divergido, e custou um defeito visível a todos os utilizadores.
Duas cópias de "verificar permissão, obter posição, ler critérios, chamar o caso de uso" divergiriam da
mesma forma, mais devagar e com pior sintoma.

**Duas interfaces (`WidgetRefresher`, `WidgetPresenceCheck`) para o que seriam duas chamadas diretas.**
Pagam-se em R4: sem elas, o coordenador e o worker deixam de ser testáveis na JVM, e passam a precisar
de uma dependência de teste que o projeto não tem. A fronteira fica onde a dependência pesada começa,
como em todas as portas já existentes.

**Dois canais de estado no widget** — o porto de domínio para o facto, e o `GlanceStateDefinition` para
o "a atualizar" do toque. Parece um a mais. Não é: um é um facto sobre o céu, partilhado e durável; o
outro é estado de UI de uma instância concreta, efémero por natureza. Juntá-los faria o "a atualizar" de
um widget aparecer nos outros, e sobreviver à morte do processo — que é precisamente o que não deve
acontecer.

**O que deliberadamente não se faz:** Robolectric (ver R4), histórico de avistamentos, notificações
(feature seguinte), e qualquer tentativa de aproximar a cadência do tempo real.
