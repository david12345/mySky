# Phase 1 — Modelo de dados: Origem e destino do voo

Um tipo de domínio novo, um campo novo no `OverheadFlight`, e um formato de ficheiro. Nenhuma
persistência de dados do utilizador: o único ficheiro escrito é a tabela de rotas, que é dado de
referência.

---

## `Route` — `domain/model`

```
data class Route(
    val originIata: String,
    val destinationIata: String,
)
```

**Invariantes**, validadas no construtor:

- as duas siglas têm exatamente **3 letras A–Z maiúsculas**;
- **nunca existe uma `Route` com um lado só**. É aqui que FR-006 fica garantido: não há forma de
  construir meia rota, por isso não há forma de a apresentar. A alternativa — dois campos opcionais
  no `OverheadFlight` — deixaria a regra à responsabilidade de quem desenha o ecrã, que é onde ela
  se perde;
- `originIata == destinationIata` é **válido**: um voo de instrução ou de regresso ao ponto de
  partida é informação legítima, não um erro a esconder (caso-limite da especificação).

### `Route.callsignKeyOf(callsign: String?): String?`

Normaliza o indicativo para a chave de pesquisa: apara espaços, passa a maiúsculas, e devolve
`null` para vazio ou para o que não possa ser chave.

Vive no domínio, ao lado do modelo, pela mesma razão que `Airline.icaoPrefixOf`: **a mesma regra tem
de valer nos dois lados**. O script de conversão gera as chaves com esta regra; a app procura com
ela. Duas implementações a divergir produziriam uma tabela cheia de rotas que nunca seriam
encontradas — e nada, em lado nenhum, daria erro.

---

## `OverheadFlight` — alteração

```
data class OverheadFlight(
    ...
    val airline: Airline? = null,
    val route: Route? = null,      // novo
)
```

Preenchido no mesmo segundo passo que `airline`, no `ObserveSkyUseCase`. `null` é o caso normal, não
a exceção: a maioria dos indicativos que passa no céu de um observador não terá rota. Nunca esconde
a aeronave nem impede a sua apresentação.

---

## Formato do ficheiro `routes.bin`

Um cabeçalho, seguido de registos de largura fixa ordenados por indicativo. Tudo em ASCII ou
inteiros big-endian; sem framework de serialização, porque o formato tem de ser lido por
deslocamento direto.

### Cabeçalho

| Campo | Tamanho | Conteúdo |
|---|---|---|
| Assinatura | 8 bytes | `MYSKYRT\0` — distingue este ficheiro de um download truncado ou de uma página de erro HTML |
| Versão do formato | 2 bytes | inteiro; a app recusa o que não souber ler |
| Contagem de registos | 4 bytes | usada para validar o tamanho do ficheiro e para a pesquisa binária |
| Data de geração | 8 bytes | epoch em segundos — é isto que FR-018 apresenta ao utilizador |
| Reservado | 10 bytes | zeros, para crescer sem mudar de versão |

**32 bytes.** Guardar a data aqui, e não num ficheiro de metadados ao lado, é deliberado: um segundo
ficheiro teria de ser escrito de forma coordenada com o binário, e uma interrupção entre os dois
deixaria a app a afirmar uma data que não corresponde aos dados que tem.

### Registo

| Campo | Tamanho | Conteúdo |
|---|---|---|
| Indicativo | 7 bytes | ASCII, alinhado à esquerda, preenchido com espaços |
| Origem | 3 bytes | sigla IATA |
| Destino | 3 bytes | sigla IATA |

**13 bytes por registo.** Sete chegam para o indicativo: o máximo medido nos 619 922 registos da
fonte é exatamente 7.

### Validação, na ordem em que a app a faz

1. assinatura correta;
2. versão do formato conhecida;
3. `(tamanho − 32)` divisível por 13;
4. `(tamanho − 32) / 13` igual à contagem do cabeçalho;
5. contagem acima de um mínimo plausível — protege contra um ficheiro tecnicamente válido mas vazio.

Qualquer falha descarta o download e **preserva a tabela anterior** (FR-020). A validação acontece
no ficheiro temporário, antes do `rename`, e é por isso que a tabela canónica nunca chega a ser
tocada por um download mau.

### Ordenação e unicidade

Os registos são ordenados pela chave de indicativo, em ordem de bytes, e a chave é **única**. A
pesquisa binária depende das duas coisas. A fonte tem indicativos repetidos — horários que mudaram
ao longo do tempo — e é o script que desduplica, de forma determinística e documentada, para que
duas gerações dos mesmos dados produzam o mesmo ficheiro.

---

## `RouteUpdateState` — `domain/model`

O que o ecrã de definições observa. Selado, porque são exatamente estas as situações que FR-017
obriga a distinguir.

```
sealed interface RouteUpdateState {
    data object Idle : RouteUpdateState
    data object InProgress : RouteUpdateState
    data class Success(val generatedAtEpochSeconds: Long, val routeCount: Int) : RouteUpdateState
    data class Failure(val reason: RouteUpdateError) : RouteUpdateState
}

enum class RouteUpdateError { NoConnection, Unreachable, InvalidData, Unexpected }
```

`Success` transporta a data e a contagem da tabela nova: é o que permite ao ecrã dizer o que mudou,
em vez de um "concluído" que não informa nada.

`RouteUpdateError` existe pela mesma razão que o `SkyError` (AD-010): o ecrã tem de distinguir
"não tens rede" de "o servidor não respondeu" de "o que veio não presta", e não pode fazê-lo
inspecionando exceções de rede.

---

## `SettingsUiState` — `presentation/settings`

```
data class SettingsUiState(
    val routeTableGeneratedAtEpochSeconds: Long? = null,
    val routeCount: Int = 0,
    val updateState: RouteUpdateState = RouteUpdateState.Idle,
)
```

Um único estado por ecrã, como manda o `CLAUDE.md`. Hoje só tem os campos da tabela de rotas; a
feature de definições estende **esta** classe em vez de criar um segundo `StateFlow` solto (AD-017).

`routeTableGeneratedAtEpochSeconds` a `null` significa que ainda não se leu o cabeçalho — não que
não haja tabela.
