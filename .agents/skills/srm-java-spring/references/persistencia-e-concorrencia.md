# Persistência, transações, concorrência e relatórios

## Migrações (Flyway)

- `backend/src/main/resources/db/migration/V{n}__descricao_snake.sql`. Migração aplicada é **imutável**: correção = nova migração.
- Tipos: dinheiro `NUMERIC(19,2)`, taxas `NUMERIC(9,6)`/`NUMERIC(18,8)`, datas `TIMESTAMPTZ` (UTC), chaves `BIGINT GENERATED ALWAYS AS IDENTITY` ou `UUID` (decidir no ADR; UUID facilita idempotência e sharding futuro).
- Constraints no banco para invariantes críticas (`CHECK`, `UNIQUE`, `FK`, `NOT NULL`). O banco é a última linha de defesa.
- `spring.jpa.hibernate.ddl-auto=validate` — nunca `update`/`create` fora de testes.
- Seeds de referência (moedas, tipos) em migração `R__` ou `V` própria; massa de dados de teste fica fora do caminho de produção.
- Após criar migração, sinalize para `srm-documentacao` atualizar ER e `docs/database/ddl.sql`.

## Escrita (JPA)

- Entidades JPA em `persistence`, mapeadas para/desde o domínio por mapper explícito (MapStruct ou manual — escolha única no projeto).
- `@Version private Long version;` em `SettlementBatch` (e `ReceivableType` se editável).
- `open-in-view: false`. Evite N+1: `@EntityGraph`/`join fetch` onde listar agregados.
- Repositórios Spring Data para escrita e buscas por id; nada de relatório via JPA.

## Caso de uso de liquidação (esqueleto)

```java
@Service
@RequiredArgsConstructor
class SettleBatchUseCase {
    private final ExchangeRateQuery rates;
    private final PricingService pricing;
    private final SettlementRepository settlements;
    private final Clock clock;

    public SettlementResult execute(SettleBatchCommand cmd) {
        var existing = settlements.findByIdempotencyKey(cmd.idempotencyKey());
        if (existing.isPresent()) return SettlementResult.replay(existing.get(), cmd); // valida payload igual
        var fx = rates.latestValid(cmd.faceCurrency(), cmd.paymentCurrency(), clock.instant()); // fora da tx
        return persist(cmd, fx);
    }

    @Transactional
    SettlementResult persist(SettleBatchCommand cmd, Optional<ExchangeRate> fx) { ... }
}
```
Atenção: `@Transactional` em método chamado pela própria classe **não** passa pelo proxy. Coloque a parte transacional em outro bean (ex.: `SettlementWriter`) ou use `TransactionTemplate`. Esse é um erro clássico — cubra com teste de integração que prove rollback.

## Tratamento de conflito

| Situação | Mecanismo | Resposta |
|---|---|---|
| Dois operadores liquidam o mesmo lote | `@Version` → `ObjectOptimisticLockingFailureException` | 409 `settlement-conflict` |
| Mesmo request repetido (retry de rede) | `Idempotency-Key` + `UNIQUE` | 201/200 com o mesmo recurso |
| Mesma chave, payload diferente | comparação de hash do payload | 422 `idempotency-key-reuse` |
| Corrida de inserção da mesma chave | `DataIntegrityViolationException` na constraint | reler e devolver o existente |

Não faça retry automático cego de optimistic lock em liquidação: o estado mudou, o operador precisa decidir.

## Teste de concorrência obrigatório

```java
@Test
void only_one_concurrent_settlement_succeeds() throws Exception {
    int threads = 10;
    var start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(threads);
    var futures = IntStream.range(0, threads)
        .mapToObj(i -> pool.submit(() -> { start.await(); return tryToSettle(batchId); }))
        .toList();
    start.countDown();
    var outcomes = futures.stream().map(this::getQuietly).toList();
    assertThat(outcomes).filteredOn(Outcome::success).hasSize(1);
    assertThat(countSettlementsInDb(batchId)).isEqualTo(1);
}
```
Rode com Testcontainers PostgreSQL (não H2: semântica de lock e tipos difere).

## Relatórios (leitura, 2 camadas)

- `reporting.api` → `reporting.persistence` (jOOQ ou `NamedParameterJdbcTemplate`). Sem passar por `service`, como o desafio permite.
- Sempre parâmetros bind; ordenação só por whitelist de colunas (nunca string do cliente direto no SQL).
- Filtros opcionais montados dinamicamente (jOOQ `Condition` / `DSL.noCondition()`).
- Paginação: `LIMIT/OFFSET` com `size ≤ 100`; para volumes muito grandes, keyset (`WHERE (settled_at, id) < (?, ?)`) — documente a escolha. `COUNT(*)` pode ser caro: considere contagem separada ou estimativa se o ADR aceitar.
- Projeção direta para `record` de leitura; nada de entidade JPA.
- Valide o plano: `EXPLAIN ANALYZE` com massa grande (ex.: 1M linhas gerada por script) e registre o resultado em `docs/` — isso evidencia o "diferencial" de performance.

```java
Condition where = DSL.noCondition()
    .and(filter.from() != null ? BATCH.SETTLED_AT.ge(filter.from()) : noCondition())
    .and(filter.to() != null ? BATCH.SETTLED_AT.lt(filter.to()) : noCondition())
    .and(filter.assignorId() != null ? BATCH.ASSIGNOR_ID.eq(filter.assignorId()) : noCondition())
    .and(filter.currency() != null ? BATCH.PAYMENT_CURRENCY.eq(filter.currency()) : noCondition());
```
