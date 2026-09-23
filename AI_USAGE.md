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
| 2026-09-23 | Consolidação após revisão humana | Propagar a aprovação formal dos nove ADRs e das decisões financeiras, arquiteturais, operacionais, de acessibilidade e Git | ADR-0001 a ADR-0009 promovidos para Aceito; Architecture Spine, PRD, stories e riscos reconciliados | A responsável revisou e aprovou as decisões; o Codex apenas registrou e verificou consistência, sem implementar ou operar Git |
| 2026-09-23 | Implementação E0-S1 | Implementar integralmente o esqueleto executável com Spring Boot, Flyway/Testcontainers, React/Vite, containers não-root, Compose, testes e documentação | Backend, frontend e PostgreSQL integrados; gates e smoke path executados | Toolchain frontend atualizada após audit; healthcheck IPv4 corrigido; porta PostgreSQL externa alterada apenas no teste por conflito local |

## Problemas detectados

### Dependências frontend inicialmente vulneráveis

- **O que a IA gerou:** versões fixadas de Vite, Vitest e ESLint que produziram sete advisories, incluindo dois críticos.
- **Como foi detectado:** `npm audit --json` após gerar o primeiro lockfile.
- **Risco:** manter ferramentas de desenvolvimento vulneráveis a leitura arbitrária de arquivos e path traversal.
- **Correção aplicada:** Vite atualizado para 6.4.3, Vitest/coverage para 4.1.11, ESLint para 9.39.5 e `typescript-eslint` para 8.70.1; lockfile regenerado.
- **Teste que evita regressão:** `npm audit` passou com zero vulnerabilidades; lint, typecheck, testes e build foram reexecutados.
- **Commit:** não executado; operações Git são exclusivamente humanas neste repositório.
- **Lição:** lockfile e audit fazem parte da validação de uma fundação reproduzível.

### Healthcheck do frontend resolveu localhost por IPv6

- **O que a IA gerou:** healthcheck do nginx usando `http://localhost:8080/`.
- **Como foi detectado:** `docker inspect` mostrou `Connection refused`, enquanto os logs confirmavam nginx ativo.
- **Risco:** container funcional permanecer `unhealthy` por diferença de resolução de loopback.
- **Correção aplicada:** healthcheck passou a consultar explicitamente `127.0.0.1`.
- **Teste que evita regressão:** `docker compose ps` confirmou os três serviços como `healthy`.
- **Commit:** não executado; operações Git são exclusivamente humanas neste repositório.
- **Lição:** healthcheck deve validar a interface efetivamente escutada pela imagem, não depender da preferência IPv4/IPv6 de `localhost`.

### Gate documental varreu dependências geradas

- **O que a IA gerou:** execução do gate com `frontend/node_modules` presente no workspace.
- **Como foi detectado:** o verificador acusou 121 links quebrados pertencentes a READMEs de dependências, não ao projeto.
- **Risco:** falso negativo no gate documental e ruído que esconderia defeitos reais.
- **Correção aplicada:** a busca de Markdown passou a podar diretórios gerados por nome em qualquer profundidade (`node_modules`, `target`, `dist` e `coverage`).
- **Teste que evita regressão:** `check-docs.sh . story` passou com zero erros mantendo `node_modules` instalado.
- **Commit:** não executado; operações Git são exclusivamente humanas neste repositório.
- **Lição:** gates que percorrem o repositório devem excluir artefatos gerados independentemente da profundidade.

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
