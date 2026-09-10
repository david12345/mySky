# Feature Specification: Definições

**Feature Branch**: `004-settings`

**Created**: 2026-09-08

**Status**: Draft

**Input**: Feature escolhida pelo utilizador a 2026-09-08, a partir das opções em aberto: o ecrã de
definições, para o utilizador poder ajustar o que conta como "o meu céu" e em que unidades o vê.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ajustar o que conta como "o meu céu" (Priority: P1)

O utilizador que vive perto de um aeroporto tem a lista sempre cheia e quer só os aviões mesmo por
cima. O que vive no interior quase nunca vê nada e quer alargar. Hoje não pode fazer nem uma coisa
nem outra: os limiares estão fixos e são os mesmos para toda a gente.

Nas definições, ajusta o raio de deteção e o ângulo mínimo acima do horizonte, e a lista passa a
refletir a sua escolha.

**Why this priority**: é a razão de existir da feature. O que está "no meu céu" depende de onde a
pessoa vive e do que lhe interessa, e é a única coisa da app que só ela pode decidir.

**Independent Test**: alterar o raio nas definições, voltar à lista e verificar que aeronaves que
antes não apareciam passam a aparecer — ou o contrário.

**Acceptance Scenarios**:

1. **Given** o utilizador nas definições, **When** altera o raio de deteção, **Then** vê o valor
   escolhido com a unidade, e a alteração fica guardada.
2. **Given** um valor alterado, **When** o utilizador volta à lista, **Then** a lista passa a usar
   o novo critério sem ser preciso reiniciar a app.
3. **Given** valores alterados, **When** a app é fechada e reaberta, **Then** as escolhas
   mantêm-se.
4. **Given** o utilizador arrependido das suas escolhas, **When** repõe os valores originais,
   **Then** a app volta ao comportamento de origem sem ter de ser reinstalada.

---

### User Story 2 - Ver as distâncias e as altitudes na unidade que uso (Priority: P2)

Quem pensa em pés e milhas lê "10 400 m" e não sente nada. A escolha de unidades não muda o que a
app faz — muda se o que ela diz significa alguma coisa para quem lê.

**Why this priority**: é uma mudança de apresentação, não de comportamento, e a app é utilizável
sem ela. Mas é barata e toca em todos os ecrãs.

**Independent Test**: mudar para milhas e pés, e verificar que a lista, o detalhe e as próprias
definições passam todos a usar essas unidades.

**Acceptance Scenarios**:

1. **Given** o utilizador escolhe milhas, **When** volta à lista, **Then** as distâncias aparecem
   em milhas, com a unidade indicada.
2. **Given** o utilizador escolhe pés, **When** abre o detalhe de uma aeronave, **Then** as
   altitudes aparecem em pés.
3. **Given** uma unidade escolhida, **When** o utilizador volta às definições, **Then** os próprios
   limiares são apresentados nessa unidade.

---

### User Story 3 - Não conseguir estragar a app com uma escolha (Priority: P2)

O utilizador experimenta os extremos — o raio no máximo, a elevação no mínimo — e a app tem de
continuar a funcionar, dentro do que a fonte de dados permite e sem esgotar o que lhe cabe dela.

**Why this priority**: dar controlo sem limites seria dar ao utilizador a possibilidade de partir a
app sem perceber porquê. Um raio enorme traz milhares de aeronaves para serem deitadas fora pelo
filtro de elevação, e um ângulo mínimo de zero enche a lista de aviões no horizonte, que ninguém
vê. **Note-se que o orçamento de consultas não é a razão** — foi verificado que não depende do raio
(ver `research.md`, D1); a razão é a utilidade, e a coerência entre as duas definições.

**Independent Test**: pôr todos os valores nos extremos permitidos e verificar que a lista continua
a aparecer, a atualizar-se e a caber no orçamento.

**Acceptance Scenarios**:

1. **Given** o utilizador a ajustar um valor, **When** tenta ir além do permitido, **Then** o
   controlo não o deixa — em vez de aceitar e falhar depois.
2. **Given** os valores nos extremos permitidos, **When** o utilizador usa a app, **Then** a lista
   continua a aparecer e a atualizar-se normalmente.
3. **Given** um valor guardado de uma versão anterior que já não seja válido, **When** a app
   arranca, **Then** usa o valor válido mais próximo em vez de rebentar.

---

### User Story 4 - Perceber o que cada definição faz (Priority: P3)

"Elevação mínima: 25°" não diz nada a quem não anda nisto. Cada definição precisa de uma frase que
explique o que muda na prática.

**Why this priority**: sem isto, as definições existem mas ninguém lhes mexe — ou mexe-lhes ao
acaso e conclui que a app está estragada.

**Independent Test**: mostrar o ecrã a alguém que nunca o viu e perguntar o que espera que aconteça
se mexer em cada uma.

**Acceptance Scenarios**:

1. **Given** o utilizador no ecrã de definições, **When** olha para cada definição, **Then** vê uma
   explicação curta do efeito prático de a alterar.
2. **Given** um valor alterado, **When** o utilizador olha para ele, **Then** percebe como se
   compara com o valor de origem.

---

### Edge Cases

- **Alterar uma definição com a lista a atualizar-se**: a atualização em curso não pode ficar a
  meio nem produzir uma lista com critérios misturados.
- **Alterar uma definição a partir do ecrã de definições enquanto o detalhe está aberto** noutro
  ponto da pilha: ao voltar, o detalhe tem de refletir os critérios novos.
- **Valores guardados por uma versão futura da app** — ou corrompidos: a app arranca com os valores
  de origem em vez de recusar arrancar.
- **Raio no máximo num aeroporto movimentado**: a lista pode ficar longa; tem de continuar fluida.
- **Ângulo mínimo no valor mais baixo permitido**: aeronaves muito próximas do horizonte entram
  na lista, e a direção a olhar passa a ser a informação mais útil.
- **Unidade alterada com valores já escolhidos**: os limiares não mudam de significado, só de
  apresentação — 30 km não passa a 30 milhas.
- **Primeira utilização**: sem nada guardado, valem os valores de origem, e o ecrã tem de o dizer.

## Requirements *(mandatory)*

### Functional Requirements

#### O que se pode ajustar

- **FR-001**: Users MUST be able to ajustar o raio de deteção — a distância até onde a app procura
  aeronaves.
- **FR-002**: Users MUST be able to ajustar o ângulo mínimo acima do horizonte a partir do qual uma
  aeronave conta como estando no céu.
- **FR-003**: Users MUST be able to ajustar a altitude mínima abaixo da qual uma aeronave é
  ignorada.
- **FR-004**: Users MUST be able to escolher a unidade de distância e a unidade de altitude.
- **FR-005**: Users MUST be able to repor todos os valores de origem numa única ação.

#### Como se comportam

- **FR-006**: A app MUST aplicar cada alteração à lista e ao detalhe sem exigir reinício.
- **FR-007**: A app MUST persistir as escolhas entre sessões.
- **FR-008**: A app MUST usar os valores de origem quando não há nada guardado, quando o que está
  guardado é ilegível, ou quando um valor guardado está fora do permitido.
- **FR-009**: A app MUST impedir, no próprio controlo, a escolha de valores fora do permitido — em
  vez de os aceitar e falhar depois.
- **FR-010**: A app MUST apresentar cada valor na unidade escolhida pelo utilizador, incluindo no
  próprio ecrã de definições.
- **FR-011**: A app MUST apresentar, junto de cada definição, uma explicação curta do que muda na
  prática ao alterá-la.
- **FR-012**: A app MUST indicar quando um valor está no seu valor de origem.

#### O que não pode acontecer

- **FR-013**: A app MUST manter cada valor dentro de limites derivados da **utilidade geométrica** —
  o alcance a que ainda há algo para ver — e não do orçamento de consultas, que os valores não
  alteram (ver `research.md`, D1).
- **FR-014**: A app MUST indicar, quando o raio escolhido excede o alcance que o ângulo mínimo torna
  visível, que aumentá-lo mais não trará aeronaves novas. As duas definições interagem, e o
  utilizador não tem como descobrir isso sozinho.
- **FR-015**: A app MUST NOT alterar a cadência de atualização da lista nesta feature.
- **FR-016**: A app MUST NOT perder ou reinterpretar as escolhas do utilizador ao ser atualizada
  para uma versão nova.
- **FR-017**: Uma alteração de definições MUST NOT deixar uma atualização em curso a meio nem
  produzir uma lista com critérios misturados.

### Key Entities

- **Preferências**: o conjunto das escolhas do utilizador — raio, ângulo mínimo, altitude mínima,
  unidade de distância, unidade de altitude. Cada uma tem um valor de origem, um mínimo e um
  máximo.
- **Critérios de céu**: o que a app usa para decidir se uma aeronave está no céu do utilizador.
  Derivam das preferências; hoje são valores fixos.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Uma alteração feita nas definições reflete-se na lista em menos de um ciclo de
  atualização, sem reiniciar a app.
- **SC-002**: 100% das escolhas sobrevivem a fechar e reabrir a app.
- **SC-003**: Nenhuma combinação de valores permitidos altera o custo de uma consulta à fonte de
  voos face ao de hoje — medido em créditos por consulta, não em número de pedidos.
- **SC-004**: Com os valores nos extremos permitidos, a lista continua a aparecer dentro do mesmo
  limite de tempo de hoje.
- **SC-005**: 100% dos valores guardados inválidos ou ilegíveis resultam num arranque normal com os
  valores de origem — nunca num erro visível.
- **SC-006**: Todas as grandezas apresentadas na app respeitam a unidade escolhida: nenhuma mistura
  de unidades em nenhum ecrã.
- **SC-007**: 8 em cada 10 utilizadores de teste conseguem prever, lendo a explicação, o que
  acontece à lista se alterarem cada definição.

## Assumptions

- **O ecrã de definições já existe** e tem uma entrada: a atualização da tabela de rotas, feita na
  feature anterior. Esta feature **estende** esse ecrã e o seu estado, em vez de criar um novo
  (decisão já registada na AD-017).
- As preferências de **notificações e de widget** ficam de fora: pertencem às features que criam
  essas coisas, e ativá-las aqui seria oferecer um interruptor que não liga a nada.
- A **cadência de atualização da lista** não é configurável nesta feature. Continua nos 30 segundos
  fixados na primeira feature, cujo orçamento diário de consultas foi calculado a partir desse
  valor.
- A escolha de unidades é de **apresentação**: as grandezas continuam a ser guardadas e calculadas
  numa só unidade, e a conversão acontece à entrada do ecrã.
- Repor os valores de origem repõe **todas** as preferências desta feature, não as da tabela de
  rotas.
- **O orçamento da fonte de voos não depende das preferências.** O custo de uma consulta é
  determinado pela área da caixa envolvente, e qualquer raio utilizável fica no primeiro degrau de
  custo. O teto real da app — cerca de 3h20m de ecrã aberto por dia — existe hoje e nenhuma
  definição o altera. Está apurado com contas em `research.md`, D1.
- **A cadência do trabalho em segundo plano não é esta.** O modelo de preferências tem um campo de
  intervalo de atualização, mas pertence ao trabalho periódico do widget e das notificações, com o
  seu próprio mínimo de 15 minutos. Esta feature não lhe liga controlo nenhum.

## Out of Scope

- Ativar ou desativar notificações e widget — pertencem às features respetivas.
- A cadência de atualização da lista, e a frequência do trabalho em segundo plano.
- Tema claro/escuro, idioma, tamanho de letra: a app segue o sistema.
- Perfis ou conjuntos de definições guardados, e sincronização entre dispositivos.
- Exportar ou importar preferências.
- Qualquer alteração à forma como as aeronaves são detetadas — esta feature muda os **limiares**,
  não a regra.
