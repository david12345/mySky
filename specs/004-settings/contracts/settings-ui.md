# Contrato de UI — ecrã de definições

O que o ecrã observa, o que envia, e as regras cuja violação não produz erro visível.

## Estado observado

```
SettingsUiState(
    routeTableGeneratedAtEpochSeconds: Long?,   // da 003, inalterado
    routeCount: Int,                            // da 003, inalterado
    updateState: RouteUpdateState,              // da 003, inalterado
    settings: SkySettings,                      // desta feature
)
```

Um único estado, estendido e não substituído (AD-017). Exposto num `StateFlow`, consumido com
`collectAsStateWithLifecycle()`.

## Eventos enviados pela UI

| Evento | Quando | Efeito |
|---|---|---|
| `onRadiusChanged(metros)` | o utilizador **larga** o controlo | grava e acorda o laço |
| `onMinElevationChanged(graus)` | idem | grava e acorda o laço |
| `onMinAltitudeChanged(metros)` | idem | grava e acorda o laço |
| `onDistanceUnitChanged` | escolha de unidade | grava, **não** acorda o laço |
| `onAltitudeUnitChanged` | idem | grava, **não** acorda o laço |
| `onResetToDefaults` | ação de repor | grava os valores de origem e acorda o laço |
| `onUpdateRouteTable` | da 003 | inalterado |

**"Quando o utilizador larga" não é detalhe de estilo.** Gravar a cada movimento do cursor faria
dezenas de escritas e dezenas de pedidos à fonte de voos num único arrasto. O valor em trânsito vive
no controlo; a escrita acontece no fim (AD-019).

**As unidades não acordam o laço.** Não afetam a deteção — pedir dados novos por causa delas
gastaria um crédito para obter exatamente as mesmas aeronaves.

## Invariantes verificadas por teste

| # | Invariante | Requisito |
|---|---|---|
| 1 | Uma alteração de critério grava **e** acorda o laço | FR-006, SC-001 |
| 2 | Uma alteração de unidade grava e **não** acorda o laço | AD-019 |
| 3 | Arrastar um controlo produz **uma** escrita, não uma por movimento | AD-019 |
| 4 | Repor devolve todos os critérios aos valores de origem numa só ação | FR-005 |
| 5 | Cada valor é apresentado na unidade escolhida, no próprio ecrã de definições | FR-010 |
| 6 | Um valor no seu valor de origem é indicado como tal | FR-012 |
| 7 | Com o raio acima do alcance útil do ângulo escolhido, o ecrã di-lo | FR-014 |
| 8 | O intervalo de um controlo **não** muda quando o outro é alterado | AD-021 |
| 9 | Nenhum controlo é ligado ao intervalo do trabalho periódico | FR-015 |

A invariante 3 é a que protege o orçamento, e falha em silêncio: um `Slider` ligado ao evento errado
funciona perfeitamente e gasta o dia de créditos num arrasto.

A invariante 8 é o contrário do que a intuição sugere. Estreitar o intervalo do raio quando o ângulo
sobe pareceria ajudar, mas move o limite debaixo do dedo do utilizador e tira sentido a "valor de
origem". A incoerência avisa-se (invariante 7), não se impede.

A invariante 9 existe porque o campo está na mesma classe que os outros e pertence a outra feature.

## Onde as preferências entram no laço

O ecrã **não** entrega critérios a ninguém. A `SkySession` lê-os da porta de preferências no início
de cada ciclo (AD-018); o ecrã limita-se a gravar e a avisar que algo mudou.

É isso que garante o FR-017 pela forma dos dados: cada ciclo trabalha com um snapshot imutável do
princípio ao fim, e uma lista com critérios misturados é estruturalmente impossível — não há nada
para alguém se lembrar de fazer.
