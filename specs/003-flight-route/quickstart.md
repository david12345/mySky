# Quickstart — validar a origem e o destino do voo

Como provar que a feature funciona, do teste unitário à observação no céu real.

## Pré-requisitos

- Os mesmos das features anteriores (JDK 17, SDK com API 36, `local.properties` com `sdk.dir`).
- Python 3 para gerar a tabela.
- Para validação manual: dispositivo API 26+, com Internet e localização, num local e hora com
  tráfego **comercial** — sem voos de linha não há rotas para ver.

## 0. A linha de base, antes de tudo

```bash
./gradlew :app:installDebug   # ainda SEM o routes.bin
```

Cronometrar cinco arranques a frio, com a permissão já concedida, do toque no ícone até a lista
aparecer. **Registar a mediana aqui:**

| Medição | Valor |
|---|---|
| Mediana do arranque, antes desta feature | **não medida — dispensada a 2026-09-08** |

**SC-005 fica inverificável nesta feature.** Exige comparação com o valor de antes, esse valor nunca
foi apurado (é a T053 da 001, que ficou por fazer), e a janela para o medir fecha-se assim que os
7,6 MB entram no APK. Foi uma decisão consciente, não um esquecimento: fica registada aqui para
quem vier a seguir não a confundir com um critério por verificar.

Continua a fazer sentido cronometrar o arranque depois da feature e anotar o valor — não prova nada
sobre a regressão, mas dá a linha de base que falta para a feature seguinte.

## 1. Gerar a tabela

```bash
python3 tools/routes/build_routes_bin.py
```

**Esperado**: `app/src/main/assets/routes.bin` com cerca de **7,6 MB** e **584 832 registos**,
mais um resumo no terminal com a contagem, a data de geração e quantas rotas se perderam por não
terem sigla IATA nas duas pontas.

Correr o script duas vezes sobre os mesmos dados de entrada tem de produzir **ficheiros idênticos** —
é o que prova que a desduplicação é determinística. Verificar com `sha256sum`.

## 2. Domínio — a chave e o modelo

```bash
./gradlew :app:testDebugUnitTest --tests 'com.mysky.app.domain.*'
```

**Esperado**: verde. Os casos que não podem faltar:

- `Route` recusa siglas que não sejam 3 letras A–Z, e recusa ser construída com um lado só;
- `Route(LIS, LIS)` é válida — voo que regressa ao ponto de partida;
- `Route.callsignKeyOf` normaliza espaços e minúsculas, e devolve `null` para vazio.

## 3. A tabela — leitura, e o que fazer quando ela está má

```bash
./gradlew :app:testDebugUnitTest --tests 'com.mysky.app.data.*'
```

**Esperado**: verde, cobrindo as sete invariantes de
[route-directory.md](./contracts/route-directory.md). As duas que interessam mais:

- **um indicativo desconhecido devolve `null`, nunca a rota do vizinho.** Numa pesquisa binária, um
  erro de comparação não dá "não encontrado" — dá a rota de outro voo, apresentada com o mesmo ar de
  certeza que a correta;
- **a chave gerada pelo script e a procurada pela app coincidem.** Uma divergência aqui produz uma
  tabela inteira de rotas que nunca são encontradas, sem erro em lado nenhum.

E os ficheiros maus: ausente, truncado a meio de um registo, com assinatura errada, com versão
desconhecida, com contagem que não bate certo com o tamanho. Todos têm de degradar para "sem rotas",
nenhum pode lançar.

## 4. A atualização

```bash
./gradlew :app:testDebugUnitTest --tests 'com.mysky.app.data.route.*'
```

**Esperado**: verde, cobrindo as sete garantias de
[route-table-update.md](./contracts/route-table-update.md). Em particular, com o download
interrompido em cada ponto — antes de escrever, a meio da escrita, depois de escrever mas antes de
validar, depois de validar mas antes do `rename` — a tabela anterior tem de ficar íntegra e
utilizável nos quatro casos.

## 5. Compilar, e o gate do `noCompress`

```bash
./gradlew :app:assembleDebug :app:lintDebug
unzip -lv app/build/outputs/apk/debug/app-debug.apk | grep routes.bin
```

**Esperado**: a coluna de método diz **`Stored`**, não `Defl:N`. Se disser `Defl`, o `noCompress`
não está a ser aplicado, a leitura por deslocamento deixa de funcionar e o ficheiro passa a ser
descomprimido inteiro para memória — **sem que nada dê erro**. É o gate mais importante desta
feature e o mais fácil de esquecer.

## 6. Validação manual — o que se vê

| # | Como forçar | Esperado |
|---|---|---|
| 1 | Lista com tráfego comercial | Voos de linha mostram `LIS → CDG`; o sentido lê-se de relance |
| 2 | Abrir o detalhe de um desses | As mesmas duas siglas, iguais às da lista |
| 3 | Uma aeronave sem rota conhecida | Aparece normalmente, **sem espaço vazio, travessão ou interrogação** |
| 4 | Um voo privado ou militar | Sem rota, sem erro |
| 5 | Apagar os dados da app e abrir | Há rotas logo à primeira, sem ir às definições |

## 7. Validação manual — a atualização

| # | Como forçar | Esperado |
|---|---|---|
| 6 | Definições | Mostra a data dos dados de rota em uso e a ação de atualizar |
| 7 | Atualizar com rede | Progresso visível, e no fim a data nova e a contagem |
| 8 | Voltar à lista logo a seguir | Rotas da tabela nova, **sem reiniciar a app** |
| 9 | Atualizar em modo de avião | Diz que não há rede, de imediato; a tabela anterior fica |
| 10 | Atualizar e matar a app a meio | Ao reabrir, a app tem tabela e funciona; a antiga ou a nova, nunca nenhuma |
| 11 | Atualizar com a lista aberta noutro sítio | A lista continua a atualizar-se durante a transferência |
| 12 | Usar a app uma sessão inteira sem tocar nas definições | **Nenhuma transferência** acontece |

O caso 10 é o que a feature toda existe para garantir, e o 12 é o que se estraga com um `init`
bem-intencionado sem dar erro nenhum.

## 8. Verificação de comportamento

**Orçamento intacto (FR-013, SC-004)** — com o Network Inspector: 15 minutos com o ecrã aberto têm
de dar exatamente os mesmos pedidos ao serviço de voos que davam antes desta feature. A tabela é
local; se aparecer um pedido a mais por ciclo, alguma coisa está a ir à rede que não devia.

**Arranque não regrediu (FR-014, SC-005)** — cronometrar cinco arranques a frio até à lista, com
permissão concedida, e comparar com o valor de antes. A tabela lê-se do APK sem ser copiada; se o
tempo subir, é sinal de que está a ser copiada ou carregada para memória.

## 9. Contra a realidade

Escolher três voos de linha visíveis na lista e confirmar a rota num site de seguimento de voos.
Lembrar que a app mostra a rota **agendada**: um voo desviado mostrará o destino previsto, e isso
não conta como erro (SC-007). Conta a rota de **outro** voo.

## Critérios de saída

- [ ] `./gradlew :app:testDebugUnitTest` verde, incluindo os 171 testes anteriores inalterados
- [ ] `./gradlew :app:lintDebug` sem erros
- [ ] `routes.bin` armazenado como `Stored` no APK
- [ ] O script produz ficheiros idênticos em duas execuções sobre os mesmos dados
- [ ] Os 12 passos manuais verificados
- [ ] Pedidos ao serviço de voos inalterados em 15 minutos (SC-004)
- [ ] Mediana do arranque medida e registada — **para servir de linha de base à feature seguinte**; SC-005 não é verificável nesta (ver secção 0)
- [ ] Pelo menos 70% dos voos comerciais com rota (SC-001) — contar em ~100 entradas
- [ ] Nenhuma rota errada em 30 voos verificados (SC-007)
- [ ] Subagente `reviewer` executado e achados tratados
