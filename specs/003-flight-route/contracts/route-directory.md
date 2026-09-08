# Contrato — `RouteDirectory`

A porta que o domínio define e o `data` implementa, a par da `AirlineDirectory` (AD-007, AD-015).

```
suspend fun findByCallsign(callsign: String?): Route?
```

## Pós-condições

- `callsign` nulo, vazio ou que não normalize para chave válida ⇒ `null`;
- chave válida mas ausente da tabela ⇒ `null`;
- **nunca lança**. Um ficheiro em falta, ilegível, truncado ou com cabeçalho inválido degrada para
  diretório vazio, e a lista continua a funcionar sem rotas (FR-014);
- **nunca faz rede.** A tabela é local; a atualização é outro caminho, e passa pelo
  `RouteTableRepository`;
- a `Route` devolvida tem sempre os dois lados preenchidos — não existe forma de construir meia
  rota (FR-006).

## Invariantes verificadas por teste

| # | Invariante | Requisito |
|---|---|---|
| 1 | Indicativo conhecido devolve a rota exata da tabela | FR-007, FR-008 |
| 2 | Indicativo desconhecido devolve `null`, nunca a rota de um indicativo vizinho | FR-008 |
| 3 | Indicativo com espaços ou em minúsculas encontra a mesma rota | FR-010 |
| 4 | Matrícula de aviação privada (`CS-DHA`, `N123AB`) devolve `null` sem erro | FR-011 |
| 5 | Ficheiro ausente, truncado, com assinatura errada ou versão desconhecida ⇒ diretório vazio, sem exceção | FR-014 |
| 6 | Uma consulta a meio de uma substituição de tabela devolve um resultado coerente — o antigo ou o novo, nunca lixo | FR-020 |
| 7 | A chave gerada pelo script e a procurada pela app coincidem para os mesmos indicativos | FR-010 |

A invariante 2 é a que protege a US2: numa pesquisa binária, um erro de comparação não devolve
"não encontrado" — devolve **o vizinho**, que é uma rota real de outro voo. Seria apresentada com o
mesmo ar de certeza que a correta.

A invariante 7 precisa de um teste que corra a mesma normalização dos dois lados. É a única forma de
apanhar uma divergência que, se acontecer, produz uma tabela inteira de rotas que nunca são
encontradas — sem erro em lado nenhum.

## Custo

A pesquisa binária corre sobre um `RouteTableReader` — acesso aleatório sobre `(descritor,
deslocamento base, comprimento)` — e não sobre um `RandomAccessFile`: o asset dentro do APK só é
acessível por `AssetManager.openFd()`, com deslocamento base dentro do zip, e a pesquisa tem de
servir as duas origens sem ser escrita duas vezes.

Medido: **20 leituras e 37 µs por consulta**, 1,8 ms para 50 aeronaves. Corre em `@IoDispatcher`, e
as consultas das várias aeronaves — e a de operador e a de rota da mesma aeronave — resolvem-se em
concorrência, nunca em cadeia (AD-015).
