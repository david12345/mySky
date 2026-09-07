# Quickstart — validar a lista de aviões no meu céu

Como provar que a feature funciona, do teste unitário à observação no céu real. Não contém código
de implementação: isso pertence a `tasks.md` e à fase de implementação.

## Pré-requisitos

- JDK 17 e Android SDK com plataforma API 36 (ver [README](../../README.md))
- `local.properties` com `sdk.dir`
- Para validação manual: dispositivo ou emulador API 26+, com ligação à Internet e localização
  ativa. Emulador serve, desde que se defina uma posição fictícia com tráfego aéreo real
  (Lisboa `38.7223, -9.1393` funciona bem a meio da tarde).

## 1. Camada de domínio — sem rede, sem Android

```bash
./gradlew :app:testDebugUnitTest --tests 'com.mysky.app.domain.*'
```

**Esperado**: verde. Cobre `GeoCalculator` (13 testes já existentes: zénite, antimeridiano, polos)
e `DetectOverheadFlightsUseCase` (7 já existentes) mais os novos de `ObserveSkyUseCase`.

Verificações que estes testes têm de sustentar:
- uma aeronave a 20 km e 3 000 m **não** entra na lista (≈8,5° de elevação, abaixo do limiar de 25°);
- uma aeronave em solo, sem posição, sem altitude ou com contacto há mais de 2 minutos é descartada;
- o resultado sai ordenado por elevação decrescente;
- o operador é resolvido a partir do indicativo, e um prefixo desconhecido devolve voo sem operador
  em vez de falhar.

## 2. Camadas de dados e apresentação

```bash
./gradlew :app:testDebugUnitTest
```

**Esperado**: verde, incluindo:
- **parsing do vetor de estado** — arrays curtos, `null` em qualquer posição, `states: null`,
  indicativo com espaços à direita, e a ordem longitude(5)/latitude(6) que é a troca mais fácil de
  fazer sem dar por ela;
- **repositório** — deduplicação por `icao24` entre duas caixas, e cada erro de rede a traduzir-se
  na variante certa de `SkyError`;
- **diretório de operadores** — prefixo conhecido, prefixo desconhecido, matrícula de aviação
  privada (`CS-DHA`), indicativo ausente, asset em falta;
- **ViewModel em tempo virtual** — os nove invariantes de
  [contracts/main-screen-ui.md](./contracts/main-screen-ui.md), com destaque para: sem pedidos
  concorrentes, o ciclo para quando não há subscritores, e uma falha não limpa a lista.

## 3. Compilar e instalar

```bash
./gradlew :app:assembleDebug :app:lintDebug
./gradlew :app:installDebug
```

**Esperado**: build verde e lint sem erros (avisos de "versão mais recente disponível" são
esperados — ver AD-006 no `CLAUDE.md`).

## 4. Validação manual — os seis estados

Cada um destes tem de produzir um ecrã distinto e compreensível.

| # | Como forçar | Esperado |
|---|---|---|
| 1 | Primeira abertura, permissão por conceder | Explicação do uso da localização **antes** de qualquer diálogo do sistema (FR-001) |
| 2 | Recusar a permissão | Explicação e ação para conceder; nunca ecrã vazio (FR-005) |
| 3 | Recusar duas vezes (recusa permanente) | A ação leva às definições do sistema (FR-005) |
| 4 | Conceder, num local com tráfego | Lista com indicativo, operador, altitude, velocidade, distância; ordenada com o mais alto no céu em primeiro |
| 5 | Modo de avião com a lista já preenchida, aguardar um ciclo | Lista **mantém-se** visível, marcada como possivelmente desatualizada (FR-025) |
| 6 | Local sem tráfego, ou raio pequeno de madrugada | Mensagem de céu vazio, sem aspeto de erro (FR-023) |

## 5. Verificações de comportamento

**Atualização automática (FR-016, FR-018)** — deixar o ecrã aberto e parado 2 minutos: a marca
temporal nunca deve indicar mais de ~40 s, e a lista muda sozinha à medida que as aeronaves
atravessam o céu.

**Paragem em segundo plano (FR-019, SC-007)** — com o ecrã aberto, ir ao Android Studio →
*App Inspection* → *Network Inspector*, mandar a app para segundo plano e observar: nenhum pedido
novo passados 10 segundos.

**Sem pedidos concorrentes (FR-020)** — puxar para atualizar repetidamente e depressa: no Network
Inspector nunca devem aparecer dois pedidos sobrepostos, e o relógio dos 30 s reinicia a cada
refresh manual.

**Tempo até à primeira lista (SC-001)** — com a permissão já concedida, matar a app e cronometrar
desde o toque no ícone até a lista aparecer, cinco vezes numa ligação móvel típica. A mediana tem
de ficar abaixo de 5 segundos. Se falhar, o suspeito habitual é a obtenção da posição, não a rede.

**Cobertura da tabela de operadores (SC-004)** — recolher 100 entradas reais da lista com
indicativo de voo comercial e contar quantas mostram operador. Tem de ser pelo menos 95. Abaixo
disso, a tabela precisa de mais registos, não o código.

**Rate limiting (FR-021)** — difícil de forçar sem gastar o orçamento; validar por teste unitário
com uma resposta 429 sintética, verificando que a espera seguinte é `max(30 s, retryAfter)` e que
a app não reintenta de imediato.

## 6. Verificação contra o céu real (SC-002, SC-003)

O teste que nenhuma suite substitui: sair à rua com a app aberta, num sítio com vista
desimpedida, e confirmar que os aviões visíveis a olho nu estão na lista e que o de maior elevação
é o que está mais alto no céu.

Junto a um aeroporto, confirmar que **nenhuma** aeronave em solo aparece — é a verificação direta
de SC-003 e o erro mais provável de todo o filtro.

## Critérios de saída

Validação em dispositivo feita a 2026-09-07.

- [X] `./gradlew :app:testDebugUnitTest` verde — 131 testes
- [X] `./gradlew :app:lintDebug` sem erros — 26 avisos, 0 erros
- [X] Os seis estados da secção 4 verificados manualmente
- [X] Verificação contra o céu real feita — a lista corresponde ao que se vê
- [X] Subagente `reviewer` executado e achados tratados (T054)
- [ ] Mediana do tempo até à primeira lista abaixo de 5 s (SC-001) — **não cronometrado**
- [ ] Pelo menos 95 em 100 voos comerciais com operador identificado (SC-004) — **não contado**
- [ ] Nenhuma atividade de rede após 10 s em segundo plano — **não instrumentado**
- [ ] Ausência de aeronaves em solo confirmada junto a um aeroporto (SC-003) — **não confirmada
      nesse cenário**; o filtro está coberto por teste unitário

Os quatro por marcar não revelaram problema — não foram medidos. SC-001 e SC-004 são limiares
numéricos: ficam por verificar até alguém os cronometrar e contar. Se a app vier a parecer lenta a
arrancar, ou se faltarem nomes de companhia com frequência, é aqui que a dívida está.
