# Specification Quality Checklist: Lista de aviões no meu céu

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-06
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- Items marked incomplete require spec updates before `/speckit-clarify` or `/speckit-plan`

### Iteração 1 — 2026-09-06

Dois itens por fechar, ambos com a mesma causa: a fonte de voos em uso devolve vetores de posição,
não dados comerciais do voo.

- **"No [NEEDS CLARIFICATION] markers remain"** — falha. Restam 2 marcadores:
  - **FR-011** (companhia aérea): a fonte devolve o indicativo de voo, cujo prefixo de 3 letras
    identifica o operador, mas não o nome da companhia. Traduzir prefixo em nome exige uma tabela
    de operadores. Questão Q1 colocada ao utilizador.
  - **FR-012** (origem e destino): a fonte não devolve rota nos dados de posição em tempo real.
    Obter origem/destino exige outra fonte ou um conjunto de dados de rotas. Questão Q2 colocada
    ao utilizador.
- **"All functional requirements have clear acceptance criteria"** — falha por consequência: FR-011
  e FR-012 não têm critério de aceitação enquanto o âmbito não estiver decidido.

Os restantes 14 dos 16 itens passam. O resto da especificação está pronto para `/speckit-clarify` ou
`/speckit-plan` assim que Q1 e Q2 estiverem respondidas.

### Iteração 2 — 2026-09-06

Q1 e Q2 respondidas pelo utilizador. Os 15 itens passam.

- **Q1 → opção A**: o nome do operador é derivado do prefixo do indicativo de voo através de uma
  tabela incluída na app, sem pedidos de rede adicionais. FR-011 reescrito com esse critério;
  FR-012 passou a cobrir o comportamento quando o prefixo é desconhecido ou não há indicativo.
  Acrescentados: cenário de aceitação na história 1, dois casos-limite (aviação privada e tabela
  desatualizada), a entidade "Operador aéreo", a suposição sobre a tabela e o SC-004 de cobertura
  (>= 95% dos voos comerciais com operador identificado).
- **Q2 → opção C**: origem e destino saem desta feature e passam para o ecrã de detalhe, obtidos
  apenas para a aeronave que o utilizador escolher. Registado nas suposições e em Out of Scope.
  SC-005 a SC-009 renumerados.

Especificação pronta para `/speckit-plan`.
