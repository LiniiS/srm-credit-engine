package com.srm.creditengine.currency;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {
      com.srm.creditengine.CreditEngineApplication.class,
      ExchangeRateIntegrationTest.FixedClock.class
    })
class ExchangeRateIntegrationTest {
  static final Instant NOW = Instant.parse("2026-09-23T12:00:00.123456789Z");

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16.6-alpine")
          .withDatabaseName("srm_credit_engine")
          .withUsername("srm")
          .withPassword("srm_test_password");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired TestRestTemplate http;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper objectMapper;

  @BeforeEach
  void cleanRates() {
    jdbc.update("DELETE FROM exchange_rate");
  }

  @Test
  void migration_creates_catalog_schema_constraints_and_index() {
    assertThat(jdbc.queryForList("SELECT code FROM currency ORDER BY code", String.class))
        .containsExactly("BRL", "USD");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE indexname='idx_exchange_rate_latest'",
                Integer.class))
        .isOne();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM exchange_rate", Integer.class)).isZero();
  }

  @Test
  void registers_append_only_and_returns_latest_applicable_rate_as_string() throws Exception {
    var first = post("5.00000000", "2026-09-22T12:00:00Z");
    var second = post("5.10000000", "2026-09-23T11:00:00.987654321Z");
    post("9.00000000", "2026-09-24T12:00:00Z");

    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(first.getHeaders().getLocation()).isNotNull();
    assertThat(second.getBody()).contains("\"rate\":\"5.10000000\"");
    assertThat(second.getBody()).contains("\"effectiveAt\":\"2026-09-23T11:00:00.987654Z\"");
    assertThat(second.getBody()).contains("\"createdAt\":\"2026-09-23T12:00:00.123456Z\"");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM exchange_rate", Integer.class))
        .isEqualTo(3);

    var latest =
        http.getForEntity("/api/v1/exchange-rates/latest?base=USD&quote=BRL", String.class);
    assertThat(latest.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(latest.getBody()).contains("\"rate\":\"5.10000000\"");
    assertThat(objectMapper.readTree(latest.getBody()).get("effectiveAt"))
        .isEqualTo(objectMapper.readTree(second.getBody()).get("effectiveAt"));
    assertThat(objectMapper.readTree(latest.getBody()).get("createdAt"))
        .isEqualTo(objectMapper.readTree(second.getBody()).get("createdAt"));
  }

  @Test
  void resolves_total_order_by_created_at_then_id() {
    insertRate(
        "00000000-0000-0000-0000-000000000001",
        "5.10000000",
        "2026-09-23T10:00:00Z",
        "2026-09-23T10:00:00Z");
    insertRate(
        "00000000-0000-0000-0000-000000000002",
        "5.20000000",
        "2026-09-23T09:00:00Z",
        "2026-09-23T11:00:00Z");
    assertLatestRate("5.10000000");

    jdbc.update("DELETE FROM exchange_rate");
    insertRate(
        "00000000-0000-0000-0000-000000000001",
        "5.10000000",
        "2026-09-23T10:00:00Z",
        "2026-09-23T10:00:00Z");
    insertRate(
        "00000000-0000-0000-0000-000000000002",
        "5.20000000",
        "2026-09-23T10:00:00Z",
        "2026-09-23T11:00:00Z");
    assertLatestRate("5.20000000");

    jdbc.update("DELETE FROM exchange_rate");
    var effective = Instant.parse("2026-09-23T10:00:00Z");
    var created = Instant.parse("2026-09-23T11:00:00Z");
    jdbc.update(
        "INSERT INTO exchange_rate VALUES (CAST(? AS uuid),'USD','BRL',5.1,'A',?,?)",
        "00000000-0000-0000-0000-000000000001",
        OffsetDateTime.ofInstant(effective, ZoneOffset.UTC),
        OffsetDateTime.ofInstant(created, ZoneOffset.UTC));
    jdbc.update(
        "INSERT INTO exchange_rate VALUES (CAST(? AS uuid),'USD','BRL',5.2,'B',?,?)",
        "00000000-0000-0000-0000-000000000002",
        OffsetDateTime.ofInstant(effective, ZoneOffset.UTC),
        OffsetDateTime.ofInstant(created, ZoneOffset.UTC));

    assertLatestRate("5.20000000");
  }

  @Test
  void distinguishes_unsupported_currency_missing_rate_and_validation() {
    var unsupportedRegistration = postJson(requestJson("5.1", "EUR", "BRL", "MANUAL"));
    assertProblem(unsupportedRegistration, HttpStatus.BAD_REQUEST, "CURRENCY_NOT_SUPPORTED");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM exchange_rate", Integer.class)).isZero();

    var unsupported =
        http.getForEntity("/api/v1/exchange-rates/latest?base=EUR&quote=BRL", String.class);
    assertProblem(unsupported, HttpStatus.BAD_REQUEST, "CURRENCY_NOT_SUPPORTED");

    var missing =
        http.getForEntity("/api/v1/exchange-rates/latest?base=BRL&quote=USD", String.class);
    assertProblem(missing, HttpStatus.NOT_FOUND, "EXCHANGE_RATE_NOT_FOUND");

    var invalid = post("0", "2026-09-23T11:00:00Z");
    assertProblem(invalid, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    assertThat(invalid.getBody()).contains("\"field\":\"rate\"");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM exchange_rate", Integer.class)).isZero();
  }

  @Test
  void rejects_all_required_invalid_inputs_without_writing() {
    assertValidation(postJson(requestJson("null", "USD", "BRL", "MANUAL")), "rate");
    assertValidation(post("-0.00000001", "2026-09-23T11:00:00Z"), "rate");
    assertValidation(post("1.000000001", "2026-09-23T11:00:00Z"), "rate");
    assertValidation(postJson(requestJson("5.1", "usd", "BRL", "MANUAL")), "baseCurrency");
    assertValidation(postJson(requestJson("5.1", "USD", "USD", "MANUAL")), null);
    assertValidation(postJson(requestJson("5.1", "USD", "BRL", " ")), "source");
    assertValidation(
        postJson(
            requestJson("5.1", "USD", "BRL", "MANUAL")
                .replace("2026-09-23T11:00:00Z", "not-an-instant")),
        "effectiveAt");
    assertValidation(
        postJson(
            requestJson("5.1", "USD", "BRL", "MANUAL")
                .replace("}", ",\"createdAt\":\"2026-09-23T12:00:00Z\"}")),
        null);

    var malformed =
        http.getForEntity("/api/v1/exchange-rates/latest?base=usd&quote=BRL", String.class);
    assertProblem(malformed, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    assertThat(malformed.getBody()).contains("\"field\":\"base\"");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM exchange_rate", Integer.class)).isZero();
  }

  @Test
  void openapi_documents_public_contract_without_a_public_instant_parameter() {
    var specification = http.getForEntity("/v3/api-docs", String.class);

    assertThat(specification.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(specification.getBody())
        .contains("\"/api/v1/exchange-rates\"")
        .contains("\"201\"")
        .contains("\"400\"")
        .contains("\"/api/v1/exchange-rates/latest\"")
        .contains("\"404\"")
        .contains("\"name\":\"base\"")
        .contains("\"name\":\"quote\"")
        .doesNotContain("\"name\":\"at\"");
  }

  private org.springframework.http.ResponseEntity<String> post(String rate, String effectiveAt) {
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    var body =
        Map.of(
            "baseCurrency", "USD",
            "quoteCurrency", "BRL",
            "rate", rate,
            "source", "MANUAL",
            "effectiveAt", effectiveAt);
    return http.exchange(
        "/api/v1/exchange-rates", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
  }

  private org.springframework.http.ResponseEntity<String> postJson(String body) {
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return http.exchange(
        "/api/v1/exchange-rates", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
  }

  private String requestJson(String rate, String base, String quote, String source) {
    return "{\"baseCurrency\":\""
        + base
        + "\",\"quoteCurrency\":\""
        + quote
        + "\",\"rate\":"
        + rate
        + ",\"source\":\""
        + source
        + "\",\"effectiveAt\":\"2026-09-23T11:00:00Z\"}";
  }

  private void assertValidation(
      org.springframework.http.ResponseEntity<String> response, String field) {
    assertProblem(response, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    if (field != null) {
      assertThat(response.getBody()).contains("\"field\":\"" + field + "\"");
    }
  }

  private void insertRate(String id, String rate, String effectiveAt, String createdAt) {
    jdbc.update(
        "INSERT INTO exchange_rate VALUES (CAST(? AS uuid),'USD','BRL',?,'TEST',?,?)",
        id,
        new java.math.BigDecimal(rate),
        OffsetDateTime.ofInstant(Instant.parse(effectiveAt), ZoneOffset.UTC),
        OffsetDateTime.ofInstant(Instant.parse(createdAt), ZoneOffset.UTC));
  }

  private void assertLatestRate(String expected) {
    var latest =
        http.getForEntity("/api/v1/exchange-rates/latest?base=USD&quote=BRL", String.class);
    assertThat(latest.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(latest.getBody()).contains("\"rate\":\"" + expected + "\"");
  }

  private void assertProblem(
      org.springframework.http.ResponseEntity<String> response, HttpStatus status, String code) {
    assertThat(response.getStatusCode()).isEqualTo(status);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(response.getBody()).contains("\"code\":\"" + code + "\"");
    assertThat(response.getBody()).doesNotContain("Exception", "SELECT", "org.springframework");
  }

  @TestConfiguration
  static class FixedClock {
    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }
}
