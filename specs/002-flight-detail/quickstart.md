# Quickstart — validar o detalhe de um voo

Como provar que a feature funciona, do teste unitário à observação no céu real. Não contém código
de implementação.

## Pré-requisitos

- Os mesmos da 001 (JDK 17, SDK com API 36, `local.properties` com `sdk.dir`).
- Para validação manual: dispositivo ou emulador API 26+, com Internet e localização ativa, num
  local e hora com tráfego aéreo — sem aviões na lista não há detalhe para abrir.

## 1. Domínio — a presença, sem rede e sem Android

```bash
./gradlew :app:testDebugUnitTest --tests 'com.mysky.app.domain.*'
```

**Esperado**: verde, incluindo a tabela de transições de
[data-model.md](./data-model.md#transições-de-presença). Os casos que não podem faltar:

- observado ⇒ `Current`;
- **não observado com ciclo bem sucedido ⇒ `LeftSky`**, com o instante da última observação;
- **ciclo falhado ⇒ estado inalterado** — uma falha não faz um avião sair do céu;
- `LeftSky` a reaparecer ⇒ `Current`;
- `LeftSky` que continua ausente ⇒ o instante da última observação **não** se mexe.

As duas linhas a negrito são a feature. Se só um destes testes existisse, seria o terceiro: é o que
separa informar de mentir.

## 2. Sessão partilhada — um pedido, dois ecrãs

```bash
./gradlew :app:testDebugUnitTest --tests 'com.mysky.app.presentation.sky.*'
```

**Esperado**: verde, cobrindo as nove invariantes de [sky-session.md](./contracts/sky-session.md).
Em tempo virtual, com dois coletores:

- dois coletores em simultâneo ⇒ **um** pedido por ciclo, não dois;
- a transição lista → detalhe (entra o segundo coletor, sai o primeiro) ⇒ nenhum pedido extra e
  nenhum reinício do laço;
- zero coletores durante mais de 5 s ⇒ nenhum pedido novo;
- os dois coletores recebem a mesma sequência de emissões.

## 3. Ecrã de detalhe

```bash
./gradlew :app:testDebugUnitTest
```

**Esperado**: verde, incluindo as oito invariantes de
[flight-detail-ui.md](./contracts/flight-detail-ui.md), e em particular o teste que compara os
valores formatados do detalhe com os que a `FlightRow` produz para o mesmo voo — é o que sustenta
SC-002.

Os testes da 001 têm de continuar todos verdes. O `MainUiState` muda de forma nesta feature, mas a
tabela de precedência do ecrã principal **não** muda: se um teste da 001 falhar, a alteração está
errada, não o teste.

## 4. Compilar e instalar

```bash
./gradlew :app:assembleDebug :app:lintDebug
./gradlew :app:installDebug
```

## 5. Validação manual — o percurso

| # | Como forçar | Esperado |
|---|---|---|
| 1 | Tocar numa entrada da lista | Detalhe da aeronave certa, já preenchido, sem passar por um estado de carregamento (SC-001) |
| 2 | Comparar linha da lista e detalhe | Distância, altitude e velocidade **iguais ao dígito**, nas mesmas unidades (FR-013, SC-002) |
| 3 | Deixar o detalhe aberto 2 minutos | Os números mexem-se sozinhos; a marca temporal nunca passa dos ~40 s |
| 4 | Puxar para atualizar no detalhe | Renova de imediato; ao voltar, a lista também está renovada |
| 5 | Voltar à lista | Regressa à posição em que estava, pelo controlo do ecrã **e** pelo gesto do sistema |
| 6 | Rodar o ecrã com o detalhe aberto | Os dados mantêm-se, sem novo carregamento visível |
| 7 | Modo de avião com o detalhe aberto, esperar um ciclo | Os dados mantêm-se, marcados como possivelmente desatualizados, com a causa; **não** aparece "saiu do céu" |
| 8 | Ficar com o detalhe aberto até o avião se afastar | Aviso explícito de que saiu do céu, com o instante da última observação; os valores continuam lá, marcados |
| 9 | Matar a app e reabrir com o detalhe no topo | Nunca apresenta dados de antes como atuais |

O caso 7 é o que mais provavelmente falha, e a falha é silenciosa: se a implementação confundir
"não veio na lista" com "não houve lista", perder a rede passa a anunciar que o avião saiu do céu.

## 6. Verificação de comportamento — o orçamento de pedidos

**Um pedido, não dois (FR-017, SC-008)** — com o Network Inspector do Android Studio aberto:
navegar para o detalhe e ficar lá 2 minutos. O número de pedidos tem de ser o mesmo que ficar na
lista o mesmo tempo — quatro, a 30 s. Se forem oito, a sessão partilhada não está partilhada.

**Paragem em segundo plano (FR-016, SC-005)** — com o detalhe aberto, mandar a app para segundo
plano: nenhum pedido novo passados 10 segundos. É aqui que se apanha a subscrição feita com
`launch` em vez de `stateIn`.

## 7. Verificação contra o céu real

Escolher um avião visível a olho nu, abrir o detalhe e confirmar que a direção indicada aponta
para onde ele está de facto, e que o rumo corresponde ao sentido em que se vê a mover. São os dois
valores mais fáceis de trocar e os únicos que o céu desmente de imediato.

## Critérios de saída

- [ ] `./gradlew :app:testDebugUnitTest` verde, incluindo os testes da 001 inalterados
- [ ] `./gradlew :app:lintDebug` sem erros
- [ ] Os nove passos da secção 5 verificados manualmente
- [ ] Mesmo número de pedidos com o detalhe aberto e com a lista aberta (SC-008)
- [ ] Nenhuma atividade de rede 10 s depois de ir para segundo plano com o detalhe aberto
- [ ] Direção e rumo confirmados contra o céu real
- [ ] Subagente `reviewer` executado e achados tratados
