# Architecture Spine — SRM Credit Engine

- **Status:** Proposto
- **Data:** 2026-09-23

## Decisões estruturais

| ID | Decisão proposta | Fonte |
|---|---|---|
| ARQ-01 | Monorepo com SPA, API e PostgreSQL; execução oficial por Docker Compose. | ADR-0001, ADR-0002 |
| ARQ-02 | Backend como monólito modular: `currency`, `pricing`, `settlement`, `reporting`, `shared`. | ADR-0001 |
| ARQ-03 | Hexagonal leve nos módulos com regra/integração; reporting usa controller→query em duas camadas. | ADR-0001 |
| ARQ-04 | Escrita com JPA e leitura analítica com jOOQ sobre o mesmo PostgreSQL. | ADR-0002 |
| ARQ-05 | Flyway é a única fonte de schema; Testcontainers usa PostgreSQL 16. | ADR-0002 |
| ARQ-06 | Finanças usam `BigDecimal`/`NUMERIC`, `DECIMAL128`, snapshots e arredondamento aprovado. | ADR-0003 |
| ARQ-07 | Câmbio usa convenção base/quote, conversão ao final e política explícita de expiração. | ADR-0004 |
| ARQ-08 | Liquidação usa transação, idempotência durável e unicidade por recebível. | ADR-0005 |
| ARQ-09 | API v1 usa DTOs, OpenAPI e RFC 9457 `ProblemDetail`. | ADR-0006 |
| ARQ-10 | Integração externa é protegida por timeout/retry/circuit breaker; logs e métricas são estruturados. | ADR-0007 |
| ARQ-11 | Frontend é organizado por features, TanStack Query, RHF/Zod e filtros na URL. | ADR-0008 |
| ARQ-12 | A experiência web atende WCAG 2.2 AA, prioriza HTML semântico e trata teclado, foco, anúncios, contraste e redução de movimento como requisitos arquiteturais. | ADR-0008 |

## Containers

| Container | Tecnologia | Responsabilidade |
|---|---|---|
| Web SPA | React + TypeScript + Vite | Formulário, simulação e extrato. |
| API | Java 21 + Spring Boot 3 | Câmbio, pricing, settlement, reporting e observabilidade. |
| Banco | PostgreSQL 16 | Fonte da verdade transacional e leitura analítica. |
| Provedor mock | HTTP ou adapter substituível | Demonstra sincronização e falhas de câmbio. |

Prometheus/Grafana são desejáveis para demonstração, mas a inclusão no Compose depende da aprovação do ADR-0007 e do orçamento de entrega.

## Limites dos módulos

| Módulo | Expõe | Pode depender de | Não pode depender de |
|---|---|---|---|
| `currency` | Portas de consulta e casos de uso de taxa | `shared` | pricing, settlement, reporting |
| `pricing` | Simulação e `PricingStrategy` | porta pública de currency, shared | settlement, persistence de currency |
| `settlement` | Casos de uso de liquidação/consulta | pricing, porta de currency, shared | reporting |
| `reporting` | Query paginada de extrato | shared, schema via jOOQ | services de settlement |
| `shared` | Tipos realmente transversais e clock | nada | qualquer módulo funcional |

## Regra de dependência

`api → service → domain ← persistence`. O domínio não importa Spring Web, Jackson nem JPA. Entidades de persistência não são DTOs. Ports existem onde há fronteira real de domínio, persistência ou integração, não para criar abstrações vazias.

## Estrutura lógica do frontend

`app/` compõe providers e rotas; `features/pricing`, `features/settlements`, `features/exchange-rates` contêm UI, hooks, schemas e serviços; `shared/` contém somente infraestrutura transversal. Fórmula financeira não existe no cliente.

## Arquitetura de acessibilidade do frontend

- Componentes de `shared/ui` oferecem semântica e comportamento acessível por padrão: HTML nativo antes de ARIA, foco visível, contraste AA e suporte a `prefers-reduced-motion`.
- `app/` concentra título de página e gerenciamento de foco em mudanças de rota, levando o foco ao heading/conteúdo principal.
- Formulários associam label, instrução e erro ao controle. Em submissão inválida, o primeiro campo inválido recebe foco; sucesso e falha de submissão são anunciados e o foco segue para a confirmação/alerta quando necessário.
- Resultados de simulação, carregamento e falhas assíncronas usam regiões `aria-live` estáveis, com mensagens concisas e sem anunciar cada tecla durante o debounce.
- O grid é uma tabela HTML com caption, cabeçalhos e estado de ordenação. A paginação é uma navegação nomeada com botões, página atual anunciada e operação completa por teclado.
- Cores não são a única indicação de estado; texto e foco atendem contraste WCAG 2.2 AA. Movimentos não essenciais são removidos/reduzidos conforme preferência do sistema.

## Verificações automáticas planejadas

- ArchUnit para camadas, ciclos e acesso somente a APIs públicas dos módulos.
- Testes unitários das Strategies e matemática financeira.
- Testcontainers para Flyway, repositórios, jOOQ, idempotência e concorrência.
- Validação OpenAPI e `application/problem+json`.
- ESLint boundaries/no-restricted-imports e TypeScript strict.
- Vitest/Testing Library/user-event/MSW verificam fluxos por teclado, foco, labels, títulos, regiões live, tabela e paginação; axe cobre violações automatizáveis.
- Verificação manual planejada por teclado e leitor de tela nos fluxos de simular, liquidar, filtrar/paginar e tratar erros; contraste e redução de movimento também são inspecionados.

## Deliberadamente em aberto

- As decisões listadas em `docs/adr/` permanecem `Proposto` até aprovação humana.
- Maven foi assumido por coerência com os gates do repositório, mas o `pom.xml` atual está vazio.
- Autenticação e topologia de produção não são definidas no MVP.
