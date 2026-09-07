# Implementation Plan: Detalhe de um voo

**Branch**: `002-flight-detail` | **Date**: 2026-09-07 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-flight-detail/spec.md`

## Summary

O toque numa entrada da lista passa a abrir um ecrã com tudo o que a app já sabe sobre aquela
aeronave, a atualizar-se sozinho enquanto está visível, e a dizer de forma explícita quando o avião
sai do céu do utilizador. Nenhuma fonte de dados nova: o ecrã mostra o `OverheadFlight` que a lista
já produz.

A dificuldade não está no ecrã — está em ter dois ecrãs a olhar para a mesma observação. A
especificação exige que mostrem os mesmos valores no mesmo instante (FR-013) e que estarem os dois
vivos não duplique os pedidos ao serviço (FR-017), enquanto a AD-008 pôs o laço de atualização
dentro do `MainViewModel`, que morre quando a lista sai de composição. Duas decisões, tomadas com o
subagente `architect` e registadas como AD-011 e AD-012:

- o laço sai do `MainViewModel` para uma `SkySession` partilhada, na `presentation`, cuja contagem
  de subscritores passa a somar os dois ecrãs — FR-013 e FR-017 saem ambas da forma da solução, sem
  mutex nem cache;
- distinguir "saiu do céu" de "a atualização falhou" é uma redução pura no `domain`, não uma
  comparação improvisada na UI.

## Technical Context

**Language/Version**: Kotlin 2.3.21, JVM target 17

**Primary Dependencies**: as existentes, sem acrescentos. Jetpack Compose (BOM 2025.12.01,
Material 3), Hilt 2.58, Coroutines 1.11.0, Lifecycle 2.9.4, Navigation Compose. Esta feature
**não** introduz nenhuma dependência nova nem mexe no `libs.versions.toml`.

**Storage**: nenhuma. Nada é escrito em disco; a memória de presença vive na sessão e perde-se com
o processo, por desenho (D2).

**Testing**: JUnit 4, MockK, Turbine, kotlinx-coroutines-test com tempo virtual — indispensável
para provar que dois coletores geram um pedido e não dois.

**Target Platform**: Android 8.0 (API 26) a Android 16 (API 36)

**Project Type**: aplicação Android nativa, módulo Gradle único (`:app`), Clean Architecture em
três camadas

**Performance Goals**: detalhe preenchido em menos de 1 s a partir do toque, sem estado de
carregamento intermédio (SC-001); atualização a cada 30 s; nenhuma atividade de rede 60 s depois
de a app ir para segundo plano com o detalhe aberto (SC-005)

**Constraints**: ter o detalhe aberto não pode aumentar o número de pedidos face a ter só a lista
(SC-008); os valores dos dois ecrãs têm de coincidir ao dígito (SC-002); o `domain` continua sem
importar `android.*`, Retrofit, Room, Compose ou WorkManager

**Scale/Scope**: um ecrã novo com seis situações de apresentação, uma classe partilhada nova, um
caso de uso puro novo, e uma alteração de forma ao `MainUiState` que não altera o comportamento do
ecrã principal

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Princípio | Gate | Estado |
|---|-----------|------|--------|
| I | Domínio isolado e testável sem rede | O caso de uso novo (`TrackFlightPresenceUseCase`) é Kotlin puro, recebe `nowEpochSeconds` por parâmetro e o estado anterior por parâmetro — sem relógio e sem memória lá dentro. `FlightPresence` é um tipo de domínio sem dependências | **PASS** — ver D2 |
| II | Fontes de dados atrás de uma interface | Nenhuma fonte nova; nenhuma classe nova conhece a OpenSky. A `SkySession` fala com `ObserveSkyUseCase`, não com o repositório de voos | **PASS** |
| III | Localização com consentimento informado | Nenhuma permissão nova. O detalhe não pede permissões; o fluxo de rationale fica todo no `MainViewModel` | **PASS** |
| IV | Respeitar os limites da plataforma e a bateria | Cadência inalterada; a partilha **reduz** os pedidos face à alternativa ingénua. A sessão para 5 s depois de o último ecrã sair de composição, e nunca é injetada em `worker/` nem em `widget/` | **PASS** — ver contrato da sessão |
| V | Decisões de arquitetura registadas | AD-011 e AD-012 registadas no `CLAUDE.md`, com a AD-008 anotada a apontar para a AD-011. Nenhum princípio muda, logo a constituição não é emendada | **PASS** |
| VI | Testar o que pode falhar em silêncio | As duas falhas silenciosas desta feature têm teste dedicado: confundir ciclo falhado com saída do céu (invariante 3 do contrato de UI) e subscrever a sessão de forma a nunca parar o laço (invariante 4 do contrato da sessão) | **PASS** |

**Re-avaliação após a Fase 1**: sem alterações. O desenho não introduziu nenhuma violação nova e a
tabela de Complexity Tracking permanece vazia.

## Project Structure

### Documentation (this feature)

```text
specs/002-flight-detail/
├── plan.md              # este ficheiro
├── spec.md
├── research.md          # D1–D6
├── data-model.md
├── quickstart.md
├── checklists/
│   └── requirements.md
├── contracts/
│   ├── sky-session.md       # o detentor partilhado do laço
│   └── flight-detail-ui.md  # estado, precedência e invariantes do ecrã
└── tasks.md             # /speckit-tasks — ainda não criado
```

### Source Code (repository root)

```text
app/src/main/java/com/mysky/app/
├── domain/
│   ├── model/
│   │   └── FlightPresence.kt              # novo — selado, 3 variantes
│   └── usecase/
│       └── TrackFlightPresenceUseCase.kt  # novo — redução pura
├── presentation/
│   ├── sky/                               # pacote novo
│   │   ├── SkySession.kt                  # o laço, @ActivityRetainedScoped
│   │   └── SkyObservation.kt              # + LoadPhase, vindo de main/
│   ├── main/
│   │   ├── MainViewModel.kt               # perde o laço; consumidor fino
│   │   ├── MainUiState.kt                 # passa a compor SkyObservation
│   │   └── MainScreen.kt                  # inalterado no comportamento
│   ├── detail/
│   │   ├── FlightDetailViewModel.kt       # deixa de ser stub
│   │   ├── FlightDetailUiState.kt         # novo
│   │   └── FlightDetailScreen.kt          # deixa de ser stub
│   └── main/format/
│       └── FlightFormatting.kt            # + razão de subida, + rumo
└── di/
    └── SkyModule.kt                       # novo, se o binding do ciclo de vida o exigir

app/src/test/java/com/mysky/app/
├── domain/usecase/TrackFlightPresenceUseCaseTest.kt
├── presentation/sky/SkySessionTest.kt
├── presentation/detail/FlightDetailViewModelTest.kt   # substitui o da 001
└── presentation/main/...                              # os existentes, adaptados à nova forma
```

**Structure Decision**: mantém-se o módulo único `:app` com as três camadas. O único acrescento à
estrutura é o pacote `presentation/sky/`, que existe porque a sessão não pertence a nenhum dos dois
ecrãs — pô-la em `main/` faria o detalhe depender do pacote da lista, que é precisamente o
acoplamento que a AD-011 evita.

## O que não muda, e porquê importa

O comportamento do ecrã principal. O `MainUiState` muda de **forma** (passa a compor
`SkyObservation`), mas a tabela de precedência, os estados derivados e as transições legais do
contrato da 001 ficam iguais. Os testes da 001 são a rede de segurança desta refatoração: se algum
falhar, é a alteração que está errada.

O `ObserveSkyUseCase`, o `FlightRepositoryImpl`, a tabela de operadores e toda a camada de dados
ficam intocados. Esta feature não desce abaixo da `presentation`, com a única exceção do caso de
uso puro novo.

## Riscos identificados

| Risco | Porque é grave | Mitigação |
|---|---|---|
| Subscrever a sessão com `launch { collect }` em vez de `stateIn(WhileSubscribed)` | O laço nunca pára: rede em segundo plano, sem sinal visível. Viola FR-016 e SC-005 | Regra explícita no contrato da sessão; teste da invariante 4; verificação manual no passo 6 do quickstart |
| Confundir "ciclo falhou" com "saiu do céu" | Perder a rede passaria a anunciar que o avião partiu | `null` distinto de lista vazia na assinatura do caso de uso; invariante 3 do contrato de UI |
| Os dois ecrãs divergirem num arredondamento | SC-002 falha de forma que ninguém nota | Uma só camada de formatação (D3); teste que compara os dois ecrãs |
| O escopo interno da sessão não ser cancelado | Job pendurado para lá do fim da Activity | `ActivityRetainedLifecycle.addOnClearedListener`, verificado por teste |
| A sessão vazar para `worker/` ou `widget/` | Rompe a fronteira da AD-003 | Proibição explícita no contrato; o `reviewer` verifica no fim |

## Ineficiência conhecida e aceite

Com o ecrã aberto, a `SkySession` e o futuro `SkyRefreshWorker` podem pedir dados em instantes
próximos. SC-008 desta feature fala de lista contra detalhe, não de primeiro plano contra segundo
plano, e o worker ainda não existe. Fica anotado para a feature do widget decidir se vale a pena
coordenar — não é regressão desta decisão.

## Complexity Tracking

> Sem violações constitucionais a justificar. Tabela deliberadamente vazia.
