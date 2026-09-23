# Riscos, premissas e decisões consolidadas

## Premissas usadas no planejamento

| ID | Premissa | Impacto se falsa |
|---|---|---|
| P-01 | Maven será usado, coerente com os gates prescritos. | Ajustar build, CI e documentação. |
| P-02 | BRL e USD têm duas casas decimais no MVP. | Generalizar escala/formatadores e casos de teste. |
| P-03 | Cada recebível é identificado por `(assignor_id, external_id)`. | Decisão aceita; alteração exige novo ADR ou substituição do ADR-0005. |
| P-04 | Taxa base é mensal, por moeda e versionada por vigência; seeds iniciais são fictícios. | Decisão aceita; fonte oficial futura exige revisão. |
| P-05 | Autenticação e autorização estão fora do MVP e são limitação conhecida. | Inclusão exige novo épico e modelo de autorização. |
| P-06 | Relatório usa consistência forte do mesmo PostgreSQL. | Redesenhar SLA/arquitetura de leitura. |
| P-07 | Uma implantação local é suficiente; escala de 1M tx/min é somente design. | Escopo e arquitetura deixam de caber no prazo. |

## Riscos

| ID | Prob. | Impacto | Risco | Mitigação proposta |
|---|---:|---:|---|---|
| R-01 | Baixa | Alto | Implementação diverge das convenções financeiras aceitas. | ADR-0003/0004, casos de referência ACT/30 e testes de calendário/câmbio. |
| R-02 | Média | Alto | Optimistic locking isolado permite dupla liquidação com agregados distintos. | Constraint única por recebível + testes concorrentes adversos. |
| R-03 | Média | Alto | Prazo de 3–4 dias não comporta todo hardening sênior/especialista. | Priorizar cortes verticais; escala/EDA ficam documentais; Prometheus/Grafana ficam em profile opcional. |
| R-04 | Média | Médio | Meta de extrato não é reproduzível sem protocolo documentado. | Benchmark com 1 milhão de registros, p95 ≤ 300 ms e registro de hardware, seed, warm-up e plano. |
| R-05 | Baixa | Alto | Taxa velha ou invertida causa perda financeira. | Clock controlado, nomenclatura base/quote, testes de ambos os sentidos e bloqueio de expiração. |
| R-06 | Média | Médio | Dependência de matemática decimal ou jOOQ aumenta tempo de build/configuração. | Spike curto na story E0/E2 e isolamento atrás de API interna. |
| R-07 | Média | Médio | Diretório vazio `supabase/` induz implementação proibida/confunde avaliação. | Remoção humana aprovada antes da implementação; nenhum arquivo foi removido agora. |
| R-08 | Média | Médio | Skills BMAD de planejamento não estão instaladas, reduzindo validação automatizada do método. | Artefatos compatíveis foram produzidos; instalar/rodar validadores BMAD é decisão do usuário. |
| R-09 | Média | Médio | Repositório inteiro aparece untracked, dificultando distinguir baseline de mudanças. | Usuário deve revisar e estabelecer baseline; agente não executa Git mutável. |

## Decisões aprovadas em 2026-09-23

1. Monólito modular com arquitetura hexagonal seletiva.
2. PostgreSQL 16, Flyway, JPA para escrita e jOOQ para relatórios.
3. ACT/30, vencimento no próximo dia útil brasileiro configurável, DECIMAL128, potência decimal e `HALF_EVEN` final.
4. Taxa base mensal por moeda e vigência, iniciada por seeds fictícios identificados.
5. Câmbio BASE/QUOTE, conversão ao final, snapshot e validade configurável inicialmente em 15 minutos.
6. Identidade `(assignor_id, external_id)`, estado persistente e proteção por versão/constraints contra dupla liquidação.
7. REST, OpenAPI e RFC 9457 `ProblemDetail`.
8. Logs JSON, métricas e Prometheus/Grafana em profile opcional.
9. React por features e WCAG 2.2 AA como requisito bloqueante.
10. Autenticação e autorização fora do MVP, registradas como limitação.
11. Benchmark local com 1 milhão de registros e p95 ≤ 300 ms.
12. GitHub Flow, branches curtas, Rebase and merge e SemVer.

## Pendências operacionais, não arquiteturais

- Definir/remover o diretório vazio `supabase/` antes da implementação, sem usá-lo no produto.
- Instalar skills BMAD específicas de planejamento apenas se a responsável desejar validação adicional; isso não bloqueia as decisões aceitas.

## Gate de início da implementação

Os ADRs 0001–0009 estão aceitos e o gate arquitetural de aprovação foi atendido. E0-S1 pode começar após refinamento da story e confirmação de sua Definition of Ready; isso não autoriza implementação nesta fase documental.
