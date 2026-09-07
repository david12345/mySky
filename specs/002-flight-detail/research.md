# Phase 0 — Research: Detalhe de um voo

Todas as incógnitas do Technical Context estão resolvidas; nenhuma marca `NEEDS CLARIFICATION`
permanece. As duas decisões estruturais (D1 e D2) foram tomadas com o subagente `architect` e são
registadas em paralelo no `CLAUDE.md` como AD-011 e AD-012, conforme o princípio V. As restantes
(D3–D6) são decisões de apresentação, sem peso estrutural.

Nenhuma dependência nova é introduzida por esta feature.

---

## D1 — Onde vive o laço de atualização quando há dois ecrãs a observá-lo

**Decision**: o laço sai do `MainViewModel` para um detentor partilhado,
`presentation/sky/SkySession`, com escopo `@ActivityRetainedScoped`. Expõe
`observation: StateFlow<SkyObservation>` produzido com `stateIn(scope próprio,
WhileSubscribed(5_000), SkyObservation())` e um `requestRefresh()` que substitui o
`Channel(CONFLATED)` hoje privado do `MainViewModel`. `MainViewModel` e `FlightDetailViewModel`
passam a consumidores finos: cada um embrulha a mesma fonte com
`stateIn(viewModelScope, WhileSubscribed(5_000), inicial)` e deriva o seu próprio estado de ecrã.

**Rationale**:
- FR-017 e SC-008 exigem uma só origem de pedidos quando os dois ecrãs estão vivos ao mesmo tempo
  — o que acontece sempre durante a transição e a cada rotação. FR-013 e SC-002 exigem que os dois
  apresentem os mesmos valores no mesmo instante. Uma contagem de subscritores partilhada resolve
  as duas exigências de uma vez, sem mutex e sem cache com validade.
- A propriedade que a AD-008 comprou — "parar em segundo plano sai de graça do ciclo de vida" —
  não se perde: passa a ser a soma dos subscritores dos dois ecrãs a decidir, o que é a
  generalização correta e não uma exceção.
- Durante a transição lista → detalhe a contagem passa por 1 → 2 → 1 sem chegar a zero. O laço
  nunca reinicia nem duplica um pedido nos 5 segundos de sobreposição.
- Fica em `presentation/` e não em `domain/` porque a cadência e a partilha são política de
  apresentação — a AD-008 já o tinha estabelecido. A regra de negócio continua inteira em
  `DetectOverheadFlightsUseCase`. Mudar o dono do laço não muda a camada a que ele pertence, e
  evita esconder uma anotação de escopo do Hilt-Android atrás de um `@Provides` só para não a
  escrever no domínio.

**Correção face à proposta inicial**: o `architect` propunha que a `SkySession` deixasse de reagir
a mudanças de permissão e se limitasse a consultar `hasLocationPermission()` a cada ciclo, aceitando
até 30 s de atraso entre conceder e ver. Isso é aceitável para o regresso das Definições, que é
raro, mas **não** para o caminho normal de primeira utilização: o utilizador concede no diálogo do
sistema e ficaria a olhar para o ecrã de rationale meio minuto, contra o SC-001 da feature 001. A
correção não precisa de mecanismo novo: o `MainViewModel`, que continua a ser o único a conhecer a
permissão, chama `requestRefresh()` na transição para `Granted`. O laço acorda de imediato e a
`SkySession` continua sem saber o que é uma permissão.

**Alternatives considered**:
- *ViewModel partilhado por `hiltViewModel(parentEntry)`*: rejeitado — acopla a partilha de estado
  à topologia da navegação (obrigaria a agrupar os dois destinos num sub-grafo só para terem um
  pai comum) e produz um ViewModel com os campos dos dois ecrãs misturados. Não escala para um
  terceiro ponto de entrada sem mexer outra vez na navegação.
- *Laços independentes com mutex ou cache com validade*: rejeitado — reintroduz a coordenação
  explícita que a AD-008 evitou de propósito, e só aproxima FR-017 e FR-013 consoante a validade
  escolhida: curta não deduplica nada, longa serve valores diferentes a cada ecrã. Duplicaria
  ainda o backoff de 429 em dois ficheiros que podem divergir.
- *Manter o laço no `MainViewModel` e passar os dados por argumento de navegação*: rejeitado — um
  argumento é fixado no momento da navegação e nunca mais muda, o que viola FR-014.

**Detalhe de implementação que não pode ser esquecido**: o escopo interno da `SkySession` não é
cancelado sozinho. O Hilt expõe `ActivityRetainedLifecycle`, injetável, com
`addOnClearedListener` — é aí que o `SupervisorJob` da sessão é cancelado. Sem isso fica um job
pendurado para lá do fim da Activity.

---

## D2 — Onde se decide que a aeronave saiu do céu

**Decision**: numa função pura nova em `domain/usecase/TrackFlightPresenceUseCase`, com a mesma
forma da `DetectOverheadFlightsUseCase`. Recebe o estado de presença anterior, a observação mais
recente (`List<OverheadFlight>?`, em que `null` significa "o ciclo falhou" e é distinto de lista
vazia), o `icao24` e `nowEpochSeconds`; devolve `Current`, `LeftSky` ou `NeverObserved`. A memória
entre chamadas vive no `FlightDetailViewModel`, não na `SkySession`.

**Rationale**:
- É precisamente a categoria de defeito que o princípio VI descreve: mostrar a última posição como
  se fosse atual não produz erro visível nenhum, produz um resultado errado com ar de certo.
  Merece o mesmo tratamento que a geometria — função pura, sem relógio implícito e sem memória
  escondida, testável na JVM.
- Passar `null` para o ciclo falhado é o que separa as três situações que FR-019 e FR-023 obrigam
  a distinguir: a aeronave saiu do céu, a atualização falhou, ou ainda não houve observação
  nenhuma. Um céu vazio por sucesso **é** saída; um céu vazio por falha **não é**.
- A memória é por ecrã e por aeronave. A lista não precisa dela, e a `SkySession` deve continuar
  sem qualquer estado por aeronave para poder servir os dois ecrãs sem carregar bagagem de um só.

**Alternatives considered**:
- *Decidir dentro da `SkySession`*: rejeitado — obrigaria a sessão partilhada a saber qual é a
  aeronave que o detalhe está a ver, acoplando-a a um ecrã.
- *Comparar listas dentro do Composable*: rejeitado — lógica com memória dentro da composição é
  intestável e recompõe de forma imprevisível.

**Consequência aceite**: depois de o processo ser morto e restaurado com o detalhe no topo da
pilha, não existe estado anterior. A resposta correta é `NeverObserved` — nunca reaproveitar um
voo de antes da morte do processo como se fosse atual.

---

## D3 — Uma só camada de formatação para os dois ecrãs

**Decision**: o detalhe não ganha formatadores próprios. As funções novas — razão de subida ou
descida e rumo da aeronave — entram no `FlightFormatting` existente, e o detalhe chama exatamente
as mesmas funções que a `FlightRow` chama.

**Rationale**:
- FR-013 e SC-002 exigem o mesmo valor, na mesma unidade, nos dois ecrãs. Com dois formatadores
  essa igualdade passa a ser coincidência que ninguém verifica, e a divergência aparece como um
  arredondamento diferente — indistinguível de um erro de cálculo para quem olha.
- O `FlightFormatting` já é puro e testado na JVM. Acrescentar duas funções custa menos do que
  criar um segundo sítio onde as unidades podem divergir.

**Alternatives considered**:
- *Formatador dedicado ao detalhe*: rejeitado pelo motivo acima.
- *Compor as frases no ViewModel*: rejeitado — perde a testabilidade sem `Context` e tira strings
  de UI dos recursos.

---

## D4 — Limiar de voo nivelado

**Decision**: `|verticalRate| < 0,5 m/s` apresenta-se como **nivelado**. Acima disso, "a subir" ou
"a descer" com o valor. Modelado como um `sealed interface VerticalMovement` de três variantes, não
como um número com sinal.

**Rationale**:
- 0,5 m/s são cerca de 100 ft/min, o limiar convencional para considerar um voo estabilizado.
  Abaixo disso o ruído do vetor de estado é da ordem do valor medido.
- Um avião a 0,4 m/s ganha 24 metros num minuto: dizer "a subir" seria verdade aritmética e
  mentira prática.
- Três variantes obrigam a UI a tratar o caso nivelado, em vez de o deixar sair como "a subir
  0 m/s".

**Alternatives considered**:
- *Número cru com sinal*: rejeitado — obriga o utilizador a saber que negativo é descida.
- *Limiar zero exato*: rejeitado — nenhuma aeronave reporta exatamente zero, e o estado nivelado
  nunca apareceria.

---

## D5 — As duas altitudes, e qual delas entra na conta

**Decision**: o detalhe apresenta as duas altitudes quando ambas existem, cada uma identificada, e
assinala qual foi usada no cálculo da elevação. Quando só existe uma, mostra só essa sem a
qualificar.

**Rationale**:
- `Aircraft.altitudeMeters` já prefere a geométrica e cai para a barométrica; é esse valor que a
  lista mostra. Sem dizer qual entrou na conta, o utilizador atento veria a lista a concordar com
  uma e não com a outra, e concluiria que uma delas está errada.
- As duas divergem legitimamente em dezenas de metros: a barométrica é referida a uma pressão
  padrão, não à pressão real do dia.

**Alternatives considered**:
- *Mostrar só a usada*: rejeitado — as duas foram pedidas, e a divergência é informação.
- *Mostrar a média*: rejeitado — inventaria um número que nenhuma fonte reportou.

---

## D6 — Rumo da aeronave e direção a partir do observador são coisas diferentes

**Decision**: os dois valores são rotulados pelo que significam — para onde olhar (azimute
observador → aeronave) e para onde a aeronave vai (rumo) — e nunca aparecem lado a lado sem essa
distinção. A regra do zénite (FR-012) aplica-se **apenas** ao azimute.

**Rationale**:
- Ambos são graus, ambos se apresentam como ponto da rosa dos ventos, ambos aparecem no mesmo
  ecrã. É a confusão mais provável desta feature, e a única cujo erro o utilizador não consegue
  detetar olhando para o céu.
- Suprimir o rumo no zénite apagaria informação válida: um avião mesmo por cima continua a ir para
  algum lado. O que perde significado é dizer "olha para nordeste" a 89 graus de elevação.

**Alternatives considered**:
- *Um só campo "direção"*: rejeitado — junta dois conceitos distintos numa palavra ambígua.
- *Aplicar a regra do zénite aos dois*: rejeitado — perde informação correta por simetria
  aparente.

---

## Correção feita a FR-024

A redação original de FR-024 pedia que "os dados da aeronave que não dependem da posição do
utilizador" continuassem visíveis sem essa posição. Isso é impossível de cumprir à letra: sem
posição do observador a app **não chega a consultar** a fonte de voos, porque é a posição que
define as caixas envolventes da consulta. Não existe, portanto, o caso "dados novos da aeronave
sem posição do observador".

O requisito foi reescrito no `spec.md` durante o `/speckit-analyze` para exigir o que é de facto
possível e útil: manter visível a última observação conhecida, com a causa explicada, sem nunca a
substituir por um ecrã de erro. É a regra de FR-022 aplicada à variante `LocationUnavailable`.
