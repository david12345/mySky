# Specification Quality Checklist: Widget de ecrã inicial

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-11
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

## Notas da validação

**Tensão central declarada antes dos requisitos.** A spec abre por dizer que o pedido — "o avião que
está no meu céu agora" — é impossível num widget, com os dois números que o provam (mínimo de 15
minutos da plataforma contra 4–5 minutos de travessia). Não é preâmbulo: FR-002, FR-003 e SC-003 são
consequência direta disso, e sem esse enquadramento pareceriam requisitos arbitrários sobre texto.

**"Glance" e "WorkManager" aparecem?** Não no corpo dos requisitos. O termo *widget* é vocabulário do
utilizador (é o que o Android lhe chama no seletor), não uma escolha técnica. "Trabalho de fundo" e
"trabalho periódico" descrevem o comportamento observável — que existe ou não existe, e é inspecionável
— sem nomear a biblioteca. O mínimo de 15 minutos está declarado como restrição da plataforma, que é o
que ele é: um facto que limita o produto, não uma decisão de implementação.

**FR-023 é uma regra de estrutura num documento de requisitos**, e está aqui de propósito: é a
constituição do projeto (princípio IV, versão 1.1.0) a impor-se ao âmbito desta feature, e a alternativa
era descobri-lo a meio da implementação.

**Três premissas com número, não com adjetivo.** A janela de frescura (5 min), a cadência por omissão
(30 min) e o custo do orçamento (12%, SC-007) são escolhas que derivam de contas explícitas na secção de
premissas. Qualquer delas pode ser contestada olhando para a conta, que é o objetivo.

**Numeração verificada**: FR-001 a FR-027 e SC-001 a SC-008, cada um definido uma vez. As repetições de
FR-004, FR-019 e FR-020 no ficheiro são referências cruzadas nos critérios de sucesso e nas premissas,
confirmadas uma a uma.

**O que fica deliberadamente de fora**: notificações de passagem (feature seguinte, mas o trabalho de
fundo criado aqui é o que elas vão usar — anotado nas premissas) e histórico de avistamentos.
