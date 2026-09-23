# Épicos e stories — SRM Credit Engine

- **Status:** Proposto para aprovação
- **Rota de planejamento:** PRD + arquitetura + épicos/stories

## Inventário coberto

RF-01–RF-12 e RNF-01–RNF-12 são cobertos pela matriz de rastreabilidade. Cada story entrega um corte verificável; nenhuma autoriza implementação antes da aprovação dos ADRs relacionados.

## E0 — Fundação executável e guardrails

### E0-S1 — Subir o esqueleto ponta a ponta

**Valor:** o avaliador confirma rapidamente que SPA, API e banco funcionam juntos.

- Dado um checkout limpo com Docker disponível, quando `docker compose up --build` terminar, então PostgreSQL, API e frontend ficam healthy e a SPA alcança o health da API.
- Dado o backend inicializado, quando Flyway executar, então o schema nasce sem intervenção manual.
- Dado o código, quando os gates forem executados, então formatação, testes, typecheck e build passam.

### E0-S2 — Impor limites arquiteturais e CI

- Dado um import proibido entre módulos/camadas, quando ArchUnit executar, então o build falha com a regra violada.
- Dado um import proibido entre features do frontend, quando o lint executar, então o gate falha.
- Dado um PR, quando a CI executar, então backend, frontend e `docker compose config` são validados.

## E1 — Câmbio auditável

### E1-S1 — Registrar e consultar taxas

- Dada uma taxa válida USD/BRL, quando cadastrada, então uma nova versão append-only é criada e retornada com vigência/fonte.
- Dada entrada inválida, então a API retorna `ProblemDetail` sem detalhes internos.

### E1-S2 — Sincronizar câmbio com resiliência

- Dado o provedor mock disponível, quando sincronizado, então a taxa é persistida e a requisição aceita.
- Dadas falhas transitórias, então timeout/retry/circuit breaker são observáveis e nenhuma chamada externa ocorre em transação aberta.

## E2 — Precificação determinística

### E2-S1 — Aplicar Strategy por tipo

- Dada uma duplicata, então o spread mensal aplicado é 1,5%; dado um cheque, 2,5%.
- Dado tipo sem Strategy ativa, então a simulação é rejeitada com erro de domínio.

### E2-S2 — Simular em moeda do título

- Dados valor, vencimento, taxa base e tipo válidos, então a resposta contém VP, deságio, prazo e parâmetros aplicados como strings decimais.
- Dado um caso de referência aprovado, então o centavo final coincide exatamente.

### E2-S3 — Simular cross-currency

- Dada moeda de pagamento diferente, então o VP é calculado primeiro na moeda do título e convertido ao final.
- Dada taxa ausente ou expirada, então a operação é rejeitada conforme ADR-0004.

## E3 — Liquidação ACID, idempotente e concorrente

### E3-S1 — Liquidar lote atomicamente

- Dado lote válido, quando confirmado, então batch, itens e snapshots são gravados numa transação e o total é consistente.
- Dado qualquer item inválido, então nada do lote é persistido.

### E3-S2 — Repetir com a mesma chave sem duplicar

- Dada a mesma `Idempotency-Key` com o mesmo payload, quando repetida, então a resposta existente é reproduzida sem novo efeito.
- Dada a mesma `Idempotency-Key` com payload divergente, então a API retorna `409 Conflict` com código `IDEMPOTENCY_KEY_REUSED`.

### E3-S3 — Impedir dupla liquidação concorrente

- Dadas N requisições simultâneas pelo mesmo recebível, inclusive com chaves diferentes, então exatamente uma conclui e as demais recebem conflito sem registros parciais.

### E3-S4 — Consultar liquidação auditável

- Dado um id existente, então a resposta expõe todos os snapshots necessários para reconstrução do cálculo, sem expor entidades JPA.

## E4 — Extrato analítico

### E4-S1 — Consultar extrato paginado com jOOQ

- Dados período obrigatório e filtros opcionais de cedente/moeda, então a consulta retorna página estável, ordenada e limitada a 100 itens.
- Dada base de referência com 1M itens, então o plano usa índices previstos e atende ao p95 aprovado.

## E5 — Experiência do operador

### E5-S1 — Simular em tempo real

- Dado formulário válido, após 300 ms sem digitação, então uma única simulação é exibida; requisição anterior é cancelada.
- Dado erro, então foco, mensagem e associação de campo são acessíveis.

### E5-S2 — Liquidar e consultar histórico

- Dada simulação válida, quando confirmada, então a UI gera/reutiliza chave idempotente e apresenta o resultado.
- Dados filtros, quando alterados, então URL e grid server-side permanecem sincronizados e recarregáveis.

## E6 — Operabilidade e documentação final

### E6-S1 — Padronizar API, segurança e observabilidade

- Toda rota aparece no OpenAPI; erros usam RFC 9457 e não vazam internals.
- Logs possuem correlação e dados sensíveis mascarados; métricas cobrem latência, resultado e circuit breaker.

### E6-S2 — Fechar documentação e evidências

- README permite execução do zero e aponta para ADRs, C4, ER, DDL, escala, EDA e AI_USAGE.
- Gates de documentação e release passam; limitações e evidências reais são registradas sem inventar resultados.

## Ordem recomendada

1. Aprovar PRD, ADRs e métricas.
2. E0 — foundation/guardrails.
3. E1 — câmbio.
4. E2 — pricing vertical completo.
5. E3 — settlement e concorrência.
6. E4 — reporting jOOQ e performance.
7. E5 — frontend integrado.
8. E6 — hardening, observabilidade e documentação final.
