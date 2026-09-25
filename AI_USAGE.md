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
| 2026-09-23 | Implementação E0-S2 | Implementar guardrails ArchUnit e ESLint, acessibilidade automatizada, CI e evidências da story sem antecipar negócio | Regras arquiteturais com provas negativas, axe nos estados existentes e workflow de PR; gates locais e smoke integrado aprovados | O agente não executou operações Git; a execução real do workflow no GitHub permanece dependente de push e PR humanos |
| 2026-09-23 | Implementação E1-S1 | Implementar registro append-only e consulta vigente de taxas conforme a story aprovada | Módulo `currency` vertical com BigDecimal, JPA/Flyway, RFC 9457, Clock e testes PostgreSQL | ArchUnit detectou acoplamento API→domain e orientou um resultado de service; Hibernate exigiu mapeamento CHAR explícito |
| 2026-09-24 | Implementação E1-S2 | Implementar sincronização USD/BRL com provider mock local, timeout, retry seletivo, Circuit Breaker e transação curta | Adapter HTTP resiliente, endpoint `202`, WireMock no Compose, métricas/logs e testes focados | A fronteira foi separada em fetch externo e writer transacional; a auto-revisão adicionou limite de 16 KiB, validação de configuração, métricas consultáveis e provas de payload/transação |
| 2026-09-25 | Implementação E1-S3 | Implementar taxa base mensal por moeda e vigência, com seeds demonstrativos e consulta interna | Migration V3, `BaseRateQuery`, adapter JPA e provas de domínio/PostgreSQL sem endpoint ou escrita pública | A revisão removeu uma segunda porta pública intermediária e corrigiu tradução de falhas de persistência, fidelidade da origem, assinatura tipada e alcance do guardrail arquitetural. O agente não executou commits; posteriormente, a autora realizou `d635e5f feat(db): add versioned base-rate configuration`, `9501b0c feat(currency): add effective base-rate query`, `643832b test(currency): prove base-rate persistence and contracts` e `151228d docs(currency): document base-rate configuration`. |

## Problemas detectados

### Dependências frontend inicialmente vulneráveis

- **O que a IA gerou:** versões fixadas de Vite, Vitest e ESLint que produziram sete advisories, incluindo dois críticos.
- **Como foi detectado:** `npm audit --json` após gerar o primeiro lockfile.
- **Risco:** manter ferramentas de desenvolvimento vulneráveis a leitura arbitrária de arquivos e path traversal.
- **Correção aplicada:** Vite atualizado para 6.4.3, Vitest/coverage para 4.1.11, ESLint para 9.39.5 e `typescript-eslint` para 8.70.1; lockfile regenerado.
- **Teste que evita regressão:** `npm audit` passou com zero vulnerabilidades; lint, typecheck, testes e build foram reexecutados.
- **Commit:** o agente não executou o commit; a autora o realizou posteriormente em `474d0db feat(web): add API readiness status page`, que contém o lockfile e as versões corrigidas.
- **Lição:** lockfile e audit fazem parte da validação de uma fundação reproduzível.

### Healthcheck do frontend resolveu localhost por IPv6

- **O que a IA gerou:** healthcheck do nginx usando `http://localhost:8080/`.
- **Como foi detectado:** `docker inspect` mostrou `Connection refused`, enquanto os logs confirmavam nginx ativo.
- **Risco:** container funcional permanecer `unhealthy` por diferença de resolução de loopback.
- **Correção aplicada:** healthcheck passou a consultar explicitamente `127.0.0.1`.
- **Teste que evita regressão:** `docker compose ps` confirmou os três serviços como `healthy`.
- **Commit:** o agente não executou o commit; a autora o realizou posteriormente em `f59b23a build(infra): orchestrate executable application stack`, que contém o healthcheck corrigido no Compose.
- **Lição:** healthcheck deve validar a interface efetivamente escutada pela imagem, não depender da preferência IPv4/IPv6 de `localhost`.

### Gate documental varreu dependências geradas

- **O que a IA gerou:** execução do gate com `frontend/node_modules` presente no workspace.
- **Como foi detectado:** o verificador acusou 121 links quebrados pertencentes a READMEs de dependências, não ao projeto.
- **Risco:** falso negativo no gate documental e ruído que esconderia defeitos reais.
- **Correção aplicada:** a busca de Markdown passou a podar diretórios gerados por nome em qualquer profundidade (`node_modules`, `target`, `dist` e `coverage`).
- **Teste que evita regressão:** `check-docs.sh . story` passou com zero erros mantendo `node_modules` instalado.
- **Commit:** o agente não executou o commit; a autora o realizou posteriormente em `b239d74 docs(project): document executable foundation`, que contém a correção do verificador documental.
- **Lição:** gates que percorrem o repositório devem excluir artefatos gerados independentemente da profundidade.

### Skills BMAD de planejamento indisponíveis

- **O que ocorreu:** a descoberta BMAD encontrou apenas `bmad` e `bmad-build`; `bmad-prd`, `bmad-architecture` e `bmad-create-epics-and-stories` não estavam instaladas.
- **Como foi detectado:** `knowledge.py --content` sobre as raízes de skills ativas.
- **Risco:** atribuir falsamente os artefatos a workflows não executados.
- **Correção aplicada:** documentos compatíveis foram produzidos pelas skills `srm-arquitetura` e `srm-documentacao`, deixando explícita a limitação e a necessidade de aprovação/validação humana.
- **Commit:** não executado; operações Git são exclusivamente humanas neste repositório.
- **Lição:** descoberta de capacidades deve preceder o uso declarado de um método.

### Classificação inicial incompleta dos imports TypeScript

- **O que a IA gerou:** testes do `eslint-plugin-boundaries` sem um resolvedor TypeScript explícito.
- **Como foi detectado:** a prova negativa da configuração classificava a origem, mas não o destino importado.
- **Risco:** falso verde para imports relativos proibidos.
- **Correção aplicada:** inclusão de `eslint-import-resolver-typescript` e execução de fixtures permitidas e proibidas exclusivamente de teste.
- **Teste que evita regressão:** `eslint-boundaries.test.js` valida três violações e dois imports permitidos; o gate frontend completo passou.
- **Commit:** não executado; o agente apenas preparou o plano para execução manual pela autora.
- **Lição:** regras de boundary precisam testar a resolução real dos imports, não apenas a sintaxe da configuração.

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
