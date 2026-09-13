# Implementation Plan: Notificações de passagem

**Branch**: `006-overhead-notifications` | **Date**: 2026-09-13 | **Spec**: [spec.md](./spec.md)

## Summary

Quando um ciclo de fundo encontra uma aeronave acima do limiar, a app avisa — uma vez por passagem, e
só se o utilizador tiver ligado a opção. O ecrã de definições diz-lhe, antes de ligar, **que fração
das passagens esperar**, porque a resposta honesta é "poucas".

Esta feature **não constrói infraestrutura nova de fundo**: reutiliza inteira a que a 005 criou, e a
condição de agendamento já foi escrita lá como `hasAnyWidget() || notificationsEnabled` com teste
próprio, precisamente para esta feature não ter de lhe tocar.

**Mas começa por corrigir um defeito da 005** (AD-029): `ACCESS_BACKGROUND_LOCATION` está declarada no
manifesto desde o esqueleto e nunca é pedida. Sem ela, o worker nunca obtém posição com a app fechada —
que é o único cenário que interessa — e a tabela de decisão manda `retry`, produzindo um ciclo infinito
de acordar-falhar-reintentar. Sem esta correção, esta feature não pode funcionar de todo: sem posição
de fundo não há ciclo, não há candidato, não há aviso.

## Technical Context

**Dependências novas**: nenhuma. Room, WorkManager, Hilt e as permissões já estão no projeto.
**Persistência**: Room, pela primeira vez com corpo real — `SightingDao.countNotifiedSince` já existe.
**Testes**: JUnit + MockK + Turbine, JVM. Sem Robolectric, o que outra vez força as decisões para
funções puras e casos de uso com portas.
**Permissões**: `POST_NOTIFICATIONS` (API 33+) e `ACCESS_BACKGROUND_LOCATION` (API 29+), ambas pedidas
só a partir das definições, com explicação prévia.

## Constitution Check

| Princípio | Como cumpre |
|---|---|
| **I. Domínio isolado** | `OverheadNotificationSelector` é puro; `DecideOverheadNotificationUseCase` usa portas. Nenhum import de Android nem de Room no `domain`. |
| **II. Fontes atrás de interface** | Inalterado. |
| **III. Consentimento informado** | As duas permissões só a partir das definições, depois de o utilizador ligar a opção, com rationale. Recusar qualquer uma deixa a app plenamente utilizável. Notificações **desligadas de origem**. |
| **IV. Plataforma e bateria** | Nenhum worker novo, nenhum ponto de agendamento novo. Sem foreground service — e é essa proibição que fixa o teto da taxa de captura, dito ao utilizador em vez de escondido. **Corrige** um ciclo de retry infinito que a 005 tinha. |
| **V. Decisões registadas** | AD-029 a AD-034 no `CLAUDE.md` antes de haver código, mais a correção da AD-011. |
| **VI. Testar o que falha em silêncio** | A seleção do candidato e a política são funções puras; a deduplicação testa-se com duplos. O defeito da localização de fundo é o exemplo perfeito: 345 testes verdes não o apanharam porque a JVM não tem sistema de permissões. |

**Veredicto: passa.** Com uma nota que vale a pena registar: os testes desta app já não conseguem
apanhar a categoria de defeito mais provável que lhe resta — a que só existe num telefone.

## Estrutura

```
domain/
├── model/
│   ├── OverheadNotificationSelector.kt   NOVO  seleção pura do candidato
│   ├── NotificationPolicy.kt             NOVO  janelas + expectedCaptureRate()
│   └── SkySettings.kt                    +notificationThresholdDegrees, coerced() dependente
├── repository/
│   ├── NotificationPermission.kt         NOVO  porto "posso notificar?"
│   ├── LocationRepository.kt             +hasBackgroundLocationPermission()
│   └── SightingRepository.kt             record ganha `notified: Boolean` sem omissão
└── usecase/
    ├── DecideOverheadNotificationUseCase.kt  NOVO
    └── RunSkyCycleUseCase.kt             +LocationAccessMode, +BackgroundLocationUnavailable
data/
├── repository/SightingRepositoryImpl.kt  corpo real + retenção em linha
├── repository/LocationRepositoryImpl.kt  +verificação de background
└── notification/AndroidNotificationPermission.kt  NOVO
notification/OverheadNotifier.kt          corpo real, nunca lança
worker/
├── SkyRefreshDecision.kt                 +mapeamento do caso novo
└── SkyRefreshWorker.kt                   +passo de notificação
presentation/
├── permission/BackgroundLocationPermission.kt  NOVO
└── settings/                             interruptor, limiar, taxa esperada
```

## Complexity Tracking

**Duas permissões numa feature só.** Não é acidente de alcance: sem a de fundo o trabalho não obtém
posição, e sem a de notificações não há aviso. Faltando qualquer uma, a feature não existe.

**A correção da 005 entra aqui e não numa correção à parte.** Podia-se corrigir a 005 isoladamente e
só depois fazer esta. Não compensa: as duas mexem no mesmo caso de uso, na mesma tabela de decisão e no
mesmo ecrã de definições, e separá-las daria dois conjuntos de alterações a colidir no mesmo sítio para
depois serem fundidos à mão.

**Room ganha corpo pela primeira vez**, depois de ter sido rejeitado duas vezes noutras features. Não é
contradição: ali era uma consulta por chave exata sobre dados estáticos, e aqui é uma consulta com
janela temporal e retenção — o caso para que Room existe.

**O que deliberadamente não se faz:** histórico completo de avistamentos, som e vibração configuráveis,
notificações agrupadas, e qualquer tentativa de subir a taxa de captura por meios que a constituição
proíbe.
