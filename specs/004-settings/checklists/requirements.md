# Specification Quality Checklist: Definições

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-08
**Feature**: [spec.md](../spec.md)

## Content Quality

- [X] No implementation details (languages, frameworks, APIs)
- [X] Focused on user value and business needs
- [X] Written for non-technical stakeholders
- [X] All mandatory sections completed

## Requirement Completeness

- [X] No [NEEDS CLARIFICATION] markers remain
- [X] Requirements are testable and unambiguous
- [X] Success criteria are measurable
- [X] Success criteria are technology-agnostic (no implementation details)
- [X] All acceptance scenarios are defined
- [X] Edge cases are identified
- [X] Scope is clearly bounded
- [X] Dependencies and assumptions identified

## Feature Readiness

- [X] All functional requirements have clear acceptance criteria
- [X] User scenarios cover primary flows
- [X] Feature meets measurable outcomes defined in Success Criteria
- [X] No implementation details leak into specification

## Notes

- **Todos os itens passam, sem clarificações em aberto.** É a primeira feature deste projeto em que
  isso acontece à primeira, e a razão é que quase tudo já estava decidido: o modelo de preferências
  existe desde o esqueleto inicial, a porta de domínio também, e a forma de o ecrã crescer ficou
  fixada na AD-017 quando a feature das rotas lhe pôs a primeira entrada.
- **FR-013 e SC-003 foram reescritos a 2026-09-10, durante o `/speckit-plan`.** A checklist original
  dizia que eram "o requisito que dá trabalho a sério", porque um raio maior gastaria mais orçamento.
  **A premissa era falsa** e foi verificada com contas: o custo de uma consulta depende da área da
  caixa envolvente, e qualquer raio utilizável — até 246 km — fica no primeiro degrau, um crédito.
  O raio não toca no orçamento.
  Os limites passaram a derivar de **utilidade geométrica**, e o que está mesmo acoplado é o raio ao
  ângulo mínimo: com os 25° de origem, nada além de ~26 km chega a ser visível. Ver `research.md`,
  D1, e a tabela de limites em `data-model.md`, toda ela com contas.
- **O teto real da app nunca esteve escrito em lado nenhum**, e agora está: 400 créditos a um por
  ciclo de 30 s dão cerca de **3h20m de ecrã aberto por dia**. Existe desde a primeira feature e
  nenhuma definição o altera.
- **A cadência ficou deliberadamente de fora**, apesar de a feature 001 ter escrito que passaria a
  ser configurável aqui. Torná-la ajustável multiplica o problema do orçamento por outra variável,
  e o ganho para o utilizador é pequeno face ao risco. Fica anotado como decisão consciente, e não
  como esquecimento de uma promessa anterior.
- **Correções do `/speckit-analyze` (2026-09-10):** um crítico e quatro menores. O crítico era uma
  lacuna de cobertura embaraçosa: a US2 construía toda a canalização das unidades — parâmetro nas
  funções, campos no estado, pontos de chamada, apresentação — e **nenhuma tarefa dava ao utilizador
  forma de escolher a unidade**. O contrato de UI já previa os eventos; faltavam as tarefas. O
  próprio teste independente da história não seria executável. Duas tarefas novas resolveram-no.
  Corrigiu-se também a falta de teste ao FR-016 (a escolha sobreviver a uma versão nova), a paridade
  de unidades no terceiro ecrã, a redação de "testes inalterados" — que é sobre comportamento e não
  sobre código, já que a US2 muda assinaturas — e a deriva entre "ângulo mínimo" e "elevação mínima".
- **SC-007 é métrica de usabilidade** e só se verifica com pessoas. Fica no quickstart como
  verificação manual, à semelhança do SC-009 da 001.
