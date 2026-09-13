# Quickstart / validação: Notificações de passagem

## 1. Testes e lint

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug
```

## 2. O que **não** se consegue testar aqui, e é o que mais interessa

A falha da localização de segundo plano (AD-029) não existe na JVM: não há sistema de permissões para
a bloquear. Foi por isso que 345 testes verdes não a apanharam na 005. **As secções seguintes são a
única forma de verificar esta feature.**

## 3. A correção da 005: o widget num telefone moderno

1. Instala num aparelho com **Android 10 ou superior**.
2. Dá a localização normal ("Durante a utilização"), **sem** a de segundo plano.
3. Adiciona o widget. Fecha a app completamente.
4. Espera por um ciclo (ou força com `adb shell cmd jobscheduler run -f com.mysky.app <jobId>`).

**Esperado:** o widget diz que precisa de permissão e encaminha para a app — **não** fica em "ainda sem
dados" nem entra em ciclo de tentativas. Confirma no `logcat` que **não** há `retry` repetido.

Este passo é o que prova que o defeito da 005 está corrigido.

## 4. Conceder a localização de segundo plano

1. Definições da app → ligar notificações → seguir o encaminhamento.
2. No Android 11+ isto leva às definições do sistema, onde se escolhe "Permitir sempre".
3. Voltar à app e confirmar que o interruptor fica ligado.

**Se recusares:** a app tem de continuar plenamente utilizável, com o ecrã principal a funcionar
normalmente. Um ecrã preso ou vazio aqui é defeito.

## 5. Receber um aviso

Com tudo concedido e o limiar no valor de origem, espera por uma aeronave acima de 30°. **Isto pode
demorar** — a taxa de captura esperada é de 9% a 18% das passagens, e o ecrã de definições diz-te qual.

Para não esperar: baixa o limiar para o mínimo e força um ciclo com um avião no céu.

| Verificar | Esperado |
|---|---|
| Conteúdo | indicativo, companhia quando conhecida, elevação |
| Toque, **app fechada à força** | abre o detalhe daquela aeronave |
| Toque, **app em segundo plano e viva** | abre o detalhe daquela aeronave — este é o caso comum, porque o aviso nasce de um ciclo que a app acabou de correr, e é o que a revisão apanhou partido |
| Cinco aeronaves acima do limiar | **um** aviso, não cinco |
| Dois ciclos seguidos com a mesma aeronave | **um** aviso |
| Ciclo falhado | nenhum aviso, e nenhum aviso de erro |

## 6. Revogar a permissão pelas costas da app

Com notificações ligadas, vai às definições do Android e desliga as notificações da app. Volta ao ecrã
de definições.

**Esperado:** o **próprio interruptor** aparece desligado, e não só o aviso por baixo dele (FR-014).
Olhar para o interruptor é o passo, e é onde a revisão encontrou o defeito: o estado derivado estava
certo e o Compose estava ligado ao campo errado. E a
intenção guardada não se perde: voltar a conceder no sistema deve repor o funcionamento **sem** ter de
tocar outra vez no interruptor.

## 7. Desligar

Desliga as notificações e remove o widget. Confirma que o trabalho periódico desaparece:

```bash
adb shell dumpsys jobscheduler | grep -A3 com.mysky.app
```

## Critérios de saída

- [ ] Secção 3: o widget explica em vez de reintentar — **a prova de que a 005 está corrigida**
- [ ] Secção 4: recusar a permissão deixa a app utilizável
- [ ] Secção 5: um aviso por ciclo, um por passagem, e abre o detalhe certo
- [ ] Secção 6: revogar pelas costas não deixa o ecrã a mentir
- [ ] Secção 7: sem widget e sem notificações, zero trabalho agendado
