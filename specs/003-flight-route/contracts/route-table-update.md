# Contrato — atualização da tabela de rotas

O que o ecrã de definições observa e envia, e as garantias da substituição do ficheiro.

## Porta de domínio

```
interface RouteTableRepository {
    val updateState: Flow<RouteUpdateState>
    suspend fun tableInfo(): RouteTableInfo?    // data de geração e contagem, do cabeçalho
    fun requestUpdate()
}
```

O `SettingsViewModel` fala **apenas** com isto. Nunca importa `androidx.work.*`: a tradução de
`WorkInfo` para `RouteUpdateState` acontece na implementação, tal como a tradução de erros de rede
para `SkyError` acontece no `FlightRepositoryImpl` (AD-010, AD-016).

## Eventos e estados

| Evento | Efeito |
|---|---|
| `requestUpdate()` | enfileira o trabalho; um segundo pedido com um já em curso **não** duplica (`ExistingWorkPolicy.KEEP`) |
| Sem rede | `Failure(NoConnection)` — dito de imediato, não depois de tentar e falhar em silêncio |
| Servidor indisponível | `Failure(Unreachable)` |
| Ficheiro inválido | `Failure(InvalidData)`, e a tabela anterior fica |
| Concluído | `Success(data de geração, contagem)` |

## As garantias da substituição

| # | Garantia | Requisito |
|---|---|---|
| 1 | O download vai para um ficheiro temporário; o canónico só é tocado por `rename` | FR-020 |
| 2 | Toda a validação acontece antes do `rename` | FR-020 |
| 3 | Uma interrupção em qualquer ponto deixa a tabela anterior íntegra e utilizável | FR-020, SC-008 |
| 4 | Um leitor com o ficheiro antigo aberto continua a lê-lo em segurança | FR-020 |
| 5 | Depois de um `rename` bem sucedido, a consulta seguinte usa a tabela nova sem reiniciar a app | FR-021 |
| 6 | A app nunca transfere nada sem o utilizador pedir | FR-019, SC-010 |
| 7 | A atualização não toca no ciclo de observação do céu nem no orçamento da fonte de voos | FR-022, FR-023 |

A garantia 3 é a que este contrato existe para proteger. **Uma tabela meio escrita é pior do que uma
tabela velha**: a velha dá rotas desatualizadas, a meio escrita dá lixo com ar de rota, ou nenhuma
rota nenhuma. O `rename` atómico é o que a torna estrutural em vez de uma questão de cuidado.

A garantia 6 tem de ser testada pela negativa — uma sessão inteira de uso sem nenhuma transferência
— porque é o género de coisa que se estraga com um `init` bem-intencionado e não dá erro nenhum.

## De onde vem o ficheiro

Uma release do repositório do próprio projeto, servida por CDN, numa **tag fixa dedicada aos dados**
(`routes-latest`) e não em `latest`. **Não** o espelho dos dados em bruto: a conversão de CSV para
binário acontece no script offline e em mais lado nenhum (AD-014).

A tag fixa não é detalhe: com `latest`, publicar uma release de aplicação sem a tabela faria o
`routes.bin` desaparecer do URL, e a atualização passaria a falhar como se o servidor não
respondesse.
