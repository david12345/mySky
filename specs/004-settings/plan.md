# Implementation Plan: Definições

**Branch**: `004-settings` | **Date**: 2026-09-10 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-settings/spec.md`

## Summary

O utilizador passa a ajustar o que conta como "o meu céu" — raio, ângulo mínimo acima do horizonte,
altitude mínima — e em que unidades o vê. As escolhas persistem, aplicam-se sem reiniciar, e nenhuma
combinação permitida parte nada.

**É a feature que estava desenhada desde o início para acontecer.** O `SkySettings` existe desde o
esqueleto, com os campos e os valores de origem certos. O `ObserveSkyUseCase` recebe os critérios por
parâmetro desde a AD-009, com uma nota a dizer que a feature de definições passaria a alimentá-los. O
ecrã existe desde a 003, com uma entrada real lá dentro e a AD-017 a fixar como cresce. Esta feature
liga o que já estava ligado por dentro.

Duas coisas a destacar antes de tudo. **Uma investigação desmentiu um requisito da especificação**: o
FR-013 pedia proteção contra as definições esgotarem o orçamento da fonte de voos, e apurou-se que o
orçamento não depende delas — foi reescrito (D1). E a promessa da AD-009 fecha-se por um caminho
diferente do que ali estava previsto: não é o ViewModel a alimentar o parâmetro, é a própria sessão a
perguntar (AD-018), porque entretanto a AD-011 mudou o dono do laço.

## Technical Context

**Language/Version**: Kotlin 2.3.21, JVM target 17

**Primary Dependencies**: as existentes. DataStore Preferences (já no catálogo e no projeto, até hoje
sem uso real). Esta feature **não** acrescenta nenhuma dependência nova.

**Storage**: DataStore Preferences, cinco chaves. Nenhuma base de dados, nenhuma migração — a
degradação de valores inválidos é aplicada em cada leitura, o que dispensa versionar o esquema
(AD-022).

**Testing**: JUnit 4, MockK, Turbine, kotlinx-coroutines-test. Os testes do repositório usam um
DataStore real sobre ficheiro temporário, não um duplo: o que se quer verificar é o comportamento
perante armazenamento ausente e valores fora dos limites.

**Target Platform**: Android 8.0 (API 26) a Android 16 (API 36)

**Project Type**: aplicação Android nativa, módulo Gradle único (`:app`), Clean Architecture

**Performance Goals**: uma alteração reflete-se em menos de um ciclo (SC-001); com os valores nos
extremos, a lista aparece no mesmo tempo de hoje (SC-004)

**Constraints**: o `domain` continua sem importar `android.*`, Retrofit, Room, Compose ou
WorkManager; o custo por consulta à fonte de voos não muda (SC-003); nenhum controlo toca no
intervalo do trabalho periódico (FR-015)

**Scale/Scope**: cinco preferências, um ecrã já existente a estender, três estados de ecrã a
alargar, e um stub de repositório a implementar

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Princípio | Gate | Estado |
|---|-----------|------|--------|
| I | Domínio isolado e testável sem rede | `SkySettings.coerced()` e o cálculo do alcance útil são Kotlin puro. O alcance útil é a inversa de uma função que já existe no `GeoCalculator` — não há geometria nova. O `SettingsRepository` é interface sem tipos de Android | **PASS** |
| II | Fontes de dados atrás de uma interface | Nenhuma fonte nova. Nada acima de `data/settings` sabe que existe DataStore | **PASS** |
| III | Localização com consentimento informado | Nenhuma permissão nova. As definições não tocam em permissões; a de segundo plano continua a pertencer às notificações | **PASS** |
| IV | Respeitar os limites da plataforma e a bateria | A cadência não é configurável nesta feature (FR-015), e o intervalo do trabalho periódico não recebe controlo nenhum. Nenhum `WorkRequest` novo | **PASS** |
| V | Decisões de arquitetura registadas | AD-018 a AD-022 no `CLAUDE.md`. Nenhum princípio muda, logo a constituição **não** é emendada — ao contrário da 003 | **PASS** |
| VI | Testar o que pode falhar em silêncio | As duas falhas silenciosas desta feature têm teste dedicado: um controlo ligado ao evento errado, que gasta o orçamento num arrasto sem dar sinal; e um `coerced()` demasiado zeloso, que "corrigiria" escolhas legítimas | **PASS** |

**Re-avaliação após a Fase 1**: sem alterações. Complexity Tracking vazia.

## Project Structure

### Documentation (this feature)

```text
specs/004-settings/
├── plan.md              # este ficheiro
├── spec.md              # 17 requisitos, 7 critérios
├── research.md          # D1–D7
├── data-model.md        # limites derivados com contas
├── quickstart.md
├── checklists/
│   └── requirements.md
├── contracts/
│   ├── settings-repository.md   # a porta de preferências
│   └── settings-ui.md           # o ecrã e as suas invariantes
└── tasks.md             # /speckit-tasks — ainda não criado
```

### Source Code (repository root)

```text
app/src/main/java/com/mysky/app/
├── domain/
│   └── model/
│       ├── SkySettings.kt              # + limites, + coerced()
│       └── SkyRange.kt                 # novo — o alcance útil, função pura
├── data/settings/
│   └── SettingsRepositoryImpl.kt       # deixa de ser stub
├── presentation/
│   ├── settings/
│   │   ├── SettingsUiState.kt          # + settings
│   │   ├── SettingsViewModel.kt        # + escritas e reposição
│   │   └── SettingsScreen.kt           # + os controlos
│   ├── format/FlightFormatting.kt      # + parâmetro de unidade
│   ├── main/{MainUiState,MainScreen,FlightRow}.kt      # + unidades
│   ├── detail/{FlightDetailUiState,FlightDetailScreen}.kt  # idem
│   └── sky/SkySession.kt               # lê critérios por ciclo
└── di/RepositoryModule.kt              # binding do SettingsRepository
```

**Structure Decision**: módulo único, três camadas, sem pacotes novos. O único ficheiro
verdadeiramente novo é o do alcance útil; todo o resto é implementar um stub e alargar o que existe.

## O que muda em código já validado

Esta feature toca em ficheiros das três features anteriores, todos com testes:

- **`SkySession`** ganha uma dependência e uma leitura por ciclo (AD-018);
- **`FlightFormatting`** ganha um parâmetro de unidade em cada função de conversão — o que altera
  todos os pontos de chamada na lista, no detalhe e nos testes;
- **`MainUiState`** e o estado do detalhe ganham dois campos.

Os **232 testes existentes são a rede de segurança**. Nenhum comportamento verificado por eles pode
mudar: os valores de origem são exatamente os que estão hoje fixos no código, portanto um teste que
falhe significa que a alteração está errada.

## Riscos identificados

| Risco | Porque é grave | Mitigação |
|---|---|---|
| Controlo ligado ao evento de cada movimento | Um arrasto grava dezenas de vezes e gasta o dia de créditos. **Funciona perfeitamente** e não dá sinal nenhum | Invariante 3 do contrato de UI, com teste; passo 8 do quickstart com o Network Inspector |
| `coerced()` demasiado zeloso | "Corrigiria" escolhas legítimas do utilizador em silêncio | Invariante 3 do contrato do repositório: um valor dentro dos limites passa **intacto** |
| Ligar um controlo ao intervalo do trabalho periódico | Está na mesma classe que os outros campos e pertence a outra feature. Violaria FR-015 e o mínimo de 15 minutos | Gate no `tasks.md` e invariante 9 do contrato de UI |
| Estreitar o intervalo de um controlo em função do outro | Pareceria ajudar e move o limite debaixo do dedo do utilizador | AD-021 e invariante 8, que testa o **contrário** do que a intuição sugere |
| A reposição apagar a tabela de rotas | As duas coisas vivem no mesmo ecrã | Portas distintas (AD-017); invariante 7 do contrato do repositório |

## Uma dívida que esta feature é a última a poder pagar

A mediana do tempo de arranque **nunca foi medida**, em três features. O SC-005 da 003 ficou
inverificável por isso. Esta feature acrescenta uma leitura de disco ao primeiro ciclo (AD-018) — é
pequena, mas é mais uma.

A secção 0 do quickstart mede-a **antes** de qualquer alteração. Não é requisito desta feature; é a
última oportunidade fácil de fixar um número que já falta a duas.

## Complexity Tracking

> Sem violações constitucionais a justificar. Ao contrário da 003, esta feature **não** exigiu
> emendar a constituição. Tabela vazia.
