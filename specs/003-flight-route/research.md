# Phase 0 — Research: Origem e destino do voo

As decisões D1 a D3 são sobre os dados: de onde vêm, o que trazem e o que se aproveita. Foram
tomadas a partir de medições sobre os ficheiros reais, descarregados e contados, não de estimativas.
As decisões estruturais (D4 em diante) foram tomadas com o subagente `architect` e sobem ao
`CLAUDE.md` como decisões de arquitetura, conforme o princípio V.

---

## D1 — A origem dos dados de rota

**Decision**: `vrs-standing-data`, os dados de referência do Virtual Radar Server, distribuídos em
CSV pelo espelho `vrs-standing-data.adsb.lol`, sob **licença CC0-1.0** (domínio público).

**Medições** (ficheiros descarregados a 2026-09-07):

| | |
|---|---|
| `routes.csv` | 19 MB, **619 922** registos |
| Formato | `Callsign,Code,Number,AirlineCode,AirportCodes` — ex.: `TLK12,TLK,12,TLK,EGLL-LFPB` |
| `airports.csv` | 2,4 MB, **34 128** aeroportos, com colunas ICAO **e** IATA |
| Aeroportos com sigla IATA | 6 426 |
| Atualização na origem | horária |

**Rationale**:
- É o único conjunto encontrado que mapeia **indicativo de voo → aeroportos** de forma livre e
  redistribuível. A CC0 é ainda mais permissiva do que a ODbL da tabela de companhias: não exige
  sequer atribuição, embora a mantenhamos por decência.
- Cobre o requisito central sem uma única chamada de rede por voo, que é o que FR-012 e FR-013
  exigem.
- É a mesma família de dados que serve o `adsbdb` e o `adsb.lol` — ou seja, o que as APIs públicas
  de rota respondem vem, na prática, daqui.

**Alternatives considered**:
- *API `adsbdb`* (gratuita, sem chave, devolve rota por indicativo com IATA e ICAO): rejeitada
  **por causa dos requisitos, não por qualidade**. São dezenas de aeronaves por ciclo de 30 s, o
  que multiplica os pedidos por uma ordem de grandeza e colide de frente com FR-013 e FR-014.
  Cumprir esses requisitos com ela obrigaria a uma cache por indicativo que, na prática,
  reconstruiria esta tabela a partir da rede — mais devagar e com uma dependência de terceiros.
- *OpenSky, a fonte que já usamos*: não serve. O `/states/all` não traz rota; o `/routes` não é
  documentado nem fiável; e o `/flights/aircraft` só devolve origem e destino **depois** de o voo
  aterrar, o que é inútil para quem está a olhar para o céu.
- *`routes.dat` do OpenFlights* (a mesma casa da tabela de companhias): rejeitada — são redes de
  rotas de 2014, por par de aeroportos e companhia, não por número de voo.
- *APIs comerciais* (AeroDataBox, AviationStack, FlightAware): rejeitadas — quota gratuita
  pequena, exigem chave e proíbem a redistribuição dos dados.

**Risco assumido**: o espelho é comunitário e pode desaparecer. Mitigação: os dados de origem são
CC0 e existem em mais do que um espelho, incluindo o repositório do próprio Virtual Radar Server; e
a app é instalada com uma tabela, por isso a desaparição do espelho degrada a atualização, nunca a
funcionalidade.

---

## D2 — De ICAO para IATA, e o que se perde pelo caminho

**Decision**: a tabela embarcada guarda **siglas IATA de três letras**, já traduzidas no momento da
geração. A tradução acontece na ferramenta de conversão, não na app.

**Medição**: das 585 391 rotas de duas pernas, **584 832 (94,3%)** têm as duas pontas traduzíveis
para IATA. Os aeroportos efetivamente usados são apenas **3 440** dos 34 128 do ficheiro.

**Rationale**:
- O utilizador pediu "LIS → CDG", que é IATA. O ICAO (`LPPT`, `LFPG`) é o que a aviação usa e o que
  quase ninguém reconhece.
- Traduzir na geração significa **não embarcar a tabela de aeroportos**: 2,4 MB que não vão no APK,
  e uma consulta a menos por voo em tempo de execução.
- Os 5,7% que se perdem são aeroportos sem designador comercial — aeródromos pequenos, militares,
  privados. Para esses não há sigla nenhuma a mostrar, por isso a perda é aparente: mesmo com a
  tabela completa, não haveria o que apresentar (caso-limite já previsto na especificação).

**Alternatives considered**:
- *Embarcar as duas tabelas e traduzir na app*: rejeitada — 2,4 MB e uma segunda consulta por voo,
  para produzir exatamente o mesmo resultado.
- *Mostrar ICAO quando não há IATA*: rejeitada — misturar códigos de três e quatro letras na mesma
  linha ensina ao utilizador uma distinção que ele não pediu, e FR-004 diz "apenas a sigla".

---

## D3 — Rotas com escalas ficam de fora

**Decision**: só se aproveitam as rotas de **duas pernas** (origem e destino diretos). As 34 420
rotas com escalas — cujas primeira e última pernas seriam aproveitáveis — não entram nesta versão.

**Rationale**:
- São **+5,9%** de registos, e o preço é uma ambiguidade que o utilizador não tem como detetar: num
  voo `LIS-SID-GRU` observado sobre Cabo Verde, "de onde vem" é Sal, não Lisboa. Apresentar
  "LIS → GRU" é verdade sobre o número de voo e mentira sobre aquele avião naquele momento.
- A especificação define rota como par origem-destino e põe escalas fora de âmbito. Aproveitar a
  primeira e a última perna seria contorná-la por 5,9%.
- Fica anotado como ampliação possível: se a cobertura medida em campo (SC-001) ficar perto do
  limiar, é o primeiro sítio onde ir buscar mais.

**Alternatives considered**:
- *Primeira e última perna*: rejeitada pelo motivo acima, mas com o número medido para quem quiser
  reabrir a decisão com dados.
- *Apresentar o itinerário completo*: fora de âmbito por decisão da especificação.

---

## D4 — Custo de uma consulta: medido, não estimado

**Medição** sobre o ficheiro de largura fixa de 7,6 MB, com pesquisa binária e sem qualquer cache
da aplicação:

| | |
|---|---|
| Registos | 584 832 |
| Saltos por consulta | **20** (log₂) |
| Tempo médio por consulta | **37 µs** |
| Custo de uma lista de 50 aeronaves | **1,8 ms** |

**Consequência para o desenho**: FR-013 — não atrasar a lista — deixa de ser uma preocupação real.
1,8 ms é ruído dentro de um ciclo que já gasta centenas de milissegundos em rede e em localização.
Isto dispensa qualquer cache em memória por cima da tabela, e a ausência de cache é o que mantém o
consumo de memória em zero, que é o problema verdadeiro com 585 mil registos.

**Nota de honestidade**: a medição foi feita na máquina de desenvolvimento, sobre um sistema de
ficheiros com cache quente. Num telefone, com armazenamento mais lento e a primeira consulta a
pagar o custo de abrir o ficheiro, espera-se pior — mas há três ordens de grandeza de folga antes
de isto se tornar visível. O quickstart mede o efeito real em SC-005.

---

## D5 — Onde vive a tabela (AD-013)

**Decision**: ficheiro binário de registos de largura fixa — 13 bytes: 7 de indicativo, 3+3 de
siglas — ordenado por indicativo, lido por pesquisa binária sobre um `RouteTableReader`. Nem `Map`
em memória, nem Room.

**Correção feita durante o `/speckit-analyze`**: a primeira redação dizia "lido com
`RandomAccessFile`" e, ao mesmo tempo, que o asset seria lido diretamente do APK. As duas coisas são
incompatíveis. Um `RandomAccessFile` opera sobre o sistema de ficheiros; um asset dentro do APK só é
acessível por `AssetManager.openFd()`, que devolve um descritor com **deslocamento base dentro do
zip**. Daí a abstração: um leitor de acesso aleatório sobre `(descritor, deslocamento base,
comprimento)`, com duas implementações — `filesDir` (que pode usar `RandomAccessFile`) e asset (que
usa o canal do `FileInputStream` posicionado em `startOffset + deslocamento`). A pesquisa binária é
escrita uma vez, sobre a abstração.

**Rationale**:
- O `Map` está fora de questão por aritmética, não por gosto: 585 mil entradas num `HashMap` custam
  da ordem de **100 MB só em overhead de objeto**, antes do conteúdo, num telemóvel cujo heap anda
  pelos 200 MB e é partilhado com o Compose.
- Room traria entidades, DAOs e um índice B-tree — que ocupa 1,5 a 2 vezes o espaço dos dados —
  para uma operação que é sempre a mesma consulta por chave exata, sem junções e sem reatividade. A
  AD-007 rejeitou-o para as companhias por serem dados sem ciclo de vida; aqui há atualização, o que
  desarma esse argumento, mas não o outro.
- O ficheiro fica na cache de páginas do sistema operativo, não no heap da app. É o que torna
  585 mil registos um não-problema de memória.

**Alternatives considered**:
- *`MappedByteBuffer`*: mais rápido, e **rejeitado por causa do ciclo de vida**. O Java não tem
  forma pública e determinística de desmapear: o unmap acontece na finalização. Como esta tabela é
  substituída em runtime — ao contrário da das companhias — um mapeamento da versão antiga podia
  ficar pendurado até o GC decidir, a cada atualização pedida. O `RandomAccessFile` fecha e reabre
  de forma previsível, e as 20 leituras extra são ruído.
- *SQLite sem Room*: mesma objeção de peso do índice, sem sequer a comodidade do Room.

---

## D6 — Como a tabela chega e como se substitui (AD-014)

**Decision**: o telemóvel **nunca** fala com o espelho dos dados. Só o script offline o faz. O
`routes.bin` que ele produz viaja no APK e é publicado como ficheiro de uma **release do GitHub**,
com URL estável, de onde a app o descarrega comprimido (2,5 MB). A substituição é: descarregar para
`cacheDir`, validar, e só então `renameTo` para `filesDir`.

**Rationale**:
- Descarregar os CSV em bruto obrigaria a reimplementar em Kotlin, a correr no telefone, a junção
  ICAO→IATA, a filtragem e a ordenação que o script já faz. Duas implementações da mesma regra em
  duas linguagens divergem em silêncio — precisamente o que o princípio VI manda evitar. E custaria
  21 MB de transferência em vez de 2,5 MB, mais o CPU da conversão.
- O `rename` é atómico no sistema de ficheiros do Android, e um leitor que já tenha o ficheiro
  antigo aberto continua a lê-lo em segurança até o fechar. FR-020 sai daqui **sem locks e sem
  coordenação** — é a propriedade do sistema de ficheiros a fazer o trabalho.
- O cabeçalho do próprio ficheiro guarda assinatura, versão, contagem e data de geração. Isso
  resolve FR-018 sem um segundo ficheiro de metadados, cuja escrita teria de ser mantida coerente
  com a do binário — mais um sítio onde uma interrupção poderia deixar as duas coisas a discordar.

**Correção face à proposta do `architect`**: ele propunha descarregar de `raw.githubusercontent.com`,
apontando ao ficheiro commitado. Duas objeções. A primeira é que cada regeneração acrescenta ~2,5 MB
permanentes ao histórico do git, e o histórico não se limpa. A segunda é que o `raw.githubusercontent`
não é um canal de distribuição: tem limites de tráfego não documentados e nenhuma garantia para este
uso. Os ficheiros de uma **release** são servidos por um CDN próprio, têm URL estável
(`/releases/latest/download/...`) e não incham o histórico. O asset do APK continua commitado, mas
só quando se corta uma versão.

**Alternatives considered**:
- *Copiar o asset para `filesDir` no primeiro arranque, para ter um só caminho de leitura*:
  rejeitada — são 7,6 MB de I/O no caminho crítico do primeiro arranque, exatamente onde a mediana
  do tempo até à lista é medida. A app lê o asset diretamente do APK enquanto não houver
  atualização.
- *Descarregar sempre, sem tabela embarcada*: rejeitada — quem instala e nunca vai às definições
  ficaria sem rotas nenhumas.

---

## D7 — Que porta, e onde entra o enriquecimento (AD-015)

**Decision**: `RouteDirectory` como porta própria do domínio, a par da `AirlineDirectory`, com o
enriquecimento no `ObserveSkyUseCase`. Operador e rota de cada aeronave — e as aeronaves entre si —
resolvem-se **em concorrência**, em `@IoDispatcher`.

**Rationale**:
- A fronteira é a mesma que a AD-007 desenhou e continua válida: dado de referência não é fonte de
  voos.
- A diferença que obriga a pensar de novo: a consulta de operador é uma leitura de `Map` depois do
  primeiro carregamento — grátis. A de rota é **sempre** I/O de disco. Encadear as duas por
  aeronave, sequencialmente, com dezenas de aeronaves por ciclo, é o género de custo que não
  aparece em teste unitário e aparece no dispositivo.
- Com 37 µs por consulta e resolução concorrente, o custo total desaparece dentro de um ciclo que
  já gasta centenas de milissegundos em rede e localização (D4).

---

## D8 — Onde vive a descarga (AD-016), e a emenda à constituição

**Decision**: par próprio `RouteTableUpdateWorker` + `RouteTableUpdateWorkScheduler`, com
`OneTimeWorkRequest`, `NetworkType.CONNECTED` e `ExistingWorkPolicy.KEEP`. O `SettingsViewModel`
observa o progresso por uma porta de domínio e nunca importa `androidx.work.*`.

**Rationale**:
- A AD-003 existe para impedir dois agendamentos de *sky refresh* a duplicar sondagens de posição.
  Esta descarga é rara, única e sempre pedida pelo utilizador, sem relação nenhuma com o orçamento
  de posições nem com o mínimo de 15 minutos. Metê-la no `SkyWorkScheduler` cumpriria a letra da
  regra e trairia o seu propósito, misturando numa classe coerente um método sem nada a ver com o
  que ela garante.
- O WorkManager continua a ser a ferramenta certa, em vez de um scope do ViewModel: uma descarga de
  alguns MB deve sobreviver a o utilizador sair do ecrã de definições.

**Consequência constitucional, e como foi tratada**: o princípio IV dizia, à letra, "**todos** os
`WorkRequest` são criados num único ponto do código". Um segundo scheduler viola isso, e a
constituição prevalece sobre o `CLAUDE.md` — não é uma regra que se possa reinterpretar em silêncio
a meio de um plano. Seguiu-se o procedimento que a própria constituição define: a redação passou a
"**cada tipo** de trabalho de fundo tem um único ponto de criação dos seus `WorkRequest`s", com
Sync Impact Report e **incremento de versão para 1.1.0**. A garantia que a regra sempre quis dar
mantém-se intacta: continua a não haver agendamento avulso espalhado pelo código, e o sky refresh
continua a ter um e um só ponto.

A alternativa era não usar WorkManager de todo e prender a descarga a um scope da aplicação. Era
viável e evitava a emenda, mas trocava a robustez de uma descarga que sobrevive ao ecrã por uma
regra cuja redação já era mais larga do que o problema que resolvia.

---

## D9 — O ecrã de definições (AD-017)

**Decision**: um único `SettingsUiState`; o `SettingsViewModel` não conhece `WorkManager`; e a
`SettingsRepository` (preferências do utilizador) e a `RouteTableRepository` (dado de referência)
são portas distintas.

**Rationale**: esta feature cria a primeira entrada real num ecrã que era um esqueleto. Fixar estas
três coisas custa pouco agora e evita a reversão a meio da feature de definições — quando se
descobrisse a tabela de rotas entalada dentro da `SettingsRepository` só porque ela já lá estava, ou
dois `StateFlow` soltos no mesmo ecrã.

**Explicitamente não decidido**: o desenho do ecrã, a organização em secções e a navegação lá
dentro. É decisão de UI e pertence à feature de definições; antecipá-la agora seria hipotecá-la
pelo lado oposto.
