# Phase 0 — Research: Lista de aviões no meu céu

Todas as incógnitas do Technical Context estão resolvidas. Nenhuma marca `NEEDS CLARIFICATION`
permanece. As quatro decisões estruturais (D1–D4) são registadas em paralelo no `CLAUDE.md` como
AD-007 a AD-010, conforme o princípio V da constituição.

---

## D1 — Onde vive a tabela de operadores aéreos

**Decision**: ficheiro JSON em `app/src/main/assets/airlines.json`, mapeando prefixo ICAO de 3
letras para nome do operador, lido preguiçosamente e mantido em memória por um singleton
`AssetAirlineDirectory` em `data/local/`. A abstração é uma porta nova no domínio,
`domain/repository/AirlineDirectory`, **distinta** do `FlightDataSource`. A extração do prefixo a
partir do indicativo de voo é regra de domínio e vive em `domain/model/Airline`.

**Proveniência e licença**: os dados vêm do **OpenFlights** (`airlines.dat`), publicado sob **Open
Database License (ODbL)**, que permite a redistribuição dentro do APK desde que a atribuição seja
preservada — daí o `assets/airlines-LICENSE.txt` que viaja ao lado da tabela. Guardam-se apenas os
registos com código ICAO de exactamente 3 letras: o resto são companhias sem designador ICAO, que
nunca poderiam aparecer num indicativo de voo. A alternativa óbvia, o **ICAO Doc 8585**, foi
descartada **por licença e não por qualidade** — é publicação paga da ICAO e o seu conteúdo não é
redistribuível, o que a torna inutilizável dentro de um APK.

**Rationale**:
- São ~1500 pares de strings (~50 KB). Carregar e indexar uma vez custa poucos milissegundos e a
  consulta passa a ser uma leitura de `Map`, o que cumpre FR-011 sem qualquer pedido de rede.
- Dados de referência estáticos não têm ciclo de vida, não têm migrações e nunca são escritos.
  Pô-los no Room obrigaria a versionar um esquema e a manter migrações para conteúdo que só muda
  quando alguém atualiza a app.
- O princípio II da constituição fala de **fontes de dados de voo**. Uma tabela de operadores é
  dado de referência, não um fornecedor de posições: dar-lhe porta própria respeita o princípio em
  vez de o contornar. Espremê-la para dentro do `FlightDataSource` é que seria a violação, porque
  passaria a haver duas responsabilidades atrás de um contrato que promete apenas posições.
- Manter a interface no `domain` permite que o widget e as notificações a reutilizem sem
  duplicação.

**Alternatives considered**:
- *Tabela Room pré-populada com `createFromAsset`*: rejeitada — traz versionamento de esquema e
  migrações para dados imutáveis, e a consulta por chave exata não beneficia de SQL.
- *Ficheiro Kotlin gerado com um `mapOf` gigante*: rejeitada — aumenta o dex, o pool de constantes
  e o tempo de compilação, e atualizar a tabela passa a exigir regenerar código.
- *Resolver o operador online*: rejeitada explicitamente pelo utilizador na clarificação Q1, e
  contraria o orçamento de pedidos (SC-008).

**Onde se aplica o enriquecimento**: em `ObserveSkyUseCase`, não em `DetectOverheadFlightsUseCase`.
O caso de uso de deteção mantém-se puro e síncrono (a consulta ao diretório é `suspend`), os seus
testes atuais ficam intactos, e `OverheadFlight` ganha um campo `airline: Airline?` preenchido no
passo seguinte. O widget e as notificações herdam o enriquecimento de graça.

---

## D2 — Onde vive a atualização automática de 30 segundos

**Decision**: um único laço sequencial no `MainViewModel`, exposto como `StateFlow` através de
`stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())` e consumido na UI
com `collectAsStateWithLifecycle()`. O laço tem a forma:

```
enquanto ativo:
    atualiza (obter posição -> obter voos -> calcular)
    espera até ao próximo tick OU até chegar um pedido manual, o que vier primeiro
```

O pedido manual chega por um `MutableSharedFlow<Unit>` com capacidade extra 1 e
`DROP_OLDEST`. **Nada de WorkManager nesta feature.**

**Rationale**:
- FR-019 (parar tudo quando o ecrã deixa de estar visível) sai de graça: o coletor com consciência
  do ciclo de vida cancela a subscrição ao parar, e `WhileSubscribed(5_000)` cancela o laço a
  montante 5 segundos depois — a janela cobre rotações de ecrã sem reiniciar trabalho, e satisfaz
  SC-007 (nada de rede nos 60 s seguintes) com folga.
- FR-020 (sem pedidos concorrentes) sai da própria forma do laço: há uma só corrotina a fazer
  trabalho, sequencialmente. Não é preciso *mutex* nem estado de "em curso" partilhado.
- Esperar "até ao tick **ou** até ao pedido manual" faz com que um refresh manual reinicie o
  relógio, em vez de disparar um pedido automático logo a seguir.
- O laço é integralmente testável com `runTest` e tempo virtual: 30 segundos passam
  instantaneamente e as asserções são sobre emissões, não sobre relógios reais.
- O WorkManager tem mínimo de 15 minutos e destina-se a trabalho fora do ecrã. Usá-lo aqui seria
  contrariar o princípio IV e o AD-003, que reserva o worker para widget e notificações.

**Alternatives considered**:
- *`while (isActive) { refresh(); delay(30s) }` lançado em `viewModelScope`*: rejeitada — o
  `viewModelScope` sobrevive ao ecrã ir para segundo plano, portanto continuaria a consumir rede e
  bateria em violação direta de FR-019.
- *`Flow` periódico exposto pelo `domain`*: rejeitada — obrigaria o domínio a conhecer política de
  atualização, que é decisão de apresentação, e a receber um dispatcher só para poder ser testado.
- *WorkManager*: rejeitada pelas razões acima.

**Localização a cada ciclo**: cada iteração pede uma posição pontual em vez de subscrever
atualizações contínuas. Cobre o caso-limite "utilizador em movimento" com uma fração do custo de
bateria de um fluxo contínuo de localização.

---

## D3 — Assinatura e responsabilidades do `ObserveSkyUseCase`

**Decision**: `ObserveSkyUseCase` deixa de depender do `SettingsRepository` e passa a receber os
critérios por parâmetro, com valores por omissão:

```
suspend operator fun invoke(
    observer: GeoPosition,
    criteria: OverheadCriteria = OverheadCriteria(),
): Result<List<OverheadFlight>>
```

Depende de `FlightRepository`, `GeoCalculator`, `DetectOverheadFlightsUseCase`, `AirlineDirectory`
e `TimeProvider`. A deduplicação por `icao24` fica no `FlightRepositoryImpl`. O relógio entra por
`domain/time/TimeProvider`, uma interface com `fun nowEpochSeconds(): Long`.

**Rationale**:
- O `SettingsRepositoryImpl` é hoje um stub que rebentaria em execução, e implementá-lo aqui seria
  invadir a feature de definições. Passar `OverheadCriteria` por parâmetro adia esse acoplamento
  sem custo: quando a feature de definições existir, é o ViewModel que passa a ler as definições e
  a alimentar este parâmetro — a assinatura não muda.
- A deduplicação pertence ao repositório porque a divisão em várias caixas envolventes é um
  artefacto de **como** as fontes são interrogadas (o antimeridiano obriga a duas caixas). O
  contrato `FlightRepository` já promete resultados deduplicados; cumprir essa promessa é
  responsabilidade de quem a fez.
- O relógio como abstração mantém o princípio I: o domínio não lê `System.currentTimeMillis()`.
  `DetectOverheadFlightsUseCase` **não** muda — continua a receber `nowEpochSeconds` como
  parâmetro puro; é o `ObserveSkyUseCase` que obtém o valor e o passa adiante. Assim os testes
  existentes ficam intactos e continua a haver um único ponto onde o tempo entra no sistema.

**Alternatives considered**:
- *Implementar já o `SettingsRepository` a devolver valores por omissão*: rejeitada — cria um
  DataStore meio implementado que a feature de definições teria de desfazer, e confunde "ainda não
  configurável" com "configurado com o valor por omissão".
- *Deduplicar no caso de uso*: rejeitada — obrigaria o domínio a saber que a consulta é feita por
  caixas, detalhe que só existe porque as APIs de voo trabalham assim.
- *`kotlin.time.Clock`*: rejeitada por enquanto — ainda experimental na versão de Kotlin em uso;
  uma interface de três linhas não justifica um opt-in a API instável.

---

## D4 — Erros de domínio e rate limiting

**Decision**: `domain/model/SkyError`, uma hierarquia selada com `NoConnection`,
`FlightServiceUnavailable`, `RateLimited(retryAfterSeconds)`, `LocationUnavailable` e `Unexpected`.
Estende `Exception` (com `fillInStackTrace` anulada) para caber no `Result<T>` que os contratos já
usam. A tradução de `IOException`/`HttpException` para `SkyError` acontece no `FlightRepositoryImpl`
— fronteira do repositório, como manda o `CLAUDE.md`. O backoff de 429 **não** é feito em segredo:
o `RateLimited` sobe até ao ViewModel, que alonga a espera do próximo ciclo para
`max(intervalo, retryAfter)` e mostra ao utilizador que está temporariamente limitado.

**Rationale**:
- FR-024 exige que a UI distinga três causas. Se o erro chegar como `IOException` genérica, essa
  distinção passa a ser feita por inspeção de tipos de rede na camada de apresentação — exatamente
  o acoplamento que o `CLAUDE.md` proíbe.
- Reintentar um 429 automaticamente gastaria o orçamento diário mais depressa e escondia do
  utilizador a razão de a lista não atualizar. Propagar e espaçar cumpre FR-021 e mantém SC-008.
- Estender `Exception` é um compromisso deliberado: evita inventar um tipo de resultado paralelo ao
  `Result<T>` já usado nas interfaces `FlightRepository` e `ObserveSkyUseCase`. O custo é que um
  erro de domínio é tecnicamente lançável; mitiga-se anulando o preenchimento de stack trace e
  nunca o lançando — só embrulhado em `Result.failure`.

**Alternatives considered**:
- *Tipo `SkyResult<T>` próprio*: rejeitada para já — obrigaria a mudar contratos já escritos e
  testados, sem ganho funcional. Fica anotado como refactor possível se o `Result` começar a
  estorvar.
- *Interceptor OkHttp a reintentar 429 com backoff*: rejeitada — esconde do utilizador o motivo, e
  o retry consome quota exatamente quando ela já se esgotou.

**Estado de UI sem combinações impossíveis**: `MainUiState` passa a ter `permission`, `phase`
(`Idle` / `LocatingUser` / `LoadingFlights`), `flights`, `lastUpdatedEpochSeconds` e `lastError`.
"Tenho resultados antigos e a última tentativa falhou" (FR-025) representa-se naturalmente por
`flights` não vazia com `lastError` não nulo; "céu vazio" (FR-023) por `flights` vazia,
`lastError` nulo e `lastUpdatedEpochSeconds` não nulo; "ainda nunca correu" por
`lastUpdatedEpochSeconds` nulo. Detalhe completo em [contracts/main-screen-ui.md](./contracts/main-screen-ui.md).

---

## Notas de investigação sobre a fonte de dados

- `GET /states/all` devolve cada aeronave como **array posicional**, não como objeto. A ordem dos
  campos é contrato da API e está fixada em `contracts/opensky-states-all.md`. Arrays mais curtos do
  que o esperado e `null` em qualquer posição são normais e têm de ser tolerados.
- O indicativo de voo vem preenchido com espaços à direita (`"TAP1234 "`), o que obriga a `trim()`
  antes de qualquer comparação ou extração de prefixo.
- O uso anónimo é limitado por créditos diários e por resolução temporal. Um ciclo de 30 s durante
  15 minutos consome 30 pedidos, o que cabe folgadamente no orçamento; é o que sustenta SC-008.
- 429 é a sinalização de excesso; o cabeçalho `X-Rate-Limit-Retry-After-Seconds`, quando presente,
  dá o tempo de espera e alimenta `RateLimited.retryAfterSeconds`.
