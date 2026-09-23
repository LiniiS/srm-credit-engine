# Critérios de aceite transversais

| ID | Categoria | Critério mensurável | Verificação planejada |
|---|---|---|---|
| AC-FIN-01 | Precisão | O centavo final coincide com casos de referência aprovados; nenhum binário floating-point é usado. | Unitários parametrizados + regra arquitetural. |
| AC-INT-01 | Atomicidade | Falha em qualquer item deixa zero batches/itens/alterações de estado persistidos. | Testcontainers com falha induzida. |
| AC-CON-01 | Concorrência | N liquidações simultâneas do mesmo recebível produzem exatamente um sucesso. | PostgreSQL 16, latch/barreira, chaves iguais e distintas. |
| AC-IDEM-01 | Idempotência | Mesma chave e mesmo payload reproduzem a resposta existente; mesma chave com payload diferente retorna 409 e código `IDEMPOTENCY_KEY_REUSED`. | Teste de integração/contrato cobrindo status, corpo e `Location` do replay. |
| AC-SEC-01 | Segurança | Entradas inválidas são rejeitadas; respostas/logs não expõem stack, SQL, credenciais ou documento completo. | Testes negativos e inspeção automatizada. |
| AC-API-01 | Contrato | OpenAPI cobre todas as rotas; erros são `application/problem+json`. | Validação do spec e testes de contrato. |
| AC-PERF-01 | Extrato | Com 1M itens, período+cedente atende p95 ≤ 300 ms no ambiente aprovado. | Seed, warm-up, amostra e `EXPLAIN ANALYZE` documentados. |
| AC-UX-01 | Simulação | Após debounce de 300 ms, resposta visual aparece em p95 ≤ 500 ms no ambiente aprovado. | Teste E2E + métrica backend. |
| AC-A11Y-01 | Conformidade | Todos os fluxos do MVP atendem WCAG 2.2 nível AA; HTML semântico/controles nativos são usados antes de ARIA. | Revisão semântica, Testing Library por role e axe sem violações. |
| AC-A11Y-02 | Teclado e foco | Simulação, liquidação, filtros, tabela e paginação operam somente por teclado, sem armadilhas e com foco visível. Rotas focam o conteúdo principal; validação foca o primeiro campo inválido; erros/sucessos de submissão recebem foco coerente. | Testing Library + user-event e roteiro manual somente por teclado. |
| AC-A11Y-03 | Formulários e anúncios | Cada controle tem label, instrução e erro associados. Resultados, carregamento, sucesso e falha são anunciados por `aria-live`/alerta apropriado sem anúncios duplicados. | Testes por nome/descrição acessível, foco e regiões live; leitor de tela manual. |
| AC-A11Y-04 | Navegação e conteúdo | Cada rota atualiza um título de página único. O extrato usa tabela com caption/cabeçalhos/ordenação anunciada; paginação possui nome, página atual e estados indisponíveis acessíveis. | Testing Library/axe e roteiro manual por teclado e leitor de tela. |
| AC-A11Y-05 | Percepção e movimento | Texto, componentes, foco e estados atendem contraste WCAG AA; informação não depende só de cor; `prefers-reduced-motion` reduz movimento não essencial. | axe quando aplicável, verificação de contraste e preferência do sistema. |
| AC-A11Y-06 | Verificação | Nenhum fluxo primário é concluído apenas com automação: simulação, liquidação, erros e extrato passam por teclado e leitor de tela antes da story ser concluída. | Checklist manual anexado à evidência da story, além de Testing Library e axe. |
| AC-OBS-01 | Observabilidade | Toda requisição possui correlação; latência/resultados de pricing/settlement e estado do circuit breaker são medidos. | Teste de integração e scrape local. |
| AC-RES-01 | Resiliência | Falhas transitórias respeitam timeout/retry; circuito abre; taxa expirada nunca é usada silenciosamente. | Testes com servidor mock. |
| AC-OPS-01 | Execução | Checkout limpo sobe via Compose e healthchecks ficam healthy. | `docker compose config/up/ps` + smoke. |
| AC-DOC-01 | Documentação | README/docs refletem portas, stack, endpoints, schema e decisões reais. | `check-docs.sh` em modo release. |

As metas temporais só se tornam compromisso após aprovação do hardware, dataset e método de medição.
