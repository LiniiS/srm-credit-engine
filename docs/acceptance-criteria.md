# Critérios de aceite transversais

| ID | Categoria | Critério mensurável | Verificação planejada |
|---|---|---|---|
| AC-FIN-01 | Precisão | O centavo final coincide com casos de referência aprovados; nenhum binário floating-point é usado. | Unitários parametrizados + regra arquitetural. |
| AC-INT-01 | Atomicidade | Falha em qualquer item deixa zero batches/itens/alterações de estado persistidos. | Testcontainers com falha induzida. |
| AC-CON-01 | Concorrência | N liquidações simultâneas do mesmo recebível produzem exatamente um sucesso. | PostgreSQL 16, latch/barreira, chaves iguais e distintas. |
| AC-IDEM-01 | Idempotência | Replay equivalente retorna o mesmo id; chave colidida com payload diferente retorna 422. | Teste de integração/contrato. |
| AC-SEC-01 | Segurança | Entradas inválidas são rejeitadas; respostas/logs não expõem stack, SQL, credenciais ou documento completo. | Testes negativos e inspeção automatizada. |
| AC-API-01 | Contrato | OpenAPI cobre todas as rotas; erros são `application/problem+json`. | Validação do spec e testes de contrato. |
| AC-PERF-01 | Extrato | Com 1M itens, período+cedente atende p95 ≤ 300 ms no ambiente aprovado. | Seed, warm-up, amostra e `EXPLAIN ANALYZE` documentados. |
| AC-UX-01 | Simulação | Após debounce de 300 ms, resposta visual aparece em p95 ≤ 500 ms no ambiente aprovado. | Teste E2E + métrica backend. |
| AC-A11Y-01 | Acessibilidade | Fluxos primários são utilizáveis por teclado, têm labels, foco e estados anunciados; sem violações axe críticas. | Testing Library/axe + teste manual. |
| AC-OBS-01 | Observabilidade | Toda requisição possui correlação; latência/resultados de pricing/settlement e estado do circuit breaker são medidos. | Teste de integração e scrape local. |
| AC-RES-01 | Resiliência | Falhas transitórias respeitam timeout/retry; circuito abre; taxa expirada nunca é usada silenciosamente. | Testes com servidor mock. |
| AC-OPS-01 | Execução | Checkout limpo sobe via Compose e healthchecks ficam healthy. | `docker compose config/up/ps` + smoke. |
| AC-DOC-01 | Documentação | README/docs refletem portas, stack, endpoints, schema e decisões reais. | `check-docs.sh` em modo release. |

As metas temporais só se tornam compromisso após aprovação do hardware, dataset e método de medição.

