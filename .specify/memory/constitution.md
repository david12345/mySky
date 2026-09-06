<!--
Sync Impact Report
- Versão: (template não preenchido) → 1.0.0
- Ratificação inicial do projeto mySky.
- Princípios adicionados:
  I. Domínio isolado e testável sem rede
  II. Fontes de dados atrás de uma interface
  III. Localização com consentimento informado
  IV. Respeitar os limites da plataforma e a bateria
  V. Decisões de arquitetura registadas
  VI. Testar o que pode falhar em silêncio
- Secções adicionadas: Restrições de Plataforma Android; Fluxo de Desenvolvimento; Governance
- Secções removidas: nenhuma
- TODOs pendentes: nenhum
-->

# mySky Constitution

## Core Principles

### I. Domínio isolado e testável sem rede

A lógica que decide se um avião está "no céu do utilizador" — `DetectOverheadFlightsUseCase` e a
geometria de que depende — vive na camada `domain` e é Kotlin puro. O `domain` NÃO PODE importar
`android.*`, Retrofit, Room, Compose, Glance ou WorkManager. Entradas não determinísticas (relógio,
localização, resultados de rede) entram como parâmetros ou por interface, nunca lidas diretamente
lá dentro.

*Porquê:* é a única parte da app que define o produto. Tem de correr na JVM em milissegundos e
sobreviver intacta a mudanças de fonte de dados, de UI e de estratégia de background.

### II. Fontes de dados atrás de uma interface

Nenhuma camada acima de `data/source` pode conhecer a OpenSky ou qualquer outro fornecedor
concreto. O contrato é `FlightDataSource`; a escolha da implementação acontece exclusivamente num
módulo de DI. Acrescentar, substituir ou combinar fontes TEM DE ser uma alteração de binding, não
um refactor que atravesse camadas. Nomes de classes acima de `data/source` não podem conter o nome
de um fornecedor.

*Porquê:* as APIs públicas de voo mudam de limites, de preço e de disponibilidade sem aviso. A app
tem de poder trocar de fonte sem tocar no domínio nem na UI.

### III. Localização com consentimento informado

Qualquer permissão de localização é pedida em runtime e SEMPRE precedida de uma explicação, em
linguagem clara, do que a app faz com a posição. `ACCESS_BACKGROUND_LOCATION` NÃO PODE ser pedida
no arranque nem como condição de uso: só a partir das definições, depois de o utilizador ativar
explicitamente as notificações, e a app tem de continuar plenamente utilizável se for recusada. A
posição do utilizador é processada apenas no dispositivo e nunca é enviada para servidores da app.
Recusar uma permissão nunca pode resultar num ecrã em branco sem explicação nem num caminho sem
saída.

*Porquê:* saber onde alguém está, continuamente e em segundo plano, é dos dados mais sensíveis que
uma app pode pedir. O utilizador tem de perceber a troca antes de a fazer — e a Play Store exige
justificação visível para esta permissão.

### IV. Respeitar os limites da plataforma e a bateria

O trabalho periódico usa WorkManager e NUNCA é agendado com período inferior a 15 minutos. A app
não contorna o Doze mode nem o App Standby e não promete tempo real ao utilizador: a UI mostra
sempre quando os dados foram atualizados pela última vez. Um foreground service permanente com
localização ativa está proibido no MVP; introduzir um exige decisão de arquitetura registada e
justificação explícita. O widget não faz rede na composição — lê o último resultado calculado e
delega qualquer atualização em trabalho enfileirado. Todos os `WorkRequest` são criados num único
ponto do código.

*Porquê:* uma app de aviões que descarrega a bateria é desinstalada na primeira semana. Os limites
do Android não são obstáculos a contornar; são o contrato com o utilizador.

### V. Decisões de arquitetura registadas

Mudanças estruturais — nova fonte de dados, alteração à estratégia de background ou de widget,
mudança no modelo de domínio, novas camadas ou dependências pesadas — são decididas antes de haver
código e registadas na secção de decisões de arquitetura do `CLAUDE.md`, com os trade-offs
explícitos. Uma decisão que altere um destes princípios TEM DE ser refletida nesta constituição,
com a versão incrementada.

*Porquê:* decisões estruturais tomadas a meio de uma implementação tendem a otimizar a feature em
curso e a hipotecar as seguintes.

### VI. Testar o que pode falhar em silêncio

Todo o caso de uso do `domain` tem testes unitários na JVM, incluindo os casos-limite geométricos:
avião exatamente no zénite, observador nos polos, círculo a cruzar o antimeridiano, vetores de
estado incompletos ou obsoletos. Uma feature não é dada como concluída sem revisão independente
(subagente `reviewer`). Bugs de geometria, de permissões e de agendamento não produzem erros
visíveis — produzem resultados errados com ar de certos, por isso são cobertos por teste e não por
inspeção visual.

*Porquê:* uma lista de aviões errada é indistinguível de uma lista de aviões certa para quem olha
para o ecrã.

## Restrições de Plataforma Android

- `minSdk` 26, `targetSdk` 36. Kotlin, Jetpack Compose, Glance, Hilt, Room, WorkManager.
- Unidades no domínio sempre em SI (metros, m/s, graus), explícitas no nome da propriedade. A
  conversão para as unidades do utilizador (km/milhas, metros/pés) pertence à `presentation`.
- Erros de rede são convertidos em resultados na fronteira do repositório; exceções de rede não
  sobem ao `domain` nem à UI.
- `POST_NOTIFICATIONS` é pedida apenas quando o utilizador ativa notificações, nunca no arranque.
- Notificações são desativadas por omissão e deduplicadas por aeronave dentro de uma janela
  temporal: a mesma passagem nunca gera mais do que um aviso.
- Versões de dependências apenas no catálogo `gradle/libs.versions.toml`.

## Fluxo de Desenvolvimento

Cada feature grande do MVP — lista de aviões, widget, notificações, definições — é desenvolvida
uma de cada vez, através de:

`/speckit-specify` → `/speckit-clarify` → `/speckit-plan` → `/speckit-tasks` → `/speckit-analyze`
→ `/speckit-implement`

- durante o `/speckit-plan`, o subagente `architect` é consultado sempre que houver decisão
  estrutural, e regista-a no `CLAUDE.md`;
- no fim de cada `/speckit-implement`, o subagente `reviewer` revê a feature antes de ser dada
  como concluída;
- não se começa a implementação de uma feature enquanto a especificação da anterior não estiver
  fechada.

## Governance

Esta constituição prevalece sobre qualquer outra prática do projeto, incluindo o `CLAUDE.md`. Em
caso de conflito, ganha a constituição e o outro documento é corrigido.

Emendas exigem: (1) alteração deste ficheiro no mesmo commit que a mudança que a motiva, (2)
descrição do impacto no Sync Impact Report no topo, e (3) incremento de versão segundo semver:

- **MAJOR** — remoção ou redefinição incompatível de um princípio ou de uma regra de governação;
- **MINOR** — novo princípio ou secção, ou alargamento material de orientação existente;
- **PATCH** — clarificações, redação e correções sem alteração de significado.

Conformidade: qualquer revisão de código ou de plano verifica o cumprimento destes princípios.
Desvios têm de ser justificados por escrito na decisão de arquitetura correspondente; complexidade
sem justificação é motivo para rejeitar a alteração. Orientação de desenvolvimento no dia a dia
(estrutura de pastas, comandos, convenções) vive no `CLAUDE.md`.

**Version**: 1.0.0 | **Ratified**: 2026-09-06 | **Last Amended**: 2026-09-06
