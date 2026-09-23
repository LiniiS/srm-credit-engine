# API, validação, erros, resiliência e observabilidade

## Controllers

- Finos: validar (`@Valid`), mapear DTO → comando, chamar caso de uso, mapear resultado → DTO, montar status.
- `POST` criando recurso → `ResponseEntity.created(location).body(dto)`; simulação → 200; aceite assíncrono → 202.
- Documente com springdoc: `@Operation`, `@ApiResponse` para cada status relevante, exemplos de payload. Swagger UI em `/swagger-ui.html`, spec em `/v3/api-docs`.
- Versão na rota (`/api/v1`). Nomes de recurso no plural, sem verbos (exceção justificada: `/pricing/simulations`, `/exchange-rates/sync`).
- Valores monetários como `String` no JSON (serialize `BigDecimal` com `@JsonFormat(shape = STRING)` ou config global `WRITE_BIGDECIMAL_AS_PLAIN` + shape string).

## DTOs e validação

```java
public record SimulationRequest(
    @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal faceValue,
    @NotNull @Pattern(regexp = "[A-Z]{3}") String faceCurrency,
    @NotNull @Pattern(regexp = "[A-Z]{3}") String paymentCurrency,
    @NotNull @Future LocalDate dueDate,
    @NotBlank @Size(max = 40) String receivableType
) {}
```
- Bean Validation cobre forma; o domínio cobre regra (tipo ativo, moeda suportada, taxa disponível).
- Limite o tamanho do lote (ex.: `@Size(max = 500)`) e do body (`server.tomcat.max-swallow-size`/`spring.servlet.multipart`), evitando abuso.
- `spring.jackson.deserialization.fail-on-unknown-properties=true`.

## Erros (RFC 9457)

```java
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(DomainRuleViolation.class)
    ProblemDetail onRule(DomainRuleViolation ex) {
        var pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setType(URI.create("https://srm.example/problems/" + ex.code()));
        pd.setTitle(ex.title());
        return pd;
    }
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail onConflict() { ... 409 ... }
    @ExceptionHandler(Exception.class)
    ProblemDetail onUnexpected(Exception ex) {
        log.error("unexpected_error", ex);             // loga completo
        return ProblemDetail.forStatusAndDetail(INTERNAL_SERVER_ERROR, "Erro inesperado"); // responde genérico
    }
}
```
- Mapa: validação 400; não encontrado 404; conflito/lock/duplicidade 409; regra de negócio 422; dependência externa indisponível 503.
- Inclua `traceId` no ProblemDetail (`pd.setProperty("traceId", ...)`) para correlação com logs.
- Hierarquia de exceções pequena: `DomainException` (abstrata, com `code`) → `DomainRuleViolation`, `NotFound`, `Conflict`. Sem exceções genéricas `RuntimeException("erro")`.

## Resiliência (provedor de câmbio mock)

```yaml
resilience4j:
  retry.instances.fxProvider: { maxAttempts: 3, waitDuration: 200ms, enableExponentialBackoff: true, retryExceptions: [java.io.IOException, java.util.concurrent.TimeoutException] }
  circuitbreaker.instances.fxProvider: { slidingWindowSize: 20, failureRateThreshold: 50, waitDurationInOpenState: 30s }
  timelimiter.instances.fxProvider: { timeoutDuration: 2s }
```
- Anotações (`@Retry`, `@CircuitBreaker`) no **adapter**, não no domínio.
- Fallback: última taxa persistida dentro do limite de idade; senão, exceção `ExchangeRateUnavailable` → 503.
- O mock deve permitir simular falha/latência (propriedade ou profile) para demonstrar o circuit breaker em teste.

## Observabilidade

- Logs JSON (`logstash-logback-encoder`) em todos os profiles não-locais; campos: timestamp, level, logger, message, traceId, spanId, e contexto de negócio (`settlementId`, `receivableType`, `currency`).
- Mascarar documento do cedente (`12.***.***/0001-**`). Nunca logar payload completo de request.
- Métricas Micrometer (Prometheus):
  - `srm.settlements` (counter; tags `currency`, `type`, `outcome=success|conflict|rejected`)
  - `srm.pricing.duration` (timer)
  - `srm.fx.rate.age` (gauge, segundos desde a última taxa)
- Actuator: expor só `health`, `info`, `prometheus`; `health` com readiness/liveness; demais endpoints fechados.
- Tracing: `micrometer-tracing-bridge-otel`; propagação W3C `traceparent`.

## Segurança básica no backend (complementa a skill de segurança)

- CORS restrito à origem do frontend via config, nunca `*` com credenciais.
- Headers de segurança padrão do Spring Security se autenticação for adicionada; se não houver autenticação no escopo, **declare isso no README** como limitação consciente.
- Dependências sem CVEs críticas (OWASP dependency-check ou `mvn versions`/Dependabot no CI).
- Dockerfile multi-stage, imagem JRE slim, usuário não-root, `HEALTHCHECK`.
