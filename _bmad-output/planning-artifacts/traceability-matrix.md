# Matriz de rastreabilidade

Esta matriz liga requisitos a entregáveis de planejamento e às verificações previstas. Testes citados ainda não existem.

| Requisito | Entregável planejado | Verificação prevista |
|---|---|---|
| RF-01, RF-02 | Épico E1; API de câmbio; ADR-0004 | Contrato HTTP, integração Testcontainers e teste do adapter mock. |
| RF-03 | Épico E2; arquitetura `pricing`; ADR-0003 | Testes unitários por Strategy e ArchUnit contra `switch`/dependências. |
| RF-04 | Story E2-S2; `POST /pricing/simulations` | Casos de fórmula e contrato; garantir ausência de persistência. |
| RF-05 | Story E2-S3; ADR-0004 | Casos BRL→USD, USD→BRL e mesma moeda com valores de referência. |
| RF-06 | Épico E3; modelo relacional; ADR-0005 | Integração transacional com rollback integral. |
| RF-07 | Stories E3-S2/E3-S3; ADR-0005 | Teste de replay para chave+payload iguais, `409 Conflict` para reutilização divergente e concorrência com exatamente um vencedor. |
| RF-08 | Story E3-S4; contrato de consulta | Teste de contrato e reconstrução de snapshots. |
| RF-09 | Épico E4; jOOQ; índices propostos | Teste de integração, paginação e `EXPLAIN ANALYZE` com 1M itens. |
| RF-10 | Épico E5; painel web | Vitest/Testing Library/MSW, axe e teste E2E do fluxo. |
| RF-11 | Story E5-S2; grid e filtros | Testes de URL, paginação server-side e estados loading/empty/error. |
| RF-12 | Épico E6; ADR-0006 | Validação OpenAPI e contratos de `ProblemDetail`. |
| RNF-01 | ADR-0003; modelo de dados | ArchUnit/grep proibitivo, testes financeiros e precisão NUMERIC. |
| RNF-02, RNF-03 | ADR-0005; Épico E3 | Testcontainers com rollback e barreira de concorrência. |
| RNF-04 | ADR-0006; Épico E6 | Testes negativos e varredura de respostas/logs. |
| RNF-05 | Épico E4; índices | Dataset reprodutível e benchmark documentado. |
| RNF-06 | Épico E5 | Medição E2E local com debounce de 300 ms. |
| RNF-07, RNF-08 | ADR-0007; Épico E6 | Testes de falha, métricas e inspeção de logs correlacionados. |
| RNF-09 | ADR-0001/0002; Épico E0 | `docker compose config/up/ps` e smoke test. |
| RNF-10 | DoD e épicos E1–E6 | Gates backend/frontend e Testcontainers. |
| RNF-11 | ADR-0001/0008 | ArchUnit, ESLint boundaries, typecheck. |
| RNF-12 | ADR-0003/0004/0005; ER | Teste de reconstrução e constraints do banco. |
| RNF-13 | ADR-0008; stories E5-S1/E5-S2; AC-A11Y-01–06; DoD | Testing Library/user-event, axe, contraste/reduced-motion e checklist manual por teclado e leitor de tela. |
