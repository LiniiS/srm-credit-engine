# ADR-0005: Garantir liquidação idempotente e concorrente

- **Status:** Proposto
- **Data:** 2026-09-23
- **Decisores:** responsável pelo projeto
- **Relacionado a:** RF-06, RF-07, RNF-02, RNF-03

## Contexto

Retries podem duplicar uma requisição e chaves distintas podem disputar o mesmo recebível. `@Version` sozinho não protege se cada requisição criar outro agregado.

## Opções consideradas

1. **Transação local + chave idempotente/request hash + estado/version + unicidade por recebível** — defesa em camadas, simples no PostgreSQL.
2. **Somente optimistic locking** — erro claro, mas não cobre todas as inserções concorrentes.
3. **Pessimistic locking** — serialização forte; maior contenção e risco operacional.
4. **Fila distribuída** — ordenação possível; complexidade fora do escopo.

## Decisão

Propor opção 1 sob READ COMMITTED. A constraint única em `settlement_item.receivable_id` é a garantia final; version/state melhora semântica; idempotency key e hash tratam retry.

## Limites e regras resultantes

- Uma transação persiste batch, itens e transições de recebíveis.
- Mesma `Idempotency-Key` com o mesmo hash de payload reproduz a resposta do recurso existente.
- Mesma `Idempotency-Key` com hash de payload diferente retorna `409 Conflict`, pois a chave já identifica outra representação da operação.
- Violação por disputa/optimistic lock retorna 409; nenhuma chamada externa dentro da transação.

## Verificação

- Testcontainers com N threads, barreira simultânea e chaves iguais/diferentes; exatamente um vencedor.
- Teste de contrato comprova replay da resposta existente para key+payload iguais e `409 Conflict` para reutilização divergente.
- Teste de rollback após falha no último item.

## Consequências

- Positivas: segurança mesmo em corrida adversa.
- Negativas: exige mapear constraints/exceções cuidadosamente.
- Revisitar se: throughput medido mostrar contenção que exija particionamento.
