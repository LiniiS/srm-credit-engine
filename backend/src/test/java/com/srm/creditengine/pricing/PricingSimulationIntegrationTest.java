package com.srm.creditengine.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = false)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PricingSimulationIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16.6-alpine")
          .withDatabaseName("srm_credit_engine")
          .withUsername("srm")
          .withPassword("srm_test_password");

  @DynamicPropertySource
  static void configurePostgres(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired TestRestTemplate restTemplate;
  @Autowired JdbcTemplate jdbcTemplate;

  @BeforeEach
  void insert_reference_exchange_rate() {
    jdbcTemplate.update(
        "DELETE FROM exchange_rate WHERE id = ?::uuid", "55555555-5555-4555-8555-555555555555");
    jdbcTemplate.update(
        "DELETE FROM exchange_rate WHERE id = ?::uuid", "66666666-6666-4666-8666-666666666666");
    jdbcTemplate.update(
        """
        INSERT INTO exchange_rate
          (id, base_currency, quote_currency, rate, source, effective_at, created_at)
        VALUES (?::uuid, 'USD', 'BRL', 5.13000000, 'REFERENCE_CASE',
                CURRENT_TIMESTAMP - INTERVAL '10 minutes', CURRENT_TIMESTAMP - INTERVAL '9 minutes')
        """,
        "55555555-5555-4555-8555-555555555555");
  }

  @Test
  void simulates_approved_case_without_persisting_business_rows() {
    var countsBefore = businessRowCounts();
    var request =
        Map.of(
            "faceValue", "1000.00",
            "currency", "BRL",
            "receivableTypeCode", "DUPLICATA_MERCANTIL",
            "calculationDate", "2026-01-02",
            "dueDate", "2026-02-01");

    var response = restTemplate.postForEntity("/api/v1/pricing/simulations", request, Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .containsEntry("faceValue", "1000.00")
        .containsEntry("currency", "BRL")
        .containsEntry("paymentCurrencyCode", "BRL")
        .containsEntry("receivableTypeCode", "DUPLICATA_MERCANTIL")
        .containsEntry("calculationDate", "2026-01-02")
        .containsEntry("dueDate", "2026-02-01")
        .containsEntry("adjustedDueDate", "2026-02-02")
        .containsEntry("termDays", 31)
        .containsEntry("termMonths", "1.033333333333333333333333333333333")
        .containsEntry("baseRate", "0.010000000000")
        .containsEntry("baseRateSource", "DEMO_SEED")
        .containsEntry("spread", "0.015")
        .containsEntry("monthlyRate", "0.025000000000")
        .containsEntry("presentValue", "974.81")
        .containsEntry("presentValueInPaymentCurrency", "974.81")
        .containsEntry("exchangeRate", null)
        .containsEntry("discount", "25.19");
    assertThat(response.getBody().get("baseRateId")).isInstanceOf(String.class);
    assertThat(businessRowCounts()).isEqualTo(countsBefore);
  }

  @Test
  void simulates_approved_usd_fractional_case_exactly() {
    var response =
        restTemplate.postForEntity(
            "/api/v1/pricing/simulations",
            Map.of(
                "faceValue", "2500.00",
                "currency", "USD",
                "receivableTypeCode", "CHEQUE_PRE_DATADO",
                "calculationDate", "2026-01-05",
                "dueDate", "2026-02-19"),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .containsEntry("termDays", 45)
        .containsEntry("baseRate", "0.005000000000")
        .containsEntry("spread", "0.025")
        .containsEntry("presentValue", "2391.58")
        .containsEntry("discount", "108.42");
  }

  @Test
  void converts_both_directions_with_the_same_persisted_snapshot() {
    var usdToBrl =
        restTemplate.postForEntity(
            "/api/v1/pricing/simulations",
            Map.of(
                "faceValue", "2500.00",
                "currency", "USD",
                "paymentCurrencyCode", "BRL",
                "receivableTypeCode", "CHEQUE_PRE_DATADO",
                "calculationDate", "2026-01-05",
                "dueDate", "2026-02-19"),
            Map.class);
    var brlToUsd =
        restTemplate.postForEntity(
            "/api/v1/pricing/simulations",
            Map.of(
                "faceValue", "1000.00",
                "currency", "BRL",
                "paymentCurrencyCode", "USD",
                "receivableTypeCode", "DUPLICATA_MERCANTIL",
                "calculationDate", "2026-01-02",
                "dueDate", "2026-02-01"),
            Map.class);

    assertThat(usdToBrl.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(usdToBrl.getBody())
        .containsEntry("paymentCurrencyCode", "BRL")
        .containsEntry("presentValue", "2391.58")
        .containsEntry("presentValueInPaymentCurrency", "12268.78");
    var snapshot = (Map<?, ?>) usdToBrl.getBody().get("exchangeRate");
    assertThat(snapshot.get("id")).isEqualTo("55555555-5555-4555-8555-555555555555");
    assertThat(snapshot.get("baseCurrencyCode")).isEqualTo("USD");
    assertThat(snapshot.get("quoteCurrencyCode")).isEqualTo("BRL");
    assertThat(snapshot.get("rate")).isEqualTo("5.13000000");
    assertThat(snapshot.get("source")).isEqualTo("REFERENCE_CASE");
    assertThat(snapshot.get("effectiveAt")).isNotNull();
    assertThat(snapshot.get("createdAt")).isNotNull();
    assertThat(brlToUsd.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(brlToUsd.getBody())
        .containsEntry("paymentCurrencyCode", "USD")
        .containsEntry("presentValue", "974.81")
        .containsEntry("presentValueInPaymentCurrency", "190.02");
    assertThat(brlToUsd.getBody().get("exchangeRate")).isEqualTo(snapshot);
  }

  @Test
  void uses_valid_usd_brl_snapshot_when_direct_brl_usd_is_expired() {
    jdbcTemplate.update(
        """
        INSERT INTO exchange_rate
          (id, base_currency, quote_currency, rate, source, effective_at, created_at)
        VALUES (?::uuid, 'BRL', 'USD', 0.19000000, 'EXPIRED_DIRECT',
                CURRENT_TIMESTAMP - INTERVAL '20 minutes', CURRENT_TIMESTAMP - INTERVAL '19 minutes')
        """,
        "66666666-6666-4666-8666-666666666666");

    var response =
        restTemplate.postForEntity(
            "/api/v1/pricing/simulations",
            Map.of(
                "faceValue", "1000.00",
                "currency", "BRL",
                "paymentCurrencyCode", "USD",
                "receivableTypeCode", "DUPLICATA_MERCANTIL",
                "calculationDate", "2026-01-02",
                "dueDate", "2026-02-01"),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsEntry("presentValueInPaymentCurrency", "190.02");
    var snapshot = (Map<?, ?>) response.getBody().get("exchangeRate");
    assertThat(snapshot.get("id")).isEqualTo("55555555-5555-4555-8555-555555555555");
    assertThat(snapshot.get("baseCurrencyCode")).isEqualTo("USD");
    assertThat(snapshot.get("quoteCurrencyCode")).isEqualTo("BRL");
  }

  @Test
  void prefers_valid_direct_snapshot_and_multiplies_in_its_persisted_orientation() {
    jdbcTemplate.update(
        """
        INSERT INTO exchange_rate
          (id, base_currency, quote_currency, rate, source, effective_at, created_at)
        VALUES (?::uuid, 'BRL', 'USD', 0.20000000, 'VALID_DIRECT',
                CURRENT_TIMESTAMP - INTERVAL '5 minutes', CURRENT_TIMESTAMP - INTERVAL '4 minutes')
        """,
        "66666666-6666-4666-8666-666666666666");

    var response =
        restTemplate.postForEntity(
            "/api/v1/pricing/simulations",
            Map.of(
                "faceValue", "1000.00",
                "currency", "BRL",
                "paymentCurrencyCode", "USD",
                "receivableTypeCode", "DUPLICATA_MERCANTIL",
                "calculationDate", "2026-01-02",
                "dueDate", "2026-02-01"),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsEntry("presentValueInPaymentCurrency", "194.96");
    var snapshot = (Map<?, ?>) response.getBody().get("exchangeRate");
    assertThat(snapshot.get("id")).isEqualTo("66666666-6666-4666-8666-666666666666");
    assertThat(snapshot.get("baseCurrencyCode")).isEqualTo("BRL");
    assertThat(snapshot.get("quoteCurrencyCode")).isEqualTo("USD");
  }

  @Test
  void zero_term_and_calendar_failure_follow_domain_contract() {
    var zero =
        Map.of(
            "faceValue", "1000.00",
            "currency", "BRL",
            "receivableTypeCode", "DUPLICATA_MERCANTIL",
            "calculationDate", "2026-01-02",
            "dueDate", "2026-01-02");
    var success = restTemplate.postForEntity("/api/v1/pricing/simulations", zero, Map.class);
    assertThat(success.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(success.getBody())
        .containsEntry("presentValue", "1000.00")
        .containsEntry("discount", "0.00");

    var unsupportedYear =
        Map.of(
            "faceValue", "1000.00",
            "currency", "BRL",
            "receivableTypeCode", "DUPLICATA_MERCANTIL",
            "calculationDate", "2031-01-01",
            "dueDate", "2031-01-02");
    var failure =
        restTemplate.postForEntity("/api/v1/pricing/simulations", unsupportedYear, Map.class);
    assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(failure.getBody()).containsEntry("code", "BUSINESS_CALENDAR_NOT_AVAILABLE");
  }

  @Test
  void invalid_unknown_and_past_inputs_return_safe_problems() {
    var invalid =
        """
        {"faceValue":"0.00","currency":"BRL","receivableTypeCode":"DUPLICATA_MERCANTIL",
         "calculationDate":"2026-01-05","dueDate":"2026-01-02","unexpected":true}
        """;
    var headers = new org.springframework.http.HttpHeaders();
    headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
    var unknown =
        restTemplate.postForEntity(
            "/api/v1/pricing/simulations",
            new org.springframework.http.HttpEntity<>(invalid, headers),
            Map.class);
    assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(unknown.getBody()).containsEntry("code", "VALIDATION_ERROR");
    assertThat((java.util.List<?>) unknown.getBody().get("violations"))
        .anySatisfy(
            violation -> assertThat(((Map<?, ?>) violation).get("field")).isEqualTo("unexpected"));

    var zero =
        Map.of(
            "faceValue", "0.00",
            "currency", "BRL",
            "receivableTypeCode", "DUPLICATA_MERCANTIL",
            "calculationDate", "2026-01-05",
            "dueDate", "2026-01-05");
    var zeroFailure = restTemplate.postForEntity("/api/v1/pricing/simulations", zero, Map.class);
    assertThat(zeroFailure.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(zeroFailure.getBody()).containsEntry("code", "VALIDATION_ERROR");
    assertThat((java.util.List<?>) zeroFailure.getBody().get("violations"))
        .anySatisfy(
            violation -> assertThat(((Map<?, ?>) violation).get("field")).isEqualTo("faceValue"));

    var past =
        Map.of(
            "faceValue", "10.00",
            "currency", "BRL",
            "receivableTypeCode", "DUPLICATA_MERCANTIL",
            "calculationDate", "2026-01-05",
            "dueDate", "2026-01-02");
    var dueFailure = restTemplate.postForEntity("/api/v1/pricing/simulations", past, Map.class);
    assertThat(dueFailure.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(dueFailure.getBody())
        .containsEntry("code", "DUE_DATE_BEFORE_CALCULATION_DATE")
        .doesNotContainKeys("stackTrace", "exception");
  }

  @Test
  void absent_currency_type_and_applicable_rate_keep_stable_errors() {
    assertProblem(
        Map.of(
            "faceValue", "10.00",
            "currency", "EUR",
            "receivableTypeCode", "DUPLICATA_MERCANTIL",
            "calculationDate", "2026-01-05",
            "dueDate", "2026-02-05"),
        HttpStatus.BAD_REQUEST,
        "CURRENCY_NOT_SUPPORTED");
    assertProblem(
        Map.of(
            "faceValue", "10.00",
            "currency", "BRL",
            "receivableTypeCode", "CONTRATO",
            "calculationDate", "2026-01-05",
            "dueDate", "2026-02-05"),
        HttpStatus.NOT_FOUND,
        "RECEIVABLE_TYPE_NOT_FOUND");
    assertProblem(
        Map.of(
            "faceValue", "10.00",
            "currency", "BRL",
            "receivableTypeCode", "DUPLICATA_MERCANTIL",
            "calculationDate", "2025-12-01",
            "dueDate", "2025-12-15"),
        HttpStatus.NOT_FOUND,
        "BASE_RATE_NOT_FOUND");
  }

  @Test
  void openapi_documents_simulation_contract_and_problem_responses() {
    var openApi = restTemplate.getForObject("/v3/api-docs", Map.class);
    var paths = (Map<?, ?>) openApi.get("paths");
    var operation = (Map<?, ?>) ((Map<?, ?>) paths.get("/api/v1/pricing/simulations")).get("post");
    var responses = (Map<?, ?>) operation.get("responses");

    assertThat(responses.keySet().stream().map(Object::toString).toList())
        .containsExactlyInAnyOrder("200", "400", "404", "422", "500");
    var components = (Map<?, ?>) openApi.get("components");
    var schemas = (Map<?, ?>) components.get("schemas");
    assertThat(schemas.get("PricingSimulationRequest")).isNotNull();
    assertThat(schemas.get("PricingSimulationResponse")).isNotNull();
    assertThat(schemas.get("PricingSimulationProblemDetail")).isNotNull();
    assertThat(schemas.toString())
        .contains(
            "calculationDate",
            "paymentCurrencyCode",
            "presentValueInPaymentCurrency",
            "exchangeRate",
            "baseCurrencyCode",
            "quoteCurrencyCode",
            "effectiveAt",
            "createdAt",
            "violations",
            "code");
    var responseSchema = (Map<?, ?>) schemas.get("PricingSimulationResponse");
    var responseProperties = (Map<?, ?>) responseSchema.get("properties");
    var exchangeRateProperty = (Map<?, ?>) responseProperties.get("exchangeRate");
    var exchangeRateSchema = (Map<?, ?>) schemas.get("ExchangeRateSnapshot");
    assertThat(
            Boolean.TRUE.equals(exchangeRateProperty.get("nullable"))
                || Boolean.TRUE.equals(exchangeRateSchema.get("nullable"))
                || openApiTypeIncludesNull(exchangeRateProperty.get("type"))
                || openApiTypeIncludesNull(exchangeRateSchema.get("type"))
                || openApiCompositionIncludesNull(exchangeRateProperty.get("oneOf")))
        .as("exchangeRate must be nullable in OpenAPI: %s", exchangeRateProperty)
        .isTrue();
  }

  private boolean openApiTypeIncludesNull(Object type) {
    return type instanceof java.util.Collection<?> values && values.contains("null");
  }

  private boolean openApiCompositionIncludesNull(Object schemas) {
    if (!(schemas instanceof java.util.Collection<?> values)) {
      return false;
    }
    return values.stream()
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .anyMatch(schema -> "null".equals(schema.get("type")));
  }

  private void assertProblem(Map<String, String> request, HttpStatus status, String code) {
    var response = restTemplate.postForEntity("/api/v1/pricing/simulations", request, Map.class);
    assertThat(response.getStatusCode()).isEqualTo(status);
    assertThat(response.getBody())
        .containsEntry("code", code)
        .doesNotContainKeys("stackTrace", "exception", "sql");
  }

  private Map<String, Long> businessRowCounts() {
    return Map.of(
        "currency", jdbcTemplate.queryForObject("SELECT count(*) FROM currency", Long.class),
        "base_rate", jdbcTemplate.queryForObject("SELECT count(*) FROM base_rate", Long.class),
        "receivable_type",
            jdbcTemplate.queryForObject("SELECT count(*) FROM receivable_type", Long.class),
        "exchange_rate",
            jdbcTemplate.queryForObject("SELECT count(*) FROM exchange_rate", Long.class));
  }
}
