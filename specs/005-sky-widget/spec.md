# Feature Specification: Widget de ecrã inicial

**Feature Branch**: `005-sky-widget`

**Created**: 2026-09-11

**Status**: Draft

**Input**: User description: widget de ecrã inicial (Glance) que mostra o avião mais relevante no céu
do utilizador sem abrir a app, atualizado por trabalho de fundo, com toque para atualizar já, estados
explícitos para dados ausentes/antigos/sem permissão/céu vazio, e trabalho de fundo que só existe
enquanto houver widget no ecrã.

## A tensão central desta feature, dita à cabeça

O pedido é "o avião que está no meu céu **agora**". Isso é impossível num widget, e é preciso que
fique escrito antes dos requisitos, porque tudo o que segue é consequência disto:

- O intervalo mínimo de trabalho periódico no Android é de **15 minutos**, e o sistema pode adiar mais
  em Doze/App Standby.
- Uma aeronave atravessa um raio de 30 km (60 km de diâmetro) em **cerca de 4 a 5 minutos**.

Logo, quando o widget acorda, a aeronave que ele calculou já saiu do céu — quase sempre. Um widget que
escreva "há um avião por cima de ti: TAP1234" está a mentir na maioria das vezes que é lido, e é o pior
tipo de mentira: a que funciona perfeitamente e parece certa.

Esta feature resolve isso **dizendo a verdade em vez de a esconder**: o widget mostra sempre o
instante da observação, e só usa linguagem de presente quando os dados são recentes o suficiente para
isso ser plausível. É essa a razão de o toque para atualizar existir — é a única forma de o utilizador
obter um "agora" verdadeiro.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver o céu sem abrir a app (Priority: P1)

O utilizador adiciona o widget ao ecrã inicial. Passa a ver, de relance, qual foi a aeronave mais alta
no seu céu, de que companhia, a que altura acima do horizonte estava, e **quando** — sem tocar em nada
e sem abrir a app. O widget mantém-se atualizado sozinho.

**Why this priority**: é a feature toda. Sem isto não há widget, e é a razão de ser da app segundo a
AD-003. Também é o que fecha a caixa avariada que a v1.0.0 deixou nos ecrãs iniciais de quem a
adicionou.

**Independent Test**: adicionar o widget, esperar por um ciclo de trabalho de fundo, e confirmar que o
widget mostra uma aeronave real com a hora da observação, sem a app ter sido aberta.

**Acceptance Scenarios**:

1. **Given** o widget acabou de ser adicionado e nunca correu trabalho de fundo, **When** o utilizador
   olha para o widget, **Then** vê uma mensagem de que ainda não há dados — nunca uma caixa em branco
   nem um espaço vazio onde devia estar uma aeronave.
2. **Given** o trabalho de fundo correu e encontrou aeronaves, **When** o utilizador olha para o
   widget, **Then** vê o indicativo, a companhia quando conhecida, a elevação acima do horizonte, e o
   instante da observação.
3. **Given** o trabalho de fundo correu e o céu estava vazio, **When** o utilizador olha para o widget,
   **Then** vê que não havia aeronaves naquele instante — distinto de "ainda não há dados" e distinto
   de um erro.
4. **Given** os dados têm mais do que a janela de frescura, **When** o utilizador olha para o widget,
   **Then** o texto não afirma que a aeronave está no céu, e o instante da observação continua visível.
5. **Given** a permissão de localização não está concedida, **When** o utilizador olha para o widget,
   **Then** vê que a app precisa da permissão e que tocar abre a app — e o trabalho de fundo não fica
   a repetir tentativas que nunca podem funcionar.
6. **Given** o widget mostra qualquer estado, **When** o utilizador toca no corpo do widget, **Then** a
   app abre.

---

### User Story 2 - Atualizar já, com um toque (Priority: P2)

O utilizador quer saber o que está no céu **neste momento** e não dentro de quinze minutos. Toca no
botão de atualizar do widget e, segundos depois, o widget mostra dados novos.

**Why this priority**: é o que torna a promessa de "agora" alcançável, e sem isto a US1 é só um retrato
do passado. Fica depois da US1 porque um widget que mostra dados honestos já tem valor, e um botão de
atualizar sem nada para atualizar não tem nenhum.

**Independent Test**: com o widget a mostrar dados de há vários minutos, tocar em atualizar e confirmar
que o instante da observação passa a ser recente.

**Acceptance Scenarios**:

1. **Given** o widget mostra dados antigos, **When** o utilizador toca em atualizar, **Then** o widget
   indica que está a atualizar e, ao terminar, mostra dados com um instante recente.
2. **Given** o utilizador toca em atualizar, **When** o pedido está em curso, **Then** tocar outra vez
   não lança um segundo pedido em paralelo.
3. **Given** não há rede, **When** o utilizador toca em atualizar, **Then** o widget diz que não foi
   possível atualizar e **mantém visíveis** os dados anteriores com o seu instante, em vez de os
   apagar.
4. **Given** o orçamento diário de consultas está esgotado, **When** o utilizador toca em atualizar,
   **Then** o widget diz que o limite diário foi atingido, em vez de uma falha genérica.

---

### User Story 3 - Não gastar bateria a calcular o que ninguém vê (Priority: P3)

Quem não tem widget no ecrã não paga bateria nem consultas por ele. Quem adiciona o primeiro widget
passa a ter trabalho de fundo; quem remove o último deixa de ter.

**Why this priority**: é higiene, não função visível — mas é a diferença entre uma app que se pode
deixar instalada e uma que se desinstala. Também protege o orçamento diário de consultas, que é
partilhado com o ecrã.

**Independent Test**: adicionar um widget e confirmar que passa a existir trabalho periódico agendado;
remover o último widget e confirmar que deixa de existir.

**Acceptance Scenarios**:

1. **Given** não há nenhum widget no ecrã inicial, **When** se inspeciona o trabalho agendado, **Then**
   não existe trabalho periódico de atualização do céu.
2. **Given** o utilizador adiciona o primeiro widget, **When** se inspeciona o trabalho agendado,
   **Then** existe exatamente um trabalho periódico.
3. **Given** existem dois widgets no ecrã, **When** se inspeciona o trabalho agendado, **Then**
   continua a existir exatamente um trabalho periódico — não um por widget.
4. **Given** o utilizador remove o último widget, **When** se inspeciona o trabalho agendado, **Then**
   o trabalho periódico deixa de existir.
5. **Given** um utilizador que já tinha o widget da v1.0.0 no ecrã, **When** atualiza a app para esta
   versão, **Then** o trabalho periódico passa a existir sem ele ter de remover e voltar a adicionar o
   widget.

---

### User Story 4 - Escolher a cadência, sabendo o que ela custa (Priority: P4)

Nas definições, o utilizador escolhe de quanto em quanto tempo o widget se atualiza, e vê o que essa
escolha custa ao tempo de ecrã disponível por dia.

**Why this priority**: sem isto a cadência é uma decisão tomada às escondidas do utilizador, com
consequência real e invisível — o widget consome do mesmo orçamento diário que o ecrã. Fica em último
porque tem um valor por omissão defensável e o widget funciona sem o controlo existir.

**Independent Test**: alterar a cadência nas definições e confirmar que o trabalho periódico passa a
ter o novo intervalo, sem reinstalar nada.

**Acceptance Scenarios**:

1. **Given** o utilizador está nas definições, **When** escolhe uma cadência diferente, **Then** o
   trabalho periódico passa a usar esse intervalo sem a app ser reiniciada.
2. **Given** o utilizador escolhe a cadência mais frequente, **When** lê o ecrã, **Then** vê quanto
   tempo de ecrã por dia essa escolha lhe deixa.
3. **Given** um valor guardado abaixo do mínimo que o Android aceita, **When** é lido, **Then** é
   corrigido para o mínimo em vez de produzir um agendamento inválido.

---

### Edge Cases

- **O trabalho de fundo é adiado pelo sistema** muito além do intervalo pedido (Doze, poupança de
  bateria, telefone desligado). O widget não pode parecer avariado: o instante da observação explica
  sozinho o que aconteceu, e nunca se promete uma cadência que não se controla.
- **Sem permissão de localização.** O trabalho termina sem erro e sem repetir — repetir não resolveria
  nada e gastaria bateria. O widget explica e encaminha para a app, que é o único sítio onde a
  permissão pode ser pedida.
- **A permissão é revogada depois de o widget já mostrar dados.** Os dados antigos deixam de ser
  apresentados como atuais; o widget passa ao estado de permissão em falta.
- **Sem rede** durante um ciclo de fundo: o ciclo volta a tentar mais tarde em vez de falhar
  definitivamente, e o widget continua a mostrar o que tinha, com o instante.
- **Orçamento diário esgotado** (limite da fonte de dados): distinto de falta de rede, e dito como tal.
- **O céu está vazio** no instante do cálculo: distinto de "ainda não há dados" e de erro.
- **Vários widgets no ecrã**, de tamanhos diferentes: um único trabalho de fundo alimenta todos, e
  todos mostram o mesmo instante.
- **O processo morre** entre ciclos: o widget continua a mostrar o último resultado, porque o resultado
  não vive na memória do processo.
- **O widget é redimensionado** para o tamanho mínimo: a informação essencial — a aeronave e o instante
  — continua legível, e o que não cabe é o que menos importa.
- **A app é atualizada** enquanto existem widgets no ecrã: ver a US3, cenário 5.
- **A aeronave não tem indicativo** ou a companhia é desconhecida: o widget mostra o que sabe e nunca
  esconde a aeronave por lhe faltar um campo.

## Requirements *(mandatory)*

### Functional Requirements

#### O que o widget mostra

- **FR-001**: O widget MUST mostrar, quando há dados, a aeronave de **maior elevação** entre as que
  estavam no céu no instante do cálculo — a mesma ordem de relevância que a lista da app usa.
- **FR-002**: O widget MUST mostrar o instante da observação **em todos os estados que tenham dados**,
  e nunca apresentar dados sem indicação de quando foram obtidos.
- **FR-003**: O widget MUST usar linguagem de presente ("está no teu céu") apenas quando a observação
  for mais recente do que a janela de frescura; fora dela MUST usar linguagem de passado com o
  instante.
- **FR-004**: O widget MUST distinguir, com texto próprio, cinco situações: sem dados ainda, com dados
  recentes, com dados antigos, céu vazio no instante do cálculo, e permissão de localização em falta.
- **FR-005**: O widget MUST mostrar o indicativo e, quando conhecida, a companhia; a ausência de
  companhia ou de indicativo NEVER esconde a aeronave nem deixa um espaço vazio.
- **FR-006**: O widget MUST mostrar a elevação acima do horizonte da aeronave apresentada.
- **FR-007**: O widget MUST nunca apresentar uma caixa vazia ou sem texto, em nenhum estado, incluindo
  antes do primeiro ciclo de trabalho de fundo.
- **FR-008**: Tocar no corpo do widget MUST abrir a app.

#### Como os dados chegam ao widget

- **FR-009**: O widget MUST NOT fazer pedidos de rede durante a sua composição; MUST ler apenas o
  último resultado já calculado.
- **FR-010**: O último resultado MUST sobreviver à morte do processo da app, de forma que o widget
  continue a mostrar dados depois de o sistema matar a app.
- **FR-011**: O trabalho de fundo MUST usar os mesmos critérios de deteção (raio, ângulo mínimo,
  altitude mínima) e as mesmas unidades que o utilizador escolheu nas definições.
- **FR-012**: Um ciclo de trabalho de fundo que falhe por falta de rede MUST ser reagendado para nova
  tentativa, e MUST NOT ser tratado como falha definitiva.
- **FR-013**: Um ciclo de trabalho de fundo sem permissão de localização MUST terminar sem repetir, e
  MUST deixar o widget no estado de permissão em falta.
- **FR-014**: Um ciclo que falhe MUST NOT apagar nem substituir o último resultado válido; o widget
  MUST continuar a mostrar o que tinha, com o instante correspondente.

#### Atualizar a pedido

- **FR-015**: O widget MUST ter uma ação de atualização imediata, visualmente distinta do resto do
  widget, que não abra a app.
- **FR-016**: A ação de atualização MUST enfileirar trabalho em vez de fazer rede na própria ação.
- **FR-017**: Toques repetidos na ação de atualização, enquanto um pedido está em curso, MUST NOT
  produzir pedidos concorrentes.
- **FR-018**: Enquanto uma atualização pedida está em curso, o widget MUST indicá-lo.
- **FR-019**: Uma atualização que falhe MUST distinguir falta de rede de orçamento diário esgotado.

#### Ciclo de vida do trabalho de fundo

- **FR-020**: O trabalho periódico MUST existir se e só se houver pelo menos um widget no ecrã inicial
  — passar a existir quando o primeiro é adicionado, e deixar de existir quando o último é removido.
- **FR-021**: MUST existir no máximo um trabalho periódico de atualização do céu, independentemente do
  número de widgets no ecrã.
- **FR-022**: A app MUST garantir o agendamento para widgets que já existiam no ecrã antes desta
  versão, sem exigir que o utilizador os remova e volte a adicionar.
- **FR-023**: Todos os `WorkRequest` de atualização do céu MUST ser criados num único ponto, distinto
  do ponto que cria os da tabela de rotas.

#### A cadência

- **FR-024**: O utilizador MUST poder escolher a cadência de atualização do widget nas definições.
- **FR-025**: A cadência escolhida MUST ser aplicada ao trabalho periódico sem exigir reinício da app.
- **FR-026**: Uma cadência abaixo do mínimo que a plataforma aceita MUST ser corrigida para o mínimo em
  vez de produzir um agendamento inválido.
- **FR-027**: O ecrã de definições MUST mostrar o custo da cadência escolhida em tempo de ecrã
  disponível por dia, porque o widget consome do mesmo orçamento diário que o ecrã.

### Key Entities

- **Último resultado do céu**: o que o trabalho de fundo calculou da última vez — a aeronave mais
  relevante (indicativo, companhia, elevação), o instante da observação, e qual das situações se
  aplica (dados, céu vazio, permissão em falta, falha). Persiste fora da memória do processo.
- **Cadência de atualização**: de quanto em quanto tempo o trabalho de fundo corre. Escolha do
  utilizador, com mínimo imposto pela plataforma.
- **Janela de frescura**: quanto tempo depois da observação ainda é honesto falar no presente.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Um utilizador que adiciona o widget vê informação real do seu céu sem nunca abrir a app,
  no primeiro ciclo de trabalho de fundo que o sistema conceder.
- **SC-002**: Em nenhum dos estados possíveis o widget aparece sem texto. Verificável enumerando os
  cinco estados de FR-004 mais os dois de falha de FR-019.
- **SC-003**: Nenhum estado do widget afirma que uma aeronave está no céu com base numa observação mais
  antiga do que a janela de frescura. Verificável por teste automático sobre a decisão de texto.
- **SC-004**: Uma atualização pedida pelo utilizador reflete-se no widget em **menos de 15 segundos**
  com rede disponível.
- **SC-005**: Sem nenhum widget no ecrã inicial, a app não faz **nenhum** pedido de rede em segundo
  plano durante 1 hora de observação.
- **SC-006**: O número de trabalhos periódicos agendados é sempre 0 ou 1, nunca mais, em qualquer
  sequência de adicionar e remover widgets.
- **SC-007**: Com a cadência por omissão, o widget consome no máximo **12%** do orçamento diário de
  consultas, deixando pelo menos 2h50m de tempo de ecrã.
- **SC-008**: Quem tinha o widget avariado da v1.0.0 passa a ver dados reais depois de atualizar a app,
  sem tocar no widget.

## Assumptions

- **"Avião mais relevante" é o de maior elevação.** É a ordem que a lista da app já usa desde a
  primeira feature, e a que responde à pergunta "o que está mais por cima de mim". Não se inventa uma
  segunda noção de relevância.
- **A janela de frescura assume-se em 5 minutos.** É a ordem de grandeza do tempo que uma aeronave
  passa dentro de um raio de 30 km (4 a 5 minutos, a 200–250 m/s). Passado isso, afirmar presença é
  afirmar o que provavelmente já não é verdade.
- **A cadência por omissão assume-se em 30 minutos, não nos 15 do mínimo.** Aos 15 minutos o widget já
  está fora da janela de frescura quando acorda, por isso duplicar a frequência não o torna
  materialmente mais útil — mas custa 96 consultas por dia contra 48, o que são 24 minutos de tempo de
  ecrã por dia. Escolhe-se o valor que não gasta o dia do utilizador por um ganho que ele não nota. O
  mínimo de 15 minutos continua disponível para quem o queira.
- **O orçamento é o da fonte anónima**: 400 consultas por dia, partilhadas entre o widget e o ecrã, a
  uma consulta por ciclo. Isto foi apurado na feature das definições e não se repete a verificação.
- **O widget não pede permissões.** Um widget não tem como mostrar um diálogo de permissão; encaminha
  para a app, onde o pedido já existe com a explicação prévia obrigatória.
- **As notificações de passagem ficam fora desta feature**, mas o trabalho de fundo que aqui se cria é
  o mesmo que elas vão usar. O ciclo de vida de FR-020 terá de passar a considerar também as
  notificações ativas quando essa feature existir — fica anotado como consequência conhecida, não
  resolvido aqui.
- **O histórico de avistamentos continua fora de âmbito.** O widget precisa do último resultado, não de
  uma série temporal.
- **Nada de serviço em primeiro plano permanente**, como a constituição do projeto exige. A
  consequência aceite é que a cadência é "best effort" e o sistema manda.
