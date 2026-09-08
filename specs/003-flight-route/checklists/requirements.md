# Specification Quality Checklist: Origem e destino do voo

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-07
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

- Items marked incomplete require spec updates before `/speckit-clarify` or `/speckit-plan`
- **Todos os itens passam.** A clarificação de FR-008 foi resolvida a 2026-09-07: a rota é a
  **agendada** do número de voo, não a real do dia. O SC-007 foi reescrito em consequência — sem
  isso, o critério exigiria uma exatidão que a decisão tomada torna impossível por desenho, e a
  feature nasceria com um critério de sucesso que nunca poderia cumprir.
- **Âmbito alargado a 2026-09-07**: a app passa a poder atualizar a tabela de rotas a pedido do
  utilizador, a partir das definições (US4, FR-016 a FR-023). Isto resolve a única parte da app que
  se degradava sozinha com o tempo, mas traz uma dependência que não existia: **o ecrã de definições
  é hoje um esqueleto vazio**, e esta feature cria nele a primeira entrada real. Ficou escrito nas
  Assunções que não constrói a feature de definições.
- **FR-020 é o requisito que protege esta adição**: uma atualização interrompida não pode deixar a
  app pior do que estava. Uma tabela meio escrita é pior do que uma tabela velha, e é o defeito mais
  provável de todo o mecanismo de atualização.
- **Correções do `/speckit-analyze` (2026-09-08):** um crítico e cinco outros. O crítico era de
  desenho: o plano mandava ler a tabela com `RandomAccessFile` **e** ler o asset diretamente do
  APK, o que é impossível — um asset só é acessível por descritor com deslocamento base dentro do
  zip. Resolvido com uma abstração de leitura com duas implementações. Corrigiram-se também
  referências a FR desatualizadas (a renumeração da spec tinha deslocado três), acrescentou-se a
  medição da linha de base do arranque como primeira tarefa de todas, a recusa de uma tabela nova
  com menos registos (SC-009), e a proibição de dar a rota por confirmada no ecrã (FR-009).
- **SC-005 só é verificável se a linha de base for medida antes.** É a T001, e é a primeira tarefa
  da feature por essa razão: os 7,6 MB do asset apagam para sempre a possibilidade de a medir.
- **SC-001 (70% de cobertura) é um número por confirmar.** Foi escolhido como limiar de utilidade —
  abaixo disso a rota aparece tão raramente que o utilizador deixa de contar com ela — mas nenhuma
  fonte foi ainda avaliada. Se o `/speckit-plan` concluir que nenhuma origem viável o atinge, é o
  critério que tem de mudar, não a implementação a ser forçada.
- **SC-007 é o critério que protege a US2**: distingue explicitamente rota em falta (aceitável) de
  rota errada (inaceitável). Sem essa distinção, uma fonte que preenchesse tudo por aproximação
  pareceria melhor do que uma honesta.
- Verificação feita: nenhuma menção a formato de dados, fonte concreta, biblioteca ou endpoint no
  corpo da especificação. FR-014 é de privacidade, não de implementação.
