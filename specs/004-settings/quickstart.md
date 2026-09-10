# Quickstart — validar as definições

Como provar que a feature funciona, do teste unitário à observação no céu real.

## Pré-requisitos

- Os mesmos das features anteriores (JDK 17, SDK com API 36, `local.properties` com `sdk.dir`).
- Para validação manual: dispositivo API 26+, com Internet e localização, num local e hora com
  tráfego — sem aviões na lista não há efeito para observar.

## 0. A linha de base que faltava

```bash
./gradlew :app:installDebug
```

Antes de mexer em nada, cronometrar cinco arranques a frio até a lista aparecer, com a permissão já
concedida, e **registar a mediana aqui**:

| Medição | Valor |
|---|---|
| Mediana do arranque, antes desta feature | _(por preencher)_ |

Não é requisito desta feature. É a dívida que ficou aberta em duas features anteriores — o SC-005 da
003 ficou inverificável por falta deste número — e esta feature acrescenta uma leitura de disco ao
primeiro ciclo (AD-018), portanto é o momento certo para o apanhar.

## 1. Domínio — os limites e a degradação

```bash
./gradlew :app:testDebugUnitTest --tests 'com.mysky.app.domain.*'
```

**Esperado**: verde. Os casos que não podem faltar:

- um valor abaixo do mínimo é corrigido para o mínimo, e um acima do máximo para o máximo;
- um valor **dentro** dos limites passa intacto — é o par do teste anterior, e sem ele um limite mal
  escrito "corrigiria" escolhas legítimas sem ninguém dar por isso;
- o alcance útil é a inversa do ângulo mínimo: 25° dá ~26 km, 5° dá ~137 km;
- os campos que não são desta feature — intervalo do trabalho periódico, notificações, widget — não
  são tocados pela reposição.

## 2. Preferências — persistência e degradação

```bash
./gradlew :app:testDebugUnitTest --tests 'com.mysky.app.data.settings.*'
```

**Esperado**: verde, cobrindo as sete invariantes de
[settings-repository.md](./contracts/settings-repository.md). Em particular: sem nada guardado saem
os valores de origem, um armazenamento ilegível também, e uma escrita com um valor fora dos limites
grava o valor corrigido — não o que o chamador pediu.

## 3. O ecrã e o laço

```bash
./gradlew :app:testDebugUnitTest
```

**Esperado**: verde, incluindo as nove invariantes de
[settings-ui.md](./contracts/settings-ui.md) e **todos os 232 testes anteriores inalterados**. As
funções de formatação ganham um parâmetro de unidade nesta feature; se um teste anterior falhar, é a
alteração que está errada.

Os dois que interessam mais:

- **arrastar um controlo produz uma escrita, não uma por movimento.** Falha em silêncio: um controlo
  ligado ao evento errado funciona perfeitamente e gasta o dia de créditos num arrasto;
- **um ciclo em curso nunca mistura critérios.** A sessão lê um snapshot no início de cada ciclo, e
  o teste verifica que uma alteração a meio de um ciclo só aparece no seguinte.

## 4. Compilar e instalar

```bash
./gradlew :app:assembleDebug :app:lintDebug
./gradlew :app:installDebug
```

## 5. Validação manual — o efeito

| # | Como forçar | Esperado |
|---|---|---|
| 1 | Reduzir o raio ao mínimo, voltar à lista | A lista encurta em menos de um ciclo, sem reiniciar |
| 2 | Subir o ângulo mínimo para 45° | Só aeronaves altas no céu ficam; a lista pode esvaziar-se, com a mensagem de céu vazio e não de erro |
| 3 | Baixar o ângulo mínimo para 5° | Aeronaves baixas no horizonte aparecem, e a direção a olhar passa a ser o campo mais útil |
| 4 | Pôr o raio no máximo com o ângulo em 25° | O ecrã avisa que aumentar mais não trará aeronaves novas |
| 5 | Mudar para milhas e pés | Lista, detalhe **e** o próprio ecrã de definições passam todos a essas unidades, sem mistura |
| 6 | Fechar e reabrir a app | Todas as escolhas se mantêm |
| 7 | Repor os valores de origem | Tudo volta ao início — e a data da tabela de rotas **não** se altera |
| 8 | Arrastar um controlo de ponta a ponta devagar | Ao largar, **uma** atualização; não uma por cada posição do dedo |
| 9 | Alterar um critério com o detalhe aberto noutro ponto da pilha | Ao voltar ao detalhe, os valores refletem o critério novo |

O passo 8 é o que protege o orçamento e o único cujo defeito não se vê: com o Network Inspector
aberto, um arrasto tem de produzir **um** pedido.

O passo 9 tem uma consequência que não é defeito: se o critério novo excluir a aeronave que o detalhe
mostra, o ecrã dirá que ela saiu do céu. É verdade — sob o critério que o utilizador acabou de
escolher, ela já não está lá.

## 6. Verificação de comportamento

**O orçamento não mudou (SC-003)** — com o Network Inspector: 15 minutos com o raio no máximo têm de
dar o mesmo número de pedidos que com o raio de origem. O custo por consulta também não muda: a
150 km a caixa continua no primeiro degrau.

**Os extremos não partem nada (SC-004)** — com todos os valores nos extremos permitidos, a lista
continua a aparecer no mesmo tempo e a atualizar-se.

**Nada de intervalo de trabalho periódico (FR-015)** — nenhum controlo no ecrã liga ao campo de
intervalo do widget. Verificável por inspeção e pelo gate do `tasks.md`.

## 7. Com pessoas

Mostrar o ecrã a três pessoas que nunca o viram e, para cada definição, perguntar o que esperam que
aconteça à lista se lhe mexerem. É o SC-007, e é a única forma de saber se as frases explicativas
servem para algo. Se ninguém acertar numa delas, a frase é que está errada — não o utilizador.

## Critérios de saída

- [ ] `./gradlew :app:testDebugUnitTest` verde, incluindo os 232 anteriores inalterados
- [ ] `./gradlew :app:lintDebug` sem erros
- [ ] Linha de base do arranque medida e registada na secção 0
- [ ] Os nove passos da secção 5 verificados
- [ ] Um arrasto produz um pedido, não vários (passo 8, no Network Inspector)
- [ ] Mesmo número de pedidos com o raio no máximo e no de origem (SC-003)
- [ ] Nenhum controlo ligado ao intervalo do trabalho periódico (FR-015)
- [ ] Três pessoas ouvidas sobre as frases explicativas (SC-007)
- [ ] Subagente `reviewer` executado e achados tratados
