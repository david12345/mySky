# Feature Specification: Lista de aviões no meu céu

**Feature Branch**: `001-sky-list`

**Created**: 2026-09-06

**Status**: Draft

**Input**: User description: "Quero o ecrã principal da app mySky, que mostra ao utilizador os aviões que estão atualmente a passar sobre o céu por cima da sua localização. Comportamento principal: ao abrir o ecrã, a app pede a localização atual do utilizador e mostra a lista de aviões atualmente 'no seu céu' (dentro do raio e do ângulo de elevação configurados); a lista atualiza-se automaticamente em intervalos razoáveis enquanto o ecrã está aberto; o utilizador pode forçar uma atualização manual. Informação por avião: callsign, companhia aérea quando disponível, altitude, velocidade, origem e destino quando disponíveis na fonte, distância aproximada. Estados a tratar: sem aviões, a carregar, erro de rede/API, permissão de localização não concedida. Tocar num avião abre o ecrã de detalhe. Fora de âmbito: detalhe, widget, notificações e definições."

> **Nota de idioma**: os títulos de secção mantêm-se em inglês porque são lidos pelos comandos
> seguintes do Spec Kit; o conteúdo está em português, como o resto da documentação do projeto.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver o que está no meu céu agora (Priority: P1)

Um utilizador ouve um avião, abre a mySky e quer saber, em segundos, o que está a passar por cima
de si. A app explica porque precisa da localização, obtém a posição depois de o utilizador aceitar
e apresenta a lista das aeronaves atualmente visíveis a partir dali, da mais alta no céu para a
mais baixa, com a informação que permite identificá-las.

**Why this priority**: É a razão de existir da app. Sem esta história não há produto — todas as
outras funcionalidades do MVP (widget, notificações) reutilizam este resultado.

**Independent Test**: Instalar a app, abrir, conceder a localização e verificar que aparece uma
lista de aeronaves coerente com o céu real naquele local e momento. Entrega valor sozinha, sem
qualquer outra história implementada.

**Acceptance Scenarios**:

1. **Given** o utilizador abre a app pela primeira vez, **When** o ecrã principal aparece,
   **Then** é-lhe explicado, antes de qualquer diálogo do sistema, porque é que a app precisa da
   localização e o que faz com ela.
2. **Given** o utilizador concedeu a localização e há aeronaves no seu céu, **When** os dados
   chegam, **Then** vê uma lista com uma entrada por aeronave, ordenada da mais alta no céu para a
   mais baixa.
3. **Given** a lista está visível, **When** o utilizador olha para uma entrada, **Then** vê o
   indicativo de voo, a altitude, a velocidade e a distância aproximada a si, cada valor com a sua
   unidade.
4. **Given** uma aeronave com indicativo de um operador conhecido, **When** aparece na lista,
   **Then** vê o nome do operador a par do indicativo, sem que a app faça pedidos de rede
   adicionais para o obter.
5. **Given** uma aeronave não reporta indicativo de voo, **When** aparece na lista, **Then** é
   identificada por um substituto estável em vez de um espaço em branco, e a linha do operador é
   simplesmente omitida.
6. **Given** o utilizador tem a localização concedida, **When** a app está a obter a posição ou os
   dados de voos, **Then** vê uma indicação visual de progresso que distingue as duas fases.

---

### User Story 2 - Confiar que estou a ver o céu de agora (Priority: P2)

Aviões atravessam o campo de visão em poucos minutos. O utilizador precisa de saber que o que está
no ecrã é atual: a lista renova-se sozinha enquanto o ecrã está aberto, mostra quando foi
atualizada pela última vez, e pode ser forçada a atualizar com um gesto.

**Why this priority**: Sem atualização, a lista fica errada em poucos minutos e a app perde a
credibilidade. Mas a história 1 já entrega valor sem isto (uma leitura pontual continua útil).

**Independent Test**: Deixar o ecrã aberto e verificar que a lista muda sozinha ao longo do tempo
sem qualquer ação; puxar para atualizar e confirmar que a marca temporal de última atualização
avança.

**Acceptance Scenarios**:

1. **Given** o ecrã principal está aberto e visível, **When** passa o intervalo de atualização
   automática, **Then** a lista é recalculada sem intervenção do utilizador.
2. **Given** a lista está visível, **When** o utilizador puxa para atualizar, **Then** a app obtém
   dados novos e mostra progresso durante a operação.
3. **Given** a lista foi atualizada, **When** o utilizador olha para o ecrã, **Then** vê há quanto
   tempo os dados foram obtidos.
4. **Given** o ecrã principal deixa de estar visível, **When** o utilizador está noutra app ou com
   o ecrã desligado, **Then** a app deixa de obter localização e dados de voos.
5. **Given** uma atualização automática está em curso, **When** o utilizador força uma atualização
   manual, **Then** não são feitos dois pedidos concorrentes nem a lista pisca entre resultados.

---

### User Story 3 - Perceber sempre o que se passa quando não há lista (Priority: P3)

Quando não há nada para mostrar, o utilizador tem de perceber porquê e o que pode fazer: não há
aviões agora, falhou a rede, ou falta a permissão. Nenhum destes casos pode resultar num ecrã
vazio e mudo.

**Why this priority**: Determina se a app parece avariada ou apenas honesta. É a diferença entre
uma desinstalação e um utilizador que volta mais tarde — mas depende de a história 1 existir.

**Independent Test**: Forçar cada condição (modo de avião, permissão recusada, zona sem tráfego) e
confirmar que cada uma produz uma mensagem distinta com a ação certa associada.

**Acceptance Scenarios**:

1. **Given** a app obteve dados com sucesso e nenhuma aeronave cumpre os critérios, **When** a
   lista é apresentada, **Then** o utilizador vê uma mensagem que deixa claro que o céu está
   simplesmente vazio naquele momento, sem aspeto de erro.
2. **Given** o pedido de dados de voos falhou, **When** o erro é apresentado, **Then** a mensagem
   distingue falta de ligação de falha do serviço de voos, e oferece uma ação de tentar de novo.
3. **Given** o utilizador recusou a permissão de localização, **When** volta ao ecrã principal,
   **Then** vê a explicação de porque a app precisa da localização e uma ação clara para a
   conceder.
4. **Given** o utilizador recusou a permissão de forma permanente, **When** toca na ação de
   conceder, **Then** é encaminhado para o sítio onde pode efetivamente alterar a permissão.
5. **Given** a permissão está concedida mas não foi possível obter uma posição, **When** o erro é
   apresentado, **Then** a mensagem distingue este caso da falha de rede e permite tentar de novo.
6. **Given** existiam resultados no ecrã, **When** uma atualização falha, **Then** os resultados
   anteriores continuam visíveis, assinalados como possivelmente desatualizados, em vez de
   desaparecerem.

---

### User Story 4 - Aprofundar um avião que me interessa (Priority: P3)

O utilizador identifica na lista o avião que está a ver e quer saber mais sobre ele.

**Why this priority**: Fecha o ciclo de interação do ecrã, mas o ecrã de destino é especificado à
parte; aqui só se garante que a navegação existe e leva à aeronave certa.

**Independent Test**: Tocar numa entrada da lista e confirmar que o ecrã de detalhe abre
identificado com aquela aeronave em concreto.

**Acceptance Scenarios**:

1. **Given** a lista tem aeronaves, **When** o utilizador toca numa entrada, **Then** abre o ecrã
   de detalhe referente a essa aeronave.
2. **Given** o utilizador está no detalhe, **When** volta atrás, **Then** regressa à lista sem
   perder o contexto de leitura.

---

### Edge Cases

- **Aeronave exatamente no zénite**: a direção relativa deixa de ter significado; a entrada tem de
  continuar legível e ser apresentada como estando diretamente por cima.
- **Utilizador junto ao antimeridiano ou a latitudes muito altas**: a zona consultada atravessa a
  linha de mudança de data ou envolve um polo; a lista tem de sair correta, sem duplicados nem
  aeronaves em falta.
- **Aeronave sem posição, sem altitude ou com dados obsoletos**: é descartada em silêncio, sem
  quebrar a lista nem contar para o total.
- **Indicativo que não identifica um operador** (aviação privada, que usa a matrícula como
  indicativo, ou operador ausente da tabela): a aeronave aparece na lista identificada pelo
  indicativo, sem nome de operador inventado.
- **Tabela de operadores desatualizada face à realidade**: um operador desconhecido nunca pode
  impedir a aeronave de aparecer na lista.
- **Aeronave no solo dentro do raio** (aeroporto próximo): não pode aparecer como estando no céu.
- **Serviço de voos a recusar por excesso de pedidos**: a app espaça os pedidos em vez de insistir,
  e diz ao utilizador que está temporariamente limitada.
- **Resposta válida mas vazia** vs. **falha do serviço**: têm de produzir estados diferentes.
- **Permissão revogada com o ecrã aberto**: ao voltar ao ecrã, o estado passa a "sem permissão" em
  vez de continuar a mostrar dados antigos indefinidamente.
- **Perda de ligação a meio de uma atualização automática**: não pode limpar a lista existente.
- **Aeronave que sai do céu entre duas atualizações**: desaparece da lista sem deixar o utilizador
  a tocar numa entrada que já não existe.
- **Rotação do ecrã ou mudança de configuração durante o carregamento**: não repete o pedido nem
  perde o resultado já obtido.
- **Localização a mudar durante a utilização** (utilizador em movimento): os resultados passam a
  referir-se à posição atual, não à inicial.

## Requirements *(mandatory)*

### Functional Requirements

**Localização e permissões**

- **FR-001**: A app MUST apresentar uma explicação do uso da localização antes de despoletar
  qualquer pedido de permissão do sistema.
- **FR-002**: A app MUST obter a posição atual do utilizador apenas depois de a permissão de
  localização estar concedida.
- **FR-003**: A app MUST funcionar com permissão de localização aproximada, sem exigir localização
  precisa.
- **FR-004**: A app MUST NOT pedir permissão de localização em segundo plano nesta feature.
- **FR-005**: A app MUST apresentar, quando a permissão está recusada, a razão de a precisar e uma
  ação que leve o utilizador a poder concedê-la, incluindo o caso de recusa permanente.
- **FR-006**: A app MUST reavaliar o estado da permissão sempre que o ecrã volta a ficar visível.

**Deteção e conteúdo da lista**

- **FR-007**: A app MUST apresentar as aeronaves que, a partir da posição do utilizador, estão
  dentro do raio de deteção em vigor E acima do ângulo de elevação mínimo em vigor.
- **FR-008**: A app MUST excluir da lista aeronaves em solo, sem posição conhecida, sem altitude
  conhecida, abaixo da altitude mínima em vigor, ou cuja última posição reportada seja demasiado
  antiga para ser fiável.
- **FR-009**: A app MUST ordenar a lista da aeronave mais alta no céu para a mais baixa.
- **FR-010**: A app MUST apresentar, por aeronave: indicativo de voo, altitude, velocidade e
  distância aproximada ao utilizador.
- **FR-011**: A app MUST apresentar o nome do operador aéreo de cada aeronave, obtido a partir do
  prefixo do indicativo de voo através de uma tabela de operadores incluída na própria app, sem
  quaisquer pedidos de rede adicionais.
- **FR-012**: A app MUST omitir a companhia, sem substituto nem espaço em branco, quando a
  aeronave não reporta indicativo de voo ou quando o prefixo não corresponde a nenhum operador
  conhecido da tabela. (Caso particular de FR-014, explicitado por ser o campo opcional mais
  visível da lista.)
- **FR-013**: A app MUST identificar uma aeronave sem indicativo de voo pelo seu `icao24` em
  maiúsculas — identificador estável, sempre presente e legível. É a única exceção à regra de
  omissão de FR-014.
- **FR-014**: A app MUST omitir de forma discreta os campos opcionais em falta, sem espaços vazios
  nem valores de substituição confusos.
- **FR-015**: A app MUST apresentar cada grandeza com a respetiva unidade.

**Atualização**

- **FR-016**: A app MUST atualizar a lista automaticamente a cada 30 segundos enquanto o ecrã
  principal estiver visível. O valor é fixo nesta feature e passa a configurável na feature de
  definições.
- **FR-017**: Users MUST be able to forçar uma atualização a qualquer momento através de um gesto
  na lista.
- **FR-018**: A app MUST indicar ao utilizador quando os dados apresentados foram obtidos.
- **FR-019**: A app MUST cessar toda a obtenção de localização e de dados de voos no prazo de 5
  segundos depois de o ecrã principal deixar de estar visível. A tolerância existe para que uma
  rotação de ecrã não cancele e reinicie trabalho já em curso.
- **FR-020**: A app MUST impedir atualizações concorrentes: um pedido em curso não é duplicado por
  um pedido manual.
- **FR-021**: A app MUST espaçar os pedidos quando o serviço de voos sinaliza excesso de
  utilização, em vez de repetir imediatamente.

**Estados do ecrã**

- **FR-022**: A app MUST distinguir visualmente as fases de obtenção de localização e de obtenção
  de dados de voos.
- **FR-023**: A app MUST apresentar uma mensagem de céu vazio, sem aspeto de erro, quando a
  consulta teve sucesso e nenhuma aeronave cumpre os critérios.
- **FR-024**: A app MUST distinguir, nas mensagens de erro, ausência de ligação, falha do serviço
  de voos e impossibilidade de obter a posição, e MUST oferecer em todas uma ação de repetição.
- **FR-025**: A app MUST manter visíveis os últimos resultados obtidos quando uma atualização
  falha, assinalando-os como possivelmente desatualizados.
- **FR-026**: A app MUST preservar o estado apresentado através de mudanças de configuração do
  dispositivo, sem repetir pedidos já concluídos.

**Navegação**

- **FR-027**: Users MUST be able to abrir o ecrã de detalhe de uma aeronave tocando na sua entrada
  na lista.
- **FR-028**: A app MUST identificar inequivocamente no ecrã de detalhe a aeronave selecionada.

### Key Entities

- **Aeronave observada**: uma aeronave que a fonte de dados reporta num dado instante — identidade
  estável, indicativo de voo, posição, altitude, velocidade, direção de voo, indicação de estar em
  solo e momento da última posição reportada. Vários destes atributos podem estar em falta.
- **Avião no meu céu**: uma aeronave observada acompanhada da geometria calculada face ao
  utilizador — distância horizontal, direção a partir do observador e altura acima do horizonte. É
  o que a lista apresenta e o que ordena a lista.
- **Operador aéreo**: a companhia que opera um voo, identificada pelo prefixo do indicativo. A
  correspondência entre prefixo e nome vive numa tabela distribuída com a app; é opcional, no
  sentido em que a lista funciona sem ela.
- **Posição do observador**: a localização do utilizador no momento da consulta, e o instante em
  que foi obtida.
- **Critérios de deteção**: raio máximo, altura mínima acima do horizonte, altitude mínima da
  aeronave e antiguidade máxima aceitável dos dados. Nesta feature têm valores fixos por omissão.
- **Momento da última atualização**: o instante em que os dados apresentados foram obtidos, e se a
  última tentativa falhou.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A partir de um arranque a frio com a permissão já concedida, o utilizador vê a lista
  de aviões no seu céu em menos de 5 segundos, numa ligação móvel típica.
- **SC-002**: Num local com tráfego aéreo, pelo menos 90% das aeronaves que um observador consegue
  ver a olho nu aparecem na lista dentro de um minuto.
- **SC-003**: Nenhuma aeronave em solo ou abaixo do ângulo de elevação configurado aparece na
  lista, verificado em pelo menos 20 observações junto a um aeroporto.
- **SC-004**: Pelo menos 95% das aeronaves com indicativo de voo comercial apresentadas na lista
  mostram o nome do operador, verificado sobre uma amostra de 100 entradas.
- **SC-005**: 100% dos estados sem lista — céu vazio, sem ligação, falha do serviço, sem posição,
  sem permissão, permissão recusada permanentemente — apresentam uma mensagem distinta e uma ação
  seguinte clara.
- **SC-006**: Com o ecrã aberto e parado durante 10 minutos, a lista reflete sempre dados obtidos
  há menos de tempo do que o intervalo de atualização acrescido de 10 segundos.
- **SC-007**: Nenhuma atividade de rede ou de localização ocorre nos 60 segundos seguintes a o
  ecrã deixar de estar visível.
- **SC-008**: Uma sessão contínua de 15 minutos com o ecrã aberto mantém-se dentro do orçamento de
  pedidos gratuito diário da fonte de dados.
- **SC-009**: Numa primeira utilização, 9 em cada 10 utilizadores de teste percebem, sem ajuda, o
  que a lista mostra e porque é que a app pediu a localização.

## Assumptions

- **Valores de deteção por omissão**: raio de 30 km, ângulo de elevação mínimo de 25° e altitude
  mínima de 300 m — os valores por omissão já definidos no domínio da app. Não são configuráveis
  nesta feature.
- **Intervalo de atualização automática**: 30 segundos com o ecrã visível. É o compromisso entre
  acompanhar aeronaves que atravessam o campo de visão em poucos minutos e caber no orçamento
  gratuito de pedidos da fonte de dados; passa a configurável na feature de definições.
- **Antiguidade máxima dos dados**: uma posição reportada há mais de 2 minutos é considerada pouco
  fiável e a aeronave é descartada.
- **Unidades**: quilómetros, metros e km/h nesta feature. A escolha de unidades pelo utilizador
  pertence à feature de definições.
- **Ordenação**: por altura acima do horizonte, decrescente — o avião mais alto no céu é o mais
  provável de estar efetivamente visível, e é o critério de relevância já definido no domínio.
- **Fonte de dados**: a fonte de voos em uso é consultada de forma anónima, com limites diários de
  utilização; a app tem de se comportar bem quando esses limites são atingidos.
- **Tabela de operadores**: distribuída com a app e consultada localmente, a partir do prefixo de
  3 letras do indicativo de voo. Cobre operadores comerciais; aviação privada, militar e de carga
  ocasional ficam sem nome, o que é aceitável. Atualizar a tabela é uma tarefa de manutenção
  periódica, não uma funcionalidade.
- **Origem e destino**: a fonte de posição em tempo real não devolve rota, por isso origem e
  destino não fazem parte desta feature. Passam a ser obtidos no ecrã de detalhe, para a aeronave
  que o utilizador escolher — um pedido por toque, em vez de um por avião a cada atualização.
- **Sem persistência nesta feature**: a lista é sempre obtida de novo; o histórico de avistamentos
  pertence à feature de detalhe.
- **Ecrã de detalhe**: assume-se que existe como destino de navegação; o seu conteúdo é
  especificado à parte.
- **Conetividade**: assume-se que o utilizador está tipicamente online; a app não funciona offline
  nesta feature, apenas explica a ausência de ligação.
- **Um único observador**: a app não tem contas nem múltiplos perfis.

## Out of Scope

- Ecrã de detalhe do avião (apenas a navegação para ele faz parte desta feature).
- Origem e destino do voo na lista — a obtenção da rota pertence ao ecrã de detalhe.
- Manutenção ou atualização em runtime da tabela de operadores.
- Widget de ecrã inicial.
- Notificações de aeronave por cima.
- Ecrã de definições e qualquer configuração dos critérios de deteção ou das unidades pelo
  utilizador.
- Visualização em mapa.
- Histórico ou persistência de avistamentos.
- Funcionamento sem ligação à Internet.
