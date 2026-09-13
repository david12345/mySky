# mySky — guia para trabalho futuro

App Android nativa que mostra os aviões a passar sobre a localização do utilizador, com widget
de ecrã inicial e notificações opcionais quando uma aeronave passa "por cima".

## Visão geral

| | |
|---|---|
| Linguagem | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Widget | Jetpack Glance |
| Arquitetura | Clean Architecture + MVVM (`data` / `domain` / `presentation`) |
| DI | Hilt |
| Rede | Retrofit + Kotlinx Serialization |
| Background | WorkManager (periódico, mínimo 15 min) |
| Persistência | Room (histórico) + DataStore (preferências) |
| Localização | Fused Location Provider |
| Testes | JUnit + MockK + Turbine |
| minSdk / targetSdk | 26 / 36 |

Fonte de dados inicial: **OpenSky Network** (`GET /states/all`), anónima por omissão.

## Decisões de arquitetura

Registar aqui **todas** as decisões estruturais. O subagente `architect` escreve nesta secção;
decisões com peso suficiente sobem também à constituição (`.specify/memory/constitution.md`).

### AD-001 — A deteção de aviões vive no `domain`, sem rede
`DetectOverheadFlightsUseCase` recebe observador + lista de aeronaves + critérios + instante de
referência e devolve os voos "no céu". Não conhece rede, Android nem UI, e o relógio entra como
parâmetro (`nowEpochSeconds`) para ser determinístico.
**Porquê:** é a regra de negócio da app; tem de ser testável na JVM em milissegundos e não pode
mudar quando a fonte de dados ou a UI mudarem.

### AD-002 — Nenhuma camada acima de `data/source` conhece a OpenSky
A abstração é `data/source/FlightDataSource`. `OpenSkyFlightDataSource` implementa-a e
`di/DataSourceModule` é o **único** ponto onde a fonte concreta é escolhida.
**Porquê:** trocar/adicionar fonte (ADS-B Exchange, airplanes.live, dataset local) tem de ser um
binding, não um refactor.
**Consequência:** a classe de repositório chama-se `FlightRepositoryImpl`, **não**
`OpenSkyRepositoryImpl` — o nome original acoplaria o repositório à fonte, contrariando esta
decisão.

### AD-003 — Widget e notificações partilham um único worker periódico
`SkyRefreshWorker` é a única coisa que vai à rede em background; `SkyWorkScheduler` é o único
sítio que cria `WorkRequest`s.
**Porquê:** garante num só ponto o mínimo de 15 minutos, as `Constraints` de rede e o backoff, e
evita dois agendamentos a duplicar consumo de bateria.
**Consequência:** o widget nunca faz rede em `provideGlance`; lê o último resultado do estado do
Glance. O "tap-to-refresh" enfileira trabalho, não bloqueia a composição.
**Alcance (nota da AD-016):** esta decisão é sobre o *sky refresh* — a cadência de posição que serve
widget e notificações. Trabalho de fundo de outra natureza, sem relação com o orçamento de posições
nem com o mínimo de 15 minutos, tem o seu próprio par worker/scheduler. A garantia mantém-se: cada
tipo de trabalho tem um e um só ponto de agendamento (princípio IV, versão 1.1.0).

### AD-004 — Sem foreground service permanente no MVP
As notificações são "best effort" dentro dos limites do Doze/App Standby.
**Porquê:** um serviço em primeiro plano permanente com localização ativa é o pior custo de
bateria possível e exige justificação adicional na Play Store. Se surgir um requisito de deteção
quase-instantânea, é decisão nova a passar pelo `architect`.

### AD-005 — MVP só com lista, sem mapa
O ecrã principal é uma lista ordenada por elevação. O mapa é feature posterior.
**Porquê:** evita a dependência do Google Maps SDK e a gestão de uma API key logo no arranque; a
informação útil (o que está por cima de mim, agora) cabe numa lista.

### AD-006 — Toolchain: AGP 8.13.2 + Hilt 2.58
O AGP 9 traz Kotlin embutido, mas o KSP — necessário para o Hilt **e** para o Room — ainda não é
compatível com ele; usá-lo obrigaria a `android.builtInKotlin=false` **e** `android.newDsl=false`,
duas flags legacy que desaparecem no AGP 10. O Hilt >= 2.59 exige AGP 9, por isso o par estável
hoje é AGP 8.13.2 com Hilt 2.58.
**Rever quando:** o KSP anunciar suporte ao Kotlin embutido do AGP. Aí sobe-se AGP e Hilt juntos.

### AD-007 — A tabela de operadores é uma porta própria, não uma fonte de voos
O nome da companhia é resolvido localmente a partir do prefixo de 3 letras do indicativo, via
`domain/repository/AirlineDirectory` implementado por `data/local/AssetAirlineDirectory` sobre um
JSON em `assets/`. O enriquecimento acontece em `ObserveSkyUseCase`, não em
`DetectOverheadFlightsUseCase` (que continua puro e síncrono).
**Porquê:** dados de referência estáticos não têm ciclo de vida nem migrações — Room seria peso
morto. E o princípio II fala de *fontes de dados de voo*: a tabela é dado de referência, não um
fornecedor de posições, por isso tem porta própria em vez de ser espremida no `FlightDataSource`.
**Consequência:** `OverheadFlight` ganha `airline: Airline?`, preenchido num segundo passo. Um
prefixo desconhecido devolve `null` — nunca esconde a aeronave.
**Origem dos dados:** OpenFlights `airlines.dat`, sob Open Database License. A ODbL exige
atribuição, por isso `assets/airlines-LICENSE.txt` viaja no APK ao lado da tabela e o script de
conversão fica em `tools/airlines/` para a atualização periódica ser reproduzível. O ICAO Doc 8585
foi descartado por ser publicação paga e não redistribuível.

### AD-008 — A atualização do ecrã vive no ViewModel, não no WorkManager
Um único laço sequencial no `MainViewModel`, exposto com
`stateIn(viewModelScope, WhileSubscribed(5_000), ...)` e consumido com
`collectAsStateWithLifecycle()`. Cada iteração espera pelo tick de 30 s **ou** por um pedido
manual, o que vier primeiro.
**Porquê:** parar quando o ecrã deixa de estar visível sai de graça do ciclo de vida, e "sem
pedidos concorrentes" sai da forma do laço — uma só corrotina, sequencial, sem mutex nem flag de
"em curso". Um `while` em `viewModelScope` continuaria a consumir rede em segundo plano.
**Consequência:** o WorkManager fica reservado para widget e notificações, como em AD-003. Cada
ciclo pede uma posição pontual em vez de subscrever localização contínua.
**Refinado pela AD-011:** o laço deixa de viver no `MainViewModel` e passa para uma `SkySession`
partilhada, para servir também o ecrã de detalhe (`002-flight-detail`). A forma do laço e as
propriedades que ela compra mantêm-se; muda o dono.
**Correção durante a implementação:** o pedido manual chega por um `Channel(CONFLATED)` e não pelo
`MutableSharedFlow` previsto. Um `SharedFlow` sem replay descarta o que é emitido enquanto não há
coletor, e entre duas iterações do laço existe esse instante — um toque em "atualizar" que caísse
lá perdia-se em silêncio. O canal conflado guarda o último toque sem nunca enfileirar dois, e a
iteração seguinte é sequencial, por isso FR-020 mantém-se.

### AD-009 — `ObserveSkyUseCase` recebe critérios por parâmetro; o tempo entra por abstração
Deixa de depender do `SettingsRepository` e passa a aceitar `criteria: OverheadCriteria =
OverheadCriteria()`. O relógio entra por `domain/time/TimeProvider`.
**Porquê:** o `SettingsRepositoryImpl` é um stub que rebentaria, e implementá-lo aqui invadiria a
feature de definições. Quando essa feature existir, é o ViewModel que alimenta o parâmetro — a
assinatura não muda. O `TimeProvider` mantém o princípio I sem tocar em
`DetectOverheadFlightsUseCase`, que continua a receber `nowEpochSeconds` como parâmetro puro.
**Consequência:** a deduplicação por `icao24` entre caixas fica no `FlightRepositoryImpl` — a
divisão em caixas é artefacto de como as fontes são interrogadas.

### AD-010 — Erros são um tipo de domínio; o rate limiting é visível
`domain/model/SkyError` (selado: `NoConnection`, `FlightServiceUnavailable`, `RateLimited`,
`LocationUnavailable`, `Unexpected`) estende `Exception` para caber no `Result<T>` já usado nos
contratos. A tradução acontece no `FlightRepositoryImpl`. Um 429 sobe até ao ViewModel, que alonga
a espera seguinte para `max(intervalo, retryAfter)` e informa o utilizador.
**Porquê:** a UI tem de distinguir três causas (FR-024) sem inspecionar tipos de rede. Reintentar
um 429 em segredo gastaria o orçamento diário exatamente quando ele já se esgotou, e esconderia do
utilizador a razão de a lista não atualizar.
**Trade-off aceite:** um erro de domínio é tecnicamente lançável. Mitigado anulando
`fillInStackTrace` e nunca o lançando — só embrulhado em `Result.failure`. Alternativa anotada: um
tipo `SkyResult<T>` próprio, se o `Result` vier a estorvar.

### AD-011 — O laço de atualização vive numa `SkySession` partilhada, na `presentation`
O laço periódico sai do `MainViewModel` para `presentation/sky/SkySession`, com escopo
`@ActivityRetainedScoped`. Expõe `observation: StateFlow<SkyObservation>` por
`stateIn(scope próprio, WhileSubscribed(5_000), ...)` e um `requestRefresh()` que substitui o
`Channel(CONFLATED)` hoje privado do ViewModel. `MainViewModel` e `FlightDetailViewModel` passam a
consumidores finos: cada um embrulha a mesma fonte com
`stateIn(viewModelScope, WhileSubscribed(5_000), inicial)` e deriva o seu próprio estado de ecrã.
**Porquê:** a 002 exige que os dois ecrãs mostrem os mesmos valores no mesmo instante e que estarem
ambos vivos não duplique os pedidos. Uma contagem de subscritores partilhada resolve as duas coisas
ao mesmo tempo, sem mutex e sem cache com validade. Na transição lista → detalhe a contagem passa
por 1 → 2 → 1 sem chegar a zero, por isso o laço não reinicia nem repete um pedido. Fica em
`presentation/` e não em `domain/` porque a cadência é política de apresentação — a regra de
negócio continua inteira em `DetectOverheadFlightsUseCase`.
**Rejeitadas:** ViewModel partilhado por `hiltViewModel(parentEntry)`, que acopla a partilha à
topologia da navegação e mistura os campos dos dois ecrãs; laços independentes com mutex ou cache
com validade, que reintroduzem a coordenação explícita que a AD-008 evitou e duplicam o backoff de
429 em dois sítios que podem divergir.
**Consequência:** `PermissionState` e o rationale ficam só no `MainViewModel` — é o único ecrã que
pede permissão, e a `SkySession` nunca sabe o que é uma permissão; limita-se a consultar
`hasLocationPermission()` a cada ciclo. Para que conceder a permissão não implique esperar pelo
tique seguinte, o `MainViewModel` chama `requestRefresh()` na transição para `Granted`. Um refresh
manual em qualquer dos ecrãs renova ambos, o que é o comportamento correto e não um efeito
colateral. O escopo interno da sessão tem de ser cancelado em
`ActivityRetainedLifecycle.addOnClearedListener`: não há limpeza automática. E a `SkySession` nunca
é injetada em `worker/` nem em `widget/`, que continuam pelo `SkyRefreshWorker` (AD-003).

### AD-012 — "Saiu do céu" é uma redução pura no `domain`
A distinção entre aeronave atual, aeronave que saiu do céu e aeronave nunca observada vive em
`domain/usecase/TrackFlightPresenceUseCase`, com a forma da `DetectOverheadFlightsUseCase`: recebe
o estado de presença anterior, a observação mais recente (`List<OverheadFlight>?`, em que `null` é
"o ciclo falhou" e é distinto de lista vazia), o `icao24` e `nowEpochSeconds`. A memória entre
chamadas vive no `FlightDetailViewModel`.
**Porquê:** é a categoria de defeito do princípio VI — mostrar a última posição como atual não
produz erro nenhum, produz um resultado errado com ar de certo. Merece o mesmo tratamento que a
geometria: função pura, sem relógio nem memória implícitos, testável na JVM. Fica fora da
`SkySession` porque a memória é por ecrã e por aeronave, e a sessão tem de continuar sem estado por
aeronave para servir os dois ecrãs.
**Consequência:** um ciclo falhado nunca faz um avião sair do céu — é a linha da tabela de
transições que separa informar de mentir. Depois de o processo morrer e ser restaurado com o
detalhe no topo da pilha não há estado anterior, e a resposta correta é `NeverObserved`: nunca
reaproveitar um voo anterior como se fosse atual.

### AD-013 — A tabela de rotas é um ficheiro binário ordenado, lido por pesquisa binária
584 832 rotas não cabem num `Map` como a tabela de operadores (169 KB, AD-007) cabe: com este
volume, um `HashMap` custa da ordem de 100 MB só em overhead de objeto. A tabela vive em
`assets/routes.bin` e, depois da primeira atualização aceite, em `filesDir` — registos de largura
fixa (13 bytes: 7 de indicativo, 3+3 de siglas IATA), ordenados por indicativo, lidos com
`RandomAccessFile` e pesquisa binária.
**Porquê:** memória praticamente nula — o ficheiro fica na cache de páginas do sistema, não no heap
— e consulta desprezível: **20 leituras e 37 µs por aeronave, 1,8 ms para uma lista de 50**,
medidos. Room foi ponderado e rejeitado: traz entidades, DAOs e um índice B-tree para uma operação
que é sempre a mesma consulta por chave exata, sem junções e sem necessidade de reatividade. O
`MappedByteBuffer` foi rejeitado a favor do `RandomAccessFile` porque o Java não tem forma
determinística de desmapear, e esta tabela **é substituída em runtime**: um mapeamento antigo podia
ficar pendurado até o GC decidir, a cada atualização.
**Consequência:** `assets/routes.bin` TEM de ser marcado `noCompress` no `build.gradle.kts`. Sem
isso o AAPT comprime a entrada, a leitura por deslocamento deixa de funcionar e o ficheiro passa a
ser descomprimido inteiro para memória — a decisão anula-se em silêncio. A construção do ficheiro
fica em `tools/routes/build_routes_bin.py`, irmão do script das companhias; o runtime nunca lê CSV.

### AD-014 — A app descarrega um binário já convertido, nunca as fontes em bruto
O telemóvel nunca fala com o espelho dos dados. Só o script offline o faz, e o `routes.bin` que
produz é publicado como **ficheiro de uma release do GitHub**, numa **tag fixa e dedicada aos
dados** (`routes-latest`), que é de onde a app o descarrega. Substituição: descarrega para `cacheDir`, valida (assinatura,
versão, contagem, data de geração no cabeçalho, tamanho múltiplo do registo) e só então
`File.renameTo()` para `filesDir`.
**Porquê:** descarregar os CSV em bruto (21 MB) obrigaria a reimplementar em Kotlin, no telefone, a
junção ICAO→IATA, a filtragem e a ordenação que o script já faz — duas implementações da mesma
regra a divergir em silêncio, que é o que o princípio VI manda evitar. O `rename` é atómico no
Android, e um leitor com o ficheiro antigo aberto continua a lê-lo em segurança: FR-020 sem locks.
O cabeçalho com a data de geração resolve FR-018 sem um segundo ficheiro de metadados cuja escrita
teria de ser coordenada com a do binário.
**A tag é fixa, não `latest`** — correção feita a 2026-09-08, depois de a primeira release ser
publicada. Com `latest`, a primeira release de aplicação (com o APK e sem a tabela) passaria a ser a
mais recente, o `routes.bin` deixaria de existir nesse URL e a atualização partia-se, com o
utilizador a ver "não foi possível chegar ao servidor" sem pista nenhuma da causa. Releases de
aplicação e de dados não têm relação uma com a outra e não podem partilhar um ponteiro.
**Nem o ponteiro implícito:** a release de dados **não pode ficar marcada como `Latest`** no GitHub, e
nenhum link para descarregar o APK pode apontar para `releases/latest`. Marcada, ela é o que
`releases/latest` resolve — e o botão "Descarregar o APK" da página do produto entregava um binário de
7,6 MB de tabela de rotas a quem quisesse instalar a app. Os links apontam para a tag da versão e para
o ficheiro, sempre. Verificado a 2026-09-10: era exatamente isso que a página fazia antes de a primeira
release existir.
**Consequência:** a app usa o ficheiro de `filesDir` se existir e validar, senão lê o asset
diretamente do APK — **nunca copia o asset só para ter tabela**, para não pôr 7,6 MB de I/O no
caminho do primeiro arranque. A atualização real só existe quando alguém volta a correr o script e
publica; se isso parar, o botão continua a funcionar e devolve a mesma tabela, o que FR-018 expõe
sem enganar ninguém. Se o espelho desaparecer, perde-se a capacidade de gerar versões novas, nunca
o funcionamento em runtime.

### AD-015 — `RouteDirectory` é porta própria; o enriquecimento resolve-se em concorrência
`domain/repository/RouteDirectory` com `findByCallsign`, implementada por `data/local/FileRouteDirectory`.
`Route(originIata, destinationIata)` nunca existe com um lado só — a garantia de FR-006 vive no
construtor, como no `Airline`. Um `Route.callsignKeyOf` no domínio normaliza o indicativo da mesma
forma que o script gera as chaves.
**Porquê:** mantém a fronteira da AD-007 — dado de referência não é fonte de voos. Mas há uma
diferença que importa: a consulta de operador é uma leitura de `Map` depois do primeiro
carregamento, a de rota é **sempre** I/O. Por isso corre em `@IoDispatcher`, e operador e rota de
cada aeronave — e as aeronaves entre si — resolvem-se **concorrentemente**. Encadear duas pesquisas
de disco por aeronave, sequencialmente, com dezenas de aeronaves por ciclo, passa despercebido em
teste e aparece como atraso no dispositivo.
**Consequência:** `OverheadFlight` ganha `route: Route? = null`, preenchido no mesmo passo que
`airline`. Indicativo desconhecido devolve `null` e nunca esconde a aeronave.

### AD-016 — A atualização da tabela tem worker e scheduler próprios
`worker/RouteTableUpdateWorker` e `worker/RouteTableUpdateWorkScheduler`, paralelos ao par do sky
refresh e não uma extensão dele. `OneTimeWorkRequest` com `NetworkType.CONNECTED` e
`ExistingWorkPolicy.KEEP`. O `SettingsViewModel` observa o progresso por uma porta de domínio
(`RouteTableRepository`, com `updateState` e `requestUpdate()`) e nunca importa `androidx.work.*`.
**Porquê:** a AD-003 resolve um problema concreto — dois agendamentos de sky refresh a duplicar
sondagens de posição. Este pedido é raro, único e sempre iniciado pelo utilizador, sem relação com
posições nem com o mínimo de 15 minutos; metê-lo no `SkyWorkScheduler` misturaria numa classe
coerente um método sem nada a ver com o que ela garante. O WorkManager continua a ser a ferramenta
certa em vez de um scope do ViewModel: uma descarga de alguns MB deve sobreviver a o utilizador
sair do ecrã.
**Consequência:** obrigou a **emendar a constituição para 1.1.0** — o princípio IV dizia "todos os
`WorkRequest` num único ponto", o que proibia isto à letra. Passou a "cada tipo de trabalho tem um
único ponto de criação dos seus", que é a garantia que a regra sempre quis dar. A `SkySession`
nunca sabe que esta atualização existe, e FR-023 fica garantido por separação de execução e não por
coordenação.

### AD-017 — Definições: um estado só, duas portas que não se misturam
Esta feature cria a primeira entrada real no ecrã de definições. Fica fixado: um único
`SettingsUiState`, que a feature de definições estenderá em vez de criar um segundo `StateFlow`; o
`SettingsViewModel` não conhece `WorkManager`; e `SettingsRepository` (preferências do utilizador,
DataStore) e `RouteTableRepository` (dado de referência, com data de geração lida do cabeçalho do
ficheiro) são portas deliberadamente distintas.
**Porquê:** custa pouco fixar agora e evita a reversão a meio da feature de definições, quando se
descobrisse a tabela de rotas entalada dentro da `SettingsRepository` só porque já lá estava.
**Consequência:** a feature de definições estende o estado existente e passa a alimentar o
`ObserveSkyUseCase` com `OverheadCriteria` reais — fechando a promessa da AD-009 — sem tocar no que
a 003 deixa.

### AD-018 — As preferências entram na `SkySession` por leitura direta, não por parâmetro
A `SkySession` passa a depender do `SettingsRepository` como já depende do `LocationRepository`, e
lê `settings.first().toCriteria()` no início de cada `refreshOnce()` — sem manter subscrição viva.
**Porquê:** cada ciclo fica com um snapshot imutável de critérios do princípio ao fim. Como
`OverheadCriteria` é um `data class` passado por valor ao `ObserveSkyUseCase` (AD-009), é
**estruturalmente impossível** uma lista com critérios misturados dentro de um ciclo: o FR-017 fica
garantido pela forma dos dados e não por cancelamento — não há nada para alguém se lembrar de fazer.
Fecha a promessa da AD-009 por outro caminho: essa dizia que "é o ViewModel que alimenta o
parâmetro", mas nessa altura o laço ainda vivia no `MainViewModel`; depois da AD-011 o laço é da
sessão, e é a sessão que pergunta.
**Rejeitadas:** `collectLatest` sobre o fluxo de preferências, que cancelaria uma chamada de rede em
curso sem desfazer o crédito já gasto — conta-se no pedido, não na resposta — e acrescentaria uma
segunda forma de interromper o laço ao lado do canal conflado que a AD-008 centralizou; o
`MainViewModel` a entregar os critérios, que tornaria a sessão dependente de qual ecrã está vivo
primeiro para um dado que não é de ecrã nenhum.
**Consequência:** a sessão ganha uma leitura local por ciclo. É memória depois do primeiro arranque,
não rede, mas o primeiro ciclo paga uma leitura de disco no caminho do arranque.

### AD-019 — Quem altera critérios acorda o laço; unidades não
Depois de uma alteração de raio, ângulo ou altitude, o `SettingsViewModel` chama
`skySession.requestRefresh()` — o mesmo método que o `MainViewModel` usa na transição para
`Granted`. Unidades **não** o chamam.
**Porquê:** o SC-001 só exige refletir "em menos de um ciclo", por isso acordar mais cedo é cortesia
sobre uma garantia que já existe, e retira-se sem violar a especificação se der problemas. O canal
já é conflado: arrastar um controlo não gasta mais do que um pedido. Unidades não afetam a deteção —
pedir dados por causa delas gastaria um crédito para obter as mesmas aeronaves.
**Consequência aceite, pré-existente:** um ajuste durante um recuo por 429 volta a tentar antes do
`retryAfter`. O botão manual já tinha esse comportamento desde a 001; esta feature alarga-lhe a
porta sem o criar. Candidato a refinamento futuro — distinguir "pedido explícito" de "aviso de
mudança" — e não bloqueia nada.
**Armadilha:** `SkySettings.refreshIntervalMinutes` pertence ao `SkyRefreshWorker` (AD-003, mínimo
15 min), **não** ao laço de 30 s desta sessão. Está na mesma classe e é fácil de confundir; o FR-015
proíbe ligar-lhe um controlo nesta feature.

### AD-020 — A unidade de apresentação viaja no estado do ecrã, não por `CompositionLocal`
As funções de `FlightFormatting` ganham um parâmetro de unidade e continuam puras, estáticas e
testáveis na JVM. Cada `UiState` combina as preferências para os dois enums, como o `MainViewModel`
já combina permissão e observação.
**Porquê:** um `CompositionLocal` seria um segundo canal de estado implícito ao lado do `UiState`,
contra a convenção já escrita neste documento — a UI nunca compõe estado a partir de vários flows
soltos. Passar a unidade pelo estado que os composables já leem não acrescenta mecanismo nenhum.

### AD-021 — Os limites são geométricos, não orçamentais; a incoerência avisa-se
Os limites de cada valor derivam da utilidade geométrica, não do orçamento — apurou-se que qualquer
raio utilizável custa 1 crédito e o orçamento não depende das preferências. A relação raio×ângulo
mínimo **não estreita** o intervalo de nenhum controlo: aparece como texto explicativo, calculado ao
vivo pela inversa da função de elevação que já existe no `GeoCalculator`.
**Porquê:** bloquear obrigaria o limite de um controlo a mover-se quando se toca no outro — um
cursor cujo topo se desloca debaixo do dedo — e tornaria "valor de origem" um sítio móvel. O FR-009
é sobre um controlo não aceitar valores fora do **seu próprio** limite, não sobre dois controlos se
restringirem. E derivar um do outro esconderia, em nome de ajudar, o efeito que o FR-011 existe para
explicar.
**Consequência:** a premissa do acoplamento assume um teto de altitude típico, e há tráfego
executivo que voa acima dele. Serve para informar, nunca para impedir uma escolha legítima.

### AD-022 — A degradação de valores fora do intervalo vive no domínio, aplicada em toda leitura
`SkySettings` ganha `coerced()`, função pura ao lado dos `DEFAULT_*`. O `SettingsRepositoryImpl`
aplica-a a **toda** leitura do DataStore, não numa migração pontual.
**Porquê:** um único ponto de verdade para os limites, consumido por três sítios sem os redefinir —
o intervalo do controlo (FR-009), a degradação de um valor guardado inválido (FR-008) e a frase
explicativa (FR-011). E aplicar em cada leitura responde de graça a "o que acontece quando os
limites mudarem": nada é versionado nem migrado, o valor antigo é corrigido sempre que é lido.

### AD-023 — O último resultado vive num porto de domínio, não no estado do Glance
**Corrige a AD-003**, que dizia "lê o último resultado do estado do Glance". Cria-se
`domain/repository/SkyWidgetRepository` (`snapshot: Flow<SkyWidgetSnapshot>`, `save(snapshot)`),
implementado sobre um DataStore próprio da app. O `SkyWidgetSnapshot` transporta o **facto bruto** —
voos, ou céu vazio, ou permissão em falta, mais o instante — nunca a interpretação em texto.
**Porquê:** verificado por `javap` sobre o AAR: `updateAppWidgetState` e `getAppWidgetState` exigem um
`GlanceId`, ou seja o estado do Glance é **por instância de widget**. Com vários widgets (FR-021) o
worker teria de escrever N cópias idênticas ou eleger uma arbitrariamente para ler depois. E as
notificações vão precisar do mesmo resultado sem serem um widget e sem terem `GlanceId` nenhum. Um
porto de domínio, à imagem do `RouteTableRepository` (AD-016), serve um valor a todos sem que nenhum
conheça o outro.
**Rejeitadas:** estado do Glance (a cardinalidade não bate); Room (uma linha, sem junções nem
histórico — o mesmo argumento das AD-007 e AD-013 contra peso morto).
**Consequência:** o `provideGlance` lê este porto, o que é permitido — a FR-009 proíbe **rede** na
composição, não leitura local. Uma falha transitória **não** chama `save()`, e o valor anterior fica:
a FR-014 sai de graça da forma do código. Fica ainda um segundo canal, efémero e por widget — esse sim
em `GlanceStateDefinition`, que é o sítio certo para estado de UI local: o "a atualizar" e o "falhou
por X" do toque (FR-017 a FR-019), que não são factos sobre o céu, não precisam de sobreviver à app
nem de ser iguais em todos os widgets.

### AD-024 — Os cinco estados são redução pura no domínio, avaliada no instante da leitura
`domain/model/SkyWidgetState` selado (`NoDataYet`, `Fresh`, `Stale`, `EmptySky`, `PermissionMissing`) e
uma função pura `evaluate(snapshot, nowEpochSeconds, freshnessWindowSeconds)`. Chamada no
`provideGlance`, com o relógio lido **nesse** instante — nunca pré-calculada pelo worker na escrita.
**Porquê:** é a categoria de defeito da AD-012, e o SC-003 exige verificação automática. Há também uma
razão temporal concreta: a cadência por omissão (30 min) é **seis vezes** a janela de frescura (5 min).
Se o worker gravasse "Fresh" à cabeça, o widget diria "está no teu céu" durante cerca de 25 dos 30
minutos **depois** de isso deixar de ser verdade. Só reavaliar a cada composição faz o texto seguir o
relógio.
**Rejeitadas:** classificar no worker (fica errado, como acima); persistir a interpretação em vez do
facto bruto (perde-se a capacidade de reavaliar com outro "agora").
**Consequência:** o `TimeProvider` entra em quem chama a função no `widget/`, no espírito da AD-009. A
FR-004 e a FR-019 não se misturam: os cinco estados são sobre o facto persistido (AD-023); os textos de
falha do toque vivem no canal efémero e não passam por aqui.

### AD-025 — O `worker/` não importa `androidx.glance`; a ponte é uma interface dele próprio
`worker/WidgetRefresher`, interface com `refreshAll()` e sem tipos de Glance na assinatura,
implementada por `widget/GlanceWidgetRefresher` com `updateAll`. O `widget/` depende do `worker/`,
nunca o contrário.
**Porquê:** importar Glance no worker acoplaria um componente de fundo — testável na JVM com MockK — a
um toolkit de UI que exigiria Robolectric ou instrumentação para testar. É a mesma técnica de todas as
portas do projeto: a fronteira fica onde a dependência pesada começa.
**Rejeitadas:** o worker a importar Glance (mistura as duas naturezas); um `BroadcastReceiver` genérico
(mecanismo a mais para um `refreshAll()` que já é idempotente).
**Consequência:** `RefreshSkyWidgetAction` chama `SkyWorkScheduler.requestImmediateRefresh()` com
`ExistingWorkPolicy.KEEP`, como o `RouteTableUpdateWorkScheduler` — dois toques com um pedido em curso
não duplicam (FR-017), e o único ponto de `WorkRequest` continua a ser o scheduler (FR-023).

### AD-026 — O agendamento é reconciliado a partir da verdade, não contado por eventos
`worker/SkyBackgroundWorkCoordinator` com um método `reconcile()`: pergunta "há pelo menos um widget?"
e decide agendar ou cancelar. `onEnabled`/`onDisabled`/`onUpdate` do receiver chamam-no para reação
imediata; `Application.onCreate()` chama-o uma vez por arranque, como rede de segurança.
**Porquê:** o `onEnabled` só dispara na transição 0→1 **de sempre** para aquele receiver. Quem já tinha
o widget da v1.0.0 nunca mais o recebe, e ficaria sem agendamento para sempre (FR-022). Reconciliar a
partir do estado real — quantos widgets existem agora — em vez de contar deltas dispensa qualquer
migração: no primeiro arranque depois da atualização, o `reconcile()` encontra o widget lá e agenda,
sem flag nenhuma. `enqueueUniquePeriodicWork` é idempotente, por isso chamá-lo de mais não custa.
**Rejeitadas:** confiar só em `onEnabled`/`onDisabled` (não cobre a FR-022); um `WorkRequest` de
arranque para reparar o agendamento (um terceiro tipo de trabalho para o que uma chamada direta e
idempotente resolve).
**Consequência, e é o que prepara a feature seguinte:** a condição escreve-se desde já como
`hasAnyWidget() || notificationsEnabled`, não como "há widget". As notificações **não reescrevem este
ficheiro** — acrescentam a leitura que já existe em `SkySettings` e chamam `reconcile()` a partir do
seu próprio interruptor. É a AD-016 lida ao contrário: um único ponto de **decisão** de agendar, tal
como já há um único ponto de **criação** de `WorkRequest`.

### AD-027 — A cadência reagenda pelo caminho das definições; o custo em orçamento é domínio
O `SettingsViewModel` ganha uma terceira categoria de escrita ao lado de "acorda a sessão" e "não
acorda nada": `updateSchedule`, que grava `refreshIntervalMinutes` e chama o `reconcile()` da AD-026,
que já sabe agendar com `ExistingPeriodicWorkPolicy.UPDATE` (FR-025). A correção de um valor abaixo do
mínimo (FR-026) **não se duplica**: continua só em `SkySettings.coerced()` (AD-022), que corre em toda
leitura — o scheduler nunca vê um valor por corrigir. O custo em consultas e em tempo de ecrã (FR-027)
vive como função pura ao lado do `SkyRange`, com constante única `DAILY_QUERY_BUDGET = 400`, consumida
por um `get()` derivado no `SettingsUiState`.
**Porquê:** passar pelo `reconcile()` em vez de chamar o scheduler evita um segundo caminho para o
mesmo agendamento — mudar a cadência também tem de respeitar "isto devia sequer existir?". E o risco
aqui é o simétrico do defeito que a revisão da 004 apanhou: lá foi um número escrito de duas maneiras
que divergiu; aqui seria o 400 escrito outra vez num sítio novo. Um ponto de verdade, com um teste que
verifica o SC-007 de ponta a ponta (30 min → 48 consultas → 12% → ~2h56m).
**Rejeitadas:** o ViewModel a chamar o scheduler diretamente (dois sítios a decidir se deve existir
trabalho); calcular o custo na `presentation` (esconderia a constante num ficheiro de UI, exatamente
onde a 004 mostrou que ela se perde).
**Consequência:** cumpre a promessa que a AD-019 deixou em aberto — era essa AD que proibia a 004 de
ligar um controlo a `refreshIntervalMinutes`. A armadilha que ela registou (não confundir com os 30 s
do laço da sessão) mantém-se válida.

### AD-028 — O "um ciclo" sai para um caso de uso partilhado; a `SkySession` nunca é injetada
`domain/usecase/RunSkyCycleUseCase` encapsula o que o `SkySession.refreshOnce()` já faz **sem** o laço:
verificar permissão, obter posição, ler os critérios, chamar o `ObserveSkyUseCase`, devolver um
resultado selado (`Success(flights, observedAt)` | `NoPermission` | `Failure(SkyError)`). A
`SkySession` passa a delegar nele e mantém por cima a sua política (o `StateFlow`, o recuo de 429, o
laço de 30 s). O `SkyRefreshWorker` chama o mesmo caso de uso e aplica a **sua** política.

| Situação | `Result` do WorkManager | Porquê |
|---|---|---|
| Sem permissão | `success()` | repetir não resolve nada; grava o snapshot de permissão em falta |
| `NoConnection` / `LocationUnavailable` | `retry()` | transitório; não apaga o snapshot anterior |
| `RateLimited` | `success()` | reintentar gasta orçamento já esgotado (AD-010); o snapshot fica |
| `Unexpected` | `failure()` | não é acionável por retry; o trabalho periódico sobrevive na mesma |
| Sucesso | `success()` | grava, chama o `WidgetRefresher` e o `OverheadNotifier` |

**Porquê:** a alternativa era duplicar no worker a sequência que a sessão já implementa, em dois sítios
obrigados a concordar para sempre. É o género de duplicação que a revisão da 004 apanhou tarde. Extrair
o núcleo comum elimina a categoria de defeito **sem** violar a AD-011: não é a `SkySession` a ser
injetada no worker — é um caso de uso de domínio, sem estado, sem laço e sem `StateFlow`, que ambos
chamam.
**Rejeitadas:** injetar a `SkySession` (proibido pela AD-011); duplicar a sequência (divergência já
demonstrada nesta app); alargar o `max(intervalo, retryAfter)` ao worker periódico (o
`PeriodicWorkRequest` não tem esse controlo por ciclo sem cancelar e reagendar).
**Consequência:** o `SkySession.refreshOnce()` é alterado nesta feature para delegar — refactor
previsto, não acidente de alcance. Os testes existentes continuam a valer porque o comportamento
observável não muda.

## Estrutura de pastas

```
app/src/main/java/com/mysky/app/
├── data/
│   ├── source/            FlightDataSource (interface) + implementações por fonte
│   │   └── opensky/       API Retrofit, DTOs, parsing do vetor de estado
│   ├── repository/        Implementações dos repositórios do domain
│   ├── local/             Room (MySkyDatabase, DAOs, entities)
│   ├── settings/          DataStore de preferências
│   └── mapper/            DTO -> modelo de domínio
├── domain/
│   ├── model/             Aircraft, OverheadFlight, GeoPosition, BoundingBox, SkySettings...
│   ├── geo/               GeoCalculator (Haversine, bearing, elevação, bounding boxes)
│   ├── repository/        Interfaces (Flight, Location, Settings, Sighting)
│   └── usecase/           DetectOverheadFlightsUseCase, ObserveSkyUseCase
├── presentation/
│   ├── main/              Lista de aviões no céu
│   ├── detail/            Detalhe de uma aeronave
│   ├── settings/          Definições
│   ├── navigation/        NavHost e rotas
│   └── theme/             Material 3
├── di/                    Módulos Hilt (único sítio com escolhas de implementação)
├── widget/                Glance: SkyWidget, receiver, ação de refresh
├── worker/                SkyRefreshWorker + SkyWorkScheduler
└── notification/          OverheadNotifier
```

Regra de dependências: `presentation` → `domain` ← `data`. **O `domain` não importa nada de
`android.*`, de Retrofit, de Room nem de Compose.** Se um import desses aparecer em `domain`, a
decisão de desenho está errada.

## Comandos úteis

```bash
./gradlew :app:testDebugUnitTest     # testes unitários (JVM) — os do domain têm de passar sempre
./gradlew :app:assembleDebug         # APK de debug
./gradlew :app:lintDebug             # Android Lint
./gradlew :app:installDebug          # instalar num dispositivo/emulador ligado
./gradlew build                      # tudo: compilar, lint e testes
./gradlew --stop                     # matar daemons quando o Gradle se porta mal
```

Relatórios: testes em `app/build/reports/tests/`, lint em `app/build/reports/lint-results-*.html`.

## Convenções de código

- **Idioma:** código, nomes e identificadores em inglês; comentários, KDoc e strings de UI em
  português. Nomes de testes em português, em crases, a descrever o comportamento.
- **Unidades no domínio sempre em SI** (metros, m/s, graus) e explícitas no nome da propriedade
  (`horizontalDistanceMeters`, não `distance`). A conversão para km/milhas/pés é da `presentation`.
- **Versões só no `gradle/libs.versions.toml`.** Nunca escrever uma versão num `build.gradle.kts`.
- **Um `data class` de estado por ecrã** (`MainUiState`), exposto num `StateFlow`; a UI nunca
  compõe estado a partir de vários flows soltos.
- **Nada de `WorkRequest` fora de um scheduler dedicado** (`SkyWorkScheduler` para o sky refresh,
  `RouteTableUpdateWorkScheduler` para a tabela de rotas) e nada de rede em `provideGlance`.
- **Erros de rede** são `Result.failure` na fronteira do repositório; exceções não sobem ao domain.
- **TODOs marcados por feature**: `TODO(feature/<nome>)` para ligar o esqueleto às specs em
  `.specify/specs/`.
- **KDoc explica o "porquê"**, não o "o quê". Não repetir o nome do método em prosa.

## Permissões — regras não negociáveis

- `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION`: pedidas em runtime, sempre com o rationale
  (`R.string.permission_location_rationale`) mostrado **antes** do diálogo do sistema.
- `ACCESS_BACKGROUND_LOCATION`: nunca pedida no arranque. Só a partir das definições, depois de o
  utilizador ativar notificações. A app tem de continuar utilizável sem ela.
- `POST_NOTIFICATIONS` (Android 13+): pedida apenas quando o utilizador ativa notificações.
- Recusar uma permissão nunca pode deixar um ecrã em branco sem explicação.

## Fluxo de desenvolvimento (Spec-Kit)

As 4 features do MVP — lista de aviões, widget, notificações, definições — seguem, **uma de cada
vez**:

```
/speckit-specify → /speckit-clarify → /speckit-plan → /speckit-tasks → /speckit-analyze → /speckit-implement
```

- durante o `/speckit-plan`, invocar o subagente `architect` se houver decisão estrutural;
- no fim de cada `/speckit-implement`, invocar o subagente `reviewer` antes de dar a feature por
  concluída.

A constituição do projeto está em `.specify/memory/constitution.md` e prevalece sobre este
documento em caso de conflito.

## Estado atual

**`001-sky-list` implementada.** O ecrã principal vai à OpenSky, calcula o que está no céu do
utilizador, resolve o operador pelo indicativo e atualiza-se de 30 em 30 segundos enquanto está
visível. 131 testes unitários verdes, lint sem erros, APK de debug a gerar.

O que ficou a funcionar de ponta a ponta: `OpenSkyFlightDataSource` → `FlightRepositoryImpl`
(caixas em paralelo, deduplicação por `icao24`, tradução para `SkyError`) → `ObserveSkyUseCase`
(deteção + operador) → `MainViewModel` (laço sequencial de AD-008) → `MainScreen` (seis estados:
rationale, recusa permanente, primeiro carregamento, erro bloqueante, céu vazio e lista).
`AssetAirlineDirectory` lê 5774 operadores de `assets/airlines.json`, gerado por
`tools/airlines/build_airlines_json.py` a partir do `airlines.dat` do OpenFlights.

Continuam por implementar, como esqueleto com `TODO(feature/...)`: `SettingsRepositoryImpl`,
`SightingRepository`/Room, `LocationRepositoryImpl.locationUpdates`, widget, worker e notificações.

**Validação em dispositivo (T053) feita a 2026-09-07:** os seis estados respondem, a lista
corresponde ao céu real. Por medir, sem sinal de problema: a mediana do arranque (SC-001), a
cobertura da tabela de operadores (SC-004) e a ausência de rede em segundo plano — ver os critérios
de saída de `specs/001-sky-list/quickstart.md`.

**`002-flight-detail` implementada.** O toque numa linha da lista abre um ecrã com tudo o que a app
sabe da aeronave, a atualizar-se sozinho e a dizer de forma explícita quando o avião sai do céu.
171 testes unitários verdes, lint sem erros, APK de debug a gerar.

O laço de atualização deixou de viver no `MainViewModel` e passou para a `SkySession` partilhada
(AD-011): a contagem de subscritores soma os dois ecrãs, o que dá um só pedido por ciclo e os
mesmos valores nos dois sítios sem mutex nem cache. A distinção entre "saiu do céu", "a atualização
falhou" e "nunca foi observada" é uma redução pura no domínio (AD-012).

Continuam por implementar, como esqueleto com `TODO(feature/...)`: `SettingsRepositoryImpl`,
`SightingRepository`/Room, `LocationRepositoryImpl.locationUpdates`, widget, worker e notificações.
O ecrã de definições continua a ser um beco sem saída — é o que resta dos dois que a 001 deixou.

**`003-flight-route` implementada.** Cada voo mostra `LIS → CDG` na lista e no detalhe, a partir de
uma tabela local de 584 832 rotas embarcada no APK. E o ecrã de definições — que era um esqueleto —
tem a sua primeira entrada real: a atualização dessa tabela, a pedido do utilizador.

232 testes unitários verdes, lint sem erros, APK de 24 para 30 MB.

A tabela é um ficheiro binário de largura fixa lido por pesquisa binária (AD-013): 20 leituras e
37 µs por aeronave, memória praticamente nula. A substituição é `rename` atómico depois de validar,
o que garante que uma atualização interrompida nunca deixa a app sem tabela — sem locks e sem
coordenação.

**Falta para dar a feature por fechada:** a validação manual de
`specs/003-flight-route/quickstart.md`, secções 6 a 9. Os passos que mais importam são o 10 (matar a
app a meio de uma atualização) e o 12 (nenhuma transferência sem o utilizador pedir), mais a
contagem de cobertura real (SC-001, ≥70%) e a verificação de 30 rotas contra uma fonte independente
(SC-007).

**Dívida conhecida:** o SC-005 — a mediana do arranque não piorar — ficou **inverificável** por
decisão de 2026-09-08: o valor de antes nunca foi medido e os 7,6 MB já entraram no APK. Vale a pena
cronometrar o arranque agora, não para provar nada sobre esta feature, mas para dar a linha de base
que falta à seguinte. O mesmo se aplica à cobertura da tabela de operadores (SC-004 da 001).

**`004-settings` implementada.** O utilizador ajusta o raio de deteção, o ângulo mínimo acima do
horizonte e a altitude mínima, e escolhe as unidades de distância e de altitude. As escolhas
persistem, aplicam-se sem reiniciar, e nenhuma combinação permitida parte nada.

**286 testes unitários verdes**, lint sem erros. APK de release com 10 MB (o R8 corta de 30).

A revisão desta feature encontrou um defeito que **todos os utilizadores** teriam visto no primeiro
arranque: o aviso "aumentar o raio não traz aviões novos" acendia com os valores de fábrica, sobre uma
escolha que ninguém tinha feito. A causa era um único número escrito de duas maneiras — o teto de
altitude era 14 km na conta que deriva o raio máximo e 12 km na que produz o aviso — e o teste que
devia ter apanhado isto chamava-se "o raio de origem cabe no alcance útil" e passava 25 km, quando o
raio de origem são 30 km. O nome afirmava o que o teste nunca verificava.

A correção não foi alinhar os números pelo valor conveniente: o aviso afirma que *nenhum* avião novo
aparece, e uma afirmação universal só é verdadeira se for medida no caso mais favorável a haver um —
o **teto** de altitude, nunca a altitude típica. Medido contra os 12 km típicos, o aviso dizia "não traz
aviões novos" enquanto o tráfego a 14 km continuava a aparecer até aos 30 km: aritmética certa, céu
errado. Os testes novos foram verificados a falhar contra o teto antigo.

Fecha a promessa que a AD-009 deixou aberta na primeira feature: os critérios deixam de estar fixos
no código. A `SkySession` lê-os no início de cada ciclo (AD-018) e passa-os por valor ao caso de uso,
o que torna uma lista com critérios misturados **estruturalmente impossível** — não há nada para
alguém se lembrar de fazer. E os 232 testes anteriores passaram sem alteração de comportamento,
porque os valores de origem são exatamente os que estavam fixos no código.

Uma investigação desta feature **desmentiu um requisito da sua própria especificação**: o orçamento
de consultas não depende do raio. O custo é determinado pela área da caixa envolvente, e qualquer
raio utilizável — até ~246 km — fica no primeiro degrau. O teto real da app, que nunca esteve escrito
em lado nenhum, são cerca de **3h20m de ecrã aberto por dia**, e nenhuma definição o altera.

**`005-sky-widget` implementada.** O widget de ecrã inicial mostra a aeronave mais alta que o
trabalho de fundo encontrou, **sempre com o instante da observação**, com toque para atualizar e
cadência escolhida nas definições. O trabalho de fundo existe se e só se houver widget no ecrã.

**345 testes unitários verdes**, lint sem erros.

A feature abre por admitir que o pedido é impossível: o mínimo da plataforma para trabalho periódico
são 15 minutos e uma aeronave atravessa um raio de 30 km em 4 a 5. Quando o widget acorda, o avião já
saiu. A saída foi dizer a verdade em vez de a esconder — o instante sempre à vista, e linguagem de
presente só dentro de uma janela de 5 minutos, avaliada **no momento da leitura** e não na escrita
(AD-024). Se fosse decidida na escrita, o widget afirmaria presença durante 25 dos 30 minutos em que
isso já era falso.

**Corrigiu a AD-003**, primeira vez que uma decisão do projeto é corrigida por outra. Verificado por
`javap` sobre o AAR que `updateAppWidgetState` exige um `GlanceId`: o estado do Glance é por instância
de widget. Guardar lá o resultado obrigava a N cópias e deixava as notificações sem o poderem ler.

A revisão encontrou **dois bloqueadores**, ambos meus:

1. **A cadência nunca era persistida.** `refreshIntervalMinutes` não aparecia uma vez no
   `SettingsRepositoryImpl` — o cursor mexia-se e o valor era descartado em silêncio, com o trabalho a
   correr sempre a 15 minutos e a gastar o dobro do orçamento. Escapou porque os testes do ecrã usam um
   repositório falso que guarda o objeto inteiro em memória, onde a lacuna não existe. Lição:
   **um duplo que guarda mais do que o real esconde exatamente o que é preciso testar.**
2. **Um toque em atualizar sem rede prendia o widget em "A atualizar…" para sempre** — o mesmo defeito
   que a 003 teve. Resolvido tirando a restrição de rede ao pedido manual, e não verificando a rede
   antes de enfileirar: essa verificação só reduz a probabilidade, porque a rede pode cair entre a
   verificação e a execução.

**Falta a validação em dispositivo** (`specs/005-sky-widget/quickstart.md`), com prioridade para a
secção 4 — o widget partido da v1.0.0 a recuperar sozinho, que só é reproduzível a partir dessa versão.

## Primeira release

**v1.0.0**, com APK assinado. A chave vive em `~/.mysky/mysky-release.jks`, fora do repositório, e as
credenciais em `keystore.properties`, que o `.gitignore` exclui. **Perder essa chave significa nunca
mais poder publicar uma atualização sob a mesma identidade.**

Página do produto em `docs/`, servida pelo GitHub Pages, com a atribuição que a ODbL do OpenFlights
exige.

**O que a app tem, ao todo:** lista dos aviões no céu com companhia e rota, detalhe de cada aeronave
que se atualiza sozinho, tabela de rotas atualizável, e definições de deteção e unidades.

**Continuam por implementar**, como esqueleto com `TODO(feature/...)`: widget (Glance),
`SkyRefreshWorker`/`SkyWorkScheduler`, notificações, e o histórico de avistamentos em Room.

## Dívida conhecida, por ordem de importância

**O APK de release nunca correu num aparelho.** É a lacuna mais séria. Os 278 testes, o lint e a
inspeção do R8 dão boa evidência — o serializer dos DTO manteve o nome, Hilt e DataStore estão
presentes — mas nada disso prova que a app arranca depois de minificada. Se falhar, o
`app/build/outputs/mapping/release/mapping.txt` desofusca o relatório.

**A 003 também nunca foi validada em dispositivo**, e é a única das quatro em que isso aconteceu.
Corrigiram-se-lhe três defeitos críticos que os testes tinham deixado passar, incluindo duas consultas
concorrentes a devolverem a rota de outro voo. O guião está em
`specs/003-flight-route/quickstart.md`, secções 6 a 9.

**Três números nunca medidos:** mediana do arranque, cobertura real das rotas (SC-001 da 003, ≥70%) e
cobertura da tabela de operadores (SC-004 da 001). A mediana foi pedida como primeira tarefa da 004 e
não foi feita — continua a ser a última oportunidade fácil antes de a app crescer mais.

**Feature seguinte:** o widget (AD-003), que é a razão de ser da app e a maior das que faltam. Vai
precisar do `SkyRefreshWorker` e do `SkyWorkScheduler`, ambos em esqueleto desde o início.
