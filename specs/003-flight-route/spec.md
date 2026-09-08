# Feature Specification: Origem e destino do voo

**Feature Branch**: `003-flight-route`

**Created**: 2026-09-07

**Status**: Draft

**Input**: User description: "Aeroporto de origem e destino de cada voo, apenas a sigla. Na lista do céu e no ecrã de detalhe, cada aeronave passa a mostrar de onde vem e para onde vai — por exemplo \"LIS → CDG\". Só a sigla do aeroporto, sem nome nem cidade. É informação opcional: muitos voos não a terão, e quando faltar a aeronave continua a aparecer normalmente, sem espaço vazio nem marcador de ausência, exatamente como já acontece com o nome da companhia. Não pode aumentar o número de pedidos por ciclo à fonte de voos nem gastar o orçamento diário, e não pode atrasar a apresentação da lista."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Saber de onde vem e para onde vai o avião (Priority: P1)

O utilizador olha para a lista do céu e, além de saber que aquele é um voo da TAP a 10 000 metros,
vê que vem de Lisboa e vai para Paris. É a informação que transforma um ponto no céu numa história:
deixa de ser "um avião" e passa a ser "aquele voo".

**Why this priority**: é a feature toda. Sem isto não há nada.

**Independent Test**: com a lista preenchida num local com tráfego comercial, verificar que as
aeronaves cuja rota é conhecida mostram as duas siglas, e que a mesma informação aparece no ecrã de
detalhe da mesma aeronave.

**Acceptance Scenarios**:

1. **Given** uma aeronave cuja origem e destino são conhecidos, **When** o utilizador vê a lista,
   **Then** a entrada mostra as duas siglas de aeroporto, com o sentido do percurso legível de
   relance.
2. **Given** a mesma aeronave, **When** o utilizador abre o detalhe, **Then** vê exatamente as
   mesmas duas siglas.
3. **Given** uma aeronave cuja rota é desconhecida, **When** o utilizador vê a lista, **Then** a
   aeronave aparece normalmente, sem espaço reservado, sem travessão e sem qualquer marca de
   ausência.

---

### User Story 2 - Não ser enganado sobre a rota (Priority: P2)

A rota é a informação desta app mais fácil de dar por certa e mais difícil de verificar: o
utilizador não tem como confirmar que aquele avião vem mesmo de Lisboa. Por isso a app só a mostra
quando a sabe, e nunca a preenche por dedução, semelhança ou probabilidade.

O que a app sabe é a rota **agendada** daquele número de voo. Num dia de desvios ou num voo fora de
horário, dirá o percurso previsto e não o real — é o limite aceite desta feature, e o preço de não
gastar um pedido por avião.

**Why this priority**: uma rota errada é indistinguível de uma rota certa para quem olha para o
ecrã — é exatamente a categoria de erro que o princípio VI da constituição manda tratar com
cuidado. Vale mais não dizer nada.

**Independent Test**: alimentar a app com um indicativo que não corresponde a nenhuma rota conhecida
e verificar que nenhuma sigla aparece, em vez de aparecer a rota de um indicativo parecido.

**Acceptance Scenarios**:

1. **Given** um indicativo de voo que a app não sabe mapear, **When** a aeronave é apresentada,
   **Then** nenhuma sigla é mostrada, nem sequer parcialmente.
2. **Given** uma aeronave sem indicativo de voo, **When** é apresentada, **Then** aparece sem rota,
   como já acontece hoje com a companhia.
3. **Given** que a origem é conhecida mas o destino não, **When** a aeronave é apresentada,
   **Then** a app não apresenta meia rota nem completa a que falta.

---

### User Story 3 - A app continua rápida e dentro do orçamento (Priority: P2)

O utilizador não repara nesta história quando ela funciona — repara quando falha, porque a lista
demora a aparecer ou porque a app deixa de atualizar a meio do dia por ter esgotado o que lhe cabe
da fonte de dados.

**Why this priority**: a app tem hoje um orçamento diário de pedidos que já foi contado ao segundo,
e um limite de 5 segundos até à primeira lista. Uma feature acessória não pode gastar nem uma coisa
nem outra.

**Independent Test**: comparar o número de pedidos ao serviço de voos e o tempo até à primeira
lista, antes e depois desta feature, no mesmo local e à mesma hora.

**Acceptance Scenarios**:

1. **Given** o ecrã aberto durante 15 minutos, **When** se conta a atividade de rede, **Then** o
   número de pedidos ao serviço de voos é o mesmo de antes desta feature.
2. **Given** um arranque a frio com a permissão concedida, **When** se cronometra até a lista
   aparecer, **Then** o tempo não é pior do que antes desta feature.
3. **Given** que a origem da rota está indisponível ou falha, **When** a app é usada, **Then** a
   lista e o detalhe continuam a funcionar exatamente como antes, apenas sem rotas.

---

### User Story 4 - Atualizar a tabela de rotas sem esperar por uma versão nova da app (Priority: P3)

As rotas mudam com as estações e com os horários das companhias. O utilizador que note que um voo
aparece sem rota — ou com uma rota que já não se faz — pode ir às definições e pedir uma
atualização, em vez de ficar à espera que saia uma versão nova da app.

**Why this priority**: a app é útil sem isto; a tabela que vem na instalação chega para a maioria
dos voos. Mas sem esta história a informação envelhece a cada semana que passa entre versões, e é
a única parte da app que se degrada sozinha com o tempo.

**Independent Test**: com uma tabela desatualizada instalada, pedir a atualização nas definições e
verificar que voos que antes apareciam sem rota passam a mostrá-la.

**Acceptance Scenarios**:

1. **Given** o utilizador nas definições, **When** pede a atualização da tabela de rotas,
   **Then** vê que a atualização está em curso e, no fim, se correu bem e de quando são os dados.
2. **Given** que a atualização falha — sem rede, servidor indisponível, transferência
   interrompida — **When** o utilizador volta à lista, **Then** a app continua a funcionar com a
   tabela que já tinha, sem nunca ficar sem nenhuma.
3. **Given** que a atualização correu bem, **When** o utilizador volta à lista, **Then** as rotas
   refletem a tabela nova sem ser preciso reiniciar a app.
4. **Given** que o utilizador nunca pediu uma atualização, **When** usa a app normalmente,
   **Then** nenhuma transferência acontece por iniciativa da app.

---

### Edge Cases

- **Voo doméstico com origem e destino no mesmo aeroporto** (voo de instrução, teste, ou regresso
  ao ponto de partida): apresentar "LIS → LIS" é informação legítima, não um erro a esconder.
- **Indicativo com espaços ou em minúsculas**: tem de ser reconhecido na mesma; a app já normaliza
  o indicativo para a companhia.
- **Indicativo de aviação privada ou militar** (matrículas como `CS-DHA`, `N123AB`): não tem número
  de voo comercial, logo não tem rota conhecida. Aparece sem rota, sem erro.
- **Rota conhecida mas aeroporto sem sigla de três letras**: aeroportos pequenos podem não ter
  designador comercial. Nesse caso não há sigla para mostrar e a rota é omitida.
- **Voo desviado ou fora de horário**: a app apresenta a rota agendada, que nesse dia está errada.
  É consequência conhecida e aceite de FR-008, não um defeito a corrigir — corrigi-la exigiria uma
  consulta por avião, que FR-012 proíbe.
- **A aeronave sai do céu com o detalhe aberto**: a rota faz parte dos últimos valores conhecidos e
  fica visível com eles, marcada como já não atual, tal como o resto.
- **Origem da rota indisponível**: a app funciona sem rotas, sem mensagem de erro e sem degradar
  nada do que já existia.
- **Atualização interrompida a meio** (rede cai, app fechada, bateria acaba): a tabela anterior tem
  de continuar íntegra e utilizável. Uma tabela meio escrita é pior do que uma tabela velha.
- **Atualização pedida sem rede**: a app di-lo de imediato, em vez de tentar e falhar em silêncio.
- **Atualização pedida com o ecrã do céu aberto noutro sítio**: a lista continua a atualizar-se
  normalmente durante a transferência.
- **Ficheiro recebido corrompido ou vazio**: é descartado, e a tabela anterior fica. O utilizador é
  informado de que a atualização não pegou.

## Requirements *(mandatory)*

### Functional Requirements

#### O que se mostra

- **FR-001**: A app MUST apresentar, na lista do céu, a sigla do aeroporto de origem e a do de
  destino das aeronaves cuja rota é conhecida.
- **FR-002**: A app MUST apresentar a mesma rota no ecrã de detalhe da mesma aeronave, com os
  mesmos valores.
- **FR-003**: A app MUST apresentar o sentido do percurso de forma inequívoca, sem o utilizador ter
  de deduzir qual das siglas é a origem.
- **FR-004**: A app MUST apresentar apenas a sigla do aeroporto. Nome, cidade e país ficam de fora.
- **FR-005**: A app MUST omitir a rota por completo, sem espaço reservado e sem marcador de
  ausência, quando ela não é conhecida — o mesmo tratamento que já é dado ao nome da companhia.
- **FR-006**: A app MUST NOT apresentar uma rota parcial: sem os dois aeroportos, não se apresenta
  nenhum.

#### De onde vem a informação

- **FR-007**: A app MUST derivar a rota a partir do indicativo de voo da aeronave.
- **FR-008**: A app MUST apresentar a rota **agendada** do número de voo — de onde aquele voo
  costuma partir e para onde costuma ir — e MUST NOT apresentar uma rota obtida por semelhança,
  dedução ou probabilidade. Só a que corresponde exatamente ao indicativo.
- **FR-009**: A app MUST NOT afirmar que a rota apresentada é a de hoje. Não a apresenta como
  confirmada nem acrescenta hora, estado ou qualquer indício de que segue o voo em curso.
- **FR-010**: A app MUST reconhecer o indicativo independentemente de espaços em excesso ou de
  maiúsculas e minúsculas.
- **FR-011**: A app MUST continuar a apresentar a aeronave, com toda a restante informação, quando
  a rota é desconhecida.

#### O que não pode acontecer

- **FR-012**: A app MUST NOT aumentar o número de pedidos ao serviço de voos por ciclo de
  atualização.
- **FR-013**: A app MUST NOT atrasar a apresentação da lista à espera da rota.
- **FR-014**: A app MUST continuar a funcionar, sem erro visível para o utilizador, quando a origem
  dos dados de rota está indisponível ou ilegível — apenas sem rotas.
- **FR-015**: A app MUST NOT enviar a posição do utilizador, nem qualquer dado que o identifique,
  para obter a rota.

#### Atualizar a tabela

- **FR-016**: Users MUST be able to pedir, a partir do ecrã de definições, a atualização da tabela
  de rotas.
- **FR-017**: A app MUST indicar que a atualização está em curso e comunicar o resultado — sucesso
  ou falha, com a causa.
- **FR-018**: A app MUST apresentar a data dos dados de rota que está a usar, para o utilizador
  saber se vale a pena atualizar.
- **FR-019**: A app MUST NOT transferir a tabela por iniciativa própria. A atualização acontece
  apenas quando o utilizador a pede.
- **FR-020**: A app MUST preservar a tabela anterior, íntegra e utilizável, quando a atualização
  falha, é interrompida ou traz dados inválidos. Em nenhum momento a app pode ficar sem tabela por
  causa de uma atualização.
- **FR-021**: A app MUST passar a usar a tabela nova sem exigir que o utilizador reinicie a app.
- **FR-022**: A app MUST continuar a apresentar a lista do céu normalmente enquanto a atualização
  decorre.
- **FR-023**: A transferência da tabela MUST NOT contar para o orçamento de pedidos ao serviço de
  voos, nem interferir com o ciclo de atualização da lista.

### Key Entities

- **Rota**: par ordenado de aeroportos — origem e destino — associado a um indicativo de voo.
  Opcional por natureza: a maioria dos indicativos que passa no céu de um observador não terá rota
  conhecida.
- **Aeroporto**: identificado apenas pela sua sigla, tal como o utilizador a reconhece nos bilhetes
  e nos painéis. Nesta feature não tem mais atributos.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Entre as aeronaves com indicativo de voo comercial apresentadas na lista, pelo menos
  70% mostram origem e destino.
- **SC-002**: Nenhuma aeronave sem rota conhecida apresenta espaço vazio, travessão, interrogação
  ou qualquer outra marca de ausência.
- **SC-003**: A rota apresentada na lista e no detalhe da mesma aeronave é idêntica em 100% dos
  casos.
- **SC-004**: O número de pedidos ao serviço de voos, com o ecrã aberto durante 15 minutos, é
  exatamente o mesmo de antes desta feature.
- **SC-005**: A mediana do tempo até à primeira lista, a partir de um arranque a frio com permissão
  concedida, não aumenta face ao valor medido antes desta feature.
- **SC-006**: Com a origem dos dados de rota indisponível, 100% das restantes funcionalidades da
  lista e do detalhe continuam a comportar-se como antes.
- **SC-007**: Numa amostra de 30 voos verificados contra uma fonte independente, nenhum apresenta
  uma rota diferente da rota agendada desse número de voo. Uma rota em falta não conta como erro; um
  desvio ou um voo fora de horário também não — é o limite conhecido da feature. Conta como erro
  apresentar a rota de outro voo, ou uma rota que aquele número nunca fez.

- **SC-008**: Uma atualização interrompida em qualquer momento deixa a app com uma tabela válida
  em 100% das tentativas — a nova se completou, a anterior se não.
- **SC-009**: Depois de uma atualização bem sucedida, a cobertura de rotas não é inferior à que
  havia antes.
- **SC-010**: Sem o utilizador pedir, a app não transfere a tabela nenhuma vez, verificado ao longo
  de uma sessão de uso normal.

## Assumptions

- **A sigla é a de três letras** que o utilizador vê nos bilhetes e nos painéis dos aeroportos
  (LIS, CDG, LHR), e não o código de quatro letras usado pela aviação. É o que o exemplo do pedido
  mostra, e é o único que a maioria das pessoas reconhece.
- A rota é derivada do indicativo de voo, como já acontece com a companhia. Uma aeronave sem
  indicativo nunca terá rota.
- **A rota é a agendada, não a real** (decisão de 2026-09-07). É o que permite cumprir FR-012 e
  FR-013 sem contorcionismos: a rota de um número de voo é informação estável, que não precisa de
  ser perguntada a ninguém no momento em que o avião passa. A alternativa — a rota real, consultada
  por aeronave — significaria dezenas de pedidos por ciclo, contra as duas restrições que a feature
  tem à cabeça.
- A rota é **decoração**, no mesmo sentido em que o nome da companhia é: nunca esconde uma
  aeronave, nunca a impede de aparecer, e nunca justifica um ecrã de erro.
- A cobertura não será completa e isso é aceitável. Voos privados, militares, de carga fora de
  rotas regulares e charters não terão rota, e não é objetivo desta feature que tenham.
- As regras de apresentação de campos ausentes, de consistência entre os dois ecrãs e de dados
  desatualizados já fixadas nas features anteriores aplicam-se sem alteração.
- **O ecrã de definições existe mas está vazio** — é um dos esqueletos deixados pela feature 001.
  Esta feature cria nele a primeira entrada real, a atualização da tabela de rotas, e **não**
  constrói a feature de definições. As restantes preferências (raio, elevação mínima, unidades)
  continuam a pertencer a uma feature própria.
- A app é instalada com uma tabela de rotas já incluída. A atualização serve para a renovar, não
  para a obter pela primeira vez: um utilizador que nunca vá às definições tem rotas na mesma.

## Out of Scope

- Nome, cidade, país ou coordenadas do aeroporto — apenas a sigla.
- Horas de partida e de chegada, atrasos, portas de embarque, número de bilhete.
- Escalas intermédias: a rota é um par origem-destino, não um itinerário.
- Representação gráfica da rota, mapa ou linha percorrida.
- Pesquisar voos por aeroporto, ou filtrar a lista por origem ou destino.
- Histórico das rotas dos voos já vistos.
- Atualização automática ou agendada da tabela: nesta feature a atualização é sempre pedida pelo
  utilizador.
- As restantes preferências do ecrã de definições — raio de deteção, elevação mínima, unidades,
  notificações — que pertencem à feature de definições.
