# Specification Quality Checklist: Notificações de passagem

**Created**: 2026-09-13 | **Feature**: [spec.md](../spec.md)

## Content Quality

- [X] No implementation details (languages, frameworks, APIs)
- [X] Focused on user value and business needs
- [X] Written for non-technical stakeholders
- [X] All mandatory sections completed

## Requirement Completeness

- [X] No [NEEDS CLARIFICATION] markers remain
- [X] Requirements are testable and unambiguous
- [X] Success criteria are measurable
- [X] Success criteria are technology-agnostic
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

**A tensão está declarada antes dos requisitos, com a tabela que a prova.** Esta feature perde entre
81% e 98% das passagens, e não há desenho que o resolva sem um serviço em primeiro plano permanente
que a constituição proíbe. Escondê-lo produziria uma app que parece avariada; declará-lo torna a FR-010
(mostrar a taxa esperada) um requisito óbvio em vez de um enfeite.

**A diferença face à 005 é o tipo de erro.** No widget o risco era *mentir* — afirmar presente sobre
uma observação velha. Aqui o risco é só *calar* — quando o aviso dispara, o avião está mesmo lá. É
isso que torna a feature defensável apesar da taxa de captura, e é a razão de a FR-008 (nunca afirmar
presença sem dizer quando) continuar a existir mesmo assim: um aviso atrasado pelo Doze pode chegar
horas depois.

**O limiar por omissão contraria a intuição, e é de propósito.** 30° e não 60°: a 60° a janela é de 55
segundos, menos tempo do que o utilizador leva a tirar o telefone do bolso e olhar para cima. A 30° são
166 segundos e a taxa de captura duplica. O número saiu da conta, não do gosto.

**FR-012 é uma restrição entre duas definições**, e é diferente do que a AD-021 rejeitou na 004: ali
recusou-se estreitar o intervalo de um controlo em função do outro, porque o limite mover-se-ia debaixo
do dedo. Aqui não é um limite que se move — é uma incoerência que não faz sentido nenhum permitir: ser
avisado de uma aeronave que a deteção descarta antes de chegar aqui. Vale a pena confirmar no
`/speckit-plan` que a correção não reintroduz o problema que a AD-021 evitou.

**Numeração verificada**: FR-001 a FR-014 e SC-001 a SC-006, cada um definido uma vez.

**Fora de âmbito**: histórico de avistamentos completo (só se gravam as aeronaves avisadas), som e
vibração configuráveis, e qualquer tentativa de aumentar a taxa de captura por meios que a constituição
proíbe.
