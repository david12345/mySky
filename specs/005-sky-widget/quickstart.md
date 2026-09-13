# Quickstart / validação: Widget de ecrã inicial

**Feature**: `005-sky-widget`

As secções 1 a 3 correm na máquina. As 4 em diante **exigem um telefone** — e esta feature é, das cinco
até agora, a que menos se deixa validar por testes: agendamento, Doze e o ciclo de vida do widget não
existem na JVM.

## 1. Testes e lint

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Espera-se: tudo verde. Os testes desta feature concentram-se em três sítios — `SkyWidgetStateTest`
(tabela de decisão dos cinco estados), `SkyBudgetTest` (as contas do orçamento, incluindo o SC-007) e
`SkyBackgroundWorkCoordinatorTest` (agendar e cancelar).

## 2. Confirmar que os 286 testes anteriores continuam a passar

O `SkySession.refreshOnce()` foi alterado para delegar no `RunSkyCycleUseCase` (AD-028). O
comportamento observável não muda, por isso **nenhum teste anterior devia precisar de alteração**. Se
algum precisou, o refactor mudou algo que não devia — é sinal, não inconveniente.

## 3. Instalar

```bash
./gradlew :app:installDebug
```

---

## 4. O caso que só existe uma vez: o widget partido da v1.0.0

**Este é o passo mais importante e o mais fácil de esquecer**, porque só é reproduzível a partir da
versão anterior.

1. Instala a **v1.0.0** (`gh release download v1.0.0`).
2. Adiciona o widget ao ecrã inicial. Confirma que mostra apenas "mySky" e mais nada.
3. Instala esta versão **por cima**, sem desinstalar (`./gradlew :app:installDebug`).
4. **Sem tocar no widget**, espera pelo primeiro ciclo.

Esperado: o widget passa a mostrar dados reais (SC-008, FR-022). Se ficar preso no "mySky", o
`reconcile()` do arranque não correu — e nenhum teste da JVM apanha isso.

## 5. Os cinco estados

Com o widget no ecrã, provoca cada um:

| Estado | Como provocar | Esperado |
|---|---|---|
| Sem dados | limpar dados da app, adicionar widget | mensagem de que ainda não há dados, nunca uma caixa vazia |
| Recentes | tocar em atualizar | linguagem de **presente** + instante |
| Antigos | esperar mais de 5 min sem atualizar | linguagem de **passado** + instante |
| Céu vazio | pôr o ângulo mínimo em 60° e atualizar | "não havia aviões", distinto de "sem dados" |
| Sem permissão | revogar a localização nas definições do Android | explica e encaminha para a app |

**O que verificar em todos:** nunca há caixa em branco (FR-007) e o instante está sempre visível
quando há dados (FR-002).

## 6. O toque para atualizar

1. Toca em atualizar → aparece indicação de que está a atualizar (FR-018) e, em segundos, dados novos
   com instante recente (SC-004: menos de 15 s).
2. **Toca duas vezes seguidas, depressa** → só um pedido (FR-017). Confirma no Network Inspector: uma
   chamada, não duas.
3. **Modo de avião ligado**, toca em atualizar → diz que não foi possível, e os dados anteriores
   **continuam lá** com o seu instante (FR-014, cenário 3 da US2). Se a caixa esvaziar, é defeito.
4. Toca no **corpo** do widget (fora do botão) → abre a app (FR-008).

## 7. O ciclo de vida do agendamento

Com `adb`:

```bash
adb shell dumpsys jobscheduler | grep -A3 com.mysky.app
```

| Passo | Esperado |
|---|---|
| Sem widget nenhum | nenhum trabalho periódico do sky refresh |
| Adicionar o primeiro widget | aparece exatamente um |
| Adicionar um **segundo** widget | continua exatamente **um** (FR-021) |
| Remover um dos dois | continua um |
| Remover o último | desaparece (FR-020) |

**SC-005:** sem widget no ecrã, deixa a app fechada 1 hora e confirma **zero** pedidos de rede em
segundo plano.

## 8. A cadência

1. Definições → muda a cadência para 15 min. Confirma no `dumpsys` que o período mudou **sem
   reinstalar nem reiniciar** a app (FR-025).
2. Confirma que o ecrã mostra o custo: a 15 min deve dizer 96 consultas/dia e ~2h32m de ecrã; a 30 min,
   48 e ~2h56m (FR-027, SC-007).

## 9. O que o Doze faz, e que não é defeito

Deixa o telefone parado, sem carregar, várias horas. O intervalo real **vai** ser maior do que o
escolhido. Isto é o sistema a funcionar como deve, não um defeito — e é precisamente por isso que a
FR-002 exige o instante sempre visível. O que **seria** defeito: o widget afirmar presente sobre uma
observação de há três horas.

Para forçar e não esperar:

```bash
adb shell cmd jobscheduler run -f com.mysky.app <jobId>
```

## Critérios de saída

- [ ] Secções 1 a 3 verdes, sem alterar testes anteriores
- [ ] Secção 4: o widget da v1.0.0 recupera sozinho (SC-008)
- [ ] Secção 5: os cinco estados, nenhum em branco
- [ ] Secção 6: um toque = um pedido; falha não apaga dados
- [ ] Secção 7: 0 ou 1 trabalho periódico, sempre
- [ ] Secção 8: cadência aplicada a quente, custo à vista
- [ ] **Nunca** vista uma afirmação de presente sobre dados fora da janela (SC-003)
