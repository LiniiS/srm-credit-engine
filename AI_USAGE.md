# Uso de IA no desenvolvimento

## Ferramentas e processo

- Codex utilizado para planejamento, implementação, testes e revisão.
- BMAD utilizado para PRD, arquitetura, épicos e stories.
- Skills do projeto utilizadas para arquitetura, Java, React, documentação e Git.
- Todo código gerado deve passar por revisão humana, testes e gates automatizados.
- Commits e demais operações Git são executados manualmente pelo autor.

## Prompts estratégicos

| Data | Objetivo | Prompt resumido | Resultado | Ajustes manuais |
|---|---|---|---|---|
| 2026-09-23 | Planejamento inicial | Analisar o desafio e criar requisitos, rastreabilidade, PRD, arquitetura, dados, APIs, C4/ER, ADRs e stories sem implementar | Artefatos propostos em `_bmad-output/planning-artifacts/` e `docs/` | Aprovação humana pendente; skills BMAD específicas de planejamento não estavam instaladas |
| 2026-09-23 | Consistência de idempotência e gate documental | Uniformizar replay/409 e tornar o gate sensível a story, release, Compose e migrations Flyway | Planejamento corrigido e `check-docs.sh` story aprovado com avisos esperados | Nenhum código de aplicação alterado; ADR-0005 permaneceu Proposto |
| 2026-09-23 | Auditoria de acessibilidade | Auditar ADR frontend, arquitetura, PRD, stories, critérios e DoD contra WCAG 2.2 AA | Requisitos de semântica, teclado, foco, anúncios, tabela, contraste, movimento e testes tornados verificáveis e rastreáveis | Nenhum código implementado; ADR-0008 permaneceu Proposto |

## Problemas detectados

### Skills BMAD de planejamento indisponíveis

- **O que ocorreu:** a descoberta BMAD encontrou apenas `bmad` e `bmad-build`; `bmad-prd`, `bmad-architecture` e `bmad-create-epics-and-stories` não estavam instaladas.
- **Como foi detectado:** `knowledge.py --content` sobre as raízes de skills ativas.
- **Risco:** atribuir falsamente os artefatos a workflows não executados.
- **Correção aplicada:** documentos compatíveis foram produzidos pelas skills `srm-arquitetura` e `srm-documentacao`, deixando explícita a limitação e a necessidade de aprovação/validação humana.
- **Commit:** não executado; operações Git são exclusivamente humanas neste repositório.
- **Lição:** descoberta de capacidades deve preceder o uso declarado de um método.

<!-- Para cada ocorrência real, utilizar:

### Título

- **O que a IA gerou:**
- **Como foi detectado:**
- **Risco:**
- **Correção aplicada:**
- **Teste que evita regressão:**
- **Commit:**
- **Lição:**

-->

## Análise crítica

### Onde a IA economizou tempo

- A estrutura BMAD e as skills SRM aceleraram a decomposição rastreável de requisitos, decisões e verificações.

### Onde a IA atrapalhou

- A ausência das skills BMAD específicas de planejamento exigiu elaboração compatível sem poder executar seus validadores/workflows próprios.

### O que foi feito manualmente

- Aprovação das decisões arquiteturais.
- Aceite dos ADRs.
- Revisão do código e das regras financeiras.
- Execução dos commits e demais operações Git.
