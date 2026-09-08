# Implementation Plan: Origem e destino do voo

**Branch**: `003-flight-route` | **Date**: 2026-09-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-flight-route/spec.md`

## Summary

Cada voo passa a mostrar de onde vem e para onde vai — só a sigla, `LIS → CDG` — na lista e no
detalhe. E o utilizador pode pedir, nas definições, a atualização da tabela que o torna possível.

A dificuldade não é apresentar duas siglas. É que a app **não tem** estes dados: o `/states/all` da
OpenSky dá posição, altitude e indicativo, e mais nada. Foi por isso que a rota ficou fora de âmbito
na 002. Esta feature traz uma fonte nova — território do princípio II — com duas restrições que
excluem a resposta óbvia: não pode aumentar os pedidos por ciclo, nem atrasar a lista. Isso mata a
consulta por avião a uma API de rotas e obriga a uma tabela local.

Cinco decisões, tomadas com o subagente `architect` e registadas como AD-013 a AD-017:

- a tabela é um **ficheiro binário ordenado**, lido por pesquisa binária sobre uma abstração de
  acesso aleatório que serve tanto o asset dentro do APK como o ficheiro atualizado — 585 mil
  registos não cabem num `Map` como os 5 774 operadores cabem;
- a app descarrega um **binário já convertido**, publicado numa release do próprio repositório, e
  nunca as fontes em bruto;
- a substituição é `rename` atómico, o que dá FR-020 **sem locks**;
- `RouteDirectory` é porta própria, e o enriquecimento resolve-se em concorrência;
- a descarga tem worker e scheduler próprios — o que obrigou a **emendar a constituição**.

## Technical Context

**Language/Version**: Kotlin 2.3.21, JVM target 17

**Primary Dependencies**: as existentes. WorkManager (já no projeto, até hoje sem uso real), OkHttp
via Retrofit (já configurado). Esta feature **não** acrescenta nenhuma dependência nova ao
`libs.versions.toml`.

**Storage**: `assets/routes.bin` (~7,6 MB, no APK) e, depois da primeira atualização aceite, um
ficheiro do mesmo formato em `filesDir`. Nenhuma base de dados. Nenhum dado do utilizador.

**Testing**: JUnit 4, MockK, Turbine, kotlinx-coroutines-test. Os testes da tabela usam ficheiros
temporários reais, não duplos do leitor: o que se quer verificar é o comportamento perante
ficheiros maus.

**Target Platform**: Android 8.0 (API 26) a Android 16 (API 36)

**Project Type**: aplicação Android nativa, módulo Gradle único (`:app`), Clean Architecture

**Performance Goals**: consulta de rota abaixo de 1 ms por aeronave (medido: 37 µs); mediana do
arranque a frio não pior do que a **linha de base a medir na T001**, antes de o asset entrar no APK
— sem essa medição SC-005 é inverificável, porque o valor de antes nunca foi apurado (dívida da
001); pedidos ao serviço de voos exatamente iguais aos de
antes (SC-004)

**Constraints**: `assets/routes.bin` tem de ser `noCompress`; o `domain` continua sem importar
`android.*`, Retrofit, Room, Compose ou WorkManager; a app não transfere nada sem o utilizador pedir

**Scale/Scope**: 584 832 rotas, 3 440 aeroportos distintos, +7,6 MB no APK (de 24 para ~32 MB)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Princípio | Gate | Estado |
|---|-----------|------|--------|
| I | Domínio isolado e testável sem rede | `Route` e `Route.callsignKeyOf` são Kotlin puro; `RouteDirectory` e `RouteTableRepository` são interfaces sem tipos de Android. Nenhum ficheiro novo em `domain/` importa `androidx.work.*` | **PASS** |
| II | Fontes de dados atrás de uma interface | A fonte nova é **dado de referência**, não fonte de voos: entra por porta própria (`RouteDirectory`), como a tabela de operadores em AD-007, e não por dentro do `FlightDataSource`. Nenhuma classe acima de `data/local` conhece o formato do ficheiro | **PASS** — ver AD-015 |
| III | Localização com consentimento informado | Nenhuma permissão nova. FR-015 proíbe explicitamente enviar a posição, ou qualquer dado que identifique o utilizador, para obter a rota — o pedido de download não leva nada além do URL | **PASS** |
| IV | Respeitar os limites da plataforma e a bateria | Trabalho pedido pelo utilizador, nunca automático (FR-019); `OneTimeWorkRequest` com `NetworkType.CONNECTED`; nada de rede em `provideGlance` | **PASS — com emenda constitucional**, ver abaixo |
| V | Decisões de arquitetura registadas | AD-013 a AD-017 no `CLAUDE.md`, com AD-003 anotada. A emenda ao princípio IV foi feita com Sync Impact Report e incremento de versão | **PASS** |
| VI | Testar o que pode falhar em silêncio | As três falhas silenciosas desta feature têm teste dedicado: a rota do vizinho numa pesquisa binária mal feita, a divergência entre a chave gerada e a procurada, e o `noCompress` em falta | **PASS** |

### A emenda ao princípio IV

O princípio IV dizia, à letra, "**todos** os `WorkRequest` são criados num único ponto do código".
A AD-016 cria um segundo scheduler, o que viola essa redação — e a constituição prevalece sobre o
`CLAUDE.md`, por isso não era coisa para reinterpretar em silêncio.

Seguiu-se o procedimento que a própria constituição define: a redação passou a "**cada tipo** de
trabalho de fundo tem um único ponto de criação dos seus `WorkRequest`s", com Sync Impact Report e
**incremento para 1.1.0**. A garantia que a regra sempre quis dar mantém-se: continua a não haver
agendamento avulso pelo código, e o sky refresh continua a ter um e um só ponto.

A alternativa era prender a descarga a um scope da aplicação e não tocar na constituição. Era
viável, mas trocava a robustez de uma descarga que sobrevive ao ecrã por uma redação que já era
mais larga do que o problema que resolvia.

**Re-avaliação após a Fase 1**: sem alterações. Tabela de Complexity Tracking vazia.

## Project Structure

### Documentation (this feature)

```text
specs/003-flight-route/
├── plan.md              # este ficheiro
├── spec.md
├── research.md          # D1–D9
├── data-model.md        # Route, formato do ficheiro, estados da atualização
├── quickstart.md
├── checklists/
│   └── requirements.md
├── contracts/
│   ├── route-directory.md      # a porta de leitura
│   └── route-table-update.md   # a atualização e as garantias da substituição
└── tasks.md             # 49 tarefas
```

### Source Code (repository root)

```text
tools/routes/
└── build_routes_bin.py                    # novo — irmão de tools/airlines/

app/src/main/
├── assets/routes.bin                      # novo — ~7,6 MB, noCompress
└── java/com/mysky/app/
    ├── domain/
    │   ├── model/
    │   │   ├── Route.kt                   # novo — + callsignKeyOf
    │   │   ├── RouteUpdateState.kt        # novo
    │   │   └── OverheadFlight.kt          # + route: Route?
    │   ├── repository/
    │   │   ├── RouteDirectory.kt          # novo
    │   │   └── RouteTableRepository.kt    # novo
    │   └── usecase/ObserveSkyUseCase.kt   # enriquecimento concorrente
    ├── data/
    │   ├── local/
    │   │   ├── FileRouteDirectory.kt      # novo — pesquisa binária
    │   │   ├── RouteTableFile.kt          # novo — cabeçalho, validação, rename
    │   │   ├── RouteTableReader.kt        # novo — acesso aleatório, 2 implementações
    │   │   └── RouteTableSource.kt        # novo — asset vs. filesDir
    │   └── route/RouteTableRepositoryImpl.kt   # novo — traduz WorkInfo
    ├── worker/
    │   ├── RouteTableUpdateWorker.kt      # novo
    │   └── RouteTableUpdateWorkScheduler.kt    # novo
    ├── presentation/
    │   ├── main/FlightRow.kt              # + a linha da rota
    │   ├── detail/FlightDetailScreen.kt   # + a rota
    │   ├── format/RouteFormatting.kt      # novo — "LIS → CDG", partilhado
    │   └── settings/                      # SettingsUiState, ViewModel e ecrã reais
    └── di/RouteModule.kt                  # novo
```

**Structure Decision**: módulo único `:app`, três camadas. O único acrescento estrutural é
`data/route/` para o repositório da atualização — não cabe em `data/local/` (que é leitura de
ficheiros) nem em `data/source/` (que é fonte de voos, princípio II).

## O que não muda

O ciclo de observação, a `SkySession`, a deteção, a tabela de operadores e a camada de rede da
OpenSky. Esta feature acrescenta um campo ao `OverheadFlight` e um passo ao enriquecimento; não toca
em nada do que as features 001 e 002 fixaram. Os 171 testes existentes são a rede de segurança.

## Riscos identificados

| Risco | Porque é grave | Mitigação |
|---|---|---|
| Ler o asset como se fosse um ficheiro | `RandomAccessFile` não serve para um asset dentro do APK: é preciso o descritor com deslocamento base. Descobrir isto a meio da Fase 2, que é indivisível, é o pior momento | `RouteTableReader` com duas implementações, fixado na T010 |
| `noCompress` em falta (FR-013) | A leitura por deslocamento deixa de funcionar **em silêncio**: o ficheiro passa a ser descomprimido inteiro para memória e nada dá erro | Gate no quickstart que inspeciona o método de armazenamento no APK |
| Chave gerada ≠ chave procurada | Tabela inteira de rotas que nunca são encontradas, sem erro em lado nenhum | `Route.callsignKeyOf` no domínio, usada pelos dois lados; teste que corre a mesma normalização |
| Pesquisa binária com erro de comparação | Não devolve "não encontrado" — devolve **a rota do vizinho**, de outro voo, com ar de certa | Invariante 2 do contrato, com teste dedicado |
| Consultas encadeadas por aeronave | Duas pesquisas de disco em série por avião, dezenas de aviões: invisível em teste, atraso real no dispositivo | Resolução concorrente, fixada na AD-015 |
| Substituição interrompida | Uma tabela meio escrita é pior do que uma velha | `rename` atómico depois de validar; testes com interrupção nos quatro pontos |
| Transferência não pedida | Um `init` bem-intencionado e a app passa a gastar dados sozinha | SC-010 testado pela negativa, numa sessão inteira |

## Trade-off aceite: o peso do APK

+7,6 MB permanentes, de 24 para ~32 MB, para um dado que cada instalação usa numa fração ínfima —
dezenas de rotas por sessão, de 585 mil disponíveis. É consciente, e é um caminho só de ida: o
conjunto de dados só cresce.

As alternativas foram pesadas em D1 e D5. Reduzir por região exigiria saber onde o utilizador vive
antes de ele abrir a app; descarregar em vez de embarcar deixaria sem rotas quem instala e nunca vai
às definições. Fica anotado para reavaliar se algum dia o tamanho se tornar queixa real.

## Complexity Tracking

> Sem violações constitucionais por justificar. A única tensão com a constituição foi resolvida
> emendando-a pelo procedimento próprio, não abrindo exceção. Tabela vazia.
