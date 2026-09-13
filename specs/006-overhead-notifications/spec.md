# Feature Specification: Notificações de passagem

**Feature Branch**: `006-overhead-notifications`
**Created**: 2026-09-13
**Status**: Draft

## A tensão central, dita à cabeça

Como na 005, o que o utilizador pede é razoável e a plataforma não o permite por inteiro. Aqui é pior,
e é preciso que fique escrito antes dos requisitos:

O trabalho de fundo corre, no melhor caso, de **15 em 15 minutos**. Uma aeronave a 12 km de altitude
fica acima de um dado ângulo durante:

| Ângulo | Raio correspondente | Janela | Passagens apanhadas @15min | @30min |
|---|---|---|---|---|
| 30° | 20,8 km | 166 s | 18,5% | 9,2% |
| 45° | 12,0 km | 96 s | 10,7% | 5,3% |
| 60° | 6,9 km | 55 s | 6,2% | 3,1% |
| 75° | 3,2 km | 26 s | 2,9% | 1,4% |

**Esta feature perde a esmagadora maioria das passagens** — entre 81% e 98%, conforme o limiar e a
cadência. Não há desenho que resolva isto sem um serviço em primeiro plano permanente, que a
constituição proíbe (AD-004) e que seria o pior custo de bateria possível.

A diferença crucial face ao widget: aqui o erro é **só de omissão**. Quando a notificação dispara, o
avião está mesmo lá — a app não mente, apenas cala-se quase sempre. É isso que torna a feature
defensável, e é isso que tem de ser dito ao utilizador **antes** de ele a ligar, não descoberto por
ele ao fim de duas semanas de silêncio.

Duas consequências de desenho saem daqui:

1. **O limiar por omissão são 30°, não 60°.** A intuição diz que "por cima" é quase no zénite; a
   aritmética diz que a 60° a janela é de 55 segundos — menos tempo do que o utilizador leva a tirar
   o telefone do bolso e olhar para cima. A 30° são quase três minutos, e a taxa de captura
   duplica para o dobro.
2. **A app tem de dizer quantas passagens espera avisar**, no próprio ecrã onde a opção é ligada.

## User Scenarios & Testing

### US1 - Ser avisado quando passa um avião por cima (P1)

O utilizador liga as notificações nas definições, dá a permissão, e a partir daí recebe um aviso
quando o trabalho de fundo encontra uma aeronave alta no seu céu. O aviso diz qual é, de que
companhia, a que altura, e abre o detalhe ao toque.

**Why this priority**: é a feature. E é a última das quatro do MVP — com ela, a app faz tudo o que
prometeu desde o início.

**Independent Test**: ligar as notificações e forçar um ciclo com uma aeronave acima do limiar.

**Acceptance Scenarios**:
1. **Given** notificações desligadas (o estado de origem), **When** passa uma aeronave por cima,
   **Then** não há aviso nenhum.
2. **Given** notificações ligadas e permissão dada, **When** um ciclo encontra uma aeronave acima do
   limiar, **Then** chega um aviso com indicativo, companhia quando conhecida, e elevação.
3. **Given** um aviso na barra, **When** o utilizador lhe toca, **Then** abre o detalhe **daquela**
   aeronave, não a lista.
4. **Given** um ciclo com cinco aeronaves acima do limiar, **When** o ciclo termina, **Then** chega
   **um** aviso — o da aeronave mais alta — e não cinco.
5. **Given** uma aeronave já avisada, **When** o ciclo seguinte a encontra outra vez, **Then** não há
   segundo aviso para a mesma passagem.

### US2 - Ligar, com a expectativa certa (P2)

O utilizador liga a opção nas definições e o ecrã diz-lhe, ali, que isto avisa uma fração das
passagens e porquê.

**Why this priority**: sem isto a feature parece avariada. Um utilizador que ligue notificações e
receba dois avisos por semana conclui que não funciona — e tem razão em concluir, porque ninguém lhe
disse o que esperar.

**Acceptance Scenarios**:
1. **Given** o ecrã de definições, **When** o utilizador olha para a opção de notificações, **Then**
   vê quantas passagens espera ser avisado, dado o limiar e a cadência atuais.
2. **Given** Android 13 ou superior e notificações por permitir, **When** o utilizador liga a opção,
   **Then** é-lhe pedida a permissão **com explicação prévia**, e nunca no arranque da app.
3. **Given** o utilizador recusa a permissão, **When** volta ao ecrã, **Then** a opção aparece
   desligada e explica que precisa da permissão — nunca ligada a fingir que funciona.

### US3 - Escolher o que conta como "por cima" (P3)

O utilizador ajusta o ângulo a partir do qual é avisado, e vê o efeito na taxa de captura.

**Why this priority**: o valor de origem é defensável e a feature funciona sem o controlo existir.
Mas o limiar certo depende de onde a pessoa vive — quem mora debaixo de uma rota de aproximação quer
subi-lo, quem mora longe de tudo quer descê-lo.

**Independent Test**: mudar o limiar e confirmar que a taxa mostrada muda e que o aviso passa a
disparar noutro ângulo.

**Acceptance Scenarios**:
1. **Given** o controlo do limiar, **When** o utilizador o sobe, **Then** o ecrã mostra que passa a
   ser avisado de menos passagens.
2. **Given** um limiar abaixo do ângulo mínimo de deteção, **When** é gravado, **Then** é corrigido —
   não se pode ser avisado de uma aeronave que a deteção descarta.

### Edge Cases

- **Notificações ligadas sem widget nenhum**: o trabalho de fundo tem de existir na mesma. Já está
  garantido e testado pela 005 (AD-026).
- **Permissão de notificações revogada** depois de ligada: a app não pode continuar a achar que
  notifica. O estado tem de refletir a realidade.
- **A mesma aeronave em dois ciclos seguidos** ainda acima do limiar: é a mesma passagem, um aviso só.
- **A mesma aeronave horas depois**, noutra passagem: é uma passagem nova, e merece aviso.
- **Doze**: o ciclo pode não correr durante horas. Nenhum aviso atrasado deve chegar a dizer "agora".
- **O ciclo falha**: nenhum aviso, e nenhum aviso de erro — ninguém quer uma notificação a dizer que
  não houve rede.
- **Processo morto** entre ciclos: a memória do que já foi avisado tem de sobreviver, senão a primeira
  passagem depois de cada arranque é avisada de novo.

## Requirements

- **FR-001**: As notificações MUST estar desligadas de origem.
- **FR-002**: A app MUST pedir `POST_NOTIFICATIONS` apenas quando o utilizador liga a opção, com
  explicação prévia, e nunca no arranque.
- **FR-003**: Um ciclo MUST produzir no máximo **um** aviso, para a aeronave de maior elevação acima
  do limiar.
- **FR-004**: A mesma aeronave MUST NOT ser avisada mais do que uma vez dentro da janela de
  deduplicação.
- **FR-005**: A memória do que já foi avisado MUST sobreviver à morte do processo.
- **FR-006**: O aviso MUST identificar a aeronave (indicativo, companhia quando conhecida, elevação).
- **FR-007**: Tocar no aviso MUST abrir o detalhe daquela aeronave.
- **FR-008**: O aviso MUST NOT afirmar presença sem indicar quando foi observada.
- **FR-009**: Uma falha de ciclo MUST NOT produzir aviso nenhum.
- **FR-010**: O ecrã de definições MUST mostrar a fração de passagens que a configuração atual espera
  apanhar.
- **FR-011**: O utilizador MUST poder escolher o ângulo a partir do qual é avisado.
- **FR-012**: O limiar de aviso MUST ser sempre maior ou igual ao ângulo mínimo de deteção.
- **FR-013**: Desligar as notificações MUST cancelar o trabalho de fundo quando também não há widget.
- **FR-014**: A app MUST refletir a permissão real: revogada, a opção não pode aparecer ligada.

## Success Criteria

- **SC-001**: Com as notificações desligadas, zero avisos em 24 h, mesmo com aviões a passar.
- **SC-002**: Um ciclo com N aeronaves acima do limiar produz exatamente 1 aviso.
- **SC-003**: A mesma aeronave em ciclos consecutivos produz 1 aviso, não 2.
- **SC-004**: Nenhum aviso é emitido a partir de um ciclo falhado.
- **SC-005**: O utilizador consegue dizer, olhando para as definições, que fração das passagens espera
  ser avisado.
- **SC-006**: A memória de deduplicação sobrevive a matar a app.

## Assumptions

- **O limiar por omissão são 30°**, pelas contas da tabela acima: é onde a janela (166 s) é longa o
  suficiente para o utilizador conseguir olhar para cima a tempo, e onde a taxa de captura é a mais
  alta que a plataforma permite sem serviço em primeiro plano.
- **A janela de deduplicação são 30 minutos.** Cobre uma passagem inteira com folga e não impede um
  aviso legítimo numa passagem posterior do mesmo voo.
- **Room já está montado** — entidade, DAO, base de dados e bindings existem desde o esqueleto. Só se
  implementa `wasNotifiedRecently` e o registo dos avisos; o histórico de avistamentos completo
  continua fora de âmbito.
- **Só se gravam as aeronaves avisadas**, não todas as observadas. Gravar todas seria implementar a
  feature do histórico por acidente, e encheria a tabela com milhares de linhas por dia.


## Key Entities

- **Limiar de aviso**: o ângulo acima do qual uma aeronave merece interromper o utilizador. Escolha
  dele, sempre maior ou igual ao ângulo mínimo de deteção.
- **Aviso emitido**: que aeronave foi avisada e quando. Persiste, porque é o que impede o segundo
  aviso para a mesma passagem — e tem de sobreviver à morte do processo.
- **Taxa de captura esperada**: que fração das passagens a configuração atual consegue apanhar.
  Derivada da janela geométrica e da cadência; existe para o utilizador saber o que esperar.

## Assumptions (continuação)

- **Só a aeronave mais alta de cada ciclo é avisada.** Um ciclo pode encontrar cinco acima do limiar;
  cinco notificações de uma vez seriam motivo para desligar a feature no mesmo minuto.
- **A permissão de notificações é verificada no momento de notificar**, e não só quando é concedida.
  O utilizador pode revogá-la nas definições do Android sem a app saber.
- **A cadência do widget e a das notificações são a mesma.** São o mesmo trabalho de fundo (AD-003),
  e separá-las duplicaria o consumo do orçamento diário para servir duas features que precisam
  exatamente do mesmo dado.
- **Sem serviço em primeiro plano**, como a constituição exige. É essa proibição que fixa o teto da
  taxa de captura, e é por isso que ela é dita ao utilizador em vez de escondida.
