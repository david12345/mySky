# Feature Specification: Detalhe de um voo

**Feature Branch**: `002-flight-detail`

**Created**: 2026-09-07

**Status**: Draft

**Input**: User description: "Ecrã de detalhe de um voo. Ao tocar numa linha da lista do céu, o utilizador vê tudo o que já sabemos sobre aquela aeronave — indicativo, operador, país de registo, altitude barométrica e geométrica, velocidade, rumo, razão de subida/descida, distância, azimute e elevação a partir da sua posição, e há quanto tempo o dado foi visto — com um botão de voltar. Sem fonte de dados nova: matrícula, tipo de aeronave e rota ficam de fora desta feature. O ecrã tem de continuar a atualizar-se enquanto está aberto e tem de dizer alguma coisa útil quando a aeronave sai do céu do utilizador."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver tudo o que se sabe do avião que escolhi (Priority: P1)

O utilizador vê na lista um avião que lhe chama a atenção — está mesmo por cima, ou é o único no
céu — e toca nele. Abre-se um ecrã dedicado a essa aeronave, com todos os dados disponíveis
organizados de forma legível: quem é, onde está em relação a ele, a que altitude e a que
velocidade vai, e se está a subir ou a descer. Um toque para voltar devolve-o à lista.

**Why this priority**: é a feature inteira no seu essencial, e fecha um beco sem saída que já
existe na app — hoje o toque numa linha abre um ecrã vazio. Sem esta história não há nada.

**Independent Test**: com a lista preenchida, tocar numa linha e verificar que o ecrã mostra a
aeronave certa com os campos disponíveis preenchidos, e que o botão de voltar devolve à lista na
mesma posição.

**Acceptance Scenarios**:

1. **Given** a lista com várias aeronaves, **When** o utilizador toca numa delas, **Then** abre-se
   um ecrã que identifica inequivocamente essa aeronave e nenhuma outra.
2. **Given** o ecrã de detalhe aberto, **When** o utilizador o lê, **Then** vê a identificação, o
   operador, a origem do registo, as duas altitudes, a velocidade, o rumo, a razão de subida ou
   descida, a distância, a direção e a elevação a partir da sua posição, e o momento a que os
   dados dizem respeito.
3. **Given** uma aeronave cujos dados não incluem alguns destes campos, **When** o detalhe é
   apresentado, **Then** os campos em falta são omitidos, sem espaços por preencher nem valores
   inventados, e os restantes continuam legíveis.
4. **Given** o ecrã de detalhe aberto, **When** o utilizador aciona voltar — pelo controlo do ecrã
   ou pelo gesto do sistema, **Then** regressa à lista tal como a deixou.

---

### User Story 2 - Acompanhar o avião enquanto ele passa (Priority: P2)

O utilizador fica com o detalhe aberto enquanto o avião atravessa o céu. Os números mexem-se: a
elevação sobe até ele passar por cima e depois desce, a distância encurta e volta a aumentar, a
altitude muda se estiver a subir ou a descer. O ecrã diz sempre a que instante correspondem os
dados que mostra.

**Why this priority**: sem isto o detalhe é uma fotografia de um objeto que se move — os números
ficariam errados ao fim de segundos, com ar de certos. É também o que torna o ecrã interessante
de ter aberto enquanto se olha para o céu.

**Independent Test**: abrir o detalhe de uma aeronave em movimento e observar durante dois
minutos que os valores mudam sozinhos e que a marca temporal nunca envelhece para além de um
ciclo de atualização.

**Acceptance Scenarios**:

1. **Given** o detalhe aberto de uma aeronave em movimento, **When** passa um ciclo de
   atualização, **Then** os valores apresentados refletem a observação mais recente sem qualquer
   ação do utilizador.
2. **Given** o detalhe aberto, **When** o utilizador força uma atualização, **Then** os dados são
   renovados de imediato e o ciclo automático recomeça a contar.
3. **Given** o detalhe aberto, **When** a app deixa de estar visível, **Then** a obtenção de dados
   e de localização cessa, e retoma quando o ecrã volta a ficar visível.
4. **Given** o detalhe aberto, **When** o ecrã roda ou a configuração do dispositivo muda,
   **Then** os dados apresentados mantêm-se, sem novo carregamento visível.

---

### User Story 3 - Perceber que o avião saiu do meu céu (Priority: P2)

O avião que o utilizador está a acompanhar afasta-se, desce demasiado no horizonte ou aterra, e
deixa de fazer parte do seu céu. O ecrã não pode continuar a mostrar os últimos números como se
fossem atuais, nem esvaziar-se sem explicação: tem de dizer o que aconteceu e desde quando.

**Why this priority**: é o desfecho normal de qualquer avião observado — acontece a **todos** ao
fim de alguns minutos. Um ecrã que congela sem avisar é a forma mais fácil de a app mentir ao
utilizador.

**Independent Test**: abrir o detalhe de uma aeronave prestes a sair do critério e observar que,
quando ela desaparece da lista, o ecrã passa a explicar a situação e deixa de apresentar os
valores como se fossem de agora.

**Acceptance Scenarios**:

1. **Given** o detalhe aberto, **When** a aeronave deixa de constar das observações do céu do
   utilizador, **Then** o ecrã indica-o de forma explícita, referindo o instante da última
   observação.
2. **Given** uma aeronave que saiu do céu, **When** o utilizador olha para os valores,
   **Then** percebe, sem ambiguidade, que são a última posição conhecida e não a posição atual.
3. **Given** uma aeronave que saiu do céu e volta a aparecer nas observações seguintes,
   **When** isso acontece, **Then** o ecrã volta a apresentar dados atuais sem exigir que o
   utilizador saia e volte a entrar.

---

### User Story 4 - Continuar a perceber o que se passa quando algo corre mal (Priority: P3)

Com o detalhe aberto, a ligação cai, o serviço de voos falha ou a permissão de localização é
retirada nas definições do sistema. O utilizador tem de perceber o que aconteceu e continuar a
ver os últimos dados obtidos.

**Why this priority**: o ecrã principal já resolve estes casos; o detalhe herda o mesmo problema e
não pode resolvê-lo pior. Fica em P3 porque nenhum deles impede o uso normal da feature.

**Independent Test**: com o detalhe aberto, ativar o modo de avião e verificar que os dados
permanecem visíveis, marcados como possivelmente desatualizados, com a causa explicada.

**Acceptance Scenarios**:

1. **Given** o detalhe aberto com dados apresentados, **When** uma atualização falha, **Then** os
   últimos dados mantêm-se visíveis e a causa da falha é comunicada.
2. **Given** o detalhe aberto, **When** a permissão de localização é retirada fora da app,
   **Then** o utilizador é informado de que as grandezas relativas à sua posição deixaram de poder
   ser calculadas.

---

### Edge Cases

- **Aeronave sem indicativo de voo**: o ecrã identifica-a pelo identificador técnico, como já
  acontece na lista, e não fica sem título.
- **Operador desconhecido**: a linha da companhia desaparece, sem substituto nem interrogação.
- **Voo nivelado**: uma razão de subida praticamente nula não pode aparecer como "a subir" nem
  como um número sem significado.
- **Rumo ausente**: uma aeronave parada ou sem rumo reportado não mostra uma direção inventada.
- **Aeronave no zénite**: a direção a partir do observador deixa de ter significado quando o avião
  está mesmo por cima; o ecrã não pode apresentar um rumo arbitrário como se fosse informação.
- **Duas altitudes divergentes**: a altitude barométrica e a geométrica podem diferir em dezenas
  de metros; ambas são apresentadas identificadas, sem que o utilizador conclua que uma está
  errada.
- **Abertura a partir de uma lista já desatualizada**: se os dados de origem já eram antigos, o
  detalhe di-lo desde o primeiro instante, em vez de os apresentar como recentes.
- **Regresso à app horas depois**, com o detalhe ainda no topo da pilha: o ecrã não pode
  apresentar dados antigos como atuais.
- **Céu vazio**: se ao voltar à lista o céu entretanto esvaziou, a lista mostra o seu próprio
  estado de céu vazio — abrir o detalhe não altera o comportamento da lista.

## Requirements *(mandatory)*

### Functional Requirements

#### Abertura e identificação

- **FR-001**: Users MUST be able to abrir o detalhe de uma aeronave tocando na sua entrada na
  lista do céu.
- **FR-002**: A app MUST apresentar no detalhe a aeronave selecionada e nenhuma outra, mesmo que
  outras partilhem indicativo de voo, operador ou posição aproximada.
- **FR-003**: A app MUST identificar a aeronave pelo indicativo de voo e, na sua ausência, pelo
  identificador técnico, sem deixar o ecrã sem título.
- **FR-004**: Users MUST be able to regressar à lista a partir de um controlo visível no ecrã e
  também pelo gesto de voltar do sistema.

#### Conteúdo apresentado

- **FR-005**: A app MUST apresentar, quando disponíveis: operador aéreo, origem do registo,
  altitude barométrica, altitude geométrica, velocidade, rumo e razão de subida ou descida.
- **FR-006**: A app MUST apresentar, quando a posição do utilizador é conhecida: distância
  horizontal, direção e ângulo de elevação da aeronave a partir dessa posição.
- **FR-007**: A app MUST apresentar o instante a que as observações dizem respeito, em termos
  compreensíveis sem cálculo mental.
- **FR-008**: A app MUST omitir os campos indisponíveis, sem espaços por preencher, sem
  marcadores de ausência e sem valores por omissão que possam ser lidos como medições.
- **FR-009**: A app MUST distinguir as duas altitudes de forma que o utilizador saiba qual é qual.
- **FR-010**: A app MUST apresentar cada grandeza com a respetiva unidade.
- **FR-011**: A app MUST exprimir a razão de subida ou descida de forma que o sentido do
  movimento seja imediato, e MUST apresentar o voo como nivelado quando a variação é
  negligenciável.
- **FR-012**: A app MUST NOT apresentar uma direção a partir do observador quando a aeronave está
  suficientemente perto do zénite para essa direção não ter significado.
- **FR-013**: A app MUST apresentar os mesmos valores que a lista apresenta para a mesma
  aeronave e no mesmo instante, nas mesmas unidades.

#### Atualização enquanto o ecrã está aberto

- **FR-014**: A app MUST atualizar os dados do detalhe automaticamente, com a mesma cadência com
  que atualiza a lista, enquanto o ecrã está visível.
- **FR-015**: Users MUST be able to forçar uma atualização a qualquer momento a partir do detalhe.
- **FR-016**: A app MUST cessar toda a obtenção de dados de voo e de localização no prazo de 5
  segundos após o detalhe deixar de estar visível, e retomá-la quando voltar a estar.
- **FR-017**: A app MUST impedir atualizações concorrentes entre a lista e o detalhe: ter os dois
  ecrãs na pilha de navegação não pode duplicar os pedidos ao serviço de voos.
- **FR-018**: A app MUST preservar os dados apresentados através de mudanças de configuração do
  dispositivo, sem novo carregamento visível.

#### Quando a aeronave sai do céu

- **FR-019**: A app MUST detetar que a aeronave selecionada deixou de constar das observações do
  céu do utilizador.
- **FR-020**: A app MUST manter visíveis os últimos valores conhecidos, indicando de forma
  explícita que a aeronave saiu do céu do utilizador e a que instante essa última observação diz
  respeito. Os valores NÃO PODEM continuar a poder ser lidos como posição atual.
- **FR-021**: A app MUST voltar a apresentar dados atuais, sem intervenção do utilizador, se a
  aeronave reaparecer nas observações seguintes.

#### Erros e ausência de posição

- **FR-022**: A app MUST manter visíveis os últimos dados obtidos quando uma atualização falha,
  comunicando a causa.
- **FR-023**: A app MUST distinguir, nas mensagens, ausência de ligação, falha do serviço de voos
  e impossibilidade de obter a posição do utilizador.
- **FR-024**: A app MUST continuar a apresentar os dados da aeronave que não dependem da posição
  do utilizador quando essa posição não pode ser obtida.

### Key Entities

- **Aeronave selecionada**: a aeronave cujo detalhe está a ser apresentado, identificada por um
  identificador estável que não muda entre observações nem depende do indicativo de voo.
- **Observação**: o conjunto de valores conhecidos sobre a aeronave num dado instante — posição,
  altitudes, velocidade, rumo, razão de subida — e o instante a que dizem respeito.
- **Relação com o observador**: distância, direção e elevação da aeronave a partir da posição do
  utilizador. Deixa de existir quando a posição do utilizador é desconhecida.
- **Operador aéreo**: nome da companhia, derivado do indicativo de voo pela tabela já existente na
  app. Opcional por natureza.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A partir do toque numa entrada da lista, o detalhe aparece já preenchido em menos de
  1 segundo, sem estado de carregamento intermédio.
- **SC-002**: 100% dos campos que a lista apresenta para uma aeronave aparecem também no detalhe,
  com o mesmo valor e a mesma unidade no mesmo instante.
- **SC-003**: Com o detalhe aberto e parado durante 5 minutos, os valores apresentados nunca
  dizem respeito a observações com mais de 40 segundos.
- **SC-004**: Em 100% dos casos em que a aeronave sai do céu do utilizador, o ecrã explica a
  situação em vez de esvaziar ou de manter os valores com aspeto de atuais.
- **SC-005**: Nenhuma atividade de rede ou de localização ocorre nos 60 segundos seguintes a
  mandar a app para segundo plano com o detalhe aberto.
- **SC-006**: Numa aeronave com campos em falta, nenhum campo aparece vazio, com marcador de
  ausência ou com um valor por omissão que possa ser confundido com uma medição.
- **SC-007**: 9 em cada 10 utilizadores de teste regressam à lista à primeira tentativa, sem
  hesitação e sem recorrer ao gesto do sistema por não encontrarem o controlo.
- **SC-008**: Ter o detalhe aberto não aumenta o número de pedidos ao serviço de voos face a ter
  apenas a lista aberta durante o mesmo período.

## Assumptions

- O detalhe apresenta **apenas** dados que a app já obtém para construir a lista. Não há fonte de
  dados nova nesta feature, e nenhum campo do ecrã justifica um pedido adicional ao serviço.
- A posição do utilizador usada no detalhe é a mesma que alimenta a lista, obtida da mesma forma e
  com a mesma cadência.
- A cadência de atualização e o gesto de atualização manual seguem o que já foi fixado para a
  lista, para que o utilizador não tenha de aprender duas regras diferentes.
- O nome do operador vem da tabela local já existente; um prefixo desconhecido continua a produzir
  ausência de operador e nunca um erro.
- O detalhe é alcançável apenas a partir da lista. Chegar a ele a partir do widget ou de uma
  notificação pertence às features que criam esses pontos de entrada.
- A app continua a não prometer tempo real: o detalhe, como a lista, mostra observações datadas.
- Uma aeronave que sai do céu não faz desaparecer o que o utilizador estava a ler: os valores
  ficam, marcados como última observação. É a mesma escolha já feita para a lista, que nunca apaga
  resultados quando uma atualização falha — duas regras diferentes para o mesmo ecrã obrigariam o
  utilizador a aprender qual delas se aplica em cada momento.

## Out of Scope

- Matrícula, tipo e modelo de aeronave, e fotografia — exigem uma fonte de dados que a app não tem.
- Origem, destino e número comercial do voo — idem.
- Trajeto percorrido, histórico de avistamentos e qualquer persistência local do que foi visto.
- Mapa ou representação gráfica da posição.
- Partilhar, exportar ou copiar os dados do voo.
- Seguir uma aeronave com aviso quando ela voltar a passar.
- Qualquer alteração ao comportamento da lista para lá do que a navegação para o detalhe exige.
