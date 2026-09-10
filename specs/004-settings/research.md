# Phase 0 — Research: Definições

A decisão D1 corrige uma premissa da especificação, com contas em vez de intuição. As decisões
estruturais (D2 em diante) foram tomadas com o subagente `architect` e sobem ao `CLAUDE.md`,
conforme o princípio V.

Nenhuma dependência nova. O DataStore e o `SkySettings` já existem no projeto desde o esqueleto
inicial, à espera desta feature.

---

## D1 — O orçamento não é a restrição que a especificação supôs

A especificação escreveu, em FR-013 e SC-003, que certas combinações de valores fariam a app
exceder o orçamento diário da fonte de voos, e o checklist chamou-lhe "o requisito que dá trabalho a
sério". **Fui verificar, e a premissa está errada.**

### Como o limite funciona de facto

O custo de uma consulta ao `/states/all` depende da **área da caixa envolvente**, não do número de
aeronaves nem do raio em si:

| Área da caixa | Créditos |
|---|---|
| ≤ 25 sq° | 1 |
| 25 – 100 sq° | 2 |
| 100 – 400 sq° | 3 |
| > 400 sq° ou global | 4 |

Um utilizador anónimo tem **400 créditos por dia**.

### As contas, à latitude de Lisboa

| Raio | Área da caixa | Custo |
|---|---|---|
| 10 km | 0,04 sq° | 1 crédito |
| 30 km *(atual)* | 0,37 sq° | 1 crédito |
| 100 km | 4,15 sq° | 1 crédito |
| 200 km | 16,59 sq° | 1 crédito |
| **246 km** | 25 sq° | **passa a 2** |

O limiar dos 2 créditos só é atingido a **246 km** em Lisboa, a 211 km a 55° de latitude e a 181 km
a 65°. Qualquer raio que faça sentido para esta app custa exatamente **um crédito**, tal como hoje.

**O teto real** é outro, e o raio não lhe toca: 400 créditos a 1 por ciclo de 30 segundos dão
**3 horas e 20 minutos de ecrã aberto por dia**. É um limite que já existe hoje, que nenhuma
definição altera, e que a feature 001 nunca escreveu em lado nenhum.

### O que está mesmo acoplado

Não é o raio ao orçamento — é o **raio ao ângulo mínimo**. Para uma aeronave a 12 km de altitude:

| Ângulo mínimo | Além disto não há nada a ver |
|---|---|
| 5° | 137 km |
| 15° | 45 km |
| **25°** *(atual)* | **26 km** |
| 45° | 12 km |

Com os 25° por omissão, tudo o que esteja além de ~26 km **já é excluído pelo filtro de elevação**.
O raio de 30 km de hoje é ligeiramente generoso face a isso, e um raio de 150 km com 25° traria
centenas de aeronaves só para as deitar fora.

**Consequência para o desenho**: FR-013 e SC-003 têm de ser reescritos. Os limites de cada valor
derivam da **utilidade e da coerência entre si**, não do orçamento — e há uma interação entre duas
definições que o ecrã tem de tratar, sob pena de o utilizador poder escolher um par que não faz
sentido nenhum e não ter como perceber porquê.

**Alternatives considered**:
- *Manter FR-013 como está e escolher limites conservadores por precaução*: rejeitado — seria
  restringir o utilizador por uma razão que não existe, e escrever no plano uma justificação falsa
  que ninguém mais poria em causa.
- *Autenticar contra a OpenSky para subir a 4 000 créditos*: fora de âmbito e contra a decisão de
  origem da app, que é funcionar sem conta. Fica anotado como a saída, se o teto de 3h20m se tornar
  queixa real.

**Sources**: [OpenSky REST API](https://openskynetwork.github.io/opensky-api/rest.html) ·
[opensky-api (GitHub)](https://github.com/openskynetwork/opensky-api)

---

## D2 — As preferências entram na sessão por leitura direta (AD-018)

**Decision**: a `SkySession` passa a depender do `SettingsRepository` como já depende do
`LocationRepository`, e lê os critérios **no início de cada ciclo**, com uma leitura pontual do
`Flow` — não mantém uma subscrição viva.

**Rationale**:
- Cada ciclo fica com um **snapshot imutável** de critérios do princípio ao fim. Como os critérios
  são um `data class` passado por valor ao caso de uso (AD-009), é estruturalmente impossível uma
  lista com critérios misturados dentro do mesmo ciclo. O FR-017 fica garantido pela **forma dos
  dados**, e não por cancelamento — que é a melhor forma de garantir uma coisa, porque não há nada
  para alguém se lembrar de fazer.
- Fecha a promessa da AD-009 por um caminho diferente do que lá estava previsto. A AD-009 escreveu
  que "é o ViewModel que alimenta o parâmetro", mas nessa altura o laço ainda vivia no
  `MainViewModel`. Depois da AD-011 o laço é da sessão, e é a sessão que deve perguntar — do mesmo
  jeito que já pergunta a localização.

**Alternatives considered**:
- *`collectLatest` sobre o fluxo de preferências*: rejeitado. Cancelaria uma chamada de rede em
  curso sem desfazer o custo já gasto do lado do servidor — o crédito conta-se no pedido, não na
  resposta — e acrescentaria uma segunda forma de interromper o laço ao lado do canal conflado que
  a AD-008 centralizou de propósito.
- *O `MainViewModel` a entregar os critérios à sessão*: rejeitado. Tornaria a sessão dependente de
  qual ecrã por acaso está vivo primeiro, para um dado que não é de nenhum ecrã em particular.

---

## D3 — Quem altera acorda o laço; unidades não (AD-019)

**Decision**: depois de uma alteração de raio, ângulo ou altitude, o ecrã de definições chama o
mesmo `requestRefresh()` que o ecrã principal já usa quando a permissão passa a concedida.
Alterações de **unidade não o chamam**.

**Rationale**:
- O SC-001 só exige que a alteração se reflita "em menos de um ciclo". Acordar mais cedo é cortesia
  sobre uma garantia que já existe — e, se der problemas, retira-se sem violar a especificação.
- O canal já é conflado: um utilizador a arrastar um controlo não gasta mais do que um pedido.
- As unidades não afetam a deteção, só a apresentação. Pedir dados novos por causa delas seria
  gastar um crédito para obter exatamente as mesmas aeronaves.

**Detalhe de implementação que evita gastar créditos a cada pixel**: o controlo mantém o valor em
trânsito localmente e a escrita — e portanto o pedido — acontece só quando o utilizador larga, não a
cada movimento.

**Consequência aceite, e pré-existente**: um ajuste durante um recuo por excesso de pedidos volta a
tentar antes do tempo indicado pelo servidor. O botão manual de atualizar já tinha esse
comportamento desde a 001; esta feature alarga-lhe a porta sem o criar. Fica anotado como candidato
a refinamento — distinguir "pedido explícito" de "aviso de que algo mudou" — e não bloqueia nada.

**Armadilha sinalizada**: o modelo de preferências tem um campo de intervalo de atualização que
pertence ao **trabalho periódico** do widget, com o seu mínimo de 15 minutos (AD-003) — e não ao
laço de 30 segundos desta sessão. Está na mesma classe, o que o torna fácil de confundir. O FR-015
proíbe esta feature de lhe ligar um controlo.

---

## D4 — A unidade viaja no estado do ecrã (AD-020)

**Decision**: as funções de formatação ganham um parâmetro de unidade e continuam puras, estáticas e
testáveis na JVM. A unidade chega a cada ecrã dentro do `data class` de estado que ele já observa.

**Rationale**:
- Um `CompositionLocal` seria um segundo canal de estado implícito ao lado do estado do ecrã,
  contra a convenção já escrita no `CLAUDE.md`: a UI nunca compõe estado a partir de vários fluxos
  soltos.
- Passar a unidade pelo estado que os composables já leem não acrescenta mecanismo de propagação
  nenhum — é mais um campo, combinado como a permissão já é combinada com a observação.

---

## D5 — Os limites são geométricos, e a incoerência avisa-se (AD-021)

**Decision**: os limites de cada valor derivam da utilidade geométrica. A relação entre o raio e o
ângulo mínimo **não estreita** o intervalo de nenhum controlo — aparece como texto explicativo,
calculado ao vivo.

**Rationale**:
- Bloquear obrigaria o limite de um controlo a mover-se quando o utilizador toca no outro. Um
  cursor cujo topo se desloca debaixo do dedo é hostil, e complica o requisito de indicar qual é o
  valor de origem — se o próprio limite se mexe, "origem" deixa de ter um sítio fixo.
- O FR-009 é sobre um controlo não aceitar valores fora do **seu próprio** limite; não é sobre dois
  controlos se restringirem um ao outro. Ler-lhe mais do que isso seria inventar requisito.
- Derivar um valor do outro automaticamente — baixar a elevação quando o raio sobe — esconderia,
  em nome de ajudar, exatamente o efeito que o FR-011 existe para explicar. Dois controlos que
  parecem independentes a reescreverem-se um ao outro é a pior das três opções.
- E a premissa do acoplamento é uma **aproximação**: assume um teto de altitude típico. Há tráfego
  executivo a voar bem mais alto, para o qual a conta dá outro resultado. Serve para informar, não
  para impedir uma escolha legítima.

**Consequência**: o alcance útil é a inversa de uma função que já existe no domínio — a que calcula
o ângulo de elevação a partir da altitude e da distância. Não há geometria nova a escrever.

---

## D6 — A degradação de valores inválidos vive no domínio (AD-022)

**Decision**: o modelo de preferências ganha uma função pura que devolve uma versão de si mesmo com
todos os campos dentro dos limites. O repositório aplica-a em **toda** leitura, não numa migração
pontual.

**Rationale**:
- Um único ponto de verdade para os limites, consumido por três sítios sem os redefinir: o intervalo
  do controlo (FR-009), a degradação de um valor guardado inválido (FR-008) e a frase explicativa
  (FR-011).
- Aplicar em cada leitura, e não uma vez só, responde de graça à pergunta "o que acontece quando os
  limites mudarem numa versão futura": nada precisa de ser versionado nem migrado. O valor antigo é
  corrigido todas as vezes que é lido, para sempre.

---

## D7 — Riscos anotados, sem mudança de desenho

- **Mudar critérios pode fazer a aeronave que o detalhe mostra sair do céu.** Isso chega ao caso de
  uso de presença como uma lista sem aquele `icao24` — e **não** como um ciclo falhado, porque o
  ciclo correu bem. O comportamento é o correto: sob o critério novo, aquela aeronave já não está no
  céu do utilizador. O risco é de vocabulário, não de lógica: alguém pode ler "saiu do teu céu" como
  defeito quando é consequência do que ele próprio acabou de mudar.
- **A sessão passa a ler preferências uma vez por ciclo.** É leitura local, em memória depois do
  primeiro arranque, e não rede — mas o primeiro ciclo paga uma leitura de disco no caminho do
  arranque. É pequena, e vale a pena tê-la presente por ser exatamente o tipo de custo que esta app
  já evitou noutro sítio de propósito.
- **Uma alteração afeta os dois ecrãs ao mesmo tempo**, porque o critério é global e a sessão é
  partilhada. É o comportamento pedido, não efeito colateral.
